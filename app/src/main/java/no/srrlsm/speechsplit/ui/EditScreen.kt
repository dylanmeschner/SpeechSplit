package no.srrlsm.speechsplit.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import no.srrlsm.speechsplit.core.AppController
import no.srrlsm.speechsplit.core.SpeechSegment
import no.srrlsm.speechsplit.core.formatTime
import no.srrlsm.speechsplit.core.formatTimeForInput
import no.srrlsm.speechsplit.core.parseTimeInput
import no.srrlsm.speechsplit.core.summaryOf
import no.srrlsm.speechsplit.ui.theme.AppTheme

// ============================================================================
// EDIT
// ============================================================================
@Composable
fun EditScreen(app: AppController) {
    val s = LocalStrings.current
    val plan = app.activePlan ?: return
    var confirmDiscard by remember { mutableStateOf(false) }
    // Back used to throw away edits silently. Now it asks first (only if something changed).
    val goBack: () -> Unit = { if (app.hasUnsavedEdits) confirmDiscard = true else app.cancelEditing() }
    AppBackHandler { goBack() }

    if (confirmDiscard) {
        AlertDialog(
            onDismissRequest = { confirmDiscard = false },
            title = { Text(s.discardTitle) },
            text = { Text(s.discardText) },
            confirmButton = {
                Button(onClick = { confirmDiscard = false; app.cancelEditing() }) { Text(s.discard) }
            },
            dismissButton = {
                TextButton(onClick = { confirmDiscard = false }) { Text(s.keepEditing, color = AppTheme.colors.textMain) }
            },
        )
    }

    Column(Modifier.fillMaxSize().padding(horizontal = 24.dp, vertical = 16.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = goBack) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = s.back)
            }
            Text(s.editTitle, fontSize = 24.sp, fontWeight = FontWeight.Bold)
        }

        OutlinedTextField(
            value = plan.title,
            onValueChange = { t -> app.updateActivePlan { it.copy(title = t) } },
            label = { Text(s.speechTitleLabel) },
            singleLine = true,
            modifier = Modifier.fillMaxWidth().padding(top = 16.dp),
        )
        Text(s.summaryOf(plan), color = AppTheme.colors.textSub, fontSize = 14.sp, modifier = Modifier.padding(vertical = 12.dp))

        LazyColumn(Modifier.weight(1f)) {
            // Stable keys: text fields keep focus/state when segments are reordered or deleted
            itemsIndexed(plan.segments, key = { _, seg -> seg.id }) { index, segment ->
                SegmentEditorCard(
                    index = index,
                    count = plan.segments.size,
                    segment = segment,
                    onTitleChange = { t -> app.updateSegment(segment.id) { it.copy(title = t) } },
                    onTargetChange = { secs -> app.updateSegment(segment.id) { it.copy(targetSeconds = secs) } },
                    onMove = { to -> app.moveSegment(index, to) },
                    onDelete = { app.removeSegment(segment.id) },
                )
            }
            item {
                TextButton(onClick = { app.addSegment() }, modifier = Modifier.padding(vertical = 8.dp)) {
                    Icon(Icons.Default.Add, contentDescription = null)
                    Text(" ${s.addSegment}")
                }
            }
        }

        Button(
            onClick = { app.saveActivePlan() },
            enabled = plan.segments.isNotEmpty(),
            modifier = Modifier.fillMaxWidth().height(56.dp),
        ) { FitText(s.saveAndReady, maxSize = 18.sp) }
    }
}

@Composable
private fun SegmentEditorCard(
    index: Int,
    count: Int,
    segment: SpeechSegment,
    onTitleChange: (String) -> Unit,
    onTargetChange: (Int) -> Unit,
    onMove: (Int) -> Unit,
    onDelete: () -> Unit,
) {
    val s = LocalStrings.current
    var timeInput by remember { mutableStateOf(formatTimeForInput(segment.targetSeconds)) }

    Card(Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
        Column(Modifier.padding(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                OutlinedTextField(
                    value = segment.title,
                    onValueChange = onTitleChange,
                    label = { Text(s.segmentLabel(index + 1)) },
                    singleLine = true,
                    modifier = Modifier.weight(1f),
                )
                IconButton(onClick = onDelete) {
                    Icon(Icons.Default.Delete, contentDescription = s.deleteSegment, tint = AppTheme.colors.textSub)
                }
            }
            Spacer(Modifier.height(8.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                OutlinedTextField(
                    value = timeInput,
                    onValueChange = {
                        timeInput = it
                        onTargetChange(parseTimeInput(it))
                    },
                    label = { Text(s.timeLabel) },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    modifier = Modifier.weight(1f).widthIn(max = 170.dp),
                )
                // Shows how the input was understood, so "4.5" vs "4.05" is never a surprise
                Text(
                    "= ${formatTime(segment.targetSeconds)}",
                    color = AppTheme.colors.textSub,
                    style = Tabular,
                    maxLines = 1,
                    modifier = Modifier.padding(horizontal = 8.dp),
                )
                IconButton(onClick = { onMove(index - 1) }, enabled = index > 0) {
                    Icon(Icons.Default.KeyboardArrowUp, contentDescription = s.moveUp)
                }
                IconButton(onClick = { onMove(index + 1) }, enabled = index < count - 1) {
                    Icon(Icons.Default.KeyboardArrowDown, contentDescription = s.moveDown)
                }
            }
        }
    }
}
