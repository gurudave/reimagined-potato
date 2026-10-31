package app.glimmer.widgets.data

import android.content.Context
import app.glimmer.widgets.ui.theme.Vibe

object AppSettings {
    private fun prefs(context: Context) =
        context.applicationContext.getSharedPreferences("glimmer_settings", Context.MODE_PRIVATE)

    fun vibe(context: Context): Vibe =
        Vibe.entries.firstOrNull { it.name == prefs(context).getString("vibe", null) } ?: Vibe.STRAWBERRY

    fun setVibe(context: Context, vibe: Vibe) {
        prefs(context).edit().putString("vibe", vibe.name).apply()
    }
}
