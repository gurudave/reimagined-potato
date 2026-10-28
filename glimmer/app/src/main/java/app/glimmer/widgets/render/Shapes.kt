package app.glimmer.widgets.render

import android.graphics.Path
import android.graphics.RectF
import app.glimmer.widgets.data.FrameShape
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sin

/** Outlines for every frame. Shared by the widget renderer and the app's shape picker. */
object Shapes {

    /** The part of [area] the shape occupies, keeping each shape's natural proportions. */
    fun box(shape: FrameShape, area: RectF): RectF = when (shape) {
        FrameShape.SOFT -> RectF(area)
        FrameShape.CIRCLE, FrameShape.HEART, FrameShape.FLOWER -> fit(area, 1f)
        FrameShape.ARCH -> fit(area, (area.width() / area.height()).coerceIn(0.55f, 0.8f))
        FrameShape.POLAROID -> fit(area, (area.width() / area.height()).coerceIn(0.72f, 1.2f))
        FrameShape.STAMP -> fit(area, (area.width() / area.height()).coerceIn(0.65f, 1.5f))
    }

    /** The silhouette of the whole frame, used for the shadow, the paper and the outline. */
    fun outline(shape: FrameShape, box: RectF): Path = when (shape) {
        FrameShape.SOFT -> roundRect(box, min(box.width(), box.height()) * 0.14f)
        FrameShape.CIRCLE -> Path().apply { addOval(box, Path.Direction.CW) }
        FrameShape.ARCH -> arch(box)
        FrameShape.HEART -> heart(box)
        FrameShape.FLOWER -> flower(box)
        FrameShape.POLAROID -> roundRect(box, box.width() * 0.025f)
        FrameShape.STAMP -> stamp(box)
    }

    /** Where the photo sits: the whole frame for clip shapes, an inset window for cards. */
    fun photoRect(shape: FrameShape, box: RectF): RectF = when (shape) {
        FrameShape.POLAROID -> {
            val margin = box.width() * 0.065f
            RectF(box.left + margin, box.top + margin, box.right - margin, box.bottom - box.height() * 0.2f)
        }
        FrameShape.STAMP -> {
            val margin = min(box.width(), box.height()) * 0.1f
            RectF(box.left + margin, box.top + margin, box.right - margin, box.bottom - margin)
        }
        else -> RectF(box)
    }

    fun photoClip(shape: FrameShape, box: RectF): Path = when (shape) {
        FrameShape.POLAROID -> roundRect(photoRect(shape, box), box.width() * 0.01f)
        FrameShape.STAMP -> Path().apply { addRect(photoRect(shape, box), Path.Direction.CW) }
        else -> outline(shape, box)
    }

    private fun fit(area: RectF, aspect: Float): RectF {
        val w = min(area.width(), area.height() * aspect)
        val h = w / aspect
        val cx = area.centerX()
        val cy = area.centerY()
        return RectF(cx - w / 2f, cy - h / 2f, cx + w / 2f, cy + h / 2f)
    }

    private fun roundRect(box: RectF, radius: Float) =
        Path().apply { addRoundRect(box, radius, radius, Path.Direction.CW) }

    private fun arch(b: RectF): Path {
        val corner = b.width() * 0.07f
        return Path().apply {
            moveTo(b.left, b.top + b.width() / 2f)
            arcTo(RectF(b.left, b.top, b.right, b.top + b.width()), 180f, 180f, false)
            lineTo(b.right, b.bottom - corner)
            quadTo(b.right, b.bottom, b.right - corner, b.bottom)
            lineTo(b.left + corner, b.bottom)
            quadTo(b.left, b.bottom, b.left, b.bottom - corner)
            close()
        }
    }

    private fun heart(b: RectF): Path {
        fun x(u: Float) = b.left + u * b.width()
        fun y(v: Float) = b.top + (v + 0.015f) * b.height()
        return Path().apply {
            moveTo(x(0.5f), y(0.95f))
            cubicTo(x(0.18f), y(0.72f), x(0f), y(0.52f), x(0f), y(0.30f))
            cubicTo(x(0f), y(0.12f), x(0.14f), y(0.02f), x(0.29f), y(0.02f))
            cubicTo(x(0.39f), y(0.02f), x(0.46f), y(0.08f), x(0.5f), y(0.16f))
            cubicTo(x(0.54f), y(0.08f), x(0.61f), y(0.02f), x(0.71f), y(0.02f))
            cubicTo(x(0.86f), y(0.02f), x(1f), y(0.12f), x(1f), y(0.30f))
            cubicTo(x(1f), y(0.52f), x(0.82f), y(0.72f), x(0.5f), y(0.95f))
            close()
        }
    }

    private fun flower(b: RectF, petals: Int = 8): Path {
        val cx = b.centerX()
        val cy = b.centerY()
        val radius = min(b.width(), b.height()) / 2f
        val petal = radius * 0.34f
        val ring = radius - petal
        val path = Path().apply { addCircle(cx, cy, ring + petal * 0.35f, Path.Direction.CW) }
        for (i in 0 until petals) {
            val angle = 2.0 * PI * i / petals - PI / 2.0
            val circle = Path().apply {
                addCircle(cx + ring * cos(angle).toFloat(), cy + ring * sin(angle).toFloat(), petal, Path.Direction.CW)
            }
            path.op(circle, Path.Op.UNION)
        }
        return path
    }

    /** A postage stamp: a rectangle with perforations nibbled out of every edge. */
    private fun stamp(b: RectF): Path {
        val hole = min(b.width(), b.height()) * 0.035f
        val step = hole * 2.9f
        val holes = Path()
        fun edge(x0: Float, y0: Float, x1: Float, y1: Float) {
            val count = max(2, (hypot(x1 - x0, y1 - y0) / step).roundToInt())
            for (i in 0..count) {
                val t = i / count.toFloat()
                holes.addCircle(x0 + (x1 - x0) * t, y0 + (y1 - y0) * t, hole, Path.Direction.CW)
            }
        }
        edge(b.left, b.top, b.right, b.top)
        edge(b.right, b.top, b.right, b.bottom)
        edge(b.left, b.bottom, b.right, b.bottom)
        edge(b.left, b.top, b.left, b.bottom)
        return Path().apply {
            addRect(b, Path.Direction.CW)
            op(holes, Path.Op.DIFFERENCE)
        }
    }
}
