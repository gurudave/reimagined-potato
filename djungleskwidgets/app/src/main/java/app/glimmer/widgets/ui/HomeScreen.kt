package app.glimmer.widgets.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import app.glimmer.widgets.data.WidgetConfig
import app.glimmer.widgets.data.WidgetStore
import app.glimmer.widgets.render.WidgetRenderer
import app.glimmer.widgets.ui.theme.LocalVibe
import app.glimmer.widgets.ui.theme.Pacifico
import app.glimmer.widgets.ui.theme.Vibe
import app.glimmer.widgets.ui.theme.family
import app.glimmer.widgets.widget.WidgetUpdater
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlin.math.roundToInt

private class HomeWidget(val id: Int, val config: WidgetConfig?, val aspect: Float)

@Composable
fun HomeScreen(onVibeChange: (Vibe) -> Unit, onEdit: (Int) -> Unit, onNew: () -> Unit) {
    val context = LocalContext.current
    val vibe = LocalVibe.current
    val changes by WidgetStore.changes.collectAsState()
    var resumes by remember { mutableIntStateOf(0) }
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { resumes++ }

    var widgets by remember { mutableStateOf<List<HomeWidget>?>(null) }
    LaunchedEffect(changes, resumes) {
        widgets = withContext(Dispatchers.Default) {
            WidgetUpdater.boundIds(context).sorted().map { id ->
                val (w, h) = WidgetUpdater.sizePx(context, id)
                HomeWidget(id, WidgetStore.load(context, id), w.toFloat() / h)
            }
        }
    }
    var logoBurst by remember { mutableIntStateOf(0) }
    val top = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()

    Box(Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(vibe.background, vibe.soft)))) {
        SparkleField(vibe.accent, Modifier.fillMaxSize())
        LazyVerticalGrid(
            columns = GridCells.Fixed(2),
            contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = top + 18.dp, bottom = 150.dp),
            horizontalArrangement = Arrangement.spacedBy(14.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
            modifier = Modifier.fillMaxSize(),
        ) {
            item(span = { GridItemSpan(maxLineSpan) }) { Header(onLogoTap = { logoBurst++ }) }
            item(span = { GridItemSpan(maxLineSpan) }) { VibePicker(vibe, onVibeChange) }
            item(span = { GridItemSpan(maxLineSpan) }) {
                SectionTitle("your widgets", Modifier.padding(start = 4.dp, top = 6.dp))
            }
            val list = widgets
            if (list != null && list.isEmpty()) {
                item(span = { GridItemSpan(maxLineSpan) }) { EmptyCard(onNew) }
            }
            if (list != null) {
                items(list, key = { it.id }) { widget -> WidgetCard(widget, onClick = { onEdit(widget.id) }) }
            }
            item(span = { GridItemSpan(maxLineSpan) }) { TipCard() }
        }
        DjungleskwidgetsButton(
            "✚  new widget",
            onClick = onNew,
            modifier = Modifier.align(Alignment.BottomCenter).navigationBarsPadding().padding(bottom = 22.dp),
        )
        SparkleBurst(
            logoBurst,
            listOf(vibe.primary, vibe.accent, vibe.soft),
            Modifier.fillMaxWidth().height(top + 200.dp),
        )
    }
}

private const val LOGO = "djungleskwidgets"

@Composable
private fun Header(onLogoTap: () -> Unit) {
    val vibe = LocalVibe.current
    Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
        // The name is long, so shrink the logo until it fits on one line of a narrow phone.
        val measurer = rememberTextMeasurer()
        BoxWithConstraints(Modifier.fillMaxWidth().padding(horizontal = 16.dp), contentAlignment = Alignment.Center) {
            val logo = TextStyle(fontFamily = Pacifico, fontSize = 54.sp, color = vibe.primary)
            val natural = remember(measurer) { measurer.measure(LOGO, logo, softWrap = false).size.width }
            val scale = if (natural > constraints.maxWidth) constraints.maxWidth / natural.toFloat() else 1f
            Text(
                LOGO,
                style = logo.copy(fontSize = 54.sp * scale),
                maxLines = 1,
                softWrap = false,
                modifier = Modifier.clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onClick = onLogoTap,
                ),
            )
        }
        Text(
            "photo widgets · zero ads · all vibes ✧",
            color = vibe.ink.copy(alpha = 0.7f),
            fontSize = 14.sp,
            fontWeight = FontWeight.Bold,
        )
    }
}

@Composable
private fun VibePicker(current: Vibe, onChange: (Vibe) -> Unit) {
    Column(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(28.dp))
            .background(current.surface.copy(alpha = 0.78f))
            .padding(vertical = 14.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text("vibe: ${current.label} ${current.emoji}", color = current.ink, fontWeight = FontWeight.Bold, fontSize = 15.sp)
        Row(Modifier.padding(top = 10.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Vibe.entries.forEach { vibe ->
                val chosen = vibe == current
                Box(
                    Modifier
                        .size(38.dp)
                        .clip(CircleShape)
                        .background(Brush.linearGradient(listOf(vibe.soft, vibe.primary)))
                        .border(if (chosen) 3.dp else 1.dp, if (chosen) current.ink else current.ink.copy(alpha = 0.12f), CircleShape)
                        .clickable { onChange(vibe) }
                        .semantics { contentDescription = "vibe ${vibe.label}" },
                    contentAlignment = Alignment.Center,
                ) {
                    if (chosen) Text("✓", color = vibe.onPrimary, fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}

@Composable
private fun WidgetCard(widget: HomeWidget, onClick: () -> Unit) {
    val context = LocalContext.current
    val vibe = LocalVibe.current
    val aspect = widget.aspect.coerceIn(0.5f, 2f)
    val bitmap by produceState<ImageBitmap?>(null, widget.config, aspect) {
        value = withContext(Dispatchers.Default) {
            val longest = 420
            val (w, h) = if (aspect >= 1f) longest to (longest / aspect).roundToInt() else (longest * aspect).roundToInt() to longest
            WidgetRenderer.render(context, widget.config ?: WidgetConfig(), w, h).asImageBitmap()
        }
    }
    val config = widget.config
    Column(
        Modifier
            .shadow(12.dp, RoundedCornerShape(28.dp), ambientColor = vibe.primary, spotColor = vibe.primary)
            .clip(RoundedCornerShape(28.dp))
            .background(vibe.surface)
            .clickable(onClick = onClick)
            .padding(10.dp),
    ) {
        Box(
            Modifier
                .fillMaxWidth()
                .aspectRatio(1f)
                .clip(RoundedCornerShape(20.dp))
                .background(Brush.linearGradient(listOf(vibe.soft, vibe.accent.copy(alpha = 0.45f)))),
            contentAlignment = Alignment.Center,
        ) {
            bitmap?.let { Image(it, contentDescription = null, contentScale = ContentScale.Fit, modifier = Modifier.fillMaxSize().padding(6.dp)) }
        }
        val caption = config?.caption?.takeIf { it.isNotBlank() }
        Text(
            caption ?: config?.let { "${it.shape.label} · ${it.photos.size} photo${if (it.photos.size == 1) "" else "s"}" } ?: "needs photos",
            fontFamily = if (caption != null && config != null) config.font.family else null,
            fontSize = if (caption != null) 18.sp else 15.sp,
            fontWeight = FontWeight.Bold,
            color = vibe.ink,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(start = 6.dp, top = 8.dp),
        )
        Text("tap to edit", fontSize = 12.sp, color = vibe.ink.copy(alpha = 0.6f), modifier = Modifier.padding(start = 6.dp, bottom = 2.dp))
    }
}

@Composable
private fun EmptyCard(onNew: () -> Unit) {
    val vibe = LocalVibe.current
    Box(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(28.dp))
            .background(vibe.surface.copy(alpha = 0.6f))
            .drawBehind {
                drawRoundRect(
                    color = vibe.primary.copy(alpha = 0.55f),
                    style = Stroke(width = 2.dp.toPx(), pathEffect = PathEffect.dashPathEffect(floatArrayOf(14f, 12f))),
                    cornerRadius = CornerRadius(28.dp.toPx()),
                )
            }
            .clickable(onClick = onNew)
            .padding(28.dp),
        contentAlignment = Alignment.Center,
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text("✧", fontSize = 40.sp, color = vibe.primary)
            Text("no widgets yet", fontWeight = FontWeight.Bold, fontSize = 18.sp, color = vibe.ink)
            Text(
                "make your first one, it only takes a minute",
                textAlign = TextAlign.Center,
                color = vibe.ink.copy(alpha = 0.65f),
                fontSize = 14.sp,
            )
        }
    }
}

@Composable
private fun TipCard() {
    val vibe = LocalVibe.current
    Text(
        "tip: you can also long-press your home screen → widgets → djungleskwidgets.\n" +
            "tap a widget to flip to the next photo ✧",
        textAlign = TextAlign.Center,
        fontSize = 13.sp,
        color = vibe.ink.copy(alpha = 0.6f),
        modifier = Modifier.fillMaxWidth().padding(top = 6.dp),
    )
}
