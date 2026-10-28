package app.glimmer.widgets.data

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import org.json.JSONObject

/** Widget designs, keyed by the launcher's widget id, kept as JSON in SharedPreferences. */
object WidgetStore {
    private const val PREFS = "glimmer_widgets"
    private const val KEY_PREFIX = "widget_"
    private const val KEY_DRAFT = "pending_draft"
    private const val KEY_DRAFT_AT = "pending_draft_at"
    private const val DRAFT_TTL_MS = 30 * 60 * 1000L

    private val _changes = MutableStateFlow(0)

    /** Ticks whenever a design is saved or removed, so open screens can refresh. */
    val changes: StateFlow<Int> = _changes

    private fun prefs(context: Context) =
        context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    fun load(context: Context, widgetId: Int): WidgetConfig? =
        prefs(context).getString(KEY_PREFIX + widgetId, null)?.let(::parse)

    fun save(context: Context, widgetId: Int, config: WidgetConfig) {
        prefs(context).edit().putString(KEY_PREFIX + widgetId, config.toJson().toString()).apply()
        _changes.update { it + 1 }
    }

    fun delete(context: Context, widgetId: Int) {
        prefs(context).edit().remove(KEY_PREFIX + widgetId).apply()
        _changes.update { it + 1 }
    }

    /**
     * A design made in the app before the launcher has handed out a widget id. It is picked up
     * by the pin callback, or by the configure screen if she adds the widget by hand instead.
     */
    fun savePendingDraft(context: Context, config: WidgetConfig) {
        prefs(context).edit()
            .putString(KEY_DRAFT, config.toJson().toString())
            .putLong(KEY_DRAFT_AT, System.currentTimeMillis())
            .apply()
    }

    fun peekPendingDraft(context: Context): WidgetConfig? {
        val prefs = prefs(context)
        val age = System.currentTimeMillis() - prefs.getLong(KEY_DRAFT_AT, 0L)
        if (age > DRAFT_TTL_MS) return null
        return prefs.getString(KEY_DRAFT, null)?.let(::parse)
    }

    fun takePendingDraft(context: Context): WidgetConfig? =
        peekPendingDraft(context).also { clearPendingDraft(context) }

    fun clearPendingDraft(context: Context) {
        prefs(context).edit().remove(KEY_DRAFT).remove(KEY_DRAFT_AT).apply()
    }

    /** Every photo file some design still points at, including an unclaimed draft. */
    fun referencedPhotos(context: Context): Set<String> {
        val prefs = prefs(context)
        val configs = prefs.all.mapNotNull { (key, value) ->
            if ((key.startsWith(KEY_PREFIX) || key == KEY_DRAFT) && value is String) parse(value) else null
        }
        return configs.flatMap { config -> config.photos.map { it.file } }.toSet()
    }

    private fun parse(json: String): WidgetConfig? =
        runCatching { WidgetConfig.fromJson(JSONObject(json)) }.getOrNull()
}
