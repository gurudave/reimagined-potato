package app.glimmer.widgets.widget

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import app.glimmer.widgets.data.ShuffleEvery
import app.glimmer.widgets.data.WidgetStore

/** Wakes every 15 minutes and moves on any widget whose shuffle interval has passed. */
class ShuffleWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val now = System.currentTimeMillis()
        for (widgetId in WidgetUpdater.boundIds(applicationContext)) {
            val config = WidgetStore.load(applicationContext, widgetId) ?: continue
            if (config.shuffle == ShuffleEvery.NEVER || config.photos.size < 2) continue
            // A minute of slack so a worker that wakes slightly early still counts.
            val due = config.lastShuffle + (config.shuffle.minutes - 1) * 60_000L
            if (now >= due) {
                WidgetStore.save(applicationContext, widgetId, config.advanced(random = true, now = now))
                WidgetUpdater.update(applicationContext, widgetId)
            }
        }
        return Result.success()
    }

    companion object {
        const val NAME = "glimmer-shuffle"
    }
}
