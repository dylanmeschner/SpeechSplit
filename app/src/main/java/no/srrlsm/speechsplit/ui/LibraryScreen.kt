package no.srrlsm.speechsplit.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
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
import no.srrlsm.speechsplit.core.Folder
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
                AppScreen.MEETING -> MeetingScreen(app)
                else -> LibraryScreen(app)
            }
        }
        NavigationBar(containerColor = c.surface) {
            NavigationBarItem(
                selected = app.currentScreen != AppScreen.SETTINGS,
                onClick = { app.libraryTab() },
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
    val s3 = LocalStrings3.current
    val ui = LocalUiActions.current
    val c = AppTheme.colors
    var showImportChoice by remember { mutableStateOf(false) }
    var showPaste by remember { mutableStateOf(false) }
    var showQuick by remember { mutableStateOf(false) }
    var planToDelete by remember { mutableStateOf<SpeechPlan?>(null) }
    var planToExport by remember { mutableStateOf<SpeechPlan?>(null) }
    var planToMove by remember { mutableStateOf<SpeechPlan?>(null) }
    var exportLibrary by remember { mutableStateOf(false) }
    var newFolder by remember { mutableStateOf(false) }
    var renameFolder by remember { mutableStateOf(false) }
    var deleteFolder by remember { mutableStateOf(false) }
    val wide = LocalWideLayout.current
    val folder = app.openFolder
    val plans = app.plansIn(folder?.id)
    val folders = if (folder == null) app.sortedFolders else emptyList()
    // Counted once, not once per folder card
    val folderCounts = if (folders.isEmpty()) emptyMap() else app.visiblePlans.groupingBy { it.folderId }.eachCount()
    val archivedCount = app.archivedPlans.size
    if (folder != null) AppBackHandler { app.openFolder(null) }
    // Narrow split-screen panes: smaller heading, so the icons still fit next to it
    BoxWithConstraints(Modifier.fillMaxSize()) {
        val narrow = maxWidth < 360.dp
        Column(Modifier.fillMaxSize().padding(if (narrow) 16.dp else 24.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                if (folder != null) {
                    IconButton(onClick = { app.openFolder(null) }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = s.back)
                    }
                }
                Text(
                    folder?.name ?: s.libraryTitle,
                    // The list pane next to an open speech is narrower: smaller heading there too
                    fontSize = if (narrow || wide || folder != null) 26.sp else 32.sp,
                    lineHeight = if (narrow || wide || folder != null) 30.sp else 36.sp,
                    fontWeight = FontWeight.Bold,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )
                SortMenu(app)
                if (folder != null) {
                    FolderMenu(onRename = { renameFolder = true }, onDelete = { deleteFolder = true })
                } else {
                    IconButton(onClick = { app.guardEdits { newFolder = true } }) {
                        Icon(Icons.Default.CreateNewFolder, contentDescription = s3.newFolder, tint = c.textMain)
                    }
                    // Export the whole library (speeches, folders, archive, history) as one file
                    IconButton(onClick = { exportLibrary = true }) {
                        Icon(Icons.Default.Upload, contentDescription = s.exportLibrary, tint = c.textMain)
                    }
                    IconButton(onClick = { app.guardEdits { if (ui.canPickDocuments) showImportChoice = true else showPaste = true } }) {
                        Icon(Icons.Default.Download, contentDescription = s.importLabel, tint = c.textMain)
                    }
                }
            }

            Spacer(Modifier.height(if (narrow) 12.dp else 24.dp))
            if (folder == null) UpdateBanner(app)

            if (plans.isEmpty() && folders.isEmpty()) {
                Column(Modifier.weight(1f)) {
                    Text(if (folder != null) s3.folderEmpty else s.noSpeeches, color = c.textSub)
                    if (ui.canPickDocuments && folder == null) {
                        Spacer(Modifier.height(8.dp))
                        Text(s3.importDropFile, color = c.textSub, fontSize = 14.sp)
                    }
                    if (archivedCount > 0 && folder == null) ArchiveLink(archivedCount) { app.guardEdits { app.openArchive() } }
                }
            } else {
                LazyColumn(Modifier.weight(1f)) {
                    items(folders, key = { "folder-" + it.id }) { f ->
                        FolderCard(f, count = folderCounts[f.id] ?: 0) { app.openFolder(f.id) }
                    }
                    if (plans.isEmpty() && folder != null) {
                        item(key = "empty") { Text(s3.folderEmpty, color = c.textSub, modifier = Modifier.padding(vertical = 8.dp)) }
                    }
                    items(plans, key = { it.id }) { plan ->
                        PlanCard(
                            plan = plan,
                            // In the wide layout the open speech is shown next to the list: mark it
                            selected = wide && app.activePlan?.id == plan.id,
                            onOpen = { if (app.activePlan?.id != plan.id || app.currentScreen != AppScreen.EDIT) app.guardEdits { app.openPlan(plan) } },
                            menu = { close ->
                                DropdownMenuItem(
                                    text = { Text(s.edit) },
                                    leadingIcon = { Icon(Icons.Default.Edit, null) },
                                    onClick = { close(); app.guardEdits { app.editPlan(plan) } },
                                )
                                DropdownMenuItem(
                                    text = { Text(s.export) },
                                    leadingIcon = { Icon(Icons.Default.Share, null) },
                                    onClick = { close(); startExport(app, plan, ui) { planToExport = plan } },
                                )
                                DropdownMenuItem(
                                    text = { Text(s3.moveToFolder) },
                                    leadingIcon = { Icon(Icons.Default.DriveFileMove, null) },
                                    onClick = { close(); planToMove = plan },
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
                    if (archivedCount > 0 && folder == null) {
                        item(key = "archive") { ArchiveLink(archivedCount) { app.guardEdits { app.openArchive() } } }
                    }
                }
            }

            Spacer(Modifier.height(16.dp))
            Button(onClick = { app.guardEdits { app.createNewPlan() } }, modifier = Modifier.fillMaxWidth().height(56.dp)) {
                Icon(Icons.Default.Add, contentDescription = null)
                Spacer(Modifier.width(8.dp))
                FitText(s.createNewSpeech, maxSize = 18.sp, fontWeight = FontWeight.Medium)
            }
            Spacer(Modifier.height(8.dp))
            // Quick timer and meeting mode side by side (stacked in a very thin pane)
            val quick: @Composable (Modifier) -> Unit = { m ->
                OutlinedButton(onClick = { app.guardEdits { showQuick = true } }, modifier = m.height(48.dp), contentPadding = PaddingValues(horizontal = 10.dp)) {
                    Icon(Icons.Default.Timer, contentDescription = null, tint = c.textMain)
                    Spacer(Modifier.width(8.dp))
                    FitText(s.quickTimer, maxSize = 16.sp, fontWeight = FontWeight.Medium, color = c.textMain)
                }
            }
            val meeting: @Composable (Modifier) -> Unit = { m ->
                OutlinedButton(onClick = { app.openMeeting() }, modifier = m.height(48.dp), contentPadding = PaddingValues(horizontal = 10.dp)) {
                    Icon(Icons.Default.Groups, contentDescription = null, tint = c.textMain)
                    Spacer(Modifier.width(8.dp))
                    FitText(s3.meetingMode, maxSize = 16.sp, fontWeight = FontWeight.Medium, color = c.textMain)
                }
            }
            if (maxWidth < 300.dp) {
                quick(Modifier.fillMaxWidth())
                Spacer(Modifier.height(8.dp))
                meeting(Modifier.fillMaxWidth())
            } else {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    quick(Modifier.weight(1f))
                    meeting(Modifier.weight(1f))
                }
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
            title = { ClosableTitle(s.importTitle) { showImportChoice = false } },
            text = {
                Column {
                    ChoiceRow(Icons.Default.Description, s.importFromFile, s3.importFileDesc) {
                        showImportChoice = false
                        ui.pickDocument()
                    }
                    Spacer(Modifier.height(4.dp))
                    ChoiceRow(Icons.Default.ContentPaste, s.importPaste, s3.importPasteOnly) {
                        showImportChoice = false
                        showPaste = true
                    }
                    Spacer(Modifier.height(12.dp))
                    Text(s3.importDropFile, fontSize = 13.sp, color = c.textSub)
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

    planToExport?.let { plan ->
        ExportDialog(app, plan, onDismiss = { planToExport = null })
    }
    if (exportLibrary) ExportDialog(app, null, onDismiss = { exportLibrary = false })

    planToMove?.let { plan ->
        MoveToFolderDialog(app, plan, onDone = { planToMove = null })
    }
    if (newFolder) {
        FolderNameDialog(title = s3.newFolder, initial = "", confirm = s3.create, onDismiss = { newFolder = false }) { name ->
            newFolder = false
            app.createFolder(name)?.let { app.openFolder(it.id) }
        }
    }
    if (renameFolder && folder != null) {
        FolderNameDialog(title = s3.renameFolder, initial = folder.name, confirm = s3.save, onDismiss = { renameFolder = false }) { name ->
            renameFolder = false
            app.renameFolder(folder.id, name)
        }
    }
    if (deleteFolder && folder != null) {
        AlertDialog(
            onDismissRequest = { deleteFolder = false },
            title = { ClosableTitle(s3.deleteFolderTitle(folder.name)) { deleteFolder = false } },
            text = { Text(s3.deleteFolderText) },
            confirmButton = {
                Button(
                    onClick = { deleteFolder = false; app.deleteFolder(folder.id) },
                    colors = ButtonDefaults.buttonColors(containerColor = c.over, contentColor = c.onOver),
                ) { Text(s.delete) }
            },
            dismissButton = { TextButton(onClick = { deleteFolder = false }) { Text(s.cancel, color = c.textMain) } },
        )
    }
}

/** A folder in the list: tap to open it. */
@Composable
private fun FolderCard(folder: Folder, count: Int, onOpen: () -> Unit) {
    val s3 = LocalStrings3.current
    val c = AppTheme.colors
    Card(onClick = onOpen, modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp)) {
        Row(Modifier.fillMaxWidth().padding(start = 16.dp, top = 12.dp, bottom = 12.dp, end = 12.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Default.Folder, contentDescription = null, tint = c.textSub, modifier = Modifier.size(26.dp))
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f)) {
                Text(folder.name, fontSize = 20.sp, fontWeight = FontWeight.Bold, maxLines = 2, overflow = TextOverflow.Ellipsis)
                Text(s3.folderCount(count), color = c.textSub, fontSize = 14.sp)
            }
            Icon(Icons.Default.ChevronRight, contentDescription = null, tint = c.textSub)
        }
    }
}

/** "⋮" in an open folder: rename or delete it. */
@Composable
private fun FolderMenu(onRename: () -> Unit, onDelete: () -> Unit) {
    val s = LocalStrings.current
    val s3 = LocalStrings3.current
    var open by remember { mutableStateOf(false) }
    Box {
        IconButton(onClick = { open = true }) {
            Icon(Icons.Default.MoreVert, contentDescription = s.moreOptions, tint = AppTheme.colors.textMain)
        }
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            DropdownMenuItem(text = { Text(s3.renameFolder) }, leadingIcon = { Icon(Icons.Default.Edit, null) }, onClick = { open = false; onRename() })
            DropdownMenuItem(text = { Text(s3.deleteFolder) }, leadingIcon = { Icon(Icons.Default.Delete, null) }, onClick = { open = false; onDelete() })
        }
    }
}

@Composable
private fun FolderNameDialog(title: String, initial: String, confirm: String, onDismiss: () -> Unit, onConfirm: (String) -> Unit) {
    val s = LocalStrings.current
    val s3 = LocalStrings3.current
    val c = AppTheme.colors
    var name by remember { mutableStateOf(initial) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { ClosableTitle(title, onDismiss) },
        text = {
            OutlinedTextField(
                value = name,
                onValueChange = { name = it.take(60) },
                label = { Text(s3.folderName) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
        },
        confirmButton = { Button(onClick = { onConfirm(name) }, enabled = name.isNotBlank()) { Text(confirm) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text(s.cancel, color = c.textMain) } },
    )
}

/** Pick the folder for a speech (or none), or make a new one for it. */
@Composable
private fun MoveToFolderDialog(app: AppController, plan: SpeechPlan, onDone: () -> Unit) {
    val s = LocalStrings.current
    val s3 = LocalStrings3.current
    val c = AppTheme.colors
    var makeNew by remember { mutableStateOf(false) }
    if (makeNew) {
        FolderNameDialog(title = s3.newFolder, initial = "", confirm = s3.create, onDismiss = onDone) { name ->
            app.createFolder(name)?.let { app.moveToFolder(plan, it.id) }
            onDone()
        }
        return
    }
    val current = app.folderById(plan.folderId)?.id
    AlertDialog(
        onDismissRequest = onDone,
        title = { ClosableTitle(s3.moveToFolder, onDone) },
        text = {
            Column(Modifier.heightIn(max = 420.dp).verticalScroll(rememberScrollState())) {
                FolderOption(s3.noFolder, current == null) { app.moveToFolder(plan, null); onDone() }
                app.sortedFolders.forEach { f ->
                    FolderOption(f.name, current == f.id) { app.moveToFolder(plan, f.id); onDone() }
                }
                TextButton(onClick = { makeNew = true }, modifier = Modifier.padding(top = 4.dp)) {
                    Icon(Icons.Default.CreateNewFolder, contentDescription = null, tint = c.textMain)
                    Spacer(Modifier.width(8.dp))
                    Text(s3.newFolder, color = c.textMain)
                }
            }
        },
        confirmButton = {},
        dismissButton = { TextButton(onClick = onDone) { Text(s.cancel, color = c.textMain) } },
    )
}

@Composable
private fun FolderOption(label: String, selected: Boolean, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().clickable(onClick = onClick).padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        RadioButton(selected = selected, onClick = null)
        Spacer(Modifier.width(12.dp))
        Text(label, maxLines = 2, overflow = TextOverflow.Ellipsis)
    }
}

/** One speech in the list. Its actions sit in a "⋮" menu, so the title keeps its room in narrow panes. */
@Composable
private fun PlanCard(
    plan: SpeechPlan,
    selected: Boolean,
    onOpen: () -> Unit,
    menu: @Composable ColumnScope.(close: () -> Unit) -> Unit,
) {
    val s = LocalStrings.current
    val c = AppTheme.colors
    var open by remember { mutableStateOf(false) }
    Card(
        onClick = onOpen,
        modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp),
        border = if (selected) BorderStroke(2.dp, c.accent) else null,
    ) {
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
internal fun ChoiceRow(icon: androidx.compose.ui.graphics.vector.ImageVector, title: String, desc: String, onClick: () -> Unit) {
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

/** Paste an exported speech or library backup. */
@Composable
private fun PasteDialog(app: AppController, onDismiss: () -> Unit) {
    val s = LocalStrings.current
    val ui = LocalUiActions.current
    val c = AppTheme.colors
    var text by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { ClosableTitle(s.importTitle, onDismiss) },
        text = {
            OutlinedTextField(
                value = text,
                onValueChange = { text = it },
                label = { Text(LocalStrings3.current.importPasteHint) },
                modifier = Modifier.fillMaxWidth().height(180.dp),
            )
        },
        confirmButton = {
            Button(
                enabled = text.isNotBlank(),
                onClick = {
                    if (app.importText(text)) onDismiss() else ui.toast(app.strings3.importPasteOnly)
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
                onClick = {
                    if (app.activePlan?.id == plan.id) app.backToLibrary()
                    app.deletePlan(plan)
                    onDone()
                },
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
        title = { ClosableTitle(s.quickTimer, onDismiss) },
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
