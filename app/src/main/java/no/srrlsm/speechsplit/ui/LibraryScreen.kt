package no.srrlsm.speechsplit.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Sort
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
import no.srrlsm.speechsplit.core.SpeechSort
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
            when (app.currentScreen) {
                AppScreen.SETTINGS -> SettingsScreen(app)
                AppScreen.ARCHIVE -> ArchiveScreen(app)
                else -> LibraryScreen(app)
            }
        }
        NavigationBar(containerColor = c.surface) {
            NavigationBarItem(
                selected = app.currentScreen != AppScreen.SETTINGS,
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
    var showImportChoice by remember { mutableStateOf(false) }
    var showPaste by remember { mutableStateOf(false) }
    var showQuick by remember { mutableStateOf(false) }
    var planToDelete by remember { mutableStateOf<SpeechPlan?>(null) }
    val plans = app.visiblePlans
    val archivedCount = app.archivedPlans.size
    // Narrow split-screen panes: smaller heading, so the icons still fit next to it
    BoxWithConstraints(Modifier.fillMaxSize()) {
        val narrow = maxWidth < 360.dp
        Column(Modifier.fillMaxSize().padding(if (narrow) 16.dp else 24.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    s.libraryTitle,
                    fontSize = if (narrow) 24.sp else 32.sp,
                    fontWeight = FontWeight.Bold,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )
                SortMenu(app)
                IconButton(onClick = { if (ui.canPickDocuments) showImportChoice = true else showPaste = true }) {
                    Icon(Icons.Default.Download, contentDescription = s.importLabel, tint = c.textMain)
                }
            }

            Spacer(Modifier.height(if (narrow) 12.dp else 24.dp))
            UpdateBanner(app)
            if (app.importBusy) {
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(bottom = 12.dp)) {
                    CircularProgressIndicator(strokeWidth = 2.dp, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(10.dp))
                    Text(s.importReading, color = c.textSub, fontSize = 14.sp)
                }
            }

            if (plans.isEmpty()) {
                Column(Modifier.weight(1f)) {
                    Text(s.noSpeeches, color = c.textSub)
                    if (ui.canPickDocuments) {
                        Spacer(Modifier.height(8.dp))
                        Text(s.dropHint, color = c.textSub, fontSize = 14.sp)
                    }
                    if (archivedCount > 0) ArchiveLink(archivedCount) { app.openArchive() }
                }
            } else {
                LazyColumn(Modifier.weight(1f)) {
                    items(plans, key = { it.id }) { plan ->
                        PlanCard(
                            plan = plan,
                            onOpen = { app.openPlan(plan) },
                            menu = { close ->
                                DropdownMenuItem(
                                    text = { Text(s.edit) },
                                    leadingIcon = { Icon(Icons.Default.Edit, null) },
                                    onClick = { close(); app.editPlan(plan) },
                                )
                                DropdownMenuItem(
                                    text = { Text(s.export) },
                                    leadingIcon = { Icon(Icons.Default.Share, null) },
                                    onClick = { close(); ui.share(app.exportPlan(plan), s.exportChooser) },
                                )
                                DropdownMenuItem(
                                    text = { Text(s.archiveAction) },
                                    leadingIcon = { Icon(Icons.Default.Archive, null) },
                                    onClick = { close(); app.archivePlan(plan); ui.toast(s.archived) },
                                )
                                DropdownMenuItem(
                                    text = { Text(s.delete) },
                                    leadingIcon = { Icon(Icons.Default.Delete, null) },
                                    onClick = { close(); planToDelete = plan },
                                )
                            },
                        )
                    }
                    if (archivedCount > 0) {
                        item(key = "archive") { ArchiveLink(archivedCount) { app.openArchive() } }
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
    }

    if (showQuick) {
        QuickTimerDialog(
            onStart = { minutes -> showQuick = false; app.startQuickTimer(minutes) },
            onDismiss = { showQuick = false },
        )
    }

    if (showImportChoice) {
        AlertDialog(
            onDismissRequest = { showImportChoice = false },
            title = { Text(s.importTitle) },
            text = {
                Column {
                    ChoiceRow(Icons.Default.Description, s.importFromFile, s.importFromFileDesc) {
                        showImportChoice = false
                        ui.pickDocument()
                    }
                    Spacer(Modifier.height(4.dp))
                    ChoiceRow(Icons.Default.ContentPaste, s.importPaste, s.importPasteDesc) {
                        showImportChoice = false
                        showPaste = true
                    }
                    Spacer(Modifier.height(12.dp))
                    Text(s.dropHint, fontSize = 13.sp, color = c.textSub)
                }
            },
            confirmButton = {},
            dismissButton = { TextButton(onClick = { showImportChoice = false }) { Text(s.cancel, color = c.textMain) } },
        )
    }

    if (showPaste) {
        PasteDialog(app, onDismiss = { showPaste = false })
    }

    planToDelete?.let { plan ->
        DeletePlanDialog(app, plan, onDone = { planToDelete = null })
    }
}

/** One speech in the list. Its actions sit in a "⋮" menu, so the title keeps its room in narrow panes. */
@Composable
private fun PlanCard(
    plan: SpeechPlan,
    onOpen: () -> Unit,
    menu: @Composable ColumnScope.(close: () -> Unit) -> Unit,
) {
    val s = LocalStrings.current
    val c = AppTheme.colors
    var open by remember { mutableStateOf(false) }
    Card(onClick = onOpen, modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(start = 20.dp, top = 12.dp, bottom = 12.dp, end = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f)) {
                Text(plan.title, fontSize = 20.sp, fontWeight = FontWeight.Bold, maxLines = 2, overflow = TextOverflow.Ellipsis)
                Text(s.summaryOf(plan), color = c.textSub, fontSize = 14.sp)
            }
            Box {
                IconButton(onClick = { open = true }) {
                    Icon(Icons.Default.MoreVert, contentDescription = s.moreOptions, tint = c.textSub)
                }
                DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
                    menu { open = false }
                }
            }
        }
    }
}

@Composable
private fun ArchiveLink(count: Int, onClick: () -> Unit) {
    val s = LocalStrings.current
    TextButton(onClick = onClick, modifier = Modifier.padding(top = 8.dp)) {
        Icon(Icons.Default.Inventory2, contentDescription = null, tint = AppTheme.colors.textSub, modifier = Modifier.size(18.dp))
        Spacer(Modifier.width(8.dp))
        Text(s.archiveCount(count), color = AppTheme.colors.textSub)
    }
}

/** Sort icon with a small menu: last used, newest, name. */
@Composable
private fun SortMenu(app: AppController) {
    val s = LocalStrings.current
    var open by remember { mutableStateOf(false) }
    Box {
        IconButton(onClick = { open = true }) {
            Icon(Icons.AutoMirrored.Filled.Sort, contentDescription = s.sortTitle, tint = AppTheme.colors.textMain)
        }
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            Text(
                s.sortTitle,
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold,
                color = AppTheme.colors.textSub,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp),
            )
            SpeechSort.entries.forEach { sort ->
                val label = when (sort) {
                    SpeechSort.LAST_USED -> s.sortLastUsed
                    SpeechSort.CREATED -> s.sortCreated
                    SpeechSort.NAME -> s.sortName
                }
                DropdownMenuItem(
                    text = { Text(label) },
                    leadingIcon = {
                        RadioButton(selected = app.settings.speechSort == sort, onClick = null)
                    },
                    onClick = { open = false; app.setSort(sort) },
                )
            }
        }
    }
}

@Composable
private fun ChoiceRow(icon: androidx.compose.ui.graphics.vector.ImageVector, title: String, desc: String, onClick: () -> Unit) {
    val c = AppTheme.colors
    Surface(
        onClick = onClick,
        color = c.surfaceHigh,
        shape = MaterialTheme.shapes.medium,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(icon, contentDescription = null, tint = c.textMain)
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f)) {
                Text(title, fontWeight = FontWeight.Bold)
                Text(desc, fontSize = 13.sp, color = c.textSub)
            }
        }
    }
}

/** Paste an exported speech (imported as is), or the speech text itself (segments are suggested). */
@Composable
private fun PasteDialog(app: AppController, onDismiss: () -> Unit) {
    val s = LocalStrings.current
    val ui = LocalUiActions.current
    val c = AppTheme.colors
    var text by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(s.importTitle) },
        text = {
            OutlinedTextField(
                value = text,
                onValueChange = { text = it },
                label = { Text(s.importHint) },
                modifier = Modifier.fillMaxWidth().height(180.dp),
            )
        },
        confirmButton = {
            Button(
                enabled = text.isNotBlank(),
                onClick = {
                    if (app.importText(text)) onDismiss() else ui.toast(s.importNothing)
                },
            ) { Text(s.importLabel) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(s.cancel, color = c.textMain) } },
    )
}

@Composable
private fun DeletePlanDialog(app: AppController, plan: SpeechPlan, onDone: () -> Unit) {
    val s = LocalStrings.current
    val c = AppTheme.colors
    AlertDialog(
        onDismissRequest = onDone,
        title = { Text(s.deleteTitle(plan.title)) },
        text = { Text(if (plan.archived) s.cannotBeUndone else s.deleteForeverText) },
        confirmButton = {
            Button(
                onClick = { app.deletePlan(plan); onDone() },
                colors = ButtonDefaults.buttonColors(containerColor = c.over, contentColor = c.onOver),
            ) { Text(s.delete) }
        },
        dismissButton = {
            Row {
                if (!plan.archived) {
                    TextButton(onClick = { app.archivePlan(plan); onDone() }) { Text(s.archiveAction, color = c.textMain) }
                }
                TextButton(onClick = onDone) { Text(s.cancel, color = c.textMain) }
            }
        },
    )
}

// ============================================================================
// ARCHIVE: hidden speeches, kept with their history. Restore or delete for good.
// ============================================================================
@Composable
fun ArchiveScreen(app: AppController) {
    val s = LocalStrings.current
    val ui = LocalUiActions.current
    val c = AppTheme.colors
    val plans = app.archivedPlans
    var planToDelete by remember { mutableStateOf<SpeechPlan?>(null) }
    AppBackHandler { app.backToLibrary() }

    Column(Modifier.fillMaxSize().padding(horizontal = 16.dp, vertical = 8.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = { app.backToLibrary() }) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = s.back)
            }
            Text(s.archiveTitle, fontSize = 24.sp, fontWeight = FontWeight.Bold)
        }
        if (plans.isEmpty()) {
            Text(s.archiveEmpty, color = c.textSub, modifier = Modifier.padding(16.dp))
            return@Column
        }
        Text(s.archiveEmpty.substringAfter(". ").ifBlank { "" }, color = c.textSub, fontSize = 13.sp, modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp))
        LazyColumn(Modifier.fillMaxSize().padding(top = 8.dp)) {
            items(plans, key = { it.id }) { plan ->
                Card(Modifier.fillMaxWidth().padding(vertical = 6.dp)) {
                    Column(Modifier.padding(start = 20.dp, end = 8.dp, top = 12.dp, bottom = 4.dp)) {
                        Text(plan.title, fontSize = 18.sp, fontWeight = FontWeight.Bold, maxLines = 2, overflow = TextOverflow.Ellipsis)
                        Text(s.summaryOf(plan), color = c.textSub, fontSize = 14.sp)
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                            TextButton(onClick = { planToDelete = plan }) {
                                Text(s.delete, color = c.textSub)
                            }
                            TextButton(onClick = { app.restorePlan(plan); ui.toast(s.restored) }) {
                                Icon(Icons.Default.Unarchive, contentDescription = null, tint = c.textMain, modifier = Modifier.size(18.dp))
                                Spacer(Modifier.width(6.dp))
                                Text(s.restore, color = c.textMain, fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }
            }
        }
    }

    planToDelete?.let { plan ->
        DeletePlanDialog(app, plan, onDone = { planToDelete = null })
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
