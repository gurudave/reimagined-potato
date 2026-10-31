package app.glimmer.widgets.widget

import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.util.Log
import app.glimmer.widgets.data.WidgetStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

class PhotoWidgetProvider : AppWidgetProvider() {

    override fun onUpdate(context: Context, manager: AppWidgetManager, widgetIds: IntArray) = inBackground {
        widgetIds.forEach { WidgetUpdater.update(context, it) }
    }

    override fun onAppWidgetOptionsChanged(context: Context, manager: AppWidgetManager, widgetId: Int, newOptions: Bundle) =
        inBackground { WidgetUpdater.update(context, widgetId) }

    override fun onDeleted(context: Context, widgetIds: IntArray) = inBackground {
        widgetIds.forEach { WidgetStore.delete(context, it) }
        WidgetUpdater.cleanupPhotos(context)
        WidgetUpdater.syncShuffleSchedule(context)
    }

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != WidgetUpdater.ACTION_NEXT) return super.onReceive(context, intent)
        val widgetId = intent.getIntExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, AppWidgetManager.INVALID_APPWIDGET_ID)
        inBackground {
            val config = WidgetStore.load(context, widgetId) ?: return@inBackground
            WidgetStore.save(context, widgetId, config.advanced())
            WidgetUpdater.update(context, widgetId)
        }
    }

    private fun inBackground(block: suspend () -> Unit) = runAsync(this, block)
}

private val receiverScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

/** Rendering takes a moment, so receivers hand the work to a coroutine and keep the broadcast alive. */
internal fun runAsync(receiver: BroadcastReceiver, block: suspend () -> Unit) {
    val pending = receiver.goAsync()
    receiverScope.launch {
        try {
            block()
        } catch (t: Throwable) {
            Log.w("Djungleskwidgets", "Widget work failed", t)
        } finally {
            pending.finish()
        }
    }
}
