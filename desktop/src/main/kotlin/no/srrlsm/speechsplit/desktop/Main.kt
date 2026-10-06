package no.srrlsm.speechsplit.desktop

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.painter.BitmapPainter
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.graphics.toComposeImageBitmap
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEvent
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.type
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.WindowPosition
import androidx.compose.ui.window.application
import androidx.compose.ui.window.rememberWindowState
import kotlinx.coroutines.delay
import no.srrlsm.speechsplit.core.AppController
import no.srrlsm.speechsplit.core.AppScreen
import no.srrlsm.speechsplit.core.ThemeMode
import no.srrlsm.speechsplit.ui.LocalUiActions
import no.srrlsm.speechsplit.ui.SpeechSplitApp
import no.srrlsm.speechsplit.ui.UiActions
import no.srrlsm.speechsplit.ui.theme.AppTheme
import no.srrlsm.speechsplit.ui.theme.SpeechSplitTheme
import java.awt.Dimension
import java.awt.Robot
import java.awt.Toolkit
import java.awt.datatransfer.StringSelection
import java.awt.event.KeyEvent as AwtKeyEvent
import javax.imageio.ImageIO

// ============================================================================
// SPEECH SPLIT FOR WINDOWS (and Mac/Linux)
// The same app logic (core/) and screens (ui/) as the Android app, in a desktop window.
// ============================================================================
fun main() = application {
    val scope = rememberCoroutineScope()
    val app = remember { AppController(DesktopStore(), DesktopPlatform(), scope) }
    val ui = remember { DesktopUi(copiedText = { app.strings.copiedToClipboard }) }
    val keys = remember { KeyHandler(app) }
    val windowState = rememberWindowState(
        width = 440.dp,
        height = 820.dp,
        position = WindowPosition.Aligned(Alignment.Center),
    )

    Window(
        onCloseRequest = {
            app.dispose()
            exitApplication()
        },
        title = "Speech Split",
        icon = remember { loadIcon() },
        state = windowState,
        onPreviewKeyEvent = keys::handle,
    ) {
        LaunchedEffect(Unit) { window.minimumSize = Dimension(320, 480) }

        val dark = when (app.settings.themeMode) {
            ThemeMode.SYSTEM -> isSystemInDarkTheme()
            ThemeMode.LIGHT -> false
            ThemeMode.DARK -> true
        }
        KeepAwake(app.keepScreenOn)

        // One-off messages from the app logic
        val message = app.message
        LaunchedEffect(message) {
            if (message != null) {
                ui.toast(message)
                app.messageShown()
            }
        }

        SpeechSplitTheme(darkTheme = dark) {
            CompositionLocalProvider(LocalUiActions provides ui) {
                Box(Modifier.fillMaxSize()) {
                    SpeechSplitApp(app)
                    ToastHost(ui)
                }
            }
        }
    }
}

/**
 * Space = pause/resume, Right/Page Down/Enter = next, Left/Page Up/Backspace = undo, F = big clock.
 * Presentation clickers send Page Down / Page Up, so they work out of the box.
 * Only active on the timer screen, so typing in the editor is never affected.
 */
private class KeyHandler(private val app: AppController) {
    private var lastNextAt = 0L

    fun handle(e: KeyEvent): Boolean {
        if (e.type != KeyEventType.KeyDown || app.currentScreen != AppScreen.TIMER) return false
        when (e.key) {
            Key.Spacebar -> app.togglePause()
            Key.DirectionRight, Key.PageDown, Key.Enter, Key.NumPadEnter -> {
                // Some clickers send a double press; ignore a second "next" within 0.4 s
                val now = System.currentTimeMillis()
                if (now - lastNextAt > 400) app.nextSegment()
                lastNextAt = now
            }
            Key.DirectionLeft, Key.PageUp, Key.Backspace -> app.previousSegment()
            Key.F -> if (!app.engine.isFinished) app.updateSettings { it.copy(lecternMode = !it.lecternMode) }
            else -> return false
        }
        return true
    }
}

/** Desktop versions of the things the screens ask the device for. */
private class DesktopUi(private val copiedText: () -> String) : UiActions {
    var toastMessage by mutableStateOf<String?>(null)
    var toastId by mutableStateOf(0)

    /** There's no share sheet on a PC, so the text goes to the clipboard instead. */
    override fun share(text: String, chooserTitle: String) {
        runCatching { Toolkit.getDefaultToolkit().systemClipboard.setContents(StringSelection(text), null) }
        toast(copiedText())
    }

    override fun toast(text: String) {
        toastMessage = text
        toastId++
    }

    override fun animationsEnabled(): Boolean = true
    override fun hasNotificationPermission(): Boolean = false
    override fun requestNotificationPermission(onResult: (Boolean) -> Unit) = onResult(false)
    override fun openDndSettings() {}
}

/** A small message at the bottom of the window that disappears after a few seconds. */
@Composable
private fun ToastHost(ui: DesktopUi) {
    val text = ui.toastMessage ?: return
    LaunchedEffect(ui.toastId) {
        delay(3000)
        ui.toastMessage = null
    }
    Box(Modifier.fillMaxSize().padding(24.dp), contentAlignment = Alignment.BottomCenter) {
        Surface(
            color = AppTheme.colors.accent,
            contentColor = AppTheme.colors.onAccent,
            shape = RoundedCornerShape(12.dp),
            shadowElevation = 6.dp,
            modifier = Modifier.widthIn(max = 400.dp),
        ) {
            Text(text, modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp))
        }
    }
}

/**
 * Stops Windows from going to sleep or dimming while the timer runs, by pressing the
 * unused F15 key every 50 seconds (a common, harmless trick; F15 does nothing).
 */
@Composable
private fun KeepAwake(active: Boolean) {
    LaunchedEffect(active) {
        if (!active || !isWindows) return@LaunchedEffect
        val robot = runCatching { Robot() }.getOrNull() ?: return@LaunchedEffect
        while (true) {
            delay(50_000)
            runCatching {
                robot.keyPress(AwtKeyEvent.VK_F15)
                robot.keyRelease(AwtKeyEvent.VK_F15)
            }
        }
    }
}

private fun loadIcon(): Painter? = runCatching {
    val stream = Thread.currentThread().contextClassLoader.getResourceAsStream("icon.png")
    stream?.use { BitmapPainter(ImageIO.read(it).toComposeImageBitmap()) }
}.getOrNull()
