package app.glimmer.widgets

import android.appwidget.AppWidgetManager
import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.lifecycleScope
import app.glimmer.widgets.ui.GlimmerApp
import app.glimmer.widgets.widget.WidgetUpdater
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {
    /** Set when a widget was tapped with "open glimmer", so the app jumps straight to its editor. */
    private var requestedWidget by mutableStateOf<Int?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (savedInstanceState == null) requestedWidget = widgetIdFrom(intent)
        setContent {
            GlimmerApp(requestedWidget = requestedWidget, onRequestHandled = { requestedWidget = null })
        }
        lifecycleScope.launch(Dispatchers.IO) { WidgetUpdater.cleanupPhotos(applicationContext) }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        widgetIdFrom(intent)?.let { requestedWidget = it }
    }

    private fun widgetIdFrom(intent: Intent?): Int? =
        intent?.getIntExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, AppWidgetManager.INVALID_APPWIDGET_ID)
            ?.takeIf { it != AppWidgetManager.INVALID_APPWIDGET_ID }
}
