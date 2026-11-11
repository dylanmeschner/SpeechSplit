package no.srrlsm.speechsplit.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import no.srrlsm.speechsplit.core.AppController
import no.srrlsm.speechsplit.core.formatTime
import no.srrlsm.speechsplit.core.summaryOf
import no.srrlsm.speechsplit.ui.theme.AppTheme

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ReadyScreen(app: AppController) {
    val s = LocalStrings.current
    val c = AppTheme.colors
    val plan = app.activePlan ?: return
    val runCount = app.runsFor(plan.id).size
    val ui = LocalUiActions.current
    val wide = LocalWideLayout.current
    var showExport by remember { mutableStateOf(false) }
    var showDrill by remember { mutableStateOf(false) }
    val s3 = LocalStrings3.current
    AppBackHandler { app.backToLibrary() }

    // Scales with the split-screen pane, like the timer screen does
    BoxWithConstraints(Modifier.fillMaxSize()) {
        val scale = (maxWidth / 420.dp).coerceIn(0.6f, 1f)
        val shortPane = maxHeight < 480.dp
        val pad = if (maxWidth < 360.dp) 16.dp else 32.dp
        val paneHeight = maxHeight
        val narrow = maxWidth < 360.dp

        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState()), // very short panes scroll instead of clipping
        ) {
            Column(
                // At least as tall as the pane, so the content stays centred when it fits
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = paneHeight)
                    .padding(horizontal = pad, vertical = if (shortPane) 16.dp else 32.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
            ) {
                Text(s.readyToSpeak, color = c.textSub, fontWeight = FontWeight.Bold, letterSpacing = 2.sp)
                Spacer(Modifier.height(if (shortPane) 8.dp else 16.dp))
                Text(
                    plan.title,
                    fontSize = (40 * scale).sp,
                    lineHeight = (44 * scale).sp,
                    fontWeight = FontWeight.Bold,
                    textAlign = TextAlign.Center,
                )
                Spacer(Modifier.height(8.dp))
                Text(s.summaryOf(plan), fontSize = (18 * scale).sp, color = c.textSub, textAlign = TextAlign.Center)
                if (plan.bufferSeconds > 0) {
                    Text(s3.bufferPlus(formatTime(plan.bufferSeconds)), fontSize = (15 * scale).sp, color = c.textSub, textAlign = TextAlign.Center)
                }

                Spacer(Modifier.height(if (shortPane) 16.dp else 40.dp))

                // Practice or a real speech: real ones are marked in the history
                Column(Modifier.fillMaxWidth(if (narrow) 1f else 0.8f).widthIn(max = 420.dp)) {
                    SegmentedChoice(
                        options = listOf(false to s3.modePractice, true to s3.modeLive),
                        selected = app.settings.runLive,
                        onSelect = { app.setRunLive(it) },
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Text(s3.modeDesc, fontSize = 13.sp, color = c.textSub, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth().padding(top = 6.dp))
                }
                Spacer(Modifier.height(if (shortPane) 12.dp else 18.dp))

                Button(
                    onClick = { app.startTimer() },
                    enabled = plan.segments.isNotEmpty(),
                    modifier = Modifier
                        .fillMaxWidth(if (narrow) 1f else 0.8f)
                        .widthIn(max = 420.dp)
                        .height(if (shortPane) 64.dp else 80.dp),
                    shape = RoundedCornerShape(16.dp),
                    contentPadding = PaddingValues(horizontal = 16.dp),
                ) {
                    Icon(Icons.Default.PlayArrow, contentDescription = null, modifier = Modifier.size(28.dp))
                    Spacer(Modifier.width(8.dp))
                    // Shrinks instead of wrapping, so "TIMER" can no longer disappear under "START"
                    FitText(s.startTimer, maxSize = 24.sp, minSize = 12.sp)
                }

                if (app.supportsDnd) {
                    Spacer(Modifier.height(16.dp))
                    // Offered right here, since this is the moment it matters
                    Box(Modifier.fillMaxWidth(if (narrow) 1f else 0.8f).widthIn(max = 420.dp)) {
                        DndSwitch(app)
                    }
                }

                Spacer(Modifier.height(if (shortPane) 8.dp else 16.dp))
                // Wraps onto two lines in a thin split-screen pane instead of pushing "Library" off the edge
                FlowRow(horizontalArrangement = Arrangement.Center) {
                    TextButton(onClick = { app.editActivePlan() }) { Text(s.edit, color = c.textSub) }
                    TextButton(onClick = { app.openHistory() }) {
                        Text(if (runCount > 0) "${s.history} ($runCount)" else s.history, color = c.textSub)
                    }
                    if (plan.segments.size > 1) {
                        TextButton(onClick = { showDrill = true }) { Text(s3.drillTitle, color = c.textSub) }
                    }
                    TextButton(onClick = { startExport(app, plan, ui) { showExport = true } }) { Text(s.export, color = c.textSub) }
                    // In the wide layout the list is already next to this
                    if (!wide) TextButton(onClick = { app.backToLibrary() }) { Text(s.library, color = c.textSub) }
                }
            }
        }
    }

    if (showExport) ExportDialog(app, plan, onDismiss = { showExport = false })
    if (showDrill) {
        AlertDialog(
            onDismissRequest = { showDrill = false },
            title = { ClosableTitle(s3.drillTitle) { showDrill = false } },
            text = {
                Column(Modifier.heightIn(max = 440.dp).verticalScroll(rememberScrollState())) {
                    Text(s3.drillPick, fontSize = 14.sp, color = c.textSub)
                    Spacer(Modifier.height(10.dp))
                    plan.segments.forEachIndexed { i, seg ->
                        Surface(
                            onClick = { showDrill = false; app.startDrill(i) },
                            color = c.surfaceHigh,
                            shape = MaterialTheme.shapes.medium,
                            modifier = Modifier.fillMaxWidth().padding(vertical = 3.dp),
                        ) {
                            Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                                Text(seg.title, fontWeight = FontWeight.Bold, maxLines = 2, modifier = Modifier.weight(1f))
                                Text(formatTime(seg.targetSeconds), color = c.textSub, style = Tabular)
                            }
                        }
                    }
                }
            },
            confirmButton = {},
            dismissButton = { TextButton(onClick = { showDrill = false }) { Text(s.cancel, color = c.textMain) } },
        )
    }
}
