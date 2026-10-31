package app.glimmer.widgets.render

import android.content.Context
import android.graphics.Typeface
import androidx.annotation.FontRes
import androidx.core.content.res.ResourcesCompat
import java.util.concurrent.ConcurrentHashMap

object Fonts {
    private val cache = ConcurrentHashMap<Int, Typeface>()

    fun get(context: Context, @FontRes res: Int): Typeface = cache.getOrPut(res) {
        runCatching { ResourcesCompat.getFont(context.applicationContext, res) }.getOrNull() ?: Typeface.DEFAULT
    }
}
