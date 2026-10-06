package no.srrlsm.speechsplit.ui

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Undo
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import no.srrlsm.speechsplit.core.AppController
import no.srrlsm.speechsplit.core.ClockMode
import no.srrlsm.speechsplit.core.SpeechSegment
import no.srrlsm.speechsplit.core.TimeStatus
import no.srrlsm.speechsplit.core.buildRunReport
import no.srrlsm.speechsplit.core.formatDiff
import no.srrlsm.speechsplit.core.formatTime
import no.srrlsm.speechsplit.core.timeStatus
import no.srrlsm.speechsplit.ui.theme.AppTheme
import kotlinx.coroutines.launch

// ============================================================================
// TIMER
// ============================================================================
@Composable
fun TimerScreen(app: AppController) {
    val s = LocalStrings.current
    val settings = LocalSettings.current
    val plan = app.activePlan ?: return
    val engine = app.engine
    val segments = plan.segments
    val index = engine.currentIndex
    val current = segments.getOrNull(index)
    val finished = engine.isFinished
    val haptics = LocalHapticFeedback.current
    var confirmStop by remember { mutableStateOf(false) }
    val requestStop: () -> Unit = { if (finished) app.stopTimer() else { confirmStop = true } }

    // System back no longer closes the app mid-speech
    AppBackHandler { requestStop() }

    // --- Math ---
    // Segment seconds come from one running total, so they always add up to the TOTAL clock.
    // pastBanked < 0: ahead of plan (time saved). pastBanked > 0: behind (time lost).
    val elapsed = engine.allElapsedSeconds()
    val totalElapsed = engine.totalElapsedSeconds()
    val pastBanked = (0 until index.coerceAtMost(segments.size)).sumOf { elapsed[it] - segments[it].targetSeconds }
    val segmentDiff = current?.let { elapsed[index] - it.targetSeconds } ?: 0
    val finalDiff = app.finalDiffSeconds(plan)
    // While running: only counts the current segment once it runs over ("where am I vs. the schedule now")
    val scheduleDiff = if (finished) finalDiff else pastBanked + segmentDiff.coerceAtLeast(0)

    // --- Screen-edge flash. The app logic decides when (once per segment and level);
    // the flash runs in its own scope so tapping NEXT can't cut it off half-way.
    val flash = remember { Animatable(0f) }
    var flashStatus by remember { mutableStateOf(TimeStatus.WARNING) }
    val flashScope = rememberCoroutineScope()
    val alert = app.alertEvent
    var lastFlashId by remember { mutableIntStateOf(alert?.id ?: 0) }
    LaunchedEffect(alert) {
        if (alert == null || alert.id == lastFlashId) return@LaunchedEffect
        lastFlashId = alert.id
        flashStatus = alert.status
        flashScope.launch {
            repeat(if (alert.status == TimeStatus.OVER) 3 else 2) {
                flash.animateTo(1f, tween(120))
                flash.animateTo(0f, tween(380))
            }
        }
    }

    val onNext = {
        // A small buzz confirms the tap without having to look at the screen
        haptics.performHapticFeedback(HapticFeedbackType.LongPress)
        app.nextSegment()
    }
    val lectern = settings.lecternMode && !finished
    val toggleLectern = { app.updateSettings { it.copy(lecternMode = !it.lecternMode) } }

    // Built for a split-screen pane: type scales with the pane width, and when the pane is
    // short, the full segment list is swapped for a single "up next" line.
    BoxWithConstraints(Modifier.fillMaxSize()) {
        val scale = (maxWidth / 420.dp).coerceIn(0.6f, 1f)
        val shortPane = maxHeight < 560.dp
        val narrowPane = maxWidth < 340.dp
        val pad = if (maxWidth < 360.dp) 16.dp else 24.dp

        if (lectern && current != null) {
            LecternView(
                app = app,
                planTitle = plan.title,
                index = index,
                count = segments.size,
                segmentTitle = current.title,
                targetSeconds = current.targetSeconds,
                elapsed = elapsed[index],
                totalElapsed = totalElapsed,
                scheduleDiff = scheduleDiff,
                narrowPane = narrowPane,
                onStop = requestStop,
                onExit = toggleLectern,
                onNext = onNext,
            )
        } else {
            Column(Modifier.fillMaxSize()) {
                Column(
                    modifier = Modifier
                        .weight(1f)
                        // Safety net for very short panes: scroll rather than clip
                        .then(if (shortPane) Modifier.verticalScroll(rememberScrollState()) else Modifier),
                ) {
                    // 0. TOP BAR (stop lives up here, away from NEXT)
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(start = pad, end = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(plan.title, color = AppTheme.colors.textSub, fontSize = 14.sp, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
                        if (!finished) {
                            IconButton(onClick = toggleLectern) {
                                Icon(Icons.Default.Fullscreen, contentDescription = s.lecternOn, tint = AppTheme.colors.textSub)
                            }
                        }
                        IconButton(onClick = requestStop) {
                            Icon(Icons.Default.Close, contentDescription = s.stopTimer, tint = AppTheme.colors.textSub)
                        }
                    }

                    // 1. TOTAL (secondary: you glance at this less often than the segment clock)
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(start = pad, end = pad, bottom = 12.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.Bottom,
                    ) {
                        Column {
                            SectionLabel(s.totalPlan(formatTime(plan.totalTargetSeconds)), size = 10.sp)
                            Text(formatTime(totalElapsed), fontSize = (40 * scale).sp, fontWeight = FontWeight.Bold, style = Tabular)
                        }
                        Column(horizontalAlignment = Alignment.End, modifier = Modifier.padding(bottom = 6.dp)) {
                            SectionLabel(s.aheadBehind, Modifier.padding(bottom = 4.dp), size = 10.sp)
                            DiffPill(diffSeconds = scheduleDiff, fontSize = (20 * scale).sp)
                        }
                    }

                    HorizontalDivider()

                    // 2. CURRENT SEGMENT (or summary when finished)
                    if (current != null) {
                        CurrentSegmentPanel(
                            index = index,
                            count = segments.size,
                            segment = current,
                            elapsed = elapsed[index],
                            pastBanked = pastBanked,
                            scale = scale,
                            pad = pad,
                        )
                    } else {
                        FinishedPanel(app = app, finalDiff = finalDiff, perfect = app.isPerfectFinish(), pad = pad)
                    }

                    // 3. WHAT'S NEXT
                    if (shortPane) {
                        UpNextRow(next = segments.getOrNull(index + 1), pad = pad)
                    } else {
                        SegmentList(
                            segments = segments,
                            index = index,
                            elapsed = elapsed,
                            segmentDiff = segmentDiff,
                            pad = pad,
                            modifier = Modifier.weight(1f),
                        )
                    }
                }

                // 4. CONTROLS (always pinned to the bottom)
                TimerControls(
                    app = app,
                    index = index,
                    isLast = index == segments.size - 1,
                    finished = finished,
                    iconOnly = narrowPane,
                    height = if (shortPane) 56.dp else 64.dp,
                    onNext = onNext,
                )
            }
        }

        // Flash overlay around the edge of the pane (drawn on top, doesn't block touches).
        // Alpha is read inside graphicsLayer, so the animation doesn't recompose the screen.
        Box(
            Modifier
                .matchParentSize()
                .graphicsLayer { alpha = flash.value }
                .border(6.dp, if (lectern) flashStatus.lecternColor() else flashStatus.color()),
        )

        // Confetti for a perfect finish (silent, over everything, doesn't block touches)
        if (app.celebrating) {
            ConfettiOverlay(onDone = { app.celebrationDone() }, modifier = Modifier.matchParentSize())
        }
    }

    if (confirmStop) {
        AlertDialog(
            onDismissRequest = { confirmStop = false },
            title = { Text(s.stopDialogTitle) },
            text = { Text(s.stopDialogText) },
            confirmButton = {
                Button(onClick = { confirmStop = false; app.stopTimer() }) { Text(s.stop) }
            },
            dismissButton = {
                TextButton(onClick = { confirmStop = false }) { Text(s.keepGoing, color = AppTheme.colors.textMain) }
            },
        )
    }
}

// ============================================================================
// LECTERN MODE: one huge, high-contrast clock, readable from a distance.
// Always black with bright status colours, whatever the app theme, for maximum contrast.
// ============================================================================
private val LecternBg = Color(0xFF000000)
private val LecternText = Color(0xFFFFFFFF)
private val LecternSub = Color(0xFFB8BDC2)
private val LecternWarning = Color(0xFFFFD54F)
private val LecternOver = Color(0xFFFF6E60)

private fun TimeStatus.lecternColor(): Color = when (this) {
    TimeStatus.ON_TRACK -> LecternText
    TimeStatus.WARNING -> LecternWarning
    TimeStatus.OVER -> LecternOver
}

@Composable
private fun LecternView(
    app: AppController,
    planTitle: String,
    index: Int,
    count: Int,
    segmentTitle: String,
    targetSeconds: Int,
    elapsed: Int,
    totalElapsed: Int,
    scheduleDiff: Int,
    narrowPane: Boolean,
    onStop: () -> Unit,
    onExit: () -> Unit,
    onNext: () -> Unit,
) {
    val s = LocalStrings.current
    val settings = LocalSettings.current
    val remaining = targetSeconds - elapsed
    val status = timeStatus(remaining, targetSeconds, settings.warningPercent)
    val clockText = when (settings.clockMode) {
        ClockMode.REMAINING -> if (remaining < 0) formatDiff(-remaining) else formatTime(remaining)
        ClockMode.ELAPSED -> formatTime(elapsed)
    }

    Column(Modifier.fillMaxSize().background(LecternBg)) {
        Row(Modifier.fillMaxWidth().padding(start = 16.dp, end = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(
                "${index + 1}/$count · $segmentTitle",
                color = LecternSub,
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )
            IconButton(onClick = onExit) {
                Icon(Icons.Default.FullscreenExit, contentDescription = s.lecternOff, tint = LecternSub)
            }
            IconButton(onClick = onStop) {
                Icon(Icons.Default.Close, contentDescription = s.stopTimer, tint = LecternSub)
            }
        }

        // The clock fills whatever space there is, in portrait or landscape
        Box(Modifier.weight(1f).fillMaxWidth().padding(horizontal = 12.dp), contentAlignment = Alignment.Center) {
            FitText(
                text = clockText,
                maxSize = 400.sp,
                minSize = 24.sp,
                fontWeight = FontWeight.Bold,
                color = status.lecternColor(),
                style = Tabular,
            )
        }

        Row(
            Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                "${s.reportTotal} ${formatTime(totalElapsed)}",
                color = LecternSub,
                fontSize = 22.sp,
                fontWeight = FontWeight.Bold,
                style = Tabular,
                maxLines = 1,
            )
            Text(
                formatDiff(scheduleDiff),
                color = if (scheduleDiff > 0) LecternOver else LecternText,
                fontSize = 22.sp,
                fontWeight = FontWeight.Bold,
                style = Tabular,
                maxLines = 1,
            )
        }

        // Keep the normal controls, so pause/undo/next work exactly the same
        Box(Modifier.background(LecternBg)) {
            TimerControls(
                app = app,
                index = index,
                isLast = index == count - 1,
                finished = false,
                iconOnly = narrowPane,
                height = 72.dp,
                onNext = onNext,
            )
        }
    }
}

@Composable
private fun CurrentSegmentPanel(
    index: Int,
    count: Int,
    segment: SpeechSegment,
    elapsed: Int,
    pastBanked: Int,
    scale: Float,
    pad: Dp,
) {
    val s = LocalStrings.current
    val settings = LocalSettings.current
    val remaining = segment.targetSeconds - elapsed
    val status = timeStatus(remaining, segment.targetSeconds, settings.warningPercent)
    val over = status == TimeStatus.OVER

    // Main clock shows time left (default) or time spoken, chosen in Settings
    val (clockLabel, clockText) = when (settings.clockMode) {
        ClockMode.REMAINING ->
            "${if (over) s.segmentOvertime else s.segmentRemaining} · ${s.elapsedShort} ${formatTime(elapsed)}" to
                    (if (over) formatDiff(-remaining) else formatTime(remaining))
        ClockMode.ELAPSED ->
            "${s.segmentElapsed} · " + (if (over) "${s.overShort} ${formatDiff(-remaining)}" else "${s.remainingShort} ${formatTime(remaining)}") to
                    formatTime(elapsed)
    }

    Column(Modifier.fillMaxWidth().background(AppTheme.colors.surface).padding(horizontal = pad, vertical = 16.dp)) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            SectionLabel(s.nowOf(index + 1, count), size = 10.sp)
            SectionLabel("${s.target}  ${formatTime(segment.targetSeconds)}", size = 10.sp)
        }
        Text(
            segment.title,
            fontSize = (24 * scale).sp,
            fontWeight = FontWeight.Bold,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(top = 4.dp, bottom = 8.dp),
        )

        // Main clock: bare, biggest number on screen
        SectionLabel(clockLabel, size = 10.sp)
        Text(
            text = clockText,
            fontSize = (72 * scale).sp,
            lineHeight = (76 * scale).sp,
            fontWeight = FontWeight.Medium,
            color = status.color(),
            style = Tabular,
        )

        if (index > 0 && settings.showAdjusted) {
            Spacer(Modifier.height(12.dp))
            AdjustedCard(
                adjusted = remaining - pastBanked,
                pastBanked = pastBanked,
                targetSeconds = segment.targetSeconds,
                scale = scale,
            )
        }
    }
}

/**
 * The adjusted time gets its own visual language so it can't be confused with the main clock:
 * a tinted, outlined box with its own accent colour (blue), an icon, and the banked time
 * that explains the difference sitting right next to it.
 * The whole box shifts to yellow/red when the adjusted time is nearly or fully used up.
 */
@Composable
private fun AdjustedCard(adjusted: Int, pastBanked: Int, targetSeconds: Int, scale: Float) {
    val s = LocalStrings.current
    val status = timeStatus(adjusted, targetSeconds, LocalSettings.current.warningPercent)
    val accent = when (status) {
        TimeStatus.ON_TRACK -> AppTheme.colors.adjusted
        TimeStatus.WARNING -> AppTheme.colors.warning
        TimeStatus.OVER -> AppTheme.colors.over
    }

    Surface(
        color = accent.copy(alpha = 0.12f),
        contentColor = AppTheme.colors.textMain,
        border = BorderStroke(1.5.dp, accent.copy(alpha = 0.7f)),
        shape = RoundedCornerShape(12.dp),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.Schedule, contentDescription = null, tint = accent, modifier = Modifier.size(14.dp))
                    Spacer(Modifier.width(6.dp))
                    Text(
                        if (status == TimeStatus.OVER) s.adjustedOver else s.adjustedIncBanked,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 0.5.sp,
                        color = accent,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                Text(
                    // Same convention as the main clock: "+" means over
                    text = if (adjusted < 0) formatDiff(-adjusted) else formatTime(adjusted),
                    fontSize = (40 * scale).sp,
                    lineHeight = (44 * scale).sp,
                    fontWeight = FontWeight.Bold,
                    color = accent,
                    style = Tabular,
                )
            }
            Column(horizontalAlignment = Alignment.End) {
                SectionLabel(s.banked, Modifier.padding(bottom = 4.dp), size = 10.sp)
                DiffPill(diffSeconds = pastBanked, fontSize = (16 * scale).sp)
            }
        }
    }
}

@Composable
private fun FinishedPanel(app: AppController, finalDiff: Int, perfect: Boolean, pad: Dp) {
    val s = LocalStrings.current
    val ui = LocalUiActions.current
    val c = AppTheme.colors
    Column(
        modifier = Modifier.fillMaxWidth().background(c.surface).padding(horizontal = pad, vertical = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Icon(
            if (perfect) Icons.Default.EmojiEvents else Icons.Default.Flag,
            contentDescription = null,
            tint = if (perfect) c.warning else c.green,
            modifier = Modifier.size(if (perfect) 40.dp else 32.dp),
        )
        Text(
            if (perfect) s.perfectTiming else s.finished,
            fontSize = 28.sp,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(top = 8.dp),
        )
        Text(
            when {
                finalDiff > 0 -> s.overPlan(formatTime(finalDiff))
                finalDiff < 0 -> s.underPlan(formatTime(-finalDiff))
                else -> s.onTime
            },
            color = c.textSub,
            fontSize = 18.sp,
            textAlign = TextAlign.Center,
        )
        TextButton(
            onClick = {
                app.currentRunSnapshot()?.let { run ->
                    ui.share(buildRunReport(run, s, app.formatDate(run.finishedAtEpochMs)), s.shareReport)
                }
            },
            modifier = Modifier.padding(top = 8.dp),
        ) {
            Icon(Icons.Default.Share, contentDescription = null, tint = c.textMain, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(8.dp))
            Text(s.shareReport, color = c.textMain)
        }
    }
}

/** Compact replacement for the segment list when the pane is short. */
@Composable
private fun UpNextRow(next: SpeechSegment?, pad: Dp) {
    if (next == null) return
    val s = LocalStrings.current
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = pad, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        SectionLabel(s.upNext, size = 10.sp)
        Spacer(Modifier.width(12.dp))
        Text(next.title, color = AppTheme.colors.textSub, fontSize = 16.sp, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
        Text(formatTime(next.targetSeconds), color = AppTheme.colors.textSub, fontSize = 14.sp, fontWeight = FontWeight.Bold, style = Tabular)
    }
}

@Composable
private fun SegmentList(
    segments: List<SpeechSegment>,
    index: Int,
    elapsed: List<Int>,
    segmentDiff: Int,
    pad: Dp,
    modifier: Modifier = Modifier,
) {
    val s = LocalStrings.current
    val warningPercent = LocalSettings.current.warningPercent
    val listState = rememberLazyListState()
    LaunchedEffect(index) {
        if (segments.isNotEmpty()) listState.animateScrollToItem(index.coerceIn(0, segments.size - 1))
    }

    LazyColumn(state = listState, modifier = modifier) {
        item {
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = pad, vertical = 12.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                SectionLabel(s.segments, size = 10.sp)
                SectionLabel(s.target, size = 10.sp)
            }
            HorizontalDivider()
        }

        itemsIndexed(segments, key = { _, seg -> seg.id }) { i, segment ->
            val isCurrent = i == index
            val isPast = i < index
            val contentColor = if (isCurrent) AppTheme.colors.textMain else AppTheme.colors.textSub

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(if (isCurrent) AppTheme.colors.surface.copy(alpha = 0.5f) else Color.Transparent)
                    .padding(horizontal = pad, vertical = 14.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                val icon = when {
                    isPast -> Icons.Default.Check
                    isCurrent -> Icons.Default.PlayArrow
                    else -> Icons.Default.RadioButtonUnchecked
                }
                Icon(icon, contentDescription = null, tint = contentColor, modifier = Modifier.size(20.dp))
                Spacer(Modifier.width(12.dp))
                Text(
                    segment.title,
                    fontSize = 16.sp,
                    color = contentColor,
                    fontWeight = if (isCurrent) FontWeight.Bold else FontWeight.Normal,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )
                if (isPast) {
                    DiffPill(elapsed[i] - segment.targetSeconds, 13.sp, Modifier.padding(horizontal = 8.dp))
                } else if (isCurrent) {
                    DiffPill(
                        diffSeconds = segmentDiff,
                        fontSize = 13.sp,
                        modifier = Modifier.padding(horizontal = 8.dp),
                        isWarning = timeStatus(-segmentDiff, segment.targetSeconds, warningPercent) == TimeStatus.WARNING,
                    )
                }
                Text(formatTime(segment.targetSeconds), fontSize = 14.sp, fontWeight = FontWeight.Bold, color = contentColor, style = Tabular)
            }
            HorizontalDivider()
        }
    }
}

@Composable
private fun TimerControls(
    app: AppController,
    index: Int,
    isLast: Boolean,
    finished: Boolean,
    iconOnly: Boolean,
    height: Dp,
    onNext: () -> Unit,
) {
    val s = LocalStrings.current
    Row(
        modifier = Modifier.fillMaxWidth().padding(12.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        FilledTonalIconButton(
            onClick = { app.previousSegment() },
            enabled = index > 0,
            modifier = Modifier.size(height),
        ) { Icon(Icons.AutoMirrored.Filled.Undo, contentDescription = s.undoNext) }

        if (finished) {
            Button(onClick = { app.stopTimer() }, modifier = Modifier.weight(1f).height(height)) {
                FitText(s.done, maxSize = 18.sp)
            }
            return@Row
        }

        val running = app.engine.isRunning
        Button(
            onClick = { app.togglePause() },
            modifier = Modifier.weight(1f).height(height),
            contentPadding = PaddingValues(horizontal = 8.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = if (running) AppTheme.colors.surface else AppTheme.colors.accent,
                contentColor = if (running) AppTheme.colors.textMain else AppTheme.colors.onAccent,
            ),
            border = if (running) BorderStroke(2.dp, AppTheme.colors.divider) else null,
        ) {
            val label = if (running) s.pause else s.resume
            Icon(
                if (running) Icons.Default.Pause else Icons.Default.PlayArrow,
                contentDescription = if (iconOnly) label else null,
                modifier = Modifier.size(if (iconOnly) 32.dp else 24.dp),
            )
            if (!iconOnly) {
                Spacer(Modifier.width(6.dp))
                FitText(label, maxSize = 16.sp, minSize = 10.sp)
            }
        }
        Button(
            onClick = onNext,
            modifier = Modifier.weight(1f).height(height),
            contentPadding = PaddingValues(horizontal = 8.dp),
        ) {
            val label = if (isLast) s.finish else s.next
            Icon(
                if (isLast) Icons.Default.Flag else Icons.Default.SkipNext,
                contentDescription = if (iconOnly) label else null,
                modifier = Modifier.size(if (iconOnly) 32.dp else 24.dp),
            )
            if (!iconOnly) {
                Spacer(Modifier.width(6.dp))
                FitText(label, maxSize = 16.sp, minSize = 10.sp)
            }
        }
    }
}