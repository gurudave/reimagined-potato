package app.glimmer.widgets

import android.appwidget.AppWidgetManager
import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import app.glimmer.widgets.data.AppSettings
import app.glimmer.widgets.data.WidgetConfig
import app.glimmer.widgets.data.WidgetStore
import app.glimmer.widgets.ui.EditorScreen
import app.glimmer.widgets.ui.theme.GlimmerTheme
import app.glimmer.widgets.widget.WidgetUpdater
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Opened by the launcher when she adds a widget from the widget picker, and again on Android 12+
 * when she long-presses a widget and chooses to reconfigure it.
 */
class ConfigureActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val widgetId = intent?.getIntExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, AppWidgetManager.INVALID_APPWIDGET_ID)
            ?: AppWidgetManager.INVALID_APPWIDGET_ID
        // Backing out without saving must cancel, or the launcher keeps an empty widget.
        setResult(RESULT_CANCELED, resultFor(widgetId))
        if (widgetId == AppWidgetManager.INVALID_APPWIDGET_ID) {
            finish()
            return
        }

        val existing = WidgetStore.load(this, widgetId)
        val initial = existing ?: WidgetStore.peekPendingDraft(this) ?: WidgetConfig()
        val (w, h) = WidgetUpdater.sizePx(this, widgetId)

        setContent {
            val vibe = remember { AppSettings.vibe(this) }
            val scope = rememberCoroutineScope()
            GlimmerTheme(vibe) {
                EditorScreen(
                    title = if (existing == null) "new widget" else "edit widget",
                    initial = initial,
                    previewAspect = w.toFloat() / h,
                    saveLabel = "save widget ✧",
                    onSave = { design ->
                        scope.launch {
                            withContext(Dispatchers.Default) {
                                WidgetUpdater.save(this@ConfigureActivity, widgetId, design)
                                if (existing == null) WidgetStore.clearPendingDraft(this@ConfigureActivity)
                            }
                            setResult(RESULT_OK, resultFor(widgetId))
                            finish()
                        }
                    },
                    onClose = { finish() },
                )
            }
        }
    }

    private fun resultFor(widgetId: Int) = Intent().putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, widgetId)
}
