package app.glimmer.widgets.data

import android.content.Context
import app.glimmer.widgets.ui.theme.Vibe

object AppSettings {
    /** The pastel vibes from before the retro look, each tuned to its nearest new channel. */
    private val retired = mapOf(
        "STRAWBERRY" to Vibe.ROLLER_RINK,
        "MATCHA" to Vibe.SUNDAY_DRIVE,
        "LAVENDER" to Vibe.HIGH_SCORE,
        "CLOUD" to Vibe.CAMCORDER,
        "BUTTER" to Vibe.RECORD_SHOP,
        "MIDNIGHT" to Vibe.ARCADE,
    )

    private fun prefs(context: Context) =
        context.applicationContext.getSharedPreferences("glimmer_settings", Context.MODE_PRIVATE)

    fun vibe(context: Context): Vibe {
        val saved = prefs(context).getString("vibe", null)
        return Vibe.entries.firstOrNull { it.name == saved } ?: retired[saved] ?: Vibe.ARCADE
    }

    fun setVibe(context: Context, vibe: Vibe) {
        prefs(context).edit().putString("vibe", vibe.name).apply()
    }
}
