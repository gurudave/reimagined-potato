package app.glimmer.widgets.data

import androidx.annotation.FontRes
import app.glimmer.widgets.R
import org.json.JSONArray
import org.json.JSONObject
import kotlin.random.Random

enum class FrameShape(val label: String) {
    SOFT("soft"),
    CIRCLE("circle"),
    ARCH("arch"),
    HEART("heart"),
    FLOWER("flower"),
    POLAROID("polaroid"),
    STAMP("stamp");

    /** Card styles draw a paper frame around the photo instead of clipping it to a shape. */
    val isCard: Boolean get() = this == POLAROID || this == STAMP
}

enum class PhotoFilter(val label: String) {
    NONE("original"),
    DREAMY("dreamy"),
    VINTAGE("vintage"),
    GOLDEN("golden hour"),
    FROST("frost"),
    NOIR("noir"),
    POP("pop"),
}

enum class CaptionFont(val label: String, @FontRes val res: Int) {
    HANDWRITTEN("handwritten", R.font.caveat_semibold),
    SCRIPT("script", R.font.pacifico),
    CLASSY("classy", R.font.playfair_italic),
    BUBBLY("bubbly", R.font.cherry_bomb),
    TYPEWRITER("typewriter", R.font.space_mono),
}

enum class ShuffleEvery(val label: String, val minutes: Long) {
    NEVER("never", 0),
    QUARTER_HOUR("15 min", 15),
    HOURLY("hourly", 60),
    SIX_HOURS("6 hours", 360),
    DAILY("daily", 1440),
}

enum class TapAction(val label: String) {
    NEXT_PHOTO("next photo"),
    OPEN_APP("open djungleskwidgets"),
    NOTHING("nothing"),
}

data class Swatch(val name: String, val argb: Int)

val Swatches = listOf(
    Swatch("white", 0xFFFFFFFF.toInt()),
    Swatch("cream", 0xFFFFF5E1.toInt()),
    Swatch("blush", 0xFFF9C9D6.toInt()),
    Swatch("strawberry", 0xFFEF7EA4.toInt()),
    Swatch("lilac", 0xFFD8CCFF.toInt()),
    Swatch("sage", 0xFFC5DBB2.toInt()),
    Swatch("sky", 0xFFC4DEF7.toInt()),
    Swatch("butter", 0xFFFFE59C.toInt()),
    Swatch("cocoa", 0xFF6E4C45.toInt()),
    Swatch("ink", 0xFF1F1B26.toInt()),
)

/** One photo plus how it is framed: the point to keep centred and how far to zoom in. */
data class PhotoRef(
    val file: String,
    val focusX: Float = 0.5f,
    val focusY: Float = 0.5f,
    val zoom: Float = 1f,
)

data class WidgetConfig(
    val photos: List<PhotoRef> = emptyList(),
    val shape: FrameShape = FrameShape.SOFT,
    val filter: PhotoFilter = PhotoFilter.NONE,
    /** Outline colour for shapes, paper colour for cards; null means no outline / white paper. */
    val frameColor: Int? = null,
    val caption: String = "",
    val font: CaptionFont = CaptionFont.HANDWRITTEN,
    /** Null picks a readable colour automatically. */
    val captionColor: Int? = null,
    val tilt: Float = 0f,
    val grain: Boolean = false,
    val shadow: Boolean = true,
    val shuffle: ShuffleEvery = ShuffleEvery.NEVER,
    val tapAction: TapAction = TapAction.NEXT_PHOTO,
    val index: Int = 0,
    val lastShuffle: Long = 0L,
) {
    val currentPhoto: PhotoRef?
        get() = if (photos.isEmpty()) null else photos[index.mod(photos.size)]

    /** Moves to the next photo, or to a random different one when [random] is set. */
    fun advanced(random: Boolean = false, now: Long = System.currentTimeMillis()): WidgetConfig {
        if (photos.size < 2) return copy(lastShuffle = now)
        val next = if (random && photos.size > 2) {
            (photos.indices - index.mod(photos.size)).random()
        } else {
            (index + 1).mod(photos.size)
        }
        return copy(index = next, lastShuffle = now)
    }

    fun withPhoto(position: Int, photo: PhotoRef): WidgetConfig =
        copy(photos = photos.mapIndexed { i, p -> if (i == position) photo else p })

    /** A random look for the "surprise me" button; photos and caption text are left alone. */
    fun surprise(random: Random = Random): WidgetConfig = copy(
        shape = FrameShape.entries.random(random),
        filter = PhotoFilter.entries.random(random),
        frameColor = (listOf<Int?>(null) + Swatches.map { it.argb }).random(random),
        font = CaptionFont.entries.random(random),
        tilt = listOf(0f, -4f, 4f).random(random),
    )

    fun toJson(): JSONObject = JSONObject().apply {
        put("photos", JSONArray().apply {
            photos.forEach {
                put(
                    JSONObject()
                        .put("file", it.file)
                        .put("fx", it.focusX.toDouble())
                        .put("fy", it.focusY.toDouble())
                        .put("zoom", it.zoom.toDouble())
                )
            }
        })
        put("shape", shape.name)
        put("filter", filter.name)
        frameColor?.let { put("frameColor", it) }
        put("caption", caption)
        put("font", font.name)
        captionColor?.let { put("captionColor", it) }
        put("tilt", tilt.toDouble())
        put("grain", grain)
        put("shadow", shadow)
        put("shuffle", shuffle.name)
        put("tap", tapAction.name)
        put("index", index)
        put("lastShuffle", lastShuffle)
    }

    companion object {
        fun fromJson(o: JSONObject): WidgetConfig {
            val photos = o.optJSONArray("photos")?.let { arr ->
                (0 until arr.length()).mapNotNull { i ->
                    arr.optJSONObject(i)?.let { p ->
                        PhotoRef(
                            file = p.getString("file"),
                            focusX = p.optDouble("fx", 0.5).toFloat(),
                            focusY = p.optDouble("fy", 0.5).toFloat(),
                            zoom = p.optDouble("zoom", 1.0).toFloat(),
                        )
                    }
                }
            } ?: emptyList()
            return WidgetConfig(
                photos = photos,
                shape = enumOr(o.optString("shape"), FrameShape.SOFT),
                filter = enumOr(o.optString("filter"), PhotoFilter.NONE),
                frameColor = if (o.has("frameColor")) o.getInt("frameColor") else null,
                caption = o.optString("caption", ""),
                font = enumOr(o.optString("font"), CaptionFont.HANDWRITTEN),
                captionColor = if (o.has("captionColor")) o.getInt("captionColor") else null,
                tilt = o.optDouble("tilt", 0.0).toFloat(),
                grain = o.optBoolean("grain", false),
                shadow = o.optBoolean("shadow", true),
                shuffle = enumOr(o.optString("shuffle"), ShuffleEvery.NEVER),
                tapAction = enumOr(o.optString("tap"), TapAction.NEXT_PHOTO),
                index = o.optInt("index", 0),
                lastShuffle = o.optLong("lastShuffle", 0L),
            )
        }
    }
}

private inline fun <reified T : Enum<T>> enumOr(name: String?, fallback: T): T =
    enumValues<T>().firstOrNull { it.name == name } ?: fallback
