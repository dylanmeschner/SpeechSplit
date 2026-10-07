package no.srrlsm.speechsplit

import android.Manifest
import android.app.Application
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.view.WindowManager
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import android.net.Uri
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import android.content.ClipDescription
import android.provider.OpenableColumns
import android.view.DragEvent
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import no.srrlsm.speechsplit.jvm.DocumentReader
import no.srrlsm.speechsplit.core.AppController
import no.srrlsm.speechsplit.core.ReleaseInfo
import no.srrlsm.speechsplit.core.ThemeMode
import no.srrlsm.speechsplit.platform.AndroidPlatform
import no.srrlsm.speechsplit.platform.AndroidStore
import no.srrlsm.speechsplit.ui.LocalUiActions
import no.srrlsm.speechsplit.ui.SpeechSplitApp
import no.srrlsm.speechsplit.ui.UiActions
import no.srrlsm.speechsplit.ui.theme.SpeechSplitTheme

// ============================================================================
// PROJECT LAYOUT
//   core/      Plain Kotlin: models, timer, history stats, translations, app logic.
//              No Android code, so it can be shared with a Windows or iPad version.
//   ui/        Compose screens. Compose Multiplatform runs these on desktop and iOS too;
//              they reach the device only through UiActions.
//   platform/  Android-only: storage, vibration, notifications, Do Not Disturb.
//   MainActivity.kt (this file): wires it all together for Android.
// ============================================================================

/** Keeps the app state alive through rotation and split-screen resizing. */
class TimerViewModel(application: Application) : AndroidViewModel(application) {
    val app = AppController(AndroidStore(application), AndroidPlatform(application), viewModelScope)

    override fun onCleared() = app.dispose()
}

class MainActivity : ComponentActivity(), UiActions {
    private val vm: TimerViewModel by viewModels()
    private var permissionCallback: ((Boolean) -> Unit)? = null
    private val notificationPermission =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
            permissionCallback?.invoke(granted)
            permissionCallback = null
        }

    private val openDocument =
        registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri -> uri?.let(::importUri) }

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        // A document shared to / opened with Speech Split (only on a fresh start, not after rotation)
        if (savedInstanceState == null) handleIncoming(intent)
        // Drag a document onto the app (tablets, split screen, Samsung DeX, Chromebooks)
        window.decorView.setOnDragListener { _, event -> onDrag(event) }
        setContent {
            val app = vm.app
            val dark = when (app.settings.themeMode) {
                ThemeMode.SYSTEM -> isSystemInDarkTheme()
                ThemeMode.LIGHT -> false
                ThemeMode.DARK -> true
            }
            // Status/nav bar icons follow the app's own theme (also when it differs from the system's)
            LaunchedEffect(dark) {
                val transparent = android.graphics.Color.TRANSPARENT
                val style = if (dark) SystemBarStyle.dark(transparent) else SystemBarStyle.light(transparent, transparent)
                enableEdgeToEdge(statusBarStyle = style, navigationBarStyle = style)
            }
            // Screen stays on only while timing (if that's switched on in Settings)
            val keepOn = app.keepScreenOn
            LaunchedEffect(keepOn) {
                if (keepOn) window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
                else window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
            }
            // One-off messages from the app logic
            val message = app.message
            LaunchedEffect(message) {
                if (message != null) {
                    toast(message)
                    app.messageShown()
                }
            }
            SpeechSplitTheme(darkTheme = dark) {
                CompositionLocalProvider(LocalUiActions provides this@MainActivity) {
                    SpeechSplitApp(app)
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handleIncoming(intent)
    }

    // --- Importing documents ----------------------------------------------------
    private fun handleIncoming(intent: Intent?) {
        intent ?: return
        when (intent.action) {
            Intent.ACTION_VIEW -> intent.data?.let(::importUri)
            Intent.ACTION_SEND -> {
                @Suppress("DEPRECATION")
                val stream = intent.getParcelableExtra<Uri>(Intent.EXTRA_STREAM)
                val text = intent.getStringExtra(Intent.EXTRA_TEXT)
                when {
                    stream != null -> importUri(stream)
                    !text.isNullOrBlank() -> if (!vm.app.importText(text)) toast(vm.app.strings.importNothing)
                }
            }
        }
    }

    private fun onDrag(event: DragEvent): Boolean = when (event.action) {
        DragEvent.ACTION_DRAG_STARTED -> event.clipDescription?.let { d ->
            (0 until d.mimeTypeCount).any { i ->
                val m = d.getMimeType(i)
                m.startsWith("application/") || m.startsWith("text/") || m == ClipDescription.MIMETYPE_TEXT_URILIST
            }
        } ?: false
        DragEvent.ACTION_DROP -> {
            val item = event.clipData?.takeIf { it.itemCount > 0 }?.getItemAt(0)
            val uri = item?.uri
            if (uri != null) {
                requestDragAndDropPermissions(event)
                importUri(uri)
            } else {
                item?.text?.toString()?.takeIf { it.isNotBlank() }?.let { text ->
                    if (!vm.app.importText(text)) toast(vm.app.strings.importNothing)
                }
            }
            true
        }
        else -> true
    }

    /** Reads the file in the background, then hands it to the shared app logic. */
    private fun importUri(uri: Uri) {
        lifecycleScope.launch {
            val file = withContext(Dispatchers.IO) {
                try {
                    val name = contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { c ->
                        if (c.moveToFirst()) c.getString(0) else null
                    } ?: uri.lastPathSegment ?: "document"
                    val bytes = contentResolver.openInputStream(uri)?.use { input ->
                        val buf = input.readBytes()
                        if (buf.size > DocumentReader.MAX_BYTES) null else buf
                    }
                    bytes?.let { name to it }
                } catch (e: Exception) {
                    null
                }
            }
            if (file == null) toast(vm.app.strings.importUnreadable) else vm.app.importDocument(file.first, file.second)
        }
    }

    // --- UiActions: the Android side of things the screens ask for -------------
    override val canPickDocuments: Boolean get() = true

    override fun pickDocument() {
        try {
            openDocument.launch(DOCUMENT_TYPES)
        } catch (e: Exception) {
            toast(vm.app.strings.importUnsupported)
        }
    }

    override fun sendEmail(to: String, subject: String, body: String) {
        val intent = Intent(Intent.ACTION_SENDTO, Uri.parse("mailto:")).apply {
            putExtra(Intent.EXTRA_EMAIL, arrayOf(to))
            putExtra(Intent.EXTRA_SUBJECT, subject)
            putExtra(Intent.EXTRA_TEXT, body)
        }
        try {
            startActivity(intent)
        } catch (e: Exception) {
            toast(vm.app.strings.noEmailApp(to))
        }
    }

    override fun share(text: String, chooserTitle: String) {
        val send = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_TEXT, text)
        }
        startActivity(Intent.createChooser(send, chooserTitle))
    }

    override fun toast(text: String) {
        Toast.makeText(this, text, Toast.LENGTH_SHORT).show()
    }

    override fun animationsEnabled(): Boolean =
        Settings.Global.getFloat(contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f) != 0f

    override fun hasNotificationPermission(): Boolean =
        NotificationManagerCompat.from(this).areNotificationsEnabled()

    override fun requestNotificationPermission(onResult: (Boolean) -> Unit) {
        val needsAsking = Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        if (needsAsking) {
            permissionCallback = onResult
            notificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
        } else {
            // Already granted, or blocked by the user in system settings
            onResult(hasNotificationPermission())
        }
    }

    override fun openUrl(url: String) {
        try {
            startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
        } catch (e: Exception) {
            // No browser installed: nothing sensible to do
        }
    }

    /** The APK downloads in the browser; tapping the finished download installs it over this version. */
    override fun installUpdate(release: ReleaseInfo) {
        openUrl(release.androidUrl ?: release.pageUrl)
        toast(vm.app.strings.updateHintAndroid)
    }

    override fun openDndSettings() {
        try {
            startActivity(Intent(Settings.ACTION_NOTIFICATION_POLICY_ACCESS_SETTINGS))
        } catch (e: Exception) {
            runCatching { startActivity(Intent(Settings.ACTION_SETTINGS)) }
        }
    }

    private companion object {
        val DOCUMENT_TYPES = arrayOf(
            "application/pdf",
            "application/vnd.openxmlformats-officedocument.wordprocessingml.document",
            "application/vnd.oasis.opendocument.text",
            "application/rtf",
            "text/*",
        )
    }
}
