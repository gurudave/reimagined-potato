package app.glimmer.widgets.ui.theme

import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.enableEdgeToEdge
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import app.glimmer.widgets.R
import app.glimmer.widgets.data.CaptionFont

/** The app's colour themes. She picks one on the home screen. */
enum class Vibe(
    val label: String,
    val emoji: String,
    val background: Color,
    val surface: Color,
    val primary: Color,
    val onPrimary: Color,
    val soft: Color,
    val ink: Color,
    val accent: Color,
    val isDark: Boolean = false,
) {
    STRAWBERRY(
        "strawberry milk", "🍓", Color(0xFFFFF3F6), Color(0xFFFFFFFF), Color(0xFFEC7AA1),
        Color(0xFFFFFFFF), Color(0xFFFFDCE7), Color(0xFF5A3442), Color(0xFFB79BF5),
    ),
    MATCHA(
        "matcha latte", "🍵", Color(0xFFF2F5EA), Color(0xFFFFFFFF), Color(0xFF7C9E66),
        Color(0xFFFFFFFF), Color(0xFFDDE9CE), Color(0xFF38442F), Color(0xFF8FB073),
    ),
    LAVENDER(
        "lavender haze", "💜", Color(0xFFF5F1FF), Color(0xFFFFFFFF), Color(0xFF9581EC),
        Color(0xFFFFFFFF), Color(0xFFE5DDFF), Color(0xFF40375E), Color(0xFFE58DB8),
    ),
    CLOUD(
        "cloud nine", "☁️", Color(0xFFEFF5FD), Color(0xFFFFFFFF), Color(0xFF6A9DDA),
        Color(0xFFFFFFFF), Color(0xFFD6E6F9), Color(0xFF2C4056), Color(0xFF9C8FE6),
    ),
    BUTTER(
        "butter", "🌼", Color(0xFFFFFAEA), Color(0xFFFFFFFF), Color(0xFFE5B04A),
        Color(0xFF4A3A16), Color(0xFFFFEDBA), Color(0xFF52421C), Color(0xFFF2A57E),
    ),
    MIDNIGHT(
        "midnight", "🌙", Color(0xFF15121C), Color(0xFF221D2C), Color(0xFFF0A6C4),
        Color(0xFF2A1A26), Color(0xFF362E45), Color(0xFFF4EEF8), Color(0xFFB7A6FF), isDark = true,
    ),
}

val Quicksand = FontFamily(
    Font(R.font.quicksand_medium, FontWeight.Medium),
    Font(R.font.quicksand_bold, FontWeight.Bold),
)
val Pacifico = FontFamily(Font(R.font.pacifico))

private val captionFamilies = CaptionFont.entries.associateWith { FontFamily(Font(it.res)) }
val CaptionFont.family: FontFamily get() = captionFamilies.getValue(this)

val LocalVibe = staticCompositionLocalOf { Vibe.STRAWBERRY }

private val DjungleskwidgetsTypography = Typography().run {
    Typography(
        displayLarge = displayLarge.copy(fontFamily = Quicksand),
        displayMedium = displayMedium.copy(fontFamily = Quicksand),
        displaySmall = displaySmall.copy(fontFamily = Quicksand),
        headlineLarge = headlineLarge.copy(fontFamily = Quicksand),
        headlineMedium = headlineMedium.copy(fontFamily = Quicksand),
        headlineSmall = headlineSmall.copy(fontFamily = Quicksand),
        titleLarge = titleLarge.copy(fontFamily = Quicksand, fontWeight = FontWeight.Bold),
        titleMedium = titleMedium.copy(fontFamily = Quicksand, fontWeight = FontWeight.Bold),
        titleSmall = titleSmall.copy(fontFamily = Quicksand, fontWeight = FontWeight.Bold),
        bodyLarge = bodyLarge.copy(fontFamily = Quicksand, fontWeight = FontWeight.Medium),
        bodyMedium = bodyMedium.copy(fontFamily = Quicksand, fontWeight = FontWeight.Medium),
        bodySmall = bodySmall.copy(fontFamily = Quicksand, fontWeight = FontWeight.Medium),
        labelLarge = labelLarge.copy(fontFamily = Quicksand, fontWeight = FontWeight.Bold),
        labelMedium = labelMedium.copy(fontFamily = Quicksand, fontWeight = FontWeight.Bold),
        labelSmall = labelSmall.copy(fontFamily = Quicksand, fontWeight = FontWeight.Bold),
    )
}

@Composable
fun DjungleskwidgetsTheme(vibe: Vibe, content: @Composable () -> Unit) {
    val activity = LocalContext.current as? ComponentActivity
    LaunchedEffect(vibe) {
        val clear = Color.Transparent.toArgb()
        val bars = if (vibe.isDark) SystemBarStyle.dark(clear) else SystemBarStyle.light(clear, clear)
        activity?.enableEdgeToEdge(statusBarStyle = bars, navigationBarStyle = bars)
    }
    val colors = if (vibe.isDark) {
        darkColorScheme(
            primary = vibe.primary, onPrimary = vibe.onPrimary, secondary = vibe.accent,
            background = vibe.background, onBackground = vibe.ink,
            surface = vibe.surface, onSurface = vibe.ink,
            surfaceVariant = vibe.soft, onSurfaceVariant = vibe.ink,
            surfaceContainerHigh = vibe.surface, outline = vibe.soft,
        )
    } else {
        lightColorScheme(
            primary = vibe.primary, onPrimary = vibe.onPrimary, secondary = vibe.accent,
            background = vibe.background, onBackground = vibe.ink,
            surface = vibe.surface, onSurface = vibe.ink,
            surfaceVariant = vibe.soft, onSurfaceVariant = vibe.ink,
            surfaceContainerHigh = vibe.surface, outline = vibe.soft,
        )
    }
    CompositionLocalProvider(LocalVibe provides vibe) {
        MaterialTheme(colorScheme = colors, typography = DjungleskwidgetsTypography, content = content)
    }
}
