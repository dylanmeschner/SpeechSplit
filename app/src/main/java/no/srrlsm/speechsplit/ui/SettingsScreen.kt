package no.srrlsm.speechsplit.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import no.srrlsm.speechsplit.core.AppController
import no.srrlsm.speechsplit.core.AppLanguage
import no.srrlsm.speechsplit.core.CelebrateOptions
import no.srrlsm.speechsplit.core.ClockMode
import no.srrlsm.speechsplit.core.ThemeMode
import no.srrlsm.speechsplit.core.WarningOptions
import no.srrlsm.speechsplit.core.languageName
import no.srrlsm.speechsplit.ui.theme.AppTheme

@Composable
fun SettingsScreen(app: AppController) {
    val s = LocalStrings.current
    val ui = LocalUiActions.current
    val settings = app.settings
    val canVibrate = app.canVibrate
    AppBackHandler { app.backToLibrary() }

    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(24.dp),
    ) {
        Text(s.settingsTitle, fontSize = 32.sp, fontWeight = FontWeight.Bold)

        SettingsSection(s.sectionGeneral) {
            SettingHeader(s.language)
            ChoiceChips(
                options = AppLanguage.entries,
                selected = settings.language,
                label = { languageName(it, s) },
                onSelect = { v -> app.updateSettings { it.copy(language = v) } },
            )
            SettingsDivider()
            SettingHeader(s.appearanceTitle)
            ChoiceChips(
                options = ThemeMode.entries,
                selected = settings.themeMode,
                label = {
                    when (it) {
                        ThemeMode.SYSTEM -> s.themeSystem
                        ThemeMode.LIGHT -> s.themeLight
                        ThemeMode.DARK -> s.themeDark
                    }
                },
                onSelect = { v -> app.updateSettings { it.copy(themeMode = v) } },
            )
            SettingsDivider()
            SwitchSetting(s.keepScreenOnTitle, s.keepScreenOnDesc, settings.keepScreenOn) { v ->
                app.updateSettings { it.copy(keepScreenOn = v) }
            }
            if (app.supportsDnd) {
                SettingsDivider()
                DndSwitch(app)
            }
        }

        SettingsSection(s.sectionTimer) {
            SettingHeader(s.clockModeTitle)
            ChoiceChips(
                options = ClockMode.entries,
                selected = settings.clockMode,
                label = { if (it == ClockMode.REMAINING) s.clockRemaining else s.clockElapsed },
                onSelect = { v -> app.updateSettings { it.copy(clockMode = v) } },
            )
            SettingsDivider()
            SettingHeader(s.warningTitle, s.warningDesc)
            ChoiceChips(
                options = WarningOptions,
                selected = settings.warningPercent,
                label = { if (it == 0) s.warningOff else s.percent(it) },
                onSelect = { v -> app.updateSettings { it.copy(warningPercent = v) } },
            )
            SettingsDivider()
            SwitchSetting(s.showAdjustedTitle, s.showAdjustedDesc, settings.showAdjusted) { v ->
                app.updateSettings { it.copy(showAdjusted = v) }
            }
            SettingsDivider()
            SettingHeader(s.celebrateTitle, s.celebrateDesc)
            ChoiceChips(
                options = CelebrateOptions,
                selected = settings.celebrateTolerance,
                label = {
                    when (it) {
                        -1 -> s.celebrateOff
                        0 -> s.celebrateExact
                        else -> s.withinSeconds(it)
                    }
                },
                onSelect = { v -> app.updateSettings { it.copy(celebrateTolerance = v) } },
            )
        }

        SettingsSection(s.sectionAlerts) {
            SwitchSetting(s.flashTitle, s.flashDesc, settings.flashAlerts) { v ->
                app.updateSettings { it.copy(flashAlerts = v) }
            }
            if (canVibrate) {
                SettingsDivider()
                SwitchSetting(s.vibrateTitle, s.vibrateDesc, settings.vibrateAlerts) { v ->
                    app.updateSettings { it.copy(vibrateAlerts = v) }
                    if (v) app.testVibration() // quick test buzz
                }
            }
            if (app.supportsWatchAlerts) {
            SettingsDivider()
            SwitchSetting(s.watchTitle, s.watchDesc, settings.watchAlerts) { v ->
                if (!v || ui.hasNotificationPermission()) {
                    app.updateSettings { it.copy(watchAlerts = v) }
                } else {
                    // The watch is reached through notifications, so those must be allowed
                    ui.requestNotificationPermission { granted ->
                        if (granted) app.updateSettings { it.copy(watchAlerts = true) }
                        else ui.toast(s.watchBlocked)
                    }
                }
            }
            }
        }

        if (app.hasKeyboardShortcuts) {
            SettingsSection(s.keyboardTitle) {
                Text(s.keyboardDesc, fontSize = 14.sp, color = AppTheme.colors.textSub)
            }
        }
        Spacer(Modifier.height(24.dp))
    }
}

/**
 * "Do Not Disturb while speaking" switch, used in Settings and on the Ready screen.
 * If Android hasn't given access yet, it explains and opens the right system page.
 */
@Composable
fun DndSwitch(app: AppController) {
    val s = LocalStrings.current
    val ui = LocalUiActions.current
    var askAccess by remember { mutableStateOf(false) }

    SwitchSetting(s.dndTitle, s.dndDesc, app.settings.dndWhileSpeaking) { v ->
        app.updateSettings { it.copy(dndWhileSpeaking = v) }
        if (v && !app.hasDndAccess()) askAccess = true
    }

    if (askAccess) {
        AlertDialog(
            onDismissRequest = { askAccess = false },
            title = { Text(s.dndPermissionTitle) },
            text = { Text(s.dndPermissionText) },
            confirmButton = {
                Button(onClick = { askAccess = false; ui.openDndSettings() }) { Text(s.openSettings) }
            },
            dismissButton = {
                TextButton(onClick = { askAccess = false }) { Text(s.cancel, color = AppTheme.colors.textMain) }
            },
        )
    }
}

@Composable
private fun SettingsSection(title: String, content: @Composable ColumnScope.() -> Unit) {
    SectionLabel(title, Modifier.padding(top = 28.dp, bottom = 8.dp, start = 4.dp))
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), content = content)
    }
}

@Composable
private fun SettingHeader(title: String, description: String? = null) {
    Text(title, fontSize = 16.sp, fontWeight = FontWeight.Medium)
    if (description != null) {
        Text(description, fontSize = 13.sp, color = AppTheme.colors.textSub, modifier = Modifier.padding(top = 2.dp))
    }
    Spacer(Modifier.height(10.dp))
}

@Composable
private fun SettingsDivider() {
    HorizontalDivider(Modifier.padding(vertical = 14.dp))
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun <T> ChoiceChips(options: List<T>, selected: T, label: (T) -> String, onSelect: (T) -> Unit) {
    val c = AppTheme.colors
    // FlowRow wraps onto a new line when the split-screen pane is narrow
    FlowRow(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        options.forEach { option ->
            FilterChip(
                selected = option == selected,
                onClick = { onSelect(option) },
                label = { Text(label(option)) },
                colors = FilterChipDefaults.filterChipColors(
                    selectedContainerColor = c.accent,
                    selectedLabelColor = c.onAccent,
                    labelColor = c.textMain,
                ),
            )
        }
    }
}

@Composable
fun SwitchSetting(
    title: String,
    description: String,
    checked: Boolean,
    enabled: Boolean = true,
    onChange: (Boolean) -> Unit,
) {
    val c = AppTheme.colors
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .toggleable(value = checked, enabled = enabled, role = Role.Switch, onValueChange = onChange)
            .padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f).padding(end = 16.dp)) {
            Text(title, fontSize = 16.sp, fontWeight = FontWeight.Medium, color = if (enabled) c.textMain else c.textSub)
            Text(description, fontSize = 13.sp, color = c.textSub, modifier = Modifier.padding(top = 2.dp))
        }
        // The whole row is the touch target, so the switch itself only displays the state
        Switch(checked = checked, onCheckedChange = null, enabled = enabled)
    }
}
