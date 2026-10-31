package app.glimmer.widgets.ui

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import app.glimmer.widgets.data.AppSettings
import app.glimmer.widgets.data.WidgetConfig
import app.glimmer.widgets.data.WidgetStore
import app.glimmer.widgets.ui.theme.DjungleskwidgetsTheme
import app.glimmer.widgets.ui.theme.LocalVibe
import app.glimmer.widgets.widget.WidgetUpdater
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

// Real widget ids are always positive, so these can't clash with them.
private const val HOME = Int.MIN_VALUE
private const val NEW = -1

/** The whole app: the widget list, and the editor for a new or existing widget. */
@Composable
fun DjungleskwidgetsApp(requestedWidget: Int?, onRequestHandled: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var vibe by remember { mutableStateOf(AppSettings.vibe(context)) }
    var screen by rememberSaveable { mutableIntStateOf(HOME) }
    var burst by remember { mutableIntStateOf(0) }
    var toast by remember { mutableStateOf("") }
    var toastShown by remember { mutableIntStateOf(0) }
    var toastVisible by remember { mutableStateOf(false) }
    var showPinHelp by remember { mutableStateOf(false) }

    fun say(message: String) {
        toast = message
        toastVisible = true
        toastShown++
    }

    LaunchedEffect(requestedWidget) {
        if (requestedWidget != null) {
            screen = requestedWidget
            onRequestHandled()
        }
    }
    LaunchedEffect(toastShown) {
        delay(3200)
        toastVisible = false
    }

    DjungleskwidgetsTheme(vibe) {
        val colors = LocalVibe.current
        Box(Modifier.fillMaxSize().background(colors.background)) {
            AnimatedContent(
                targetState = screen,
                transitionSpec = {
                    (fadeIn(tween(260)) + scaleIn(tween(260), initialScale = 0.97f)) togetherWith fadeOut(tween(180))
                },
                label = "screen",
            ) { target ->
                when (target) {
                    HOME -> HomeScreen(
                        onVibeChange = {
                            vibe = it
                            AppSettings.setVibe(context, it)
                        },
                        onEdit = { screen = it },
                        onNew = { screen = NEW },
                    )
                    NEW -> EditorScreen(
                        title = "new widget",
                        initial = remember { WidgetStore.peekPendingDraft(context) ?: WidgetConfig() },
                        previewAspect = 1f,
                        saveLabel = "add to home screen ✧",
                        onSave = { design ->
                            if (WidgetUpdater.requestPin(context, design)) {
                                screen = HOME
                                burst++
                                say("nearly there, confirm on your home screen ✧")
                            } else {
                                WidgetStore.savePendingDraft(context, design)
                                showPinHelp = true
                            }
                        },
                        onClose = { screen = HOME },
                    )
                    else -> {
                        val initial = remember(target) { WidgetStore.load(context, target) ?: WidgetConfig() }
                        val aspect = remember(target) {
                            WidgetUpdater.sizePx(context, target).let { (w, h) -> w.toFloat() / h }
                        }
                        EditorScreen(
                            title = "edit widget",
                            initial = initial,
                            previewAspect = aspect,
                            saveLabel = "save ✧",
                            onSave = { design ->
                                scope.launch {
                                    withContext(Dispatchers.Default) { WidgetUpdater.save(context, target, design) }
                                    screen = HOME
                                    burst++
                                    say("saved ✧ your widget is updated")
                                }
                            },
                            onClose = { screen = HOME },
                        )
                    }
                }
            }

            SparkleBurst(burst, listOf(colors.primary, colors.accent, colors.soft), Modifier.fillMaxSize())
            ToastPill(
                toast,
                toastVisible,
                Modifier.align(Alignment.BottomCenter).navigationBarsPadding().padding(bottom = 96.dp),
            )

            if (showPinHelp) {
                AlertDialog(
                    onDismissRequest = { showPinHelp = false },
                    confirmButton = {
                        TextButton(onClick = {
                            showPinHelp = false
                            screen = HOME
                        }) { Text("got it ✧", fontWeight = FontWeight.Bold, color = colors.primary) }
                    },
                    title = { Text("add it from your home screen", color = colors.ink) },
                    text = {
                        Text(
                            "your phone wants you to place widgets yourself: long-press an empty spot on " +
                                "your home screen → widgets → djungleskwidgets. this design is saved and will be " +
                                "waiting for you for the next half hour ✧",
                            color = colors.ink,
                        )
                    },
                    containerColor = colors.surface,
                )
            }
        }

        BackHandler(enabled = screen != HOME) { screen = HOME }
    }
}
