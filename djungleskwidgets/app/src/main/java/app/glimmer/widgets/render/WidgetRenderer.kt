package app.glimmer.widgets.render

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapShader
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RadialGradient
import android.graphics.RectF
import android.graphics.Shader
import android.text.TextPaint
import app.glimmer.widgets.R
import app.glimmer.widgets.data.FrameShape
import app.glimmer.widgets.data.PhotoFilter
import app.glimmer.widgets.data.PhotoRef
import app.glimmer.widgets.data.PhotoStore
import app.glimmer.widgets.data.WidgetConfig
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.math.sqrt
import kotlin.random.Random

/**
 * Draws a whole widget (frame, photo, filter, caption) into a single bitmap.
 * The same code renders the home-screen widget and every preview inside the app, so what
 * she designs is exactly what she gets.
 */
object WidgetRenderer {
    /** Keeps widget bitmaps small enough for the launcher to accept (about 2 MB each). */
    private const val MAX_PIXELS = 520_000

    private const val COCOA = 0xFF3B2A20.toInt()
    /** A dusk sky, purple through magenta to orange, for a widget still waiting for photos. */
    private val PLACEHOLDER_SKY = intArrayOf(0xFF22163F.toInt(), 0xFF8E3A7C.toInt(), 0xFFFF8A3D.toInt())
    private val CABINET_STRIPES = intArrayOf(0xFFFFC233.toInt(), 0xFFFF7A1A.toInt(), 0xFFE8322B.toInt())

    private val grainTile: Bitmap by lazy {
        val size = 160
        val random = Random(7)
        val pixels = IntArray(size * size) {
            val v = if (random.nextBoolean()) 255 else 0
            Color.argb(random.nextInt(0, 21), v, v, v)
        }
        Bitmap.createBitmap(pixels, size, size, Bitmap.Config.ARGB_8888)
    }

    fun render(context: Context, config: WidgetConfig, widthPx: Int, heightPx: Int): Bitmap {
        val (w, h) = fitSize(widthPx, heightPx)
        val bitmap = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        val unit = min(w, h).toFloat()
        val pad = unit * 0.06f
        val area = RectF(pad, pad, w - pad, h - pad)
        val box = tiltSafe(Shapes.box(config.shape, area), area, config.tilt)

        // Draw everything into one layer and fade it as a whole, so overlapping parts
        // (paper, photo, outline, caption) do not show through each other.
        val opacity = config.opacity.coerceIn(WidgetConfig.MIN_OPACITY, 1f)
        val faded = opacity < 1f
        if (faded) canvas.saveLayerAlpha(0f, 0f, w.toFloat(), h.toFloat(), (opacity * 255).roundToInt())

        canvas.save()
        canvas.rotate(config.tilt, box.centerX(), box.centerY())

        val outline = Shapes.outline(config.shape, box)
        val paper = config.frameColor ?: Color.WHITE
        val base = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = paper
            if (config.shadow) setShadowLayer(unit * 0.035f, 0f, unit * 0.014f, 0x40000000)
        }
        canvas.drawPath(outline, base)

        val photoRect = Shapes.photoRect(config.shape, box)
        val photo = config.currentPhoto
        val source = photo?.let {
            PhotoStore.load(context, it.file, (max(photoRect.width(), photoRect.height()) * it.zoom).roundToInt())
        }
        canvas.save()
        canvas.clipPath(Shapes.photoClip(config.shape, box))
        if (photo != null && source != null) {
            drawPhoto(canvas, source, photo, photoRect, config.filter, config.grain)
            if (config.camcorder) drawCamcorderStamp(context, canvas, config.shape, photo, photoRect)
        } else {
            drawPlaceholder(context, canvas, photoRect, unit)
        }
        canvas.restore()

        if (!config.shape.isCard && config.frameColor != null) {
            canvas.drawPath(outline, Paint(Paint.ANTI_ALIAS_FLAG).apply {
                style = Paint.Style.STROKE
                strokeWidth = unit * 0.035f
                color = config.frameColor
            })
        }
        if (config.caption.isNotBlank()) drawCaption(context, canvas, config, box, photoRect, unit)
        canvas.restore()

        if (faded) canvas.restore()
        return bitmap
    }

    /** A small square of one photo with one filter, for the filter picker. */
    fun filterThumb(context: Context, photo: PhotoRef?, filter: PhotoFilter, sizePx: Int): Bitmap {
        val bitmap = Bitmap.createBitmap(sizePx, sizePx, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        val rect = RectF(0f, 0f, sizePx.toFloat(), sizePx.toFloat())
        val source = photo?.let { PhotoStore.load(context, it.file, sizePx) } ?: sampleScene(sizePx)
        drawPhoto(canvas, source, photo ?: PhotoRef(""), rect, filter, grain = false)
        return bitmap
    }

    fun fitSize(widthPx: Int, heightPx: Int): Pair<Int, Int> {
        var w = widthPx.coerceAtLeast(48)
        var h = heightPx.coerceAtLeast(48)
        val pixels = w.toLong() * h
        if (pixels > MAX_PIXELS) {
            val scale = sqrt(MAX_PIXELS.toDouble() / pixels)
            w = (w * scale).toInt()
            h = (h * scale).toInt()
        }
        return w to h
    }

    /** Shrinks [box] just enough that it still fits inside [area] once rotated. */
    private fun tiltSafe(box: RectF, area: RectF, degrees: Float): RectF {
        if (degrees == 0f) return box
        val radians = Math.toRadians(degrees.toDouble())
        val c = abs(cos(radians)).toFloat()
        val s = abs(sin(radians)).toFloat()
        val rotatedW = box.width() * c + box.height() * s
        val rotatedH = box.width() * s + box.height() * c
        val scale = min(1f, min(area.width() / rotatedW, area.height() / rotatedH))
        val halfW = box.width() * scale / 2f
        val halfH = box.height() * scale / 2f
        return RectF(box.centerX() - halfW, box.centerY() - halfH, box.centerX() + halfW, box.centerY() + halfH)
    }

    /** Centre-crops the photo into [dst], honouring the chosen focus point and zoom. */
    private fun drawPhoto(canvas: Canvas, source: Bitmap, photo: PhotoRef, dst: RectF, filter: PhotoFilter, grain: Boolean) {
        val scale = max(dst.width() / source.width, dst.height() / source.height) * photo.zoom.coerceIn(1f, 4f)
        val scaledW = source.width * scale
        val scaledH = source.height * scale
        val offsetX = (photo.focusX * scaledW - dst.width() / 2f).coerceIn(0f, max(0f, scaledW - dst.width()))
        val offsetY = (photo.focusY * scaledH - dst.height() / 2f).coerceIn(0f, max(0f, scaledH - dst.height()))
        val matrix = Matrix().apply {
            setScale(scale, scale)
            postTranslate(dst.left - offsetX, dst.top - offsetY)
        }
        val paint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG).apply {
            colorFilter = Filters.colorFilter(filter)
        }
        canvas.drawBitmap(source, matrix, paint)

        if (Filters.hasVignette(filter)) {
            val radius = max(dst.width(), dst.height()) * 0.75f
            canvas.drawRect(dst, Paint().apply {
                shader = RadialGradient(
                    dst.centerX(), dst.centerY(), radius,
                    intArrayOf(Color.TRANSPARENT, Color.TRANSPARENT, 0x66000000),
                    floatArrayOf(0f, 0.55f, 1f),
                    Shader.TileMode.CLAMP,
                )
            })
        }
        if (Filters.hasScanlines(filter)) drawScanlines(canvas, dst)
        if (grain) {
            canvas.drawRect(dst, Paint().apply {
                shader = BitmapShader(grainTile, Shader.TileMode.REPEAT, Shader.TileMode.REPEAT)
            })
        }
    }

    private fun drawPlaceholder(context: Context, canvas: Canvas, rect: RectF, unit: Float) {
        canvas.drawRect(rect, Paint().apply {
            shader = LinearGradient(
                rect.left, rect.top, rect.left, rect.bottom,
                PLACEHOLDER_SKY, floatArrayOf(0f, 0.6f, 1f), Shader.TileMode.CLAMP,
            )
        })
        drawScanlines(canvas, rect)
        val paint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.WHITE
            textAlign = Paint.Align.CENTER
            typeface = Fonts.get(context, R.font.press_start)
            setShadowLayer(1f, unit * 0.008f, unit * 0.008f, 0xAA000000.toInt())
        }
        fitText(paint, "INSERT PHOTO", rect.width() * 0.78f, unit * 0.07f)
        canvas.drawText("INSERT PHOTO", rect.centerX(), rect.centerY(), paint)
        // Three cabinet stripes underneath.
        val band = unit * 0.014f
        val half = paint.measureText("INSERT PHOTO") / 2f
        CABINET_STRIPES.forEachIndexed { i, stripe ->
            val top = rect.centerY() + paint.textSize * 0.7f + i * band * 1.6f
            canvas.drawRect(rect.centerX() - half, top, rect.centerX() + half, top + band, Paint().apply { color = stripe })
        }
    }

    /** Fine dark lines across [rect], like the rows of a television picture. */
    private fun drawScanlines(canvas: Canvas, rect: RectF) {
        val step = max(3f, rect.height() / 110f)
        val paint = Paint().apply { color = 0x26000000 }
        var y = rect.top
        while (y < rect.bottom) {
            canvas.drawRect(rect.left, y, rect.right, y + step * 0.4f, paint)
            y += step
        }
    }

    /**
     * "PLAY ▶" in one corner and the time and date the photo was taken in the other, in the
     * lettering of a home-video camera. Placed to stay inside each frame's outline.
     */
    private fun drawCamcorderStamp(context: Context, canvas: Canvas, shape: FrameShape, photo: PhotoRef, r: RectF) {
        val side = min(r.width(), r.height())
        val paint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.WHITE
            typeface = Fonts.get(context, R.font.vt323)
            textSize = side * 0.1f
            setShadowLayer(1f, textSize * 0.06f, textSize * 0.06f, 0xB0000000.toInt())
        }
        val line = paint.textSize * 0.92f
        val inset = side * 0.075f
        val centred = shape == FrameShape.CIRCLE || shape == FrameShape.FLOWER || shape == FrameShape.HEART
        // Where the top of "PLAY" and the foot of the date go.
        val (playTop, dateBottom) = when (shape) {
            FrameShape.CIRCLE, FrameShape.FLOWER -> r.top + r.height() * 0.13f to r.top + r.height() * 0.88f
            FrameShape.HEART -> r.top + r.height() * 0.27f to r.top + r.height() * 0.76f
            FrameShape.ARCH -> r.top + r.width() * 0.3f to r.bottom - inset
            else -> r.top + inset to r.bottom - inset
        }
        val left = if (shape == FrameShape.ARCH) r.left + r.width() * 0.12f else r.left + inset

        fun draw(text: String, baseline: Float, extra: Float = 0f): Float {
            val width = paint.measureText(text) + extra
            val x = if (centred) r.centerX() - width / 2f else left
            canvas.drawText(text, x, baseline, paint)
            return x + paint.measureText(text)
        }

        // VT323 has no ▶, so the triangle is drawn to match the lettering's capital height.
        val cap = paint.textSize * 0.56f
        val gap = paint.textSize * 0.25f
        val playBaseline = playTop + cap
        val end = draw("PLAY", playBaseline, extra = gap + cap * 0.9f)
        val triangle = Path().apply {
            moveTo(end + gap, playBaseline - cap)
            lineTo(end + gap + cap * 0.9f, playBaseline - cap / 2f)
            lineTo(end + gap, playBaseline)
            close()
        }
        canvas.drawPath(triangle, paint)

        val taken = photo.taken ?: PhotoStore.addedAt(context, photo.file) ?: return
        val twentyFour = android.text.format.DateFormat.is24HourFormat(context)
        draw(Camcorder.date(taken), dateBottom)
        draw(Camcorder.time(taken, twentyFour), dateBottom - line)
    }

    private fun drawCaption(context: Context, canvas: Canvas, config: WidgetConfig, box: RectF, photoRect: RectF, unit: Float) {
        val text = config.caption.trim()
        val paint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            typeface = Fonts.get(context, config.font.res)
            textAlign = Paint.Align.CENTER
        }
        if (config.shape == FrameShape.POLAROID) {
            val strip = RectF(box.left, photoRect.bottom, box.right, box.bottom)
            paint.color = config.captionColor ?: inkFor(config.frameColor ?: Color.WHITE)
            fitText(paint, text, strip.width() * 0.86f, strip.height() * 0.5f)
            canvas.drawText(text, strip.centerX(), strip.centerY() - (paint.ascent() + paint.descent()) / 2f, paint)
            return
        }
        paint.color = config.captionColor ?: Color.WHITE
        paint.setShadowLayer(unit * 0.03f, 0f, unit * 0.008f, 0x99000000.toInt())
        val narrow = config.shape == FrameShape.CIRCLE || config.shape == FrameShape.HEART || config.shape == FrameShape.FLOWER
        fitText(paint, text, photoRect.width() * if (narrow) 0.62f else 0.84f, unit * 0.12f)
        // With the camcorder stamp in the bottom corner, the caption moves up out of its way.
        val baseline = when (config.shape) {
            FrameShape.HEART -> if (config.camcorder) 0.5f else 0.68f
            FrameShape.CIRCLE, FrameShape.FLOWER -> if (config.camcorder) 0.6f else 0.8f
            else -> if (config.camcorder) 0.66f else 0.88f
        }
        canvas.drawText(text, photoRect.centerX(), photoRect.top + photoRect.height() * baseline, paint)
    }

    private fun fitText(paint: TextPaint, text: String, maxWidth: Float, startSize: Float) {
        paint.textSize = startSize
        while (paint.textSize > 8f && paint.measureText(text) > maxWidth) paint.textSize *= 0.92f
    }

    /** Dark cocoa writing on light paper, white on dark paper. */
    private fun inkFor(paper: Int): Int {
        val luminance = (0.299 * Color.red(paper) + 0.587 * Color.green(paper) + 0.114 * Color.blue(paper)) / 255
        return if (luminance > 0.55) COCOA else Color.WHITE
    }

    /** A little dusk scene that stands in for a photo before she has picked any. */
    private fun sampleScene(size: Int): Bitmap {
        val bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        val s = size.toFloat()
        canvas.drawRect(0f, 0f, s, s, Paint().apply {
            shader = LinearGradient(
                0f, 0f, 0f, s,
                intArrayOf(0xFF2A1B4E.toInt(), 0xFFD9507A.toInt(), 0xFFFFB259.toInt()),
                floatArrayOf(0f, 0.55f, 1f),
                Shader.TileMode.CLAMP,
            )
        })
        canvas.drawCircle(s * 0.62f, s * 0.6f, s * 0.16f, Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0xFFFFF0C2.toInt() })
        val hills = Path().apply {
            moveTo(0f, s * 0.78f)
            quadTo(s * 0.3f, s * 0.6f, s * 0.6f, s * 0.8f)
            quadTo(s * 0.8f, s * 0.9f, s, s * 0.72f)
            lineTo(s, s)
            lineTo(0f, s)
            close()
        }
        canvas.drawPath(hills, Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0xFF3A2150.toInt() })
        return bitmap
    }
}
