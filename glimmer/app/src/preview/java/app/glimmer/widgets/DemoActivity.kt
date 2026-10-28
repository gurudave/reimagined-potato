package app.glimmer.widgets

import android.appwidget.AppWidgetManager
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RadialGradient
import android.graphics.RectF
import android.graphics.Shader
import android.os.Bundle
import android.util.Log
import android.widget.FrameLayout
import android.widget.RemoteViews
import androidx.activity.ComponentActivity
import androidx.lifecycle.lifecycleScope
import app.glimmer.widgets.data.CaptionFont
import app.glimmer.widgets.data.FrameShape
import app.glimmer.widgets.data.PhotoFilter
import app.glimmer.widgets.data.PhotoRef
import app.glimmer.widgets.data.PhotoStore
import app.glimmer.widgets.data.WidgetConfig
import app.glimmer.widgets.data.WidgetStore
import app.glimmer.widgets.render.WidgetRenderer
import app.glimmer.widgets.widget.WidgetUpdater
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import kotlin.random.Random

/**
 * Drives the app for CI screenshots:
 *   mode=gallery  render every frame and filter into PNG contact sheets
 *   mode=pin      ask the launcher to pin one of the demo designs (extra "style")
 *   mode=edit     open the editor on a demo design
 */
class DemoActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val mode = intent.getStringExtra("mode") ?: "gallery"
        lifecycleScope.launch {
            val photos = withContext(Dispatchers.Default) { samplePhotos(this@DemoActivity) }
            when (mode) {
                "gallery" -> {
                    withContext(Dispatchers.Default) { renderGallery(this@DemoActivity, photos) }
                    finish()
                }
                "pin" -> {
                    val ok = WidgetUpdater.requestPin(this@DemoActivity, design(intent.getIntExtra("style", 0), photos))
                    Log.i(TAG, "requestPin -> $ok")
                    // Stay up long enough for the launcher to show its dialog, then get out of the way.
                    delay(1500)
                    finish()
                }
                "edit" -> {
                    WidgetStore.save(this@DemoActivity, DEMO_ID, design(1, photos))
                    startActivity(
                        Intent(this@DemoActivity, MainActivity::class.java)
                            .putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, DEMO_ID)
                    )
                    finish()
                }
            }
        }
    }

    private companion object {
        const val TAG = "GlimmerDemo"
        const val DEMO_ID = 9999

        fun design(style: Int, photos: List<String>): WidgetConfig {
            fun pics(vararg order: Int) = order.map { PhotoRef(photos[it % photos.size]) }
            return when (style % 7) {
                0 -> WidgetConfig(
                    photos = pics(0, 1, 2), shape = FrameShape.POLAROID, filter = PhotoFilter.DREAMY,
                    caption = "besties ♡", font = CaptionFont.HANDWRITTEN, sticker = "🎀", tilt = -4f,
                    frameColor = 0xFFFFF5E1.toInt(),
                )
                1 -> WidgetConfig(
                    photos = pics(1, 0), shape = FrameShape.HEART, filter = PhotoFilter.POP,
                    sticker = "✨", frameColor = 0xFFF9C9D6.toInt(),
                )
                2 -> WidgetConfig(
                    photos = pics(2, 3), shape = FrameShape.ARCH, filter = PhotoFilter.VINTAGE, grain = true,
                    caption = "summer '26", font = CaptionFont.SCRIPT,
                )
                3 -> WidgetConfig(
                    photos = pics(3, 0), shape = FrameShape.FLOWER, filter = PhotoFilter.GOLDEN,
                    sticker = "🌸", frameColor = 0xFFFFE59C.toInt(),
                )
                4 -> WidgetConfig(
                    photos = pics(0), shape = FrameShape.STAMP, filter = PhotoFilter.FROST, sticker = "🦋", tilt = 4f,
                )
                5 -> WidgetConfig(
                    photos = pics(3), shape = FrameShape.CIRCLE, filter = PhotoFilter.NOIR,
                    caption = "main character", font = CaptionFont.CLASSY, frameColor = 0xFF1F1B26.toInt(),
                )
                else -> WidgetConfig(
                    photos = pics(2), shape = FrameShape.SOFT, caption = "us ♡", font = CaptionFont.BUBBLY, sticker = "🍓",
                )
            }
        }

        /** Four made-up "photos" so the emulator has something pretty to frame. */
        fun samplePhotos(context: Context): List<String> = listOf(
            PhotoStore.importBitmap(context, sunset(1200, 1600)),
            PhotoStore.importBitmap(context, seaside(1600, 1200)),
            PhotoStore.importBitmap(context, meadow(1200, 1600)),
            PhotoStore.importBitmap(context, night(1600, 1200)),
        )

        fun renderGallery(context: Context, photos: List<String>) {
            val dir = File(context.getExternalFilesDir(null), "preview").apply { mkdirs() }
            val filters = listOf(PhotoFilter.NONE, PhotoFilter.DREAMY, PhotoFilter.VINTAGE, PhotoFilter.NOIR)
            val shapes = FrameShape.entries.flatMap { shape ->
                filters.mapIndexed { i, filter ->
                    WidgetConfig(photos = listOf(PhotoRef(photos[i % photos.size])), shape = shape, filter = filter)
                }
            }
            sheet(context, shapes.map { it to (320 to 320) }, columns = 4, File(dir, "shapes-and-filters.png"))

            val designs = (0 until 7).map { design(it, photos) to (420 to 420) } +
                listOf(
                    design(0, photos) to (300 to 520),
                    design(2, photos) to (300 to 520),
                    design(6, photos) to (640 to 320),
                    design(4, photos) to (640 to 320),
                    WidgetConfig() to (420 to 420),
                )
            sheet(context, designs, columns = 4, File(dir, "designs.png"))

            // Inflate the real widget layout in-process to prove the RemoteViews are valid.
            val report = runCatching {
                val views = RemoteViews(context.packageName, R.layout.widget_photo)
                views.setImageViewBitmap(R.id.widget_image, WidgetRenderer.render(context, design(0, photos), 400, 400))
                views.apply(context, FrameLayout(context))
                "remote views inflate: ok"
            }.getOrElse { "remote views inflate: FAILED $it" }
            File(dir, "checks.txt").writeText(report + "\n")
            Log.i(TAG, "gallery written to $dir ($report)")
        }

        private fun sheet(context: Context, items: List<Pair<WidgetConfig, Pair<Int, Int>>>, columns: Int, out: File) {
            val cell = 340
            val rows = (items.size + columns - 1) / columns
            val bitmap = Bitmap.createBitmap(columns * cell, rows * cell, Bitmap.Config.ARGB_8888)
            val canvas = Canvas(bitmap)
            canvas.drawRect(0f, 0f, bitmap.width.toFloat(), bitmap.height.toFloat(), Paint().apply {
                shader = LinearGradient(0f, 0f, bitmap.width.toFloat(), bitmap.height.toFloat(), 0xFFF6C7D9.toInt(), 0xFFC9D8F7.toInt(), Shader.TileMode.CLAMP)
            })
            items.forEachIndexed { i, (config, size) ->
                val tile = WidgetRenderer.render(context, config, size.first, size.second)
                val scale = minOf((cell - 20f) / tile.width, (cell - 20f) / tile.height, 1f)
                val w = tile.width * scale
                val h = tile.height * scale
                val left = (i % columns) * cell + (cell - w) / 2f
                val top = (i / columns) * cell + (cell - h) / 2f
                canvas.drawBitmap(tile, null, RectF(left, top, left + w, top + h), Paint(Paint.FILTER_BITMAP_FLAG))
            }
            out.outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
        }

        private fun scene(w: Int, h: Int, sky: IntArray): Pair<Bitmap, Canvas> {
            val bitmap = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
            val canvas = Canvas(bitmap)
            canvas.drawRect(0f, 0f, w.toFloat(), h.toFloat(), Paint().apply {
                shader = LinearGradient(0f, 0f, 0f, h.toFloat(), sky, null, Shader.TileMode.CLAMP)
            })
            return bitmap to canvas
        }

        private fun paint(color: Long) = Paint(Paint.ANTI_ALIAS_FLAG).apply { this.color = color.toInt() }

        private fun sunset(w: Int, h: Int): Bitmap {
            val (bitmap, canvas) = scene(w, h, intArrayOf(0xFF2B1B4A.toInt(), 0xFFB04A7C.toInt(), 0xFFF2836B.toInt(), 0xFFFFC27A.toInt()))
            val sun = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                shader = RadialGradient(w * 0.6f, h * 0.62f, w * 0.3f, intArrayOf(0xFFFFF1C9.toInt(), 0x00FFE3A3), null, Shader.TileMode.CLAMP)
            }
            canvas.drawCircle(w * 0.6f, h * 0.62f, w * 0.3f, sun)
            canvas.drawCircle(w * 0.6f, h * 0.62f, w * 0.11f, paint(0xFFFFF3D6))
            hills(canvas, w, h, 0.72f, paint(0xFF5A2E5E))
            hills(canvas, w, h, 0.82f, paint(0xFF2E1734))
            return bitmap
        }

        private fun seaside(w: Int, h: Int): Bitmap {
            val (bitmap, canvas) = scene(w, h, intArrayOf(0xFF8CC8F2.toInt(), 0xFFE6F5FF.toInt()))
            val random = Random(3)
            repeat(5) {
                val cx = random.nextFloat() * w
                val cy = h * (0.1f + random.nextFloat() * 0.3f)
                val cloud = paint(0xEEFFFFFF)
                canvas.drawOval(cx - 120f, cy - 40f, cx + 120f, cy + 40f, cloud)
                canvas.drawOval(cx - 60f, cy - 80f, cx + 70f, cy + 20f, cloud)
            }
            canvas.drawRect(0f, h * 0.55f, w.toFloat(), h.toFloat(), Paint().apply {
                shader = LinearGradient(0f, h * 0.55f, 0f, h.toFloat(), 0xFF3FA7C9.toInt(), 0xFF155B85.toInt(), Shader.TileMode.CLAMP)
            })
            val sand = Path().apply {
                moveTo(0f, h * 0.8f)
                quadTo(w * 0.5f, h * 0.7f, w.toFloat(), h * 0.86f)
                lineTo(w.toFloat(), h.toFloat())
                lineTo(0f, h.toFloat())
                close()
            }
            canvas.drawPath(sand, paint(0xFFF4DDB8))
            return bitmap
        }

        private fun meadow(w: Int, h: Int): Bitmap {
            val (bitmap, canvas) = scene(w, h, intArrayOf(0xFFBFE3FF.toInt(), 0xFFFFF4FA.toInt()))
            hills(canvas, w, h, 0.5f, paint(0xFF9CCB7A))
            hills(canvas, w, h, 0.62f, paint(0xFF6FAA58))
            val random = Random(5)
            val petals = listOf(0xFFFFB3CF, 0xFFFFFFFF, 0xFFFFE07A, 0xFFE7B8FF)
            repeat(260) {
                val y = h * (0.6f + random.nextFloat() * 0.4f)
                canvas.drawCircle(random.nextFloat() * w, y, 6f + (y / h) * 14f, paint(petals[random.nextInt(petals.size)]))
            }
            return bitmap
        }

        private fun night(w: Int, h: Int): Bitmap {
            val (bitmap, canvas) = scene(w, h, intArrayOf(0xFF0B1030.toInt(), 0xFF2A2766.toInt(), 0xFF5A3C8C.toInt()))
            val random = Random(9)
            repeat(180) { canvas.drawCircle(random.nextFloat() * w, random.nextFloat() * h * 0.7f, 1.5f + random.nextFloat() * 3f, paint(0xCCFFFFFF)) }
            canvas.drawCircle(w * 0.7f, h * 0.25f, h * 0.1f, paint(0xFFFFF4D6))
            canvas.drawCircle(w * 0.74f, h * 0.22f, h * 0.09f, paint(0xFF1B1A4A))
            var x = 0f
            while (x < w) {
                val bw = 60f + random.nextFloat() * 120f
                val bh = h * (0.12f + random.nextFloat() * 0.25f)
                canvas.drawRect(x, h - bh, x + bw, h.toFloat(), paint(0xFF151230))
                repeat(6) {
                    canvas.drawRect(x + 10 + random.nextFloat() * (bw - 30), h - bh + 10 + random.nextFloat() * (bh - 30), x + 22 + random.nextFloat() * (bw - 30), h - bh + 24 + random.nextFloat() * (bh - 30), paint(0xFFFFD68A))
                }
                x += bw + 6f
            }
            return bitmap
        }

        private fun hills(canvas: Canvas, w: Int, h: Int, level: Float, paint: Paint) {
            val path = Path().apply {
                moveTo(0f, h * level)
                quadTo(w * 0.25f, h * (level - 0.08f), w * 0.5f, h * level)
                quadTo(w * 0.75f, h * (level + 0.07f), w.toFloat(), h * (level - 0.04f))
                lineTo(w.toFloat(), h.toFloat())
                lineTo(0f, h.toFloat())
                close()
            }
            canvas.drawPath(path, paint)
        }
    }
}
