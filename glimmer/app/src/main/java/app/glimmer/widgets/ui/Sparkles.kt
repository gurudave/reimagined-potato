package app.glimmer.widgets.ui

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin
import kotlin.random.Random

/** A four-pointed twinkle: ✦ */
fun DrawScope.drawSparkle(center: Offset, radius: Float, color: Color, alpha: Float = 1f) {
    val (x, y) = center
    val k = radius / 3f
    val path = Path().apply {
        moveTo(x, y - radius)
        cubicTo(x, y - k, x + k, y, x + radius, y)
        cubicTo(x + k, y, x, y + k, x, y + radius)
        cubicTo(x, y + k, x - k, y, x - radius, y)
        cubicTo(x - k, y, x, y - k, x, y - radius)
        close()
    }
    drawPath(path, color, alpha = alpha.coerceIn(0f, 1f))
}

private class Twinkle(val x: Float, val y: Float, val size: Float, val phase: Float)

/** Gently twinkling stars scattered behind a screen. */
@Composable
fun SparkleField(color: Color, modifier: Modifier = Modifier, count: Int = 16) {
    val stars = remember(count) {
        val random = Random(11)
        List(count) { Twinkle(random.nextFloat(), random.nextFloat(), 3f + random.nextFloat() * 6f, random.nextFloat()) }
    }
    val time by rememberInfiniteTransition(label = "twinkle").animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(5200, easing = LinearEasing)),
        label = "time",
    )
    Canvas(modifier) {
        stars.forEach { star ->
            val wave = sin(((time + star.phase) % 1f) * 2f * PI.toFloat())
            drawSparkle(
                Offset(star.x * size.width, star.y * size.height),
                star.size * density,
                color,
                alpha = 0.18f + 0.4f * (0.5f + 0.5f * wave),
            )
        }
    }
}

private class Spark(val angle: Float, val reach: Float, val size: Float, val tint: Int)

/** A burst of sparkles from the middle of [modifier]'s area each time [trigger] changes. */
@Composable
fun SparkleBurst(trigger: Int, colors: List<Color>, modifier: Modifier = Modifier) {
    val progress = remember { Animatable(1f) }
    val sparks = remember(trigger) {
        val random = Random(trigger)
        List(22) {
            Spark(
                angle = random.nextFloat() * 2f * PI.toFloat(),
                reach = 0.45f + random.nextFloat() * 0.55f,
                size = 5f + random.nextFloat() * 9f,
                tint = random.nextInt(colors.size.coerceAtLeast(1)),
            )
        }
    }
    LaunchedEffect(trigger) {
        if (trigger > 0) {
            progress.snapTo(0f)
            progress.animateTo(1f, tween(1100, easing = FastOutSlowInEasing))
        }
    }
    Canvas(modifier) {
        val p = progress.value
        if (p >= 1f || colors.isEmpty()) return@Canvas
        val maxReach = size.minDimension * 0.48f
        sparks.forEach { spark ->
            val distance = maxReach * spark.reach * p
            drawSparkle(
                center + Offset(cos(spark.angle) * distance, sin(spark.angle) * distance),
                spark.size * density * (1f - 0.4f * p),
                colors[spark.tint % colors.size],
                alpha = 1f - p,
            )
        }
    }
}
