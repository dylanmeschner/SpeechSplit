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
import androidx.compose.ui.unit.DpSize
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
import no.srrlsm.speechsplit.ui.SecondScreenClock
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
import no.srrlsm.speechsplit.core.MAX_IMPORT_BYTES
import no.srrlsm.speechsplit.ui.urlEncode
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.draganddrop.DragAndDropEvent
import androidx.compose.ui.draganddrop.DragAndDropTarget
import androidx.compose.ui.draganddrop.awtTransferable
import androidx.compose.foundation.draganddrop.dragAndDropTarget
import java.awt.FileDialog
import java.awt.Frame
import java.awt.datatransfer.DataFlavor

private val DOC_EXTENSIONS = setOf("speechsplit", "json")

// ============================================================================
// SPEECH SPLIT FOR WINDOWS (and Mac/Linux)
// The same app logic (core/) and screens (ui/) as the Android app, in a desktop window.
// ============================================================================
fun main() {
    // Notes down what the app was doing if the window ever stops responding (see FreezeWatch.kt)
    FreezeWatch.start(System.getProperty("speechsplit.version") ?: "dev")
    runApp()
}

@OptIn(ExperimentalComposeUiApi::class, androidx.compose.foundation.ExperimentalFoundationApi::class)
private fun runApp() = application {
    val scope = rememberCoroutineScope()
    val app = remember { AppController(DesktopStore(), DesktopPlatform(), scope) }
    val ui = remember {
        DesktopUi(
            copiedText = { app.strings.copiedToClipboard },
            downloadingText = { app.strings.downloadingUpdate },
            pickTitle = { app.strings.importFromFile },
            noEmailText = { app.strings.noEmailApp(it) },
            savedText = { app.strings.savedFile(it) },
            saveFailedText = { app.strings.saveFailed },
            openFile = { file -> importFile(app, file) },
            quit = {
                app.dispose()
                exitApplication()
            },
        )
    }
    val keys = remember { KeyHandler(app) }
    // Big enough for the speech list and the open speech side by side, but never bigger than the screen
    val screen = remember { runCatching { java.awt.GraphicsEnvironment.getLocalGraphicsEnvironment().maximumWindowBounds }.getOrNull() }
    val windowState = rememberWindowState(
        width = (screen?.width?.let { minOf(1180, it - 80) } ?: 1180).coerceAtLeast(440).dp,
        height = (screen?.height?.let { minOf(800, it - 60) } ?: 800).coerceAtLeast(560).dp,
        position = WindowPosition.Aligned(Alignment.Center),
    )
    // The main window itself, so the big clock can go on the screen it is NOT on
    var mainWindow by remember { mutableStateOf<java.awt.Window?>(null) }

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
        LaunchedEffect(Unit) {
            window.minimumSize = Dimension(320, 480)
            mainWindow = window
        }

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
                // Drop a .speechsplit file anywhere on the window to import it
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
                                        if (!app.importText(text)) ui.toast(app.strings3.importPasteOnly)
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

    // Big clock on a second screen: only while the timer is open, and only if a second screen is there
    if (app.settings.secondScreen && app.currentScreen == AppScreen.TIMER && app.activePlan != null) {
        SecondScreenWindow(app, mainWindow, onClose = { app.updateSettings { it.copy(secondScreen = false) } })
    }
}

/** The bounds of a screen other than the one the main window is on, or null when there's only one. */
private fun otherScreenBounds(main: java.awt.Window?): java.awt.Rectangle? = runCatching {
    val screens = java.awt.GraphicsEnvironment.getLocalGraphicsEnvironment().screenDevices
    if (screens.size < 2) return@runCatching null
    val mine = main?.graphicsConfiguration?.device
    (screens.firstOrNull { it != mine } ?: screens.last()).defaultConfiguration.bounds
}.getOrNull()

/**
 * A borderless window that fills the other screen with the part's name and a big clock.
 * It never takes the keyboard, so Space, the arrow keys and a presentation clicker keep
 * working in the main window. Always dark, like the big-clock mode.
 */
@Composable
private fun SecondScreenWindow(app: AppController, main: java.awt.Window?, onClose: () -> Unit) {
    val bounds = remember(main) { otherScreenBounds(main) } ?: return
    Window(
        onCloseRequest = onClose,
        title = "Speech Split",
        icon = remember { loadIcon() },
        state = rememberWindowState(
            position = WindowPosition(bounds.x.dp, bounds.y.dp),
            size = DpSize(bounds.width.dp, bounds.height.dp),
        ),
        undecorated = true,
        resizable = false,
        focusable = false,
        alwaysOnTop = true,
    ) {
        // Exactly the other screen, also when the two screens have different scaling
        LaunchedEffect(bounds) { window.bounds = bounds }
        SpeechSplitTheme(darkTheme = true) {
            SecondScreenClock(app)
        }
    }
}

/**
 * Space = pause/resume, Right/Page Down/Enter = next, Left/Page Up/Backspace = undo, F = big clock,
 * S = big clock on a second screen.
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
            Key.S -> app.toggleSecondScreen()
            else -> return false
        }
        return true
    }
}

/**
 * Reads a dropped or picked file and hands it to the shared import logic. Reading happens off
 * the window's thread: a file in Google Drive / OneDrive may first have to be downloaded.
 */
private fun importFile(app: AppController, file: File) {
    Thread({
        val bytes = if (!file.isFile || file.length() > MAX_IMPORT_BYTES) ByteArray(0)
        else runCatching { file.readBytes() }.getOrNull()
        if (bytes != null) SwingUtilities.invokeLater { app.importDocument(file.name, bytes) }
    }, "SpeechSplit-import").apply { isDaemon = true }.start()
}

/** Desktop versions of the things the screens ask the device for. */
private class DesktopUi(
    private val copiedText: () -> String,
    private val downloadingText: () -> String,
    private val pickTitle: () -> String,
    private val noEmailText: (String) -> String,
    private val savedText: (String) -> String,
    private val saveFailedText: () -> String,
    private val openFile: (File) -> Unit,
    private val quit: () -> Unit,
) : UiActions {
    override val canPickDocuments: Boolean = true

    /** The system's own "Open" window, filtered to saved speeches (.speechsplit). */
    override fun pickDocument() {
        val dialog = FileDialog(null as Frame?, pickTitle(), FileDialog.LOAD).apply {
            // Windows ignores filenameFilter, but uses this pattern
            file = "*.speechsplit;*.json"
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
    override fun share(text: String, chooserTitle: String) = copyText(text)

    /**
     * On its own thread: Windows can keep the clipboard locked for seconds (clipboard history,
     * Phone Link, other apps), which used to freeze the whole window.
     */
    override fun copyText(text: String) {
        Thread({
            runCatching { Toolkit.getDefaultToolkit().systemClipboard.setContents(StringSelection(text), null) }
        }, "SpeechSplit-clipboard").apply { isDaemon = true }.start()
        toast(copiedText())
    }

    override val canSaveFiles: Boolean = true
    private var lastSaveDir: String? = null

    /**
     * The system's own "Save as" window. Pick Google Drive / OneDrive (they show up as
     * folders on a PC with their desktop apps), a USB stick, or anywhere else.
     */
    override fun saveFile(fileName: String, mimeType: String, text: String) {
        val dialog = FileDialog(null as Frame?, null, FileDialog.SAVE).apply {
            directory = lastSaveDir ?: System.getProperty("user.home")?.let { File(it, "Documents").takeIf(File::isDirectory)?.path ?: it }
            file = fileName
        }
        dialog.isVisible = true // blocks until closed
        val name = dialog.file ?: return
        val dir = dialog.directory ?: return
        lastSaveDir = dir
        val ext = ".${fileName.substringAfterLast('.')}"
        val target = File(dir, if (name.endsWith(ext, ignoreCase = true)) name else name + ext)
        Thread({
            val ok = runCatching { target.writeText(text) }.isSuccess
            SwingUtilities.invokeLater { toast(if (ok) savedText(target.name) else saveFailedText()) }
        }, "SpeechSplit-export").apply { isDaemon = true }.start()
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
