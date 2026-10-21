package no.srrlsm.speechsplit.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import no.srrlsm.speechsplit.core.AppController
import no.srrlsm.speechsplit.core.ExportScope
import no.srrlsm.speechsplit.core.SPEECH_FILE_MIME
import no.srrlsm.speechsplit.core.SpeechPlan
import no.srrlsm.speechsplit.ui.theme.AppTheme

// ============================================================================
// EXPORT: one speech, one speech with its history, or the whole library (speeches, archive,
// history) as a .speechsplit file. Save it anywhere (Drive, Dropbox, OneDrive, this device),
// or share it (email, Quick Share...). Every version opens it with Import → Open a file.
// ============================================================================

/** Opens the export dialog, or copies the speech as text on a platform that can't do files. */
fun startExport(app: AppController, plan: SpeechPlan?, ui: UiActions, showChoices: () -> Unit) {
    if (ui.canSaveFiles || ui.canSendFiles || plan == null) showChoices()
    else ui.share(app.exportPlan(plan), app.strings.exportChooser)
}

/** [plan] null: opened from the library, so only "whole library" is offered. */
@Composable
fun ExportDialog(app: AppController, plan: SpeechPlan?, onDismiss: () -> Unit) {
    val s = LocalStrings.current
    val ui = LocalUiActions.current
    val c = AppTheme.colors
    val runCount = plan?.let { app.runsFor(it.id).size } ?: 0
    var scope by remember { mutableStateOf(if (plan != null) ExportScope.SPEECH else ExportScope.LIBRARY) }

    fun file() = app.exportFile(plan, scope)

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(s.exportChooser) },
        text = {
            Column(Modifier.selectableGroup()) {
                if (plan != null) {
                    ScopeRow(s.exportThisSpeech, plan.title, scope == ExportScope.SPEECH) { scope = ExportScope.SPEECH }
                    ScopeRow(
                        s.exportWithHistory,
                        s.exportWithHistoryDesc(runCount),
                        scope == ExportScope.SPEECH_WITH_HISTORY,
                        enabled = runCount > 0,
                    ) { scope = ExportScope.SPEECH_WITH_HISTORY }
                }
                ScopeRow(
                    s.exportLibrary,
                    s.exportLibraryDesc(app.speechPlans.size, app.runs.size),
                    scope == ExportScope.LIBRARY,
                ) { scope = ExportScope.LIBRARY }
                Spacer(Modifier.height(12.dp))
                Text(s.exportNote, fontSize = 13.sp, color = c.textSub)
            }
        },
        confirmButton = {
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp), verticalAlignment = Alignment.CenterVertically) {
                if (ui.canSendFiles) {
                    TextButton(onClick = {
                        val (name, text) = file()
                        onDismiss()
                        ui.sendFile(name, SPEECH_FILE_MIME, text, s.exportChooser)
                    }) { Text(s.shareAction, color = c.textMain) }
                }
                Button(onClick = {
                    val (name, text) = file()
                    onDismiss()
                    if (ui.canSaveFiles) ui.saveFile(name, SPEECH_FILE_MIME, text) else ui.copyText(text)
                }) { Text(s.export) }
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(s.cancel, color = c.textMain) } },
    )
}

@Composable
private fun ScopeRow(title: String, desc: String, selected: Boolean, enabled: Boolean = true, onClick: () -> Unit) {
    val c = AppTheme.colors
    Row(
        Modifier
            .fillMaxWidth()
            .selectable(selected = selected, enabled = enabled, role = Role.RadioButton, onClick = onClick)
            .padding(vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        RadioButton(selected = selected, onClick = null, enabled = enabled)
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(title, fontWeight = FontWeight.Bold, color = if (enabled) c.textMain else c.textSub)
            Text(desc, fontSize = 13.sp, color = c.textSub, maxLines = 2)
        }
    }
}

/** A library backup was opened: add what's missing, or (after a second question) replace everything. */
@Composable
fun ImportLibraryDialog(app: AppController) {
    val file = app.pendingLibrary ?: return
    val s = LocalStrings.current
    val c = AppTheme.colors
    var confirmReplace by remember { mutableStateOf(false) }
    if (confirmReplace) {
        AlertDialog(
            onDismissRequest = { confirmReplace = false },
            title = { Text(s.replaceConfirmTitle) },
            text = { Text(s.replaceConfirmText) },
            confirmButton = {
                Button(
                    onClick = { app.applyLibrary(replace = true) },
                    colors = ButtonDefaults.buttonColors(containerColor = c.over, contentColor = c.onOver),
                ) { Text(s.importReplace) }
            },
            dismissButton = { TextButton(onClick = { confirmReplace = false }) { Text(s.cancel, color = c.textMain) } },
        )
        return
    }
    AlertDialog(
        onDismissRequest = { app.dismissLibrary() },
        title = { Text(s.importLibraryTitle) },
        text = { Text(s.importLibraryText(file.plans.size, file.runs.size), style = MaterialTheme.typography.bodyMedium) },
        confirmButton = { Button(onClick = { app.applyLibrary(replace = false) }) { Text(s.importAdd) } },
        dismissButton = {
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                TextButton(onClick = { app.dismissLibrary() }) { Text(s.cancel, color = c.textMain) }
                TextButton(onClick = { confirmReplace = true }) { Text(s.importReplace, color = c.over) }
            }
        },
    )
}
