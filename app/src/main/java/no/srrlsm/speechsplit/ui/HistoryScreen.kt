package no.srrlsm.speechsplit.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import no.srrlsm.speechsplit.core.AppController
import no.srrlsm.speechsplit.core.PracticeRun
import no.srrlsm.speechsplit.core.RecentRuns
import no.srrlsm.speechsplit.core.buildRunReport
import no.srrlsm.speechsplit.core.formatDiff
import no.srrlsm.speechsplit.core.formatTime
import no.srrlsm.speechsplit.core.planStats
import no.srrlsm.speechsplit.ui.theme.AppTheme
import kotlin.math.abs

// ============================================================================
// PRACTICE HISTORY for one speech: stats, trend, suggestions, and every run.
// ============================================================================
@Composable
fun HistoryScreen(app: AppController) {
    val s = LocalStrings.current
    val ui = LocalUiActions.current
    val c = AppTheme.colors
    val plan = app.activePlan ?: return
    val runs = app.runsFor(plan.id) // newest first
    val stats = remember(plan, runs, app.settings.celebrateTolerance) {
        planStats(plan, runs, app.settings.celebrateTolerance)
    }
    var expandedRun by remember { mutableStateOf<String?>(null) }
    var confirmClear by remember { mutableStateOf(false) }
    AppBackHandler { app.closeHistory() }

    fun share(run: PracticeRun) =
        ui.share(buildRunReport(run, s, app.formatDate(run.finishedAtEpochMs)), s.shareReport)

    Column(Modifier.fillMaxSize().padding(horizontal = 16.dp, vertical = 8.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = { app.closeHistory() }) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = s.back)
            }
            Column(Modifier.weight(1f)) {
                Text(s.historyTitle, fontSize = 24.sp, fontWeight = FontWeight.Bold)
                Text(plan.title, color = c.textSub, fontSize = 14.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }

        if (runs.isEmpty()) {
            Text(s.noHistory, color = c.textSub, modifier = Modifier.padding(16.dp))
            return@Column
        }

        LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 24.dp)) {
            // --- Key numbers ---
            item {
                Row(
                    Modifier.fillMaxWidth().padding(top = 12.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    StatBox(s.statRuns, Modifier.weight(1f)) { BigNumber("${stats.runCount}") }
                    StatBox(s.statOnTarget, Modifier.weight(1f)) { BigNumber("${stats.onTargetCount}") }
                }
                Spacer(Modifier.height(8.dp))
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    StatBox(s.statAverage(minOf(RecentRuns, runs.size)), Modifier.weight(1f)) {
                        DiffPill(stats.avgTotalDiffSeconds, 16.sp)
                    }
                    StatBox(s.statBest, Modifier.weight(1f)) {
                        stats.bestRun?.let { DiffPill(it.diffSeconds, 16.sp) }
                    }
                }
            }

            // --- Trend: one bar per run, up = over, down = under ---
            if (runs.size >= 2) {
                item {
                    SectionLabel(s.trendTitle, Modifier.padding(top = 24.dp, bottom = 8.dp, start = 4.dp))
                    Card(Modifier.fillMaxWidth()) {
                        TrendBars(runs.take(12).reversed().map { it.diffSeconds }, Modifier.fillMaxWidth().height(96.dp).padding(12.dp))
                    }
                }
            }

            // --- Suggestions (never applied automatically) ---
            if (stats.suggestions.isNotEmpty()) {
                item {
                    SectionLabel(s.suggestionsTitle, Modifier.padding(top = 24.dp, bottom = 8.dp, start = 4.dp))
                    Surface(
                        color = c.adjusted.copy(alpha = 0.10f),
                        border = BorderStroke(1.dp, c.adjusted.copy(alpha = 0.5f)),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Column(Modifier.padding(14.dp)) {
                            stats.suggestions.forEach { seg ->
                                val avg = formatDiff(seg.avgDiffSeconds)
                                val sug = formatTime(seg.suggestedSeconds ?: seg.segment.targetSeconds)
                                Row(Modifier.padding(bottom = 10.dp)) {
                                    Icon(Icons.Default.Lightbulb, contentDescription = null, tint = c.adjusted, modifier = Modifier.size(18.dp).padding(top = 2.dp))
                                    Spacer(Modifier.width(8.dp))
                                    Column {
                                        Text(seg.segment.title, fontWeight = FontWeight.Bold)
                                        Text(
                                            if (seg.avgDiffSeconds > 0) s.suggestOver(avg, sug) else s.suggestUnder(avg, sug),
                                            color = c.textSub,
                                            fontSize = 14.sp,
                                        )
                                    }
                                }
                            }
                            Text(s.suggestionsNote, color = c.textSub, fontSize = 12.sp)
                        }
                    }
                }
            }

            // --- Average per segment ---
            item {
                SectionLabel(s.segmentAveragesTitle, Modifier.padding(top = 24.dp, bottom = 8.dp, start = 4.dp))
                Card(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(horizontal = 14.dp, vertical = 6.dp)) {
                        stats.segments.forEachIndexed { i, seg ->
                            if (i > 0) HorizontalDivider()
                            Row(Modifier.fillMaxWidth().padding(vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                                Text(seg.segment.title, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
                                Text(formatTime(seg.segment.targetSeconds), color = c.textSub, fontSize = 13.sp, style = Tabular)
                                Spacer(Modifier.width(8.dp))
                                if (seg.runs > 0) DiffPill(seg.avgDiffSeconds, 13.sp)
                                else Text("–", color = c.textSub)
                            }
                        }
                    }
                }
            }

            // --- Every run ---
            item { SectionLabel(s.runsTitle, Modifier.padding(top = 24.dp, bottom = 8.dp, start = 4.dp)) }
            items(runs, key = { it.id }) { run ->
                val expanded = expandedRun == run.id
                Card(Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
                    Column(Modifier.clickable { expandedRun = if (expanded) null else run.id }.padding(start = 14.dp, end = 4.dp, top = 6.dp, bottom = 6.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Column(Modifier.weight(1f)) {
                                Text(app.formatDate(run.finishedAtEpochMs), fontWeight = FontWeight.Medium, maxLines = 1)
                                Text(
                                    "${formatTime(run.totalActualSeconds)} · ${s.reportPlan(formatTime(run.totalTargetSeconds))}",
                                    color = c.textSub, fontSize = 13.sp, style = Tabular,
                                )
                            }
                            DiffPill(run.diffSeconds, 14.sp)
                            IconButton(onClick = { share(run) }) {
                                Icon(Icons.Default.Share, contentDescription = s.shareReport, tint = c.textSub)
                            }
                            IconButton(onClick = { app.deleteRun(run.id) }) {
                                Icon(Icons.Default.Delete, contentDescription = s.deleteRun, tint = c.textSub)
                            }
                        }
                        if (expanded) {
                            run.segments.forEach { seg ->
                                Row(Modifier.fillMaxWidth().padding(end = 12.dp, top = 4.dp, bottom = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                                    Text(seg.title, color = c.textSub, fontSize = 14.sp, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
                                    Text(formatTime(seg.actualSeconds), fontSize = 14.sp, style = Tabular)
                                    Spacer(Modifier.width(8.dp))
                                    DiffPill(seg.diffSeconds, 12.sp)
                                }
                            }
                        }
                    }
                }
            }

            item {
                TextButton(onClick = { confirmClear = true }, modifier = Modifier.padding(top = 12.dp)) {
                    Icon(Icons.Default.DeleteSweep, contentDescription = null, tint = c.textSub)
                    Spacer(Modifier.width(8.dp))
                    Text(s.clearHistory, color = c.textSub)
                }
            }
        }
    }

    if (confirmClear) {
        AlertDialog(
            onDismissRequest = { confirmClear = false },
            title = { Text(s.clearHistoryTitle) },
            text = { Text(s.cannotBeUndone) },
            confirmButton = {
                Button(onClick = { confirmClear = false; app.clearHistory(plan.id) }) { Text(s.delete) }
            },
            dismissButton = {
                TextButton(onClick = { confirmClear = false }) { Text(s.cancel, color = c.textMain) }
            },
        )
    }
}

@Composable
private fun StatBox(label: String, modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    Card(modifier) {
        Column(Modifier.fillMaxWidth().padding(12.dp)) {
            SectionLabel(label.uppercase(), size = 10.sp, modifier = Modifier.padding(bottom = 6.dp))
            content()
        }
    }
}

@Composable
private fun BigNumber(text: String) {
    Text(text, fontSize = 26.sp, fontWeight = FontWeight.Bold, style = Tabular)
}

/** Bars around a middle line: up for over the plan, down for under. Oldest run on the left. */
@Composable
private fun TrendBars(diffs: List<Int>, modifier: Modifier = Modifier) {
    val c = AppTheme.colors
    val maxAbs = (diffs.maxOfOrNull { abs(it) } ?: 0).coerceAtLeast(5).toFloat()
    Canvas(modifier) {
        val mid = size.height / 2
        drawLine(c.divider, Offset(0f, mid), Offset(size.width, mid), strokeWidth = 1.dp.toPx())
        if (diffs.isEmpty()) return@Canvas
        val slot = size.width / diffs.size
        val barW = (slot * 0.6f).coerceAtMost(28.dp.toPx())
        diffs.forEachIndexed { i, d ->
            val h = (abs(d) / maxAbs) * (mid - 2.dp.toPx())
            val x = i * slot + (slot - barW) / 2
            val color = if (d > 0) c.over else c.green
            if (d == 0) {
                // Exactly on time: a small dot on the line
                drawCircle(c.green, radius = 3.dp.toPx(), center = Offset(x + barW / 2, mid))
            } else {
                drawRect(
                    color = color,
                    topLeft = Offset(x, if (d > 0) mid - h else mid),
                    size = Size(barW, h.coerceAtLeast(2.dp.toPx())),
                )
            }
        }
    }
}
