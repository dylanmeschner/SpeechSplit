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
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
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

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
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

    // --- UiActions: the Android side of things the screens ask for -------------
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
            startActivity(Intent(Settings.ACTION_SETTINGS))
        }
    }
}
