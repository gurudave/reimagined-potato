package app.glimmer.widgets.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.glimmer.widgets.ui.theme.LocalVibe

@Composable
fun SectionTitle(text: String, modifier: Modifier = Modifier) {
    Text(
        text,
        color = LocalVibe.current.ink.copy(alpha = 0.75f),
        fontWeight = FontWeight.Bold,
        fontSize = 14.sp,
        letterSpacing = 0.6.sp,
        modifier = modifier,
    )
}

/** A rounded, tappable label. The selected one is filled with the vibe's colour. */
@Composable
fun PillChip(
    text: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    fontFamily: FontFamily? = null,
    fontSize: TextUnit = 14.sp,
) {
    val vibe = LocalVibe.current
    val background by animateColorAsState(if (selected) vibe.primary else vibe.surface, label = "chip")
    Box(
        modifier
            .clip(CircleShape)
            .background(background)
            .border(1.5.dp, if (selected) vibe.primary else vibe.soft, CircleShape)
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 9.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text,
            color = if (selected) vibe.onPrimary else vibe.ink,
            fontFamily = fontFamily,
            fontSize = fontSize,
            fontWeight = FontWeight.Bold,
            maxLines = 1,
        )
    }
}

@Composable
fun <T> ChipRow(options: List<T>, selected: T, label: (T) -> String, onSelect: (T) -> Unit) {
    LazyRow(
        contentPadding = PaddingValues(horizontal = 20.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        items(options) { option ->
            PillChip(label(option), option == selected, onClick = { onSelect(option) })
        }
    }
}

/** A colour dot. Pass a null [color] for "none"/"auto", with [content] to say which. */
@Composable
fun SwatchDot(
    color: Color?,
    selected: Boolean,
    label: String,
    onClick: () -> Unit,
    content: @Composable BoxScope.() -> Unit = {},
) {
    val vibe = LocalVibe.current
    Box(
        Modifier
            .size(42.dp)
            .clip(CircleShape)
            .clickable(onClick = onClick)
            .semantics { contentDescription = label }
            .border(if (selected) 3.dp else 1.5.dp, if (selected) vibe.primary else vibe.soft, CircleShape)
            .padding(5.dp)
            .clip(CircleShape)
            .background(color ?: vibe.surface)
            .border(1.dp, vibe.ink.copy(alpha = 0.08f), CircleShape),
        contentAlignment = Alignment.Center,
        content = content,
    )
}

/** The diagonal line drawn inside a "none" swatch. */
fun Modifier.noneSlash(color: Color) = drawBehind {
    drawLine(color, Offset(size.width * 0.78f, size.height * 0.22f), Offset(size.width * 0.22f, size.height * 0.78f), strokeWidth = 2.dp.toPx())
}

@Composable
fun ToggleRow(label: String, hint: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    val vibe = LocalVibe.current
    Row(
        Modifier
            .fillMaxWidth()
            .clickable { onChange(!checked) }
            .padding(horizontal = 20.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(label, color = vibe.ink, fontWeight = FontWeight.Bold, fontSize = 16.sp)
            Text(hint, color = vibe.ink.copy(alpha = 0.6f), fontSize = 13.sp)
        }
        Switch(
            checked = checked,
            onCheckedChange = onChange,
            colors = SwitchDefaults.colors(
                checkedThumbColor = vibe.onPrimary,
                checkedTrackColor = vibe.primary,
                checkedBorderColor = vibe.primary,
                uncheckedThumbColor = vibe.primary.copy(alpha = 0.7f),
                uncheckedTrackColor = vibe.surface,
                uncheckedBorderColor = vibe.soft,
            ),
        )
    }
}

/** The big gradient call-to-action pill. */
@Composable
fun DjungleskwidgetsButton(text: String, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val vibe = LocalVibe.current
    Box(
        modifier
            .shadow(14.dp, CircleShape, ambientColor = vibe.primary, spotColor = vibe.primary)
            .clip(CircleShape)
            .background(Brush.horizontalGradient(listOf(vibe.primary, vibe.accent)))
            .clickable(onClick = onClick)
            .padding(horizontal = 30.dp, vertical = 16.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(text, color = vibe.onPrimary, fontWeight = FontWeight.Bold, fontSize = 17.sp, textAlign = TextAlign.Center)
    }
}

/** A short message that slides up from the bottom of the screen. */
@Composable
fun ToastPill(text: String, visible: Boolean, modifier: Modifier = Modifier) {
    val vibe = LocalVibe.current
    AnimatedVisibility(
        visible = visible,
        enter = fadeIn() + slideInVertically { it },
        exit = fadeOut() + slideOutVertically { it },
        modifier = modifier,
    ) {
        Text(
            text,
            color = vibe.background,
            fontWeight = FontWeight.Bold,
            fontSize = 15.sp,
            textAlign = TextAlign.Center,
            modifier = Modifier
                .padding(horizontal = 24.dp)
                .clip(CircleShape)
                .background(vibe.ink.copy(alpha = 0.92f))
                .padding(horizontal = 20.dp, vertical = 12.dp),
        )
    }
}
