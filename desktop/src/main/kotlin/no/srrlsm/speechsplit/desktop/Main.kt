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
import javax.swing.SwingUtilities
import java.io.File
import java.net.URI
import no.srrlsm.speechsplit.core.ReleaseInfo
import no.srrlsm.speechsplit.jvm.DocumentReader
import no.srrlsm.speechsplit.ui.urlEncode
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.draganddrop.DragAndDropEvent
import androidx.compose.ui.draganddrop.DragAndDropTarget
import androidx.compose.ui.draganddrop.awtTransferable
import androidx.compose.foundation.draganddrop.dragAndDropTarget
import java.awt.FileDialog
import java.awt.Frame
import java.awt.datatransfer.DataFlavor

private val DOC_EXTENSIONS = setOf("pdf", "docx", "odt", "txt", "md", "rtf", "text", "markdown")

// ============================================================================
// SPEECH SPLIT FOR WINDOWS (and Mac/Linux)
// The same app logic (core/) and screens (ui/) as the Android app, in a desktop window.
// ============================================================================
@OptIn(ExperimentalComposeUiApi::class, androidx.compose.foundation.ExperimentalFoundationApi::class)
fun main() = application {
    val scope = rememberCoroutineScope()
    val app = remember { AppController(DesktopStore(), DesktopPlatform(), scope) }
    val ui = remember {
        DesktopUi(
            copiedText = { app.strings.copiedToClipboard },
            downloadingText = { app.strings.downloadingUpdate },
            pickTitle = { app.strings.importFromFile },
            noEmailText = { app.strings.noEmailApp(it) },
            openFile = { file -> importFile(app, file) },
            quit = {
                app.dispose()
                exitApplication()
            },
        )
    }
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
                // Drop a PDF, Word or text file anywhere on the window to import it
                val dropTarget = remember {
                    object : DragAndDropTarget {
                        override fun onDrop(event: DragAndDropEvent): Boolean {
                            val t = event.awtTransferable
                            return try {
                                when {
                                    t.isDataFlavorSupported(DataFlavor.javaFileListFlavor) -> {
                                        val files = t.getTransferData(DataFlavor.javaFileListFlavor) as List<*>
                                        (files.firstOrNull() as? File)?.let { importFile(app, it) } != null
                                    }
                                    t.isDataFlavorSupported(DataFlavor.stringFlavor) -> {
                                        val text = t.getTransferData(DataFlavor.stringFlavor) as String
                                        if (!app.importText(text)) ui.toast(app.strings.importNothing)
                                        true
                                    }
                                    else -> false
                                }
                            } catch (e: Exception) {
                                false
                            }
                        }
                    }
                }
                Box(
                    Modifier.fillMaxSize().dragAndDropTarget(
                        shouldStartDragAndDrop = { app.currentScreen != AppScreen.TIMER },
                        target = dropTarget,
                    )
                ) {
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

/** Reads a dropped or picked file and hands it to the shared import logic. */
private fun importFile(app: AppController, file: File) {
    if (!file.isFile || file.length() > DocumentReader.MAX_BYTES) {
        app.importDocument(file.name, ByteArray(0))
        return
    }
    val bytes = runCatching { file.readBytes() }.getOrNull() ?: return
    app.importDocument(file.name, bytes)
}

/** Desktop versions of the things the screens ask the device for. */
private class DesktopUi(
    private val copiedText: () -> String,
    private val downloadingText: () -> String,
    private val pickTitle: () -> String,
    private val noEmailText: (String) -> String,
    private val openFile: (File) -> Unit,
    private val quit: () -> Unit,
) : UiActions {
    override val canPickDocuments: Boolean = true

    /** The system's own "Open" window, filtered to speech documents. */
    override fun pickDocument() {
        val dialog = FileDialog(null as Frame?, pickTitle(), FileDialog.LOAD).apply {
            // Windows ignores filenameFilter, but uses this pattern
            file = "*.pdf;*.docx;*.odt;*.txt;*.md;*.rtf"
            setFilenameFilter { _, name -> name.substringAfterLast('.').lowercase() in DOC_EXTENSIONS }
        }
        dialog.isVisible = true // blocks until closed
        val name = dialog.file ?: return
        openFile(File(dialog.directory, name))
    }

    override fun sendEmail(to: String, subject: String, body: String) {
        val uri = URI("mailto:$to?subject=${urlEncode(subject)}&body=${urlEncode(body)}")
        val desktop = runCatching { java.awt.Desktop.getDesktop() }.getOrNull()
        val ok = runCatching { desktop!!.mail(uri) }.isSuccess || runCatching { desktop!!.browse(uri) }.isSuccess
        if (!ok) {
            runCatching { Toolkit.getDefaultToolkit().systemClipboard.setContents(StringSelection(to), null) }
            toast(noEmailText(to))
        }
    }

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

    override fun openUrl(url: String) {
        runCatching { java.awt.Desktop.getDesktop().browse(URI(url)) }
    }

    /**
     * Windows: downloads the new installer, starts it and closes the app, so the installer can
     * replace it. Speeches and history are kept. Other systems: opens the download page.
     */
    override fun installUpdate(release: ReleaseInfo) {
        val msi = release.windowsUrl
        if (!isWindows || msi == null) {
            openUrl(release.pageUrl)
            return
        }
        toast(downloadingText())
        Thread {
            try {
                val file = File(System.getProperty("java.io.tmpdir"), "SpeechSplit-Setup-${release.version}.msi")
                URI(msi).toURL().openStream().use { input -> file.outputStream().use { input.copyTo(it) } }
                ProcessBuilder("msiexec", "/i", file.absolutePath).start()
                SwingUtilities.invokeLater { quit() }
            } catch (e: Exception) {
                // Download failed: let the browser handle it instead
                SwingUtilities.invokeLater { openUrl(msi) }
            }
        }.start()
    }
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
