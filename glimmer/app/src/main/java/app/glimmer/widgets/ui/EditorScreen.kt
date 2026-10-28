package app.glimmer.widgets.ui

import android.graphics.RectF
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
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
import androidx.compose.ui.draw.shadow
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.glimmer.widgets.data.CaptionFont
import app.glimmer.widgets.data.FrameShape
import app.glimmer.widgets.data.PhotoFilter
import app.glimmer.widgets.data.PhotoRef
import app.glimmer.widgets.data.PhotoStore
import app.glimmer.widgets.data.ShuffleEvery
import app.glimmer.widgets.data.Stickers
import app.glimmer.widgets.data.Swatches
import app.glimmer.widgets.data.TapAction
import app.glimmer.widgets.data.WidgetConfig
import app.glimmer.widgets.render.Shapes
import app.glimmer.widgets.render.WidgetRenderer
import app.glimmer.widgets.ui.theme.LocalVibe
import app.glimmer.widgets.ui.theme.Pacifico
import app.glimmer.widgets.ui.theme.family
import kotlinx.coroutines.Dispatchers
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

    val current: Int? = if (config.photos.isEmpty()) null else selected.coerceIn(0, config.photos.lastIndex)

    val picker = rememberLauncherForActivityResult(ActivityResultContracts.PickMultipleVisualMedia()) { uris ->
        if (uris.isEmpty()) return@rememberLauncherForActivityResult
        scope.launch {
            importing = true
            val room = (MAX_PHOTOS - config.photos.size).coerceAtLeast(0)
            val names = uris.take(room).mapNotNull { PhotoStore.import(context, it) }
            if (names.isNotEmpty()) {
                val firstNew = config.photos.size
                config = config.copy(photos = config.photos + names.map { PhotoRef(it) })
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

    Box(Modifier.fillMaxSize().background(vibe.background)) {
        SparkleField(vibe.accent, Modifier.fillMaxSize())
        Column(Modifier.fillMaxSize().statusBarsPadding()) {
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                PillChip("‹ back", selected = false, onClick = onClose)
                Text(
                    title,
                    fontFamily = Pacifico,
                    fontSize = 24.sp,
                    color = vibe.primary,
                    modifier = Modifier.weight(1f).padding(horizontal = 8.dp),
                    maxLines = 1,
                    textAlign = TextAlign.Center,
                )
                PillChip("🎲 surprise", selected = false, onClick = { config = config.surprise() })
            }

            // The live preview, on a little stand-in wallpaper.
            Box(
                Modifier
                    .padding(horizontal = 20.dp, vertical = 6.dp)
                    .fillMaxWidth()
                    .height(280.dp)
                    .shadow(16.dp, RoundedCornerShape(36.dp), ambientColor = vibe.primary, spotColor = vibe.primary)
                    .clip(RoundedCornerShape(36.dp))
                    .background(Brush.linearGradient(listOf(vibe.soft, vibe.accent.copy(alpha = 0.55f), vibe.primary.copy(alpha = 0.5f))))
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
                if (importing) {
                    CircularProgressIndicator(color = vibe.primary, modifier = Modifier.align(Alignment.Center))
                }
                if (config.photos.isNotEmpty()) {
                    Text(
                        "pinch & drag to frame · double-tap to reset",
                        fontSize = 11.sp,
                        color = vibe.ink.copy(alpha = 0.7f),
                        modifier = Modifier
                            .align(Alignment.TopCenter)
                            .padding(top = 8.dp)
                            .clip(CircleShape)
                            .background(vibe.surface.copy(alpha = 0.7f))
                            .padding(horizontal = 10.dp, vertical = 3.dp),
                    )
                }
                if (config.photos.size > 1) {
                    Row(
                        Modifier.align(Alignment.BottomCenter).padding(bottom = 10.dp),
                        horizontalArrangement = Arrangement.spacedBy(5.dp),
                    ) {
                        config.photos.indices.take(12).forEach { i ->
                            Box(
                                Modifier
                                    .size(if (i == current) 8.dp else 6.dp)
                                    .clip(CircleShape)
                                    .background(if (i == current) vibe.surface else vibe.surface.copy(alpha = 0.55f)),
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

                Section("sticker") {
                    LazyRow(
                        contentPadding = PaddingValues(horizontal = 20.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        item { StickerTile("none", config.sticker.isEmpty(), small = true) { config = config.copy(sticker = "") } }
                        items(Stickers) { sticker ->
                            StickerTile(sticker, sticker == config.sticker) { config = config.copy(sticker = sticker) }
                        }
                    }
                }

                Section("caption") {
                    OutlinedTextField(
                        value = config.caption,
                        onValueChange = { config = config.copy(caption = it.take(28)) },
                        placeholder = { Text("say something cute…", fontFamily = config.font.family, fontSize = 18.sp) },
                        singleLine = true,
                        textStyle = TextStyle(fontFamily = config.font.family, fontSize = 20.sp, color = vibe.ink),
                        keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.None),
                        shape = RoundedCornerShape(22.dp),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = vibe.primary,
                            unfocusedBorderColor = vibe.soft,
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
                            PillChip(font.label, font == config.font, onClick = { config = config.copy(font = font) }, fontFamily = font.family, fontSize = 16.sp)
                        }
                    }
                    Spacer(Modifier.height(10.dp))
                    LazyRow(
                        contentPadding = PaddingValues(horizontal = 20.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        item {
                            SwatchDot(null, config.captionColor == null, "automatic colour", onClick = { config = config.copy(captionColor = null) }) {
                                Text("auto", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = vibe.ink)
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

                Section("finishing touches") {
                    ToggleRow("film grain", "a soft analogue texture", config.grain) { config = config.copy(grain = it) }
                    ToggleRow("soft shadow", "lifts it off your wallpaper", config.shadow) { config = config.copy(shadow = it) }
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
            GlimmerButton(
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

@Composable
private fun Section(title: String, content: @Composable () -> Unit) {
    Column(Modifier.padding(top = 18.dp)) {
        SectionTitle(title, Modifier.padding(start = 24.dp, bottom = 10.dp))
        content()
    }
}

@Composable
private fun Hint(text: String) {
    Text(
        text,
        fontSize = 12.sp,
        color = LocalVibe.current.ink.copy(alpha = 0.55f),
        modifier = Modifier.padding(start = 24.dp, top = 8.dp),
    )
}

@Composable
private fun AddPhotoTile(onClick: () -> Unit) {
    val vibe = LocalVibe.current
    Box(
        Modifier
            .padding(top = 6.dp)
            .size(70.dp)
            .clip(RoundedCornerShape(20.dp))
            .background(vibe.soft)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text("+", fontSize = 26.sp, fontWeight = FontWeight.Bold, color = vibe.primary)
            Text("add", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = vibe.primary)
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
    Box(Modifier.size(76.dp)) {
        Box(
            Modifier
                .align(Alignment.BottomStart)
                .size(70.dp)
                .clip(RoundedCornerShape(20.dp))
                .background(vibe.soft)
                .then(if (selected) Modifier.border(3.dp, vibe.primary, RoundedCornerShape(20.dp)) else Modifier)
                .clickable(onClick = onClick),
        ) {
            thumb?.let {
                Image(
                    it,
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize().padding(if (selected) 3.dp else 0.dp).clip(RoundedCornerShape(17.dp)),
                )
            }
        }
        Box(
            Modifier
                .align(Alignment.TopEnd)
                .size(24.dp)
                .clip(CircleShape)
                .background(vibe.ink.copy(alpha = 0.78f))
                .clickable(onClick = onRemove),
            contentAlignment = Alignment.Center,
        ) {
            Text("×", color = Color.White, fontSize = 15.sp, fontWeight = FontWeight.Bold)
        }
    }
}

@Composable
private fun ShapeTile(shape: FrameShape, selected: Boolean, onClick: () -> Unit) {
    val vibe = LocalVibe.current
    val fill = if (selected) vibe.primary else vibe.surface
    val ink = if (selected) vibe.onPrimary else vibe.primary
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Box(
            Modifier
                .size(64.dp)
                .clip(RoundedCornerShape(22.dp))
                .background(fill)
                .border(1.5.dp, if (selected) vibe.primary else vibe.soft, RoundedCornerShape(22.dp))
                .clickable(onClick = onClick)
                .padding(14.dp),
        ) {
            Canvas(Modifier.fillMaxSize()) {
                val box = Shapes.box(shape, RectF(0f, 0f, size.width, size.height))
                drawPath(Shapes.outline(shape, box).asComposePath(), ink)
                if (shape.isCard) drawPath(Shapes.photoClip(shape, box).asComposePath(), fill)
            }
        }
        Text(shape.label, fontSize = 12.sp, color = vibe.ink, modifier = Modifier.padding(top = 4.dp))
    }
}

@Composable
private fun FilterTile(filter: PhotoFilter, thumb: ImageBitmap?, selected: Boolean, onClick: () -> Unit) {
    val vibe = LocalVibe.current
    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.width(72.dp)) {
        Box(
            Modifier
                .size(64.dp)
                .clip(RoundedCornerShape(20.dp))
                .background(vibe.soft)
                .then(if (selected) Modifier.border(3.dp, vibe.primary, RoundedCornerShape(20.dp)) else Modifier)
                .clickable(onClick = onClick),
        ) {
            thumb?.let {
                Image(
                    it,
                    contentDescription = filter.label,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize().padding(if (selected) 3.dp else 0.dp).clip(RoundedCornerShape(17.dp)),
                )
            }
        }
        Text(
            filter.label,
            fontSize = 12.sp,
            maxLines = 1,
            color = if (selected) vibe.primary else vibe.ink,
            fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
            modifier = Modifier.padding(top = 4.dp),
        )
    }
}

@Composable
private fun StickerTile(sticker: String, selected: Boolean, small: Boolean = false, onClick: () -> Unit) {
    val vibe = LocalVibe.current
    Box(
        Modifier
            .size(52.dp)
            .clip(RoundedCornerShape(18.dp))
            .background(if (selected) vibe.soft else vibe.surface)
            .border(if (selected) 2.5.dp else 1.5.dp, if (selected) vibe.primary else vibe.soft, RoundedCornerShape(18.dp))
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Text(sticker, fontSize = if (small) 12.sp else 24.sp, fontWeight = FontWeight.Bold, color = vibe.ink)
    }
}
