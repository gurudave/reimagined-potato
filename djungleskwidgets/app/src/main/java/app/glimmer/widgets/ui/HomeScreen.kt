package app.glimmer.widgets.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
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
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
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
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
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
import app.glimmer.widgets.ui.theme.Arcade
import app.glimmer.widgets.ui.theme.LocalVibe
import app.glimmer.widgets.ui.theme.Vcr
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

    Box(Modifier.fillMaxSize()) {
        RetroBackdrop(Modifier.fillMaxSize())
        LazyVerticalGrid(
            columns = GridCells.Fixed(2),
            contentPadding = PaddingValues(start = 18.dp, end = 18.dp, top = top + 12.dp, bottom = 150.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
            modifier = Modifier.fillMaxSize(),
        ) {
            item(span = { GridItemSpan(maxLineSpan) }) { Header(widgets?.size ?: 0, onLogoTap = { logoBurst++ }) }
            item(span = { GridItemSpan(maxLineSpan) }) { ChannelPicker(vibe, onVibeChange) }
            item(span = { GridItemSpan(maxLineSpan) }) {
                SectionTitle("your widgets", Modifier.padding(start = 2.dp, top = 6.dp))
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
            "+ new widget",
            onClick = onNew,
            modifier = Modifier.align(Alignment.BottomCenter).navigationBarsPadding().padding(bottom = 22.dp),
        )
        PixelBurst(
            logoBurst,
            vibe.stripes,
            Modifier.fillMaxWidth().height(top + 200.dp),
        )
    }
}

private const val LOGO = "DJUNGLESKWIDGETS"

/** Lettering for the on-screen display: white on dark vibes, the vibe's ink on light ones. */
@Composable
fun osdColor(): Color = LocalVibe.current.let { if (it.isDark) Color.White else it.ink }

@Composable
private fun Header(count: Int, onLogoTap: () -> Unit) {
    val vibe = LocalVibe.current
    Column(Modifier.fillMaxWidth()) {
        Row(Modifier.fillMaxWidth().padding(bottom = 10.dp), verticalAlignment = Alignment.CenterVertically) {
            PlayMark(color = osdColor())
            Box(Modifier.weight(1f))
            Text(vibe.channelName, style = osdStyle(color = osdColor()))
        }
        // The name is long, so shrink the logo until it fits on one line of a narrow phone.
        val measurer = rememberTextMeasurer()
        BoxWithConstraints(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
            val logo = TextStyle(fontFamily = Arcade, fontSize = 30.sp)
            val natural = remember(measurer) { measurer.measure(LOGO, logo, softWrap = false).size.width }
            // Leave room for the drop shadow on the right.
            val room = constraints.maxWidth * 0.97f
            val scale = if (natural > room) room / natural else 1f
            StripedLogo(
                LOGO,
                logo.copy(fontSize = 30.sp * scale),
                vibe.stripes,
                blockShadow(),
                Modifier.clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onClick = onLogoTap,
                ),
            )
        }
        ArcadeStripes(vibe.stripes, Modifier.fillMaxWidth().padding(top = 12.dp), band = 3.dp, gap = 2.dp)
        Row(Modifier.fillMaxWidth().padding(top = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(
                "PHOTO WIDGETS · ZERO ADS · ALL VIBES",
                color = vibe.ink.copy(alpha = 0.75f),
                fontFamily = Vcr,
                fontSize = 18.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )
            // Player one's score is simply how many widgets she has made.
            Column(horizontalAlignment = Alignment.End, modifier = Modifier.semantics { contentDescription = "$count widgets" }) {
                Text("1UP", color = vibe.accent, fontFamily = Arcade, fontSize = 9.sp)
                Text(count.toString().padStart(2, '0'), color = vibe.ink, fontFamily = Arcade, fontSize = 12.sp, modifier = Modifier.padding(top = 3.dp))
            }
        }
    }
}

/**
 * Lettering banded top to bottom in [colors], like the marquee of an arcade cabinet, with a
 * hard [shadow] beneath it.
 */
@Composable
private fun StripedLogo(text: String, style: TextStyle, colors: List<Color>, shadow: Color, modifier: Modifier = Modifier) {
    Box(modifier) {
        Text(text, style = style.copy(color = shadow), maxLines = 1, softWrap = false, modifier = Modifier.offset(3.dp, 3.dp))
        Text(
            text,
            style = style.copy(color = Color.White),
            maxLines = 1,
            softWrap = false,
            modifier = Modifier
                .graphicsLayer { compositingStrategy = CompositingStrategy.Offscreen }
                .drawWithContent {
                    drawContent()
                    val band = size.height / colors.size
                    colors.forEachIndexed { i, color ->
                        drawRect(color, Offset(0f, i * band), Size(size.width, band + 1f), blendMode = BlendMode.SrcAtop)
                    }
                },
        )
    }
}

/** The vibes, as channels on a television. */
@Composable
private fun ChannelPicker(current: Vibe, onChange: (Vibe) -> Unit) {
    RetroBlock(Modifier.fillMaxWidth(), fill = current.surface, contentPadding = PaddingValues(12.dp)) {
        Column(Modifier.fillMaxWidth()) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(current.channelName, color = current.accent, fontFamily = Vcr, fontSize = 24.sp)
                Text(" · ${current.label.uppercase()}", color = current.ink, fontFamily = Vcr, fontSize = 24.sp)
            }
            Row(Modifier.fillMaxWidth().padding(top = 8.dp), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                Vibe.entries.forEach { vibe ->
                    RetroBlock(
                        Modifier
                            .weight(1f)
                            .aspectRatio(0.9f)
                            .semantics { contentDescription = "vibe ${vibe.label}" },
                        fill = vibe.background,
                        border = if (vibe == current) current.accent else blockShadow(),
                        onClick = { onChange(vibe) },
                        held = vibe == current,
                        depth = 3.dp,
                        contentPadding = PaddingValues(5.dp),
                    ) {
                        Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.SpaceBetween) {
                            Text(vibe.channel.toString(), color = vibe.ink, fontFamily = Arcade, fontSize = 11.sp)
                            ArcadeStripes(vibe.stripes, Modifier.fillMaxWidth(), band = 3.dp, gap = 1.dp)
                        }
                    }
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
    RetroBlock(onClick = onClick, contentPadding = PaddingValues(8.dp)) {
        Column {
            // A little television screen, gently rounded like the glass of a tube.
            Box(
                Modifier
                    .fillMaxWidth()
                    .aspectRatio(1f)
                    .clip(RoundedCornerShape(14.dp))
                    .background(vibe.soft)
                    .scanlines(Color.Black.copy(alpha = if (vibe.isDark) 0.22f else 0.06f)),
                contentAlignment = Alignment.Center,
            ) {
                bitmap?.let { Image(it, contentDescription = null, contentScale = ContentScale.Fit, modifier = Modifier.fillMaxSize().padding(6.dp)) }
            }
            val caption = config?.caption?.takeIf { it.isNotBlank() }
            Text(
                caption ?: config?.let { "${it.shape.label} · ${it.photos.size} photo${if (it.photos.size == 1) "" else "s"}".uppercase() } ?: "NEEDS PHOTOS",
                fontFamily = if (caption != null && config != null) config.font.family else Vcr,
                fontSize = if (caption != null) 18.sp else 20.sp,
                color = vibe.ink,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(start = 4.dp, top = 8.dp),
            )
            Row(Modifier.padding(start = 4.dp, bottom = 2.dp), verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.padding(end = 6.dp).height(8.dp).aspectRatio(1f).background(vibe.accent))
                Text("TAP TO EDIT", fontFamily = Vcr, fontSize = 17.sp, color = vibe.ink.copy(alpha = 0.6f))
            }
        }
    }
}

@Composable
private fun EmptyCard(onNew: () -> Unit) {
    val vibe = LocalVibe.current
    val blink = rememberBlink(650L)
    Box(
        Modifier
            .fillMaxWidth()
            .background(vibe.surface.copy(alpha = 0.7f))
            .drawBehind {
                drawRect(
                    color = vibe.primary.copy(alpha = 0.7f),
                    style = Stroke(width = 3.dp.toPx(), pathEffect = PathEffect.dashPathEffect(floatArrayOf(12f, 10f))),
                )
            }
            .clickable(onClick = onNew)
            .padding(horizontal = 20.dp, vertical = 26.dp),
        contentAlignment = Alignment.Center,
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                "INSERT PHOTO",
                color = if (blink) vibe.primary else Color.Transparent,
                fontFamily = Arcade,
                fontSize = 16.sp,
            )
            Text("NO WIDGETS YET", color = vibe.ink, fontFamily = Vcr, fontSize = 24.sp, modifier = Modifier.padding(top = 14.dp))
            Text(
                "make your first one, it only takes a minute",
                textAlign = TextAlign.Center,
                color = vibe.ink.copy(alpha = 0.7f),
                fontFamily = Vcr,
                fontSize = 19.sp,
            )
        }
    }
}

@Composable
private fun TipCard() {
    val vibe = LocalVibe.current
    Text(
        "tip: you can also long-press your home screen > widgets > djungleskwidgets.\n" +
            "tap a widget to flip to the next photo.",
        textAlign = TextAlign.Center,
        fontFamily = Vcr,
        fontSize = 18.sp,
        color = vibe.ink.copy(alpha = 0.6f),
        modifier = Modifier.fillMaxWidth().padding(top = 6.dp),
    )
}
