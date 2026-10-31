package app.glimmer.widgets.widget

import android.appwidget.AppWidgetManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import app.glimmer.widgets.data.WidgetStore

/** Told by the launcher which id a widget pinned from inside the app was given. */
class PinReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val widgetId = intent.getIntExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, AppWidgetManager.INVALID_APPWIDGET_ID)
        if (widgetId == AppWidgetManager.INVALID_APPWIDGET_ID) return
        runAsync(this) {
            // Some launchers open the configure screen too; if she has already saved there, keep that.
            val draft = WidgetStore.takePendingDraft(context)
            if (draft != null && WidgetStore.load(context, widgetId) == null) {
                WidgetUpdater.save(context, widgetId, draft)
            }
        }
    }
}
