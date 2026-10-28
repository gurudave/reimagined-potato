package app.glimmer.widgets.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.res.Configuration
import android.os.Build
import android.util.Log
import android.widget.RemoteViews
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import app.glimmer.widgets.MainActivity
import app.glimmer.widgets.R
import app.glimmer.widgets.data.PhotoStore
import app.glimmer.widgets.data.ShuffleEvery
import app.glimmer.widgets.data.TapAction
import app.glimmer.widgets.data.WidgetConfig
import app.glimmer.widgets.data.WidgetStore
import app.glimmer.widgets.render.WidgetRenderer
import java.util.concurrent.TimeUnit
import kotlin.math.roundToInt

object WidgetUpdater {
    private const val TAG = "WidgetUpdater"
    const val ACTION_NEXT = "app.glimmer.widgets.action.NEXT"

    fun provider(context: Context) = ComponentName(context, PhotoWidgetProvider::class.java)

    fun boundIds(context: Context): IntArray =
        AppWidgetManager.getInstance(context).getAppWidgetIds(provider(context))

    /** The widget's current size on the home screen, in pixels. */
    fun sizePx(context: Context, widgetId: Int): Pair<Int, Int> {
        val options = AppWidgetManager.getInstance(context).getAppWidgetOptions(widgetId)
        val portrait = context.resources.configuration.orientation != Configuration.ORIENTATION_LANDSCAPE
        var widthDp = options.getInt(
            if (portrait) AppWidgetManager.OPTION_APPWIDGET_MIN_WIDTH else AppWidgetManager.OPTION_APPWIDGET_MAX_WIDTH
        )
        var heightDp = options.getInt(
            if (portrait) AppWidgetManager.OPTION_APPWIDGET_MAX_HEIGHT else AppWidgetManager.OPTION_APPWIDGET_MIN_HEIGHT
        )
        if (widthDp <= 0 || heightDp <= 0) {
            widthDp = 160
            heightDp = 160
        }
        val density = context.resources.displayMetrics.density
        return (widthDp * density).roundToInt() to (heightDp * density).roundToInt()
    }

    /** Re-renders one widget from its saved design. Safe to call from a background thread. */
    fun update(context: Context, widgetId: Int) {
        val config = WidgetStore.load(context, widgetId)
        val (w, h) = sizePx(context, widgetId)
        val views = RemoteViews(context.packageName, R.layout.widget_photo)
        views.setImageViewBitmap(R.id.widget_image, WidgetRenderer.render(context, config ?: WidgetConfig(), w, h))
        views.setContentDescription(
            R.id.widget_image,
            config?.caption?.takeIf { it.isNotBlank() } ?: context.getString(R.string.widget_photo_desc),
        )
        val tap = when {
            config == null || config.photos.isEmpty() -> openEditor(context, widgetId)
            config.tapAction == TapAction.NEXT_PHOTO && config.photos.size > 1 -> nextPhoto(context, widgetId)
            config.tapAction == TapAction.NOTHING -> null
            else -> openEditor(context, widgetId)
        }
        if (tap != null) views.setOnClickPendingIntent(R.id.widget_root, tap)
        runCatching { AppWidgetManager.getInstance(context).updateAppWidget(widgetId, views) }
            .onFailure { Log.w(TAG, "Could not update widget $widgetId", it) }
    }

    fun updateAll(context: Context) = boundIds(context).forEach { update(context, it) }

    /** Saves a design, paints it onto the home screen and starts or stops the shuffle timer. */
    fun save(context: Context, widgetId: Int, config: WidgetConfig) {
        WidgetStore.save(context, widgetId, config.copy(lastShuffle = System.currentTimeMillis()))
        update(context, widgetId)
        syncShuffleSchedule(context)
    }

    /**
     * Asks the launcher to drop a new widget on the home screen. The design waits as a draft
     * until [PinReceiver] learns the new widget's id. Returns false if the launcher can't do it.
     */
    fun requestPin(context: Context, draft: WidgetConfig): Boolean {
        val manager = AppWidgetManager.getInstance(context)
        if (!manager.isRequestPinAppWidgetSupported) return false
        WidgetStore.savePendingDraft(context, draft)
        // Mutable so the launcher can add the new widget's id to the callback.
        val mutable = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) PendingIntent.FLAG_MUTABLE else 0
        val callback = PendingIntent.getBroadcast(
            context, 0, Intent(context, PinReceiver::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or mutable,
        )
        return manager.requestPinAppWidget(provider(context), null, callback)
    }

    /** Runs the shuffle worker only while at least one widget wants shuffling. */
    fun syncShuffleSchedule(context: Context) {
        val work = WorkManager.getInstance(context)
        val wanted = boundIds(context).any { id ->
            WidgetStore.load(context, id)?.let { it.shuffle != ShuffleEvery.NEVER && it.photos.size > 1 } == true
        }
        if (wanted) {
            work.enqueueUniquePeriodicWork(
                ShuffleWorker.NAME,
                ExistingPeriodicWorkPolicy.KEEP,
                PeriodicWorkRequestBuilder<ShuffleWorker>(15, TimeUnit.MINUTES).build(),
            )
        } else {
            work.cancelUniqueWork(ShuffleWorker.NAME)
        }
    }

    fun cleanupPhotos(context: Context) {
        PhotoStore.cleanup(context, WidgetStore.referencedPhotos(context))
    }

    private fun nextPhoto(context: Context, widgetId: Int): PendingIntent = PendingIntent.getBroadcast(
        context,
        widgetId,
        Intent(context, PhotoWidgetProvider::class.java)
            .setAction(ACTION_NEXT)
            .putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, widgetId),
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
    )

    private fun openEditor(context: Context, widgetId: Int): PendingIntent = PendingIntent.getActivity(
        context,
        widgetId,
        Intent(context, MainActivity::class.java)
            .putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, widgetId)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP),
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
    )
}
