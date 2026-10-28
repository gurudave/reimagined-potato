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
 * Draws a whole widget (frame, photo, filter, caption, sticker) into a single bitmap.
 * The same code renders the home-screen widget and every preview inside the app, so what
 * she designs is exactly what she gets.
 */
object WidgetRenderer {
    /** Keeps widget bitmaps small enough for the launcher to accept (about 2 MB each). */
    private const val MAX_PIXELS = 520_000

    private const val COCOA = 0xFF5B443F.toInt()
    private const val PLACEHOLDER_TOP = 0xFFFBC9DA.toInt()
    private const val PLACEHOLDER_BOTTOM = 0xFFD5C8FF.toInt()

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

        if (config.sticker.isNotEmpty()) drawSticker(canvas, config.sticker, box, unit, w, h)
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
        if (grain) {
            canvas.drawRect(dst, Paint().apply {
                shader = BitmapShader(grainTile, Shader.TileMode.REPEAT, Shader.TileMode.REPEAT)
            })
        }
    }

    private fun drawPlaceholder(context: Context, canvas: Canvas, rect: RectF, unit: Float) {
        canvas.drawRect(rect, Paint().apply {
            shader = LinearGradient(
                rect.left, rect.top, rect.right, rect.bottom,
                PLACEHOLDER_TOP, PLACEHOLDER_BOTTOM, Shader.TileMode.CLAMP,
            )
        })
        val paint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.WHITE
            textAlign = Paint.Align.CENTER
            typeface = Fonts.get(context, R.font.quicksand_bold)
            textSize = unit * 0.085f
            setShadowLayer(unit * 0.015f, 0f, unit * 0.004f, 0x33000000)
        }
        val lineHeight = paint.textSize * 1.25f
        canvas.drawText("add photos", rect.centerX(), rect.centerY() - lineHeight * 0.15f, paint)
        canvas.drawText("✧", rect.centerX(), rect.centerY() + lineHeight * 0.95f, paint)
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
        val baseline = when (config.shape) {
            FrameShape.HEART -> 0.68f
            FrameShape.CIRCLE, FrameShape.FLOWER -> 0.8f
            else -> 0.88f
        }
        canvas.drawText(text, photoRect.centerX(), photoRect.top + photoRect.height() * baseline, paint)
    }

    private fun drawSticker(canvas: Canvas, sticker: String, box: RectF, unit: Float, w: Int, h: Int) {
        val size = unit * 0.24f
        val paint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            textSize = size
            textAlign = Paint.Align.CENTER
            setShadowLayer(unit * 0.015f, 0f, unit * 0.006f, 0x33000000)
        }
        val cx = (box.right - size * 0.28f).coerceAtMost(w - size * 0.6f)
        val cy = (box.top + size * 0.25f).coerceIn(size * 0.6f, h - size * 0.6f)
        canvas.save()
        canvas.rotate(14f, cx, cy)
        canvas.drawText(sticker, cx, cy - (paint.ascent() + paint.descent()) / 2f, paint)
        canvas.restore()
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

    /** A little pastel sunset that stands in for a photo before she has picked any. */
    private fun sampleScene(size: Int): Bitmap {
        val bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        val s = size.toFloat()
        canvas.drawRect(0f, 0f, s, s, Paint().apply {
            shader = LinearGradient(
                0f, 0f, 0f, s,
                intArrayOf(0xFF7A6FD6.toInt(), 0xFFF28FB0.toInt(), 0xFFFFC98B.toInt()),
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
        canvas.drawPath(hills, Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0xFF5B3F73.toInt() })
        return bitmap
    }
}
