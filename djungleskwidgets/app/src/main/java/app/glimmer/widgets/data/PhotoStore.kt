package app.glimmer.widgets.data

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.ImageDecoder
import android.media.ExifInterface
import android.net.Uri
import android.util.Log
import android.util.LruCache
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

/**
 * Photos are copied into the app's private storage when picked, so widgets keep working
 * even if the original is deleted from the gallery, and nothing needs storage permission.
 */
object PhotoStore {
    private const val TAG = "PhotoStore"
    private const val MAX_SIDE = 1600
    private const val ORPHAN_GRACE_MS = 24 * 60 * 60 * 1000L

    private val cache = object : LruCache<String, Bitmap>(
        (Runtime.getRuntime().maxMemory() / 8).coerceAtMost(64L * 1024 * 1024).toInt()
    ) {
        override fun sizeOf(key: String, value: Bitmap) = value.allocationByteCount
    }
    private val aspects = ConcurrentHashMap<String, Float>()

    private fun dir(context: Context) = File(context.filesDir, "photos").apply { mkdirs() }

    /**
     * Copies a picked image in, shrunk to a sensible size with its orientation fixed, and notes
     * when it was taken for the camcorder stamp.
     */
    suspend fun import(context: Context, uri: Uri): PhotoRef? = withContext(Dispatchers.IO) {
        runCatching {
            val source = ImageDecoder.createSource(context.contentResolver, uri)
            val bitmap = ImageDecoder.decodeBitmap(source) { decoder, info, _ ->
                decoder.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
                val w = info.size.width
                val h = info.size.height
                val longest = max(w, h)
                if (longest > MAX_SIDE) {
                    val scale = MAX_SIDE.toFloat() / longest
                    decoder.setTargetSize(
                        (w * scale).roundToInt().coerceAtLeast(1),
                        (h * scale).roundToInt().coerceAtLeast(1),
                    )
                }
            }
            val name = importBitmap(context, bitmap).also { bitmap.recycle() }
            PhotoRef(name, taken = takenAt(context, uri) ?: System.currentTimeMillis())
        }.onFailure { Log.w(TAG, "Could not import $uri", it) }.getOrNull()
    }

    /** When the camera says the photo was taken, or null if it does not say. */
    private fun takenAt(context: Context, uri: Uri): Long? = runCatching {
        context.contentResolver.openInputStream(uri)?.use { stream ->
            val exif = ExifInterface(stream)
            val stamp = exif.getAttribute(ExifInterface.TAG_DATETIME_ORIGINAL)
                ?: exif.getAttribute(ExifInterface.TAG_DATETIME)
                ?: return@use null
            // Camera clocks keep local time with no zone, which is how the stamp should read too.
            SimpleDateFormat("yyyy:MM:dd HH:mm:ss", Locale.US).parse(stamp.trim())?.time
        }
    }.getOrNull()

    /** When a stored photo was added, for photos that predate [PhotoRef.taken]. */
    fun addedAt(context: Context, name: String): Long? =
        File(dir(context), name).lastModified().takeIf { it > 0L }

    /** Saves [bitmap] as a new photo and returns its file name. */
    fun importBitmap(context: Context, bitmap: Bitmap): String {
        val opaque = if (bitmap.hasAlpha()) {
            // JPEG has no transparency; put see-through images on white rather than black.
            Bitmap.createBitmap(bitmap.width, bitmap.height, Bitmap.Config.ARGB_8888).also {
                Canvas(it).apply { drawColor(Color.WHITE); drawBitmap(bitmap, 0f, 0f, null) }
            }
        } else {
            bitmap
        }
        val name = "${UUID.randomUUID()}.jpg"
        File(dir(context), name).outputStream().use { opaque.compress(Bitmap.CompressFormat.JPEG, 92, it) }
        if (opaque !== bitmap) opaque.recycle()
        return name
    }

    /**
     * Loads a stored photo, downsampled as far as possible while keeping its shorter side at
     * least [minSide] pixels. Results are cached and shared, so callers must not modify them.
     */
    fun load(context: Context, name: String, minSide: Int): Bitmap? {
        val file = File(dir(context), name)
        if (!file.exists()) return null
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(file.path, bounds)
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null
        aspects[name] = bounds.outWidth.toFloat() / bounds.outHeight

        var sample = 1
        val shorter = min(bounds.outWidth, bounds.outHeight)
        while (shorter / (sample * 2) >= minSide) sample *= 2

        val key = "$name@$sample"
        cache.get(key)?.let { return it }
        val bitmap = BitmapFactory.decodeFile(file.path, BitmapFactory.Options().apply { inSampleSize = sample })
            ?: return null
        cache.put(key, bitmap)
        return bitmap
    }

    /** Width divided by height, or null if the photo is missing. */
    fun aspect(context: Context, name: String): Float? {
        aspects[name]?.let { return it }
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(File(dir(context), name).path, bounds)
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null
        return (bounds.outWidth.toFloat() / bounds.outHeight).also { aspects[name] = it }
    }

    /**
     * Deletes photos no design uses any more. Files younger than a day are spared, because the
     * editor may be holding photos that have not been saved into a design yet.
     */
    fun cleanup(context: Context, keep: Set<String>) {
        val cutoff = System.currentTimeMillis() - ORPHAN_GRACE_MS
        dir(context).listFiles()?.forEach { file ->
            if (file.name !in keep && file.lastModified() < cutoff) {
                cache.snapshot().keys.filter { it.startsWith(file.name + "@") }.forEach(cache::remove)
                aspects.remove(file.name)
                file.delete()
            }
        }
    }
}
