package no.srrlsm.speechsplit.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import no.srrlsm.speechsplit.core.AppController
import no.srrlsm.speechsplit.core.AppScreen
import no.srrlsm.speechsplit.core.QuickTimerOptions
import no.srrlsm.speechsplit.core.SpeechPlan
import no.srrlsm.speechsplit.core.summaryOf
import no.srrlsm.speechsplit.ui.theme.AppTheme

// ============================================================================
// HOME TABS (Speeches | Settings)
// ============================================================================
@Composable
fun HomeTabs(app: AppController) {
    val s = LocalStrings.current
    val c = AppTheme.colors
    val itemColors = NavigationBarItemDefaults.colors(
        selectedIconColor = c.textMain,
        selectedTextColor = c.textMain,
        indicatorColor = c.surfaceHigh,
        unselectedIconColor = c.textSub,
        unselectedTextColor = c.textSub,
    )

    Column(Modifier.fillMaxSize()) {
        Box(Modifier.weight(1f)) {
            if (app.currentScreen == AppScreen.SETTINGS) SettingsScreen(app) else LibraryScreen(app)
        }
        NavigationBar(containerColor = c.surface) {
            NavigationBarItem(
                selected = app.currentScreen == AppScreen.LIBRARY,
                onClick = { app.backToLibrary() },
                icon = { Icon(Icons.Default.Timer, contentDescription = null) },
                label = { Text(s.tabSpeeches) },
                colors = itemColors,
            )
            NavigationBarItem(
                selected = app.currentScreen == AppScreen.SETTINGS,
                onClick = { app.openSettings() },
                icon = { Icon(Icons.Default.Settings, contentDescription = null) },
                label = { Text(s.tabSettings) },
                colors = itemColors,
            )
        }
    }
}

// ============================================================================
// LIBRARY
// ============================================================================
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LibraryScreen(app: AppController) {
    val s = LocalStrings.current
    val ui = LocalUiActions.current
    val c = AppTheme.colors
    var showImport by remember { mutableStateOf(false) }
    var showQuick by remember { mutableStateOf(false) }
    var importText by remember { mutableStateOf("") }
    var planToDelete by remember { mutableStateOf<SpeechPlan?>(null) }

    Column(Modifier.fillMaxSize().padding(24.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(s.libraryTitle, fontSize = 32.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
            IconButton(onClick = { showImport = true }) {
                Icon(Icons.Default.Download, contentDescription = s.importLabel, tint = c.textMain)
            }
        }

        Spacer(Modifier.height(24.dp))
        UpdateBanner(app)

        if (app.speechPlans.isEmpty()) {
            Text(s.noSpeeches, color = c.textSub, modifier = Modifier.weight(1f))
        } else {
            LazyColumn(Modifier.weight(1f)) {
                items(app.speechPlans, key = { it.id }) { plan ->
                    Card(
                        onClick = { app.openPlan(plan) },
                        modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp),
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(start = 20.dp, top = 12.dp, bottom = 12.dp, end = 4.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Column(Modifier.weight(1f)) {
                                Text(plan.title, fontSize = 20.sp, fontWeight = FontWeight.Bold, maxLines = 2, overflow = TextOverflow.Ellipsis)
                                Text(s.summaryOf(plan), color = c.textSub, fontSize = 14.sp)
                            }
                            IconButton(onClick = { app.editPlan(plan) }) {
                                Icon(Icons.Default.Edit, contentDescription = s.edit, tint = c.textSub)
                            }
                            IconButton(onClick = { ui.share(app.exportPlan(plan), s.exportChooser) }) {
                                Icon(Icons.Default.Share, contentDescription = s.export, tint = c.textSub)
                            }
                            IconButton(onClick = { planToDelete = plan }) {
                                Icon(Icons.Default.Delete, contentDescription = s.delete, tint = c.textSub)
                            }
                        }
                    }
                }
            }
        }

        Spacer(Modifier.height(16.dp))
        Button(onClick = { app.createNewPlan() }, modifier = Modifier.fillMaxWidth().height(56.dp)) {
            Icon(Icons.Default.Add, contentDescription = null)
            Spacer(Modifier.width(8.dp))
            FitText(s.createNewSpeech, maxSize = 18.sp, fontWeight = FontWeight.Medium)
        }
        Spacer(Modifier.height(8.dp))
        OutlinedButton(onClick = { showQuick = true }, modifier = Modifier.fillMaxWidth().height(48.dp)) {
            Icon(Icons.Default.Timer, contentDescription = null, tint = c.textMain)
            Spacer(Modifier.width(8.dp))
            FitText(s.quickTimer, maxSize = 16.sp, fontWeight = FontWeight.Medium, color = c.textMain)
        }
    }

    if (showQuick) {
        QuickTimerDialog(
            onStart = { minutes -> showQuick = false; app.startQuickTimer(minutes) },
            onDismiss = { showQuick = false },
        )
    }

    if (showImport) {
        AlertDialog(
            onDismissRequest = { showImport = false },
            title = { Text(s.importTitle) },
            text = {
                OutlinedTextField(
                    value = importText,
                    onValueChange = { importText = it },
                    label = { Text(s.importHint) },
                    modifier = Modifier.fillMaxWidth().height(150.dp),
                )
            },
            confirmButton = {
                Button(onClick = {
                    if (app.importPlan(importText)) {
                        ui.toast(s.imported)
                        importText = ""
                        showImport = false
                    } else {
                        ui.toast(s.importFailed)
                    }
                }) { Text(s.importLabel) }
            },
            dismissButton = { TextButton(onClick = { showImport = false }) { Text(s.cancel, color = c.textMain) } },
        )
    }

    planToDelete?.let { plan ->
        AlertDialog(
            onDismissRequest = { planToDelete = null },
            title = { Text(s.deleteTitle(plan.title)) },
            text = { Text(s.cannotBeUndone) },
            confirmButton = {
                Button(onClick = { app.deletePlan(plan); planToDelete = null }) { Text(s.delete) }
            },
            dismissButton = { TextButton(onClick = { planToDelete = null }) { Text(s.cancel, color = c.textMain) } },
        )
    }
}

/** Pick or type a length, and the countdown starts right away. Nothing is saved. */
@Composable
private fun QuickTimerDialog(onStart: (Int) -> Unit, onDismiss: () -> Unit) {
    val s = LocalStrings.current
    val c = AppTheme.colors
    var minutesText by remember { mutableStateOf("20") }
    val minutes = minutesText.trim().toIntOrNull()?.takeIf { it in 1..600 }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(s.quickTimer) },
        text = {
            Column {
                ChoiceChips(
                    options = QuickTimerOptions,
                    selected = minutes ?: -1,
                    label = { "$it min" },
                    onSelect = { minutesText = it.toString() },
                )
                Spacer(Modifier.height(12.dp))
                OutlinedTextField(
                    value = minutesText,
                    onValueChange = { v -> minutesText = v.filter { it.isDigit() }.take(3) },
                    label = { Text(s.quickMinutesLabel) },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        },
        confirmButton = {
            Button(onClick = { minutes?.let(onStart) }, enabled = minutes != null) { Text(s.start) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(s.cancel, color = c.textMain) } },
    )
}
