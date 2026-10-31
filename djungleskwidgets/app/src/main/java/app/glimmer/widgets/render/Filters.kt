package app.glimmer.widgets.render

import android.graphics.ColorMatrix
import android.graphics.ColorMatrixColorFilter
import app.glimmer.widgets.data.PhotoFilter

/** Photo filters as colour matrices: saturation, then contrast, then a per-channel tint. */
object Filters {
    private val ONE = floatArrayOf(1f, 1f, 1f)
    private val ZERO = floatArrayOf(0f, 0f, 0f)

    private val filters: Map<PhotoFilter, ColorMatrixColorFilter> = mapOf(
        PhotoFilter.DREAMY to build(0.8f, 0.86f, floatArrayOf(1f, 0.98f, 1.02f), floatArrayOf(22f, 8f, 20f)),
        PhotoFilter.VINTAGE to build(0.55f, 0.9f, floatArrayOf(1.05f, 0.97f, 0.82f), floatArrayOf(22f, 12f, 0f)),
        PhotoFilter.GOLDEN to build(1.12f, 1.02f, floatArrayOf(1.08f, 1f, 0.84f), floatArrayOf(14f, 6f, -8f)),
        PhotoFilter.FROST to build(0.85f, 1f, floatArrayOf(0.92f, 1f, 1.08f), floatArrayOf(-2f, 6f, 18f)),
        PhotoFilter.NOIR to build(0f, 1.18f, ONE, ZERO),
        PhotoFilter.POP to build(1.4f, 1.12f, ONE, ZERO),
        // A tape that has been played a few too many times: washed out, with blue-green shadows.
        PhotoFilter.VHS to build(0.72f, 0.82f, floatArrayOf(1.02f, 0.98f, 1.04f), floatArrayOf(8f, 18f, 30f)),
        // An old print left in the sun: lifted blacks, a warm cast, and gently drained colour.
        PhotoFilter.FADED to build(0.68f, 0.76f, floatArrayOf(1.04f, 1f, 0.86f), floatArrayOf(34f, 28f, 14f)),
    )

    fun colorFilter(filter: PhotoFilter): ColorMatrixColorFilter? = filters[filter]

    fun hasVignette(filter: PhotoFilter) =
        filter == PhotoFilter.VINTAGE || filter == PhotoFilter.NOIR || filter == PhotoFilter.FADED

    fun hasScanlines(filter: PhotoFilter) = filter == PhotoFilter.VHS

    private fun build(saturation: Float, contrast: Float, scale: FloatArray, offset: FloatArray): ColorMatrixColorFilter {
        val matrix = ColorMatrix().apply { setSaturation(saturation) }
        val shift = 128f * (1f - contrast)
        matrix.postConcat(
            ColorMatrix(
                floatArrayOf(
                    contrast, 0f, 0f, 0f, shift,
                    0f, contrast, 0f, 0f, shift,
                    0f, 0f, contrast, 0f, shift,
                    0f, 0f, 0f, 1f, 0f,
                )
            )
        )
        matrix.postConcat(
            ColorMatrix(
                floatArrayOf(
                    scale[0], 0f, 0f, 0f, offset[0],
                    0f, scale[1], 0f, 0f, offset[1],
                    0f, 0f, scale[2], 0f, offset[2],
                    0f, 0f, 0f, 1f, 0f,
                )
            )
        )
        return ColorMatrixColorFilter(matrix)
    }
}
