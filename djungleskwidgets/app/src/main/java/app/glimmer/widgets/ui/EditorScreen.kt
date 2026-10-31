package app.glimmer.widgets.ui

import android.graphics.RectF
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.Saver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asComposePath
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.glimmer.widgets.data.CaptionFont
import app.glimmer.widgets.data.FrameShape
import app.glimmer.widgets.data.PhotoFilter
import app.glimmer.widgets.data.PhotoStore
import app.glimmer.widgets.data.ShuffleEvery
import app.glimmer.widgets.data.Swatches
import app.glimmer.widgets.data.TapAction
import app.glimmer.widgets.data.WidgetConfig
import app.glimmer.widgets.render.Shapes
import app.glimmer.widgets.render.WidgetRenderer
import app.glimmer.widgets.ui.theme.LocalVibe
import app.glimmer.widgets.ui.theme.Arcade
import app.glimmer.widgets.ui.theme.Vcr
import app.glimmer.widgets.ui.theme.family
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject
import kotlin.math.roundToInt

private const val MAX_PHOTOS = 30
private const val PREVIEW_SIDE = 760

private val ConfigSaver = Saver<WidgetConfig, String>(
    save = { it.toJson().toString() },
    restore = { WidgetConfig.fromJson(JSONObject(it)) },
)

/**
 * Designs one widget. The big preview at the top is drawn by the same renderer as the real
 * widget, and can be pinched and dragged to frame the photo.
 */
@Composable
fun EditorScreen(
    title: String,
    initial: WidgetConfig,
    previewAspect: Float,
    saveLabel: String,
    onSave: (WidgetConfig) -> Unit,
    onClose: () -> Unit,
) {
    val context = LocalContext.current
    val vibe = LocalVibe.current
    val scope = rememberCoroutineScope()
    var config by rememberSaveable(stateSaver = ConfigSaver) { mutableStateOf(initial) }
    var selected by rememberSaveable { mutableIntStateOf(initial.index.coerceAtLeast(0)) }
    var importing by remember { mutableStateOf(false) }
    var lastSave by remember { mutableLongStateOf(0L) }
    var showFramingHint by remember { mutableStateOf(false) }

    val current: Int? = if (config.photos.isEmpty()) null else selected.coerceIn(0, config.photos.lastIndex)

    val picker = rememberLauncherForActivityResult(ActivityResultContracts.PickMultipleVisualMedia()) { uris ->
        if (uris.isEmpty()) return@rememberLauncherForActivityResult
        scope.launch {
            importing = true
            val room = (MAX_PHOTOS - config.photos.size).coerceAtLeast(0)
            val added = uris.take(room).mapNotNull { PhotoStore.import(context, it) }
            if (added.isNotEmpty()) {
                val firstNew = config.photos.size
                config = config.copy(photos = config.photos + added)
                selected = firstNew
            }
            importing = false
        }
    }
    val pickPhotos = { picker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) }

    val (previewW, previewH) = remember(previewAspect) {
        val aspect = previewAspect.coerceIn(0.4f, 2.5f)
        if (aspect >= 1f) PREVIEW_SIDE to (PREVIEW_SIDE / aspect).roundToInt()
        else (PREVIEW_SIDE * aspect).roundToInt() to PREVIEW_SIDE
    }
    var preview by remember { mutableStateOf<ImageBitmap?>(null) }
    LaunchedEffect(previewW, previewH) {
        snapshotFlow {
            val photos = config.photos
            config.copy(index = if (photos.isEmpty()) 0 else selected.coerceIn(0, photos.lastIndex))
        }.collectLatest { shown ->
            preview = withContext(Dispatchers.Default) {
                WidgetRenderer.render(context, shown, previewW, previewH).asImageBitmap()
            }
        }
    }

    val thumbSource = current?.let { config.photos[it].copy(focusX = 0.5f, focusY = 0.5f, zoom = 1f) }
    val filterThumbs by produceState(emptyMap<PhotoFilter, ImageBitmap>(), thumbSource?.file) {
        value = withContext(Dispatchers.Default) {
            PhotoFilter.entries.associateWith { WidgetRenderer.filterThumb(context, thumbSource, it, 168).asImageBitmap() }
        }
    }

    val keyboardOpen = WindowInsets.ime.getBottom(LocalDensity.current) > 0

    // Explain the gestures briefly once there is a photo to frame, then get out of the way.
    val hasPhotos = config.photos.isNotEmpty()
    LaunchedEffect(hasPhotos) {
        showFramingHint = hasPhotos
        delay(4000)
        showFramingHint = false
    }

    val shadow = blockShadow()
    Box(Modifier.fillMaxSize()) {
        RetroBackdrop(Modifier.fillMaxSize())
        Column(Modifier.fillMaxSize().statusBarsPadding()) {
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                RetroChip("‹ back", selected = false, onClick = onClose)
                Text(
                    title.uppercase(),
                    fontFamily = Arcade,
                    fontSize = 10.sp,
                    color = vibe.primary,
                    modifier = Modifier.weight(1f).padding(horizontal = 8.dp),
                    maxLines = 1,
                    textAlign = TextAlign.Center,
                )
                RetroChip("🎲 surprise", selected = false, onClick = { config = config.surprise() })
            }

            // The live preview, on a little stand-in wallpaper, seen through a camcorder's viewfinder.
            val screen = RoundedCornerShape(22.dp)
            Box(
                Modifier
                    .padding(start = 20.dp, end = 15.dp, top = 6.dp, bottom = 1.dp)
                    .fillMaxWidth()
                    .height(280.dp)
                    .padding(end = 5.dp, bottom = 5.dp)
                    .drawBehind {
                        drawRoundRect(shadow, Offset(5.dp.toPx(), 5.dp.toPx()), size, CornerRadius(22.dp.toPx()))
                    }
                    .clip(screen)
                    .background(vibe.soft)
                    .background(Brush.linearGradient(listOf(vibe.soft, vibe.accent.copy(alpha = 0.55f), vibe.primary.copy(alpha = 0.5f))))
                    .scanlines(Color.Black.copy(alpha = 0.1f))
                    .border(3.dp, shadow, screen)
                    .clickable(enabled = config.photos.isEmpty() && !importing, onClick = pickPhotos),
            ) {
                preview?.let { bitmap ->
                    Image(
                        bitmap,
                        contentDescription = "widget preview",
                        contentScale = ContentScale.Fit,
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(16.dp)
                            .pointerInput(current) {
                                val position = current ?: return@pointerInput
                                detectTransformGestures { _, pan, zoomChange, _ ->
                                    showFramingHint = false
                                    val photo = config.photos.getOrNull(position) ?: return@detectTransformGestures
                                    val zoom = (photo.zoom * zoomChange).coerceIn(1f, 4f)
                                    // Convert finger movement into a shift of the photo's focus point.
                                    val photoAspect = PhotoStore.aspect(context, photo.file) ?: 1f
                                    val frameAspect = size.width.toFloat() / size.height
                                    val spanX = size.width * zoom * maxOf(1f, photoAspect / frameAspect)
                                    val spanY = size.height * zoom * maxOf(1f, frameAspect / photoAspect)
                                    config = config.withPhoto(
                                        position,
                                        photo.copy(
                                            focusX = (photo.focusX - pan.x / spanX).coerceIn(0f, 1f),
                                            focusY = (photo.focusY - pan.y / spanY).coerceIn(0f, 1f),
                                            zoom = zoom,
                                        ),
                                    )
                                }
                            }
                            .pointerInput(current) {
                                val position = current ?: return@pointerInput
                                detectTapGestures(onDoubleTap = {
                                    config.photos.getOrNull(position)?.let {
                                        config = config.withPhoto(position, it.copy(focusX = 0.5f, focusY = 0.5f, zoom = 1f))
                                    }
                                })
                            },
                    )
                }
                PlayMark(Modifier.align(Alignment.TopStart).padding(start = 14.dp, top = 10.dp), size = 20.sp)
                Text("SP", style = osdStyle(20.sp), modifier = Modifier.align(Alignment.TopEnd).padding(end = 16.dp, top = 10.dp))
                VhsClock(Modifier.align(Alignment.BottomStart).padding(start = 14.dp, bottom = 10.dp), size = 20.sp)
                if (importing) {
                    val blink = rememberBlink(450L)
                    Text(
                        "LOADING",
                        style = osdStyle(26.sp, if (blink) Color.White else Color.Transparent),
                        modifier = Modifier.align(Alignment.Center),
                    )
                }
                FramingHint(showFramingHint, Modifier.align(Alignment.Center))
                if (config.photos.size > 1) {
                    Row(
                        // Along the top, between PLAY and SP, clear of the clock in the bottom corner.
                        Modifier.align(Alignment.TopCenter).padding(top = 18.dp),
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        config.photos.indices.take(12).forEach { i ->
                            Box(
                                Modifier
                                    .size(if (i == current) 8.dp else 6.dp)
                                    .background(if (i == current) Color.White else Color.White.copy(alpha = 0.5f)),
                            )
                        }
                    }
                }
            }

            Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).imePadding()) {
                Section("photos" + if (config.photos.isNotEmpty()) " · ${config.photos.size}" else "") {
                    LazyRow(
                        contentPadding = PaddingValues(horizontal = 20.dp),
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        item { AddPhotoTile(onClick = pickPhotos) }
                        itemsIndexed(config.photos, key = { _, photo -> photo.file }) { i, photo ->
                            PhotoThumb(
                                file = photo.file,
                                selected = i == current,
                                onClick = { selected = i },
                                onRemove = {
                                    config = config.copy(photos = config.photos.filterIndexed { j, _ -> j != i })
                                    if (selected >= config.photos.size) selected = (config.photos.size - 1).coerceAtLeast(0)
                                },
                            )
                        }
                    }
                }

                Section("shape") {
                    LazyRow(
                        contentPadding = PaddingValues(horizontal = 20.dp),
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        items(FrameShape.entries) { shape ->
                            ShapeTile(shape, shape == config.shape) { config = config.copy(shape = shape) }
                        }
                    }
                }

                Section("filter") {
                    LazyRow(
                        contentPadding = PaddingValues(horizontal = 20.dp),
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        items(PhotoFilter.entries) { filter ->
                            FilterTile(filter, filterThumbs[filter], filter == config.filter) {
                                config = config.copy(filter = filter)
                            }
                        }
                    }
                }

                Section(if (config.shape.isCard) "paper colour" else "outline") {
                    LazyRow(
                        contentPadding = PaddingValues(horizontal = 20.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        if (!config.shape.isCard) {
                            item {
                                SwatchDot(null, config.frameColor == null, "no outline", onClick = { config = config.copy(frameColor = null) }) {
                                    Box(Modifier.fillMaxSize().noneSlash(vibe.primary))
                                }
                            }
                        }
                        items(Swatches) { swatch ->
                            val chosen = config.frameColor == swatch.argb ||
                                (config.shape.isCard && config.frameColor == null && swatch.name == "white")
                            SwatchDot(Color(swatch.argb), chosen, swatch.name, onClick = { config = config.copy(frameColor = swatch.argb) })
                        }
                    }
                }

                Section("caption") {
                    OutlinedTextField(
                        value = config.caption,
                        onValueChange = { config = config.copy(caption = it.take(28)) },
                        placeholder = { Text("type a caption…", fontFamily = config.font.family, fontSize = 18.sp) },
                        singleLine = true,
                        textStyle = TextStyle(fontFamily = config.font.family, fontSize = 20.sp, color = vibe.ink),
                        keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.None),
                        shape = RoundedCornerShape(4.dp),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = vibe.primary,
                            unfocusedBorderColor = shadow,
                            focusedContainerColor = vibe.surface,
                            unfocusedContainerColor = vibe.surface,
                            cursorColor = vibe.primary,
                            focusedTextColor = vibe.ink,
                            unfocusedTextColor = vibe.ink,
                            focusedPlaceholderColor = vibe.ink.copy(alpha = 0.4f),
                            unfocusedPlaceholderColor = vibe.ink.copy(alpha = 0.4f),
                        ),
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp),
                    )
                    Spacer(Modifier.height(10.dp))
                    LazyRow(
                        contentPadding = PaddingValues(horizontal = 20.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        items(CaptionFont.entries) { font ->
                            RetroChip(font.label, font == config.font, onClick = { config = config.copy(font = font) }, fontFamily = font.family)
                        }
                    }
                    Spacer(Modifier.height(10.dp))
                    LazyRow(
                        contentPadding = PaddingValues(horizontal = 20.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        item {
                            SwatchDot(null, config.captionColor == null, "automatic colour", onClick = { config = config.copy(captionColor = null) }) {
                                Text("AUTO", fontFamily = Vcr, fontSize = 16.sp, color = vibe.ink)
                            }
                        }
                        items(Swatches) { swatch ->
                            SwatchDot(Color(swatch.argb), config.captionColor == swatch.argb, swatch.name, onClick = { config = config.copy(captionColor = swatch.argb) })
                        }
                    }
                }

                Section("tilt") {
                    ChipRow(listOf(0f, -4f, 4f), config.tilt, label = { tiltLabel(it) }) { config = config.copy(tilt = it) }
                }

                Section("opacity · ${(config.opacity * 100).roundToInt()}%") {
                    SegmentBar(
                        value = config.opacity,
                        min = WidgetConfig.MIN_OPACITY,
                        steps = ((1f - WidgetConfig.MIN_OPACITY) * 20).roundToInt() + 1,
                        label = "opacity",
                        onChange = { config = config.copy(opacity = (it * 20).roundToInt() / 20f) },
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp),
                    )
                    Hint(opacityHint(config.opacity))
                }

                Section("finishing touches") {
                    ToggleRow("film grain", "a soft analogue texture", config.grain) { config = config.copy(grain = it) }
                    ToggleRow("soft shadow", "lifts it off your wallpaper", config.shadow) { config = config.copy(shadow = it) }
                    ToggleRow("camcorder stamp", "PLAY and the day it was shot, like a home video", config.camcorder) {
                        config = config.copy(camcorder = it)
                    }
                }

                Section("shuffle photos") {
                    ChipRow(ShuffleEvery.entries, config.shuffle, label = { it.label }) { config = config.copy(shuffle = it) }
                    Hint(if (config.photos.size < 2) "add two or more photos and they'll take turns" else "swaps to a random photo on its own")
                }

                Section("when tapped") {
                    ChipRow(TapAction.entries, config.tapAction, label = { it.label }) { config = config.copy(tapAction = it) }
                }

                Spacer(Modifier.navigationBarsPadding().height(if (keyboardOpen) 24.dp else 120.dp))
            }
        }

        if (!keyboardOpen) {
            DjungleskwidgetsButton(
                saveLabel,
                onClick = {
                    val now = System.currentTimeMillis()
                    if (now - lastSave > 1500) {
                        lastSave = now
                        onSave(config.copy(index = current ?: 0))
                    }
                },
                modifier = Modifier.align(Alignment.BottomCenter).navigationBarsPadding().padding(bottom = 18.dp),
            )
        }
    }
}

private fun tiltLabel(tilt: Float) = when {
    tilt < 0f -> "lean left"
    tilt > 0f -> "lean right"
    else -> "straight"
}

private fun opacityHint(opacity: Float) = when {
    opacity >= 0.99f -> "fully solid"
    opacity >= 0.75f -> "a little sheer"
    opacity >= 0.5f -> "misty, your wallpaper peeks through"
    else -> "ghostly, more wallpaper than widget"
}

@Composable
private fun Section(title: String, content: @Composable () -> Unit) {
    Column(Modifier.padding(top = 18.dp)) {
        SectionTitle(title, Modifier.padding(start = 22.dp, bottom = 8.dp))
        content()
    }
}

@Composable
private fun FramingHint(visible: Boolean, modifier: Modifier) {
    AnimatedVisibility(visible = visible, enter = fadeIn(), exit = fadeOut(), modifier = modifier) {
        Text(
            "PINCH & DRAG TO FRAME\nDOUBLE-TAP TO RESET",
            style = osdStyle(19.sp),
            textAlign = TextAlign.Center,
            modifier = Modifier
                .background(Color.Black.copy(alpha = 0.6f))
                .padding(horizontal = 10.dp, vertical = 4.dp),
        )
    }
}

@Composable
private fun Hint(text: String) {
    Text(
        text,
        fontFamily = Vcr,
        fontSize = 18.sp,
        color = LocalVibe.current.ink.copy(alpha = 0.6f),
        modifier = Modifier.padding(start = 22.dp, top = 6.dp),
    )
}

@Composable
private fun AddPhotoTile(onClick: () -> Unit) {
    val vibe = LocalVibe.current
    RetroBlock(Modifier.padding(top = 6.dp).size(74.dp), fill = vibe.soft, onClick = onClick) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text("+", fontFamily = Arcade, fontSize = 18.sp, color = vibe.primary)
            Text("ADD", fontFamily = Vcr, fontSize = 18.sp, color = vibe.primary, modifier = Modifier.padding(top = 4.dp))
        }
    }
}

@Composable
private fun PhotoThumb(file: String, selected: Boolean, onClick: () -> Unit, onRemove: () -> Unit) {
    val context = LocalContext.current
    val vibe = LocalVibe.current
    val thumb by produceState<ImageBitmap?>(null, file) {
        value = withContext(Dispatchers.IO) { PhotoStore.load(context, file, 200)?.asImageBitmap() }
    }
    Box(Modifier.size(80.dp)) {
        RetroBlock(
            Modifier.align(Alignment.BottomStart).size(74.dp),
            fill = vibe.soft,
            border = if (selected) vibe.primary else blockShadow(),
            onClick = onClick,
            held = selected,
            depth = 4.dp,
        ) {
            thumb?.let {
                Image(
                    it,
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize().padding(if (selected) 4.dp else 2.dp),
                )
            }
        }
        Box(
            Modifier
                .align(Alignment.TopEnd)
                .size(24.dp)
                .background(blockShadow())
                .border(2.dp, vibe.ink.copy(alpha = 0.8f))
                .clickable(onClick = onRemove),
            contentAlignment = Alignment.Center,
        ) {
            Text("×", color = Color.White, fontFamily = Vcr, fontSize = 22.sp)
        }
    }
}

@Composable
private fun ShapeTile(shape: FrameShape, selected: Boolean, onClick: () -> Unit) {
    val vibe = LocalVibe.current
    val fill = if (selected) vibe.primary else vibe.surface
    val ink = if (selected) vibe.onPrimary else vibe.primary
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        RetroBlock(
            Modifier.size(68.dp),
            fill = fill,
            onClick = onClick,
            held = selected,
            contentPadding = PaddingValues(14.dp),
        ) {
            Canvas(Modifier.fillMaxSize()) {
                val box = Shapes.box(shape, RectF(0f, 0f, size.width, size.height))
                drawPath(Shapes.outline(shape, box).asComposePath(), ink)
                if (shape.isCard) drawPath(Shapes.photoClip(shape, box).asComposePath(), fill)
            }
        }
        Text(shape.label.uppercase(), fontFamily = Vcr, fontSize = 17.sp, color = vibe.ink, modifier = Modifier.padding(top = 2.dp))
    }
}

@Composable
private fun FilterTile(filter: PhotoFilter, thumb: ImageBitmap?, selected: Boolean, onClick: () -> Unit) {
    val vibe = LocalVibe.current
    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.width(80.dp)) {
        RetroBlock(
            Modifier.size(68.dp),
            fill = vibe.soft,
            border = if (selected) vibe.primary else blockShadow(),
            onClick = onClick,
            held = selected,
        ) {
            thumb?.let {
                Image(
                    it,
                    contentDescription = filter.label,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize().padding(if (selected) 4.dp else 2.dp),
                )
            }
        }
        Text(
            filter.label.uppercase(),
            fontFamily = Vcr,
            fontSize = 17.sp,
            maxLines = 1,
            color = if (selected) vibe.primary else vibe.ink,
            modifier = Modifier.padding(top = 2.dp),
        )
    }
}
