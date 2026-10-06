package no.srrlsm.speechsplit.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import no.srrlsm.speechsplit.core.AppController
import no.srrlsm.speechsplit.core.AppScreen
import no.srrlsm.speechsplit.ui.theme.AppTheme

/**
 * The whole app UI. Platform-neutral: give it an [AppController] and provide [LocalUiActions],
 * and it runs (on Android today; with Compose Multiplatform on desktop/iPad later).
 * Call it inside SpeechSplitTheme.
 */
@Composable
fun SpeechSplitApp(app: AppController) {
    Surface(modifier = Modifier.fillMaxSize(), color = AppTheme.colors.bg) {
        CompositionLocalProvider(
            LocalStrings provides app.strings,
            LocalSettings provides app.settings,
        ) {
            // Keeps content clear of the status bar, nav bar, camera cutout and keyboard
            Box(Modifier.fillMaxSize().safeDrawingPadding()) {
                when (app.currentScreen) {
                    AppScreen.LIBRARY, AppScreen.SETTINGS -> HomeTabs(app)
                    AppScreen.EDIT -> EditScreen(app)
                    AppScreen.READY -> ReadyScreen(app)
                    AppScreen.TIMER -> TimerScreen(app)
                    AppScreen.HISTORY -> HistoryScreen(app)
                }
            }
        }
    }
}
