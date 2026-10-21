package no.srrlsm.speechsplit.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.NavigationRailItemDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.VerticalDivider
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import no.srrlsm.speechsplit.core.AppController
import no.srrlsm.speechsplit.core.AppScreen
import no.srrlsm.speechsplit.ui.theme.AppTheme

/** From this width the speech list and the open speech are shown side by side. */
private val WideLayoutMinWidth = 820.dp

/**
 * The whole app UI. Platform-neutral: give it an [AppController] and provide [LocalUiActions],
 * and it runs on Android and on desktop (Compose Multiplatform).
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
            BoxWithConstraints(Modifier.fillMaxSize().safeDrawingPadding()) {
                // PC windows and tablets in landscape: list on the left, the speech on the right.
                // The timer always gets the whole window.
                val wide = maxWidth >= WideLayoutMinWidth && maxHeight >= 460.dp
                CompositionLocalProvider(LocalWideLayout provides wide) {
                    if (wide && app.currentScreen != AppScreen.TIMER) {
                        WideLayout(app)
                    } else {
                        when (app.currentScreen) {
                            AppScreen.LIBRARY, AppScreen.ARCHIVE, AppScreen.SETTINGS -> HomeTabs(app)
                            AppScreen.EDIT -> EditScreen(app)
                            AppScreen.READY -> ReadyScreen(app)
                            AppScreen.TIMER -> TimerScreen(app)
                            AppScreen.HISTORY -> HistoryScreen(app)
                        }
                    }
                }
            }
            // A document that was opened, dropped or shared: show the suggested segments.
            // Not over a running timer or unsaved edits; it waits until the user leaves those.
            val draft = app.importDraft
            if (draft != null && app.currentScreen != AppScreen.TIMER && app.currentScreen != AppScreen.EDIT) {
                ImportSuggestDialog(app, draft)
            }
            // A library backup was opened: add or replace
            ImportLibraryDialog(app)
            // Leaving the editor with unsaved changes (wide layout: the list is right next to it)
            if (app.pendingLeave != null) {
                val s = app.strings
                AlertDialog(
                    onDismissRequest = { app.cancelLeave() },
                    title = { Text(s.discardTitle) },
                    text = { Text(s.discardText) },
                    confirmButton = { Button(onClick = { app.confirmLeave() }) { Text(s.discard) } },
                    dismissButton = {
                        TextButton(onClick = { app.cancelLeave() }) { Text(s.keepEditing, color = AppTheme.colors.textMain) }
                    },
                )
            }
        }
    }
}

/** Side rail with the two tabs, the speech list, and the open speech (or settings) to the right. */
@Composable
private fun WideLayout(app: AppController) {
    val s = LocalStrings.current
    val c = AppTheme.colors
    val itemColors = NavigationRailItemDefaults.colors(
        selectedIconColor = c.textMain,
        selectedTextColor = c.textMain,
        indicatorColor = c.surfaceHigh,
        unselectedIconColor = c.textSub,
        unselectedTextColor = c.textSub,
    )
    Row(Modifier.fillMaxSize()) {
        NavigationRail(containerColor = c.surface) {
            Spacer(Modifier.height(12.dp))
            NavigationRailItem(
                selected = app.currentScreen != AppScreen.SETTINGS,
                onClick = { app.guardEdits { app.backToLibrary() } },
                icon = { Icon(Icons.Default.Timer, contentDescription = null) },
                label = { Text(s.tabSpeeches) },
                colors = itemColors,
            )
            NavigationRailItem(
                selected = app.currentScreen == AppScreen.SETTINGS,
                onClick = { app.guardEdits { app.openSettings() } },
                icon = { Icon(Icons.Default.Settings, contentDescription = null) },
                label = { Text(s.tabSettings) },
                colors = itemColors,
            )
        }
        when (app.currentScreen) {
            AppScreen.SETTINGS -> CenteredPane(720.dp) { SettingsScreen(app) }
            AppScreen.ARCHIVE -> CenteredPane(720.dp) { ArchiveScreen(app) }
            else -> {
                Box(Modifier.width(380.dp).fillMaxHeight()) { LibraryScreen(app) }
                VerticalDivider(color = c.surfaceHigh)
                Box(Modifier.weight(1f).fillMaxHeight()) {
                    when (app.currentScreen) {
                        AppScreen.READY -> CenteredPane(640.dp) { ReadyScreen(app) }
                        AppScreen.EDIT -> CenteredPane(820.dp) { EditScreen(app) }
                        AppScreen.HISTORY -> CenteredPane(760.dp) { HistoryScreen(app) }
                        else -> EmptyDetail(s.pickSpeech)
                    }
                }
            }
        }
    }
}

/** Keeps content at a readable width on a big screen, centred. */
@Composable
private fun CenteredPane(maxWidth: Dp, content: @Composable () -> Unit) {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
        Box(Modifier.widthIn(max = maxWidth).fillMaxSize()) { content() }
    }
}

@Composable
private fun EmptyDetail(text: String) {
    val c = AppTheme.colors
    Box(Modifier.fillMaxSize().padding(32.dp), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(Icons.Default.Timer, contentDescription = null, tint = c.textSub, modifier = Modifier.size(48.dp))
            Spacer(Modifier.height(12.dp))
            Text(text, color = c.textSub, fontSize = 16.sp, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth())
        }
    }
}
