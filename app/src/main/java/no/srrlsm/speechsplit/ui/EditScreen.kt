package no.srrlsm.speechsplit.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.isAltPressed
import androidx.compose.ui.input.key.isCtrlPressed
import androidx.compose.ui.input.key.isMetaPressed
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import no.srrlsm.speechsplit.core.AppController
import no.srrlsm.speechsplit.core.SpeechSegment
import no.srrlsm.speechsplit.core.formatTime
import no.srrlsm.speechsplit.core.formatTimeForInput
import no.srrlsm.speechsplit.core.parseTimeInput
import no.srrlsm.speechsplit.core.summaryOf
import no.srrlsm.speechsplit.ui.theme.AppTheme

/** Which field should get the keyboard next. A new [n] makes the same request count again. */
private data class FocusRequest(val segmentId: String, val timeField: Boolean, val n: Int)

// ============================================================================
// EDIT
// With a keyboard: Enter goes title → time → next segment (and adds one after the last),
// Ctrl+S saves, Ctrl+Enter adds a segment, Alt+↑/↓ moves the segment you're in, Esc goes back.
// ============================================================================
@Composable
fun EditScreen(app: AppController) {
    val s = LocalStrings.current
    val plan = app.activePlan ?: return
    var confirmDiscard by remember { mutableStateOf(false) }
    // Back used to throw away edits silently. Now it asks first (only if something changed).
    val goBack: () -> Unit = { if (app.hasUnsavedEdits) confirmDiscard = true else app.cancelEditing() }
    AppBackHandler { goBack() }

    val listState = rememberLazyListState()
    val focusManager = LocalFocusManager.current
    var focusRequest by remember { mutableStateOf<FocusRequest?>(null) }
    var focusedSegmentId by remember { mutableStateOf<String?>(null) }
    val keyboardFirst = app.hasKeyboardShortcuts

    fun focus(id: String, timeField: Boolean) {
        focusRequest = FocusRequest(id, timeField, (focusRequest?.n ?: 0) + 1)
    }

    fun addAfter(index: Int?) {
        focus(app.addSegment(index), timeField = false)
    }

    // Bring a segment into view before it gets the keyboard
    LaunchedEffect(focusRequest) {
        val req = focusRequest ?: return@LaunchedEffect
        val index = app.activePlan?.segments?.indexOfFirst { it.id == req.segmentId } ?: -1
        if (index >= 0 && listState.layoutInfo.visibleItemsInfo.none { it.index == index }) {
            listState.animateScrollToItem(index)
        }
    }

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

    BoxWithConstraints(Modifier.fillMaxSize()) {
        // Enough room: each segment on one line (title, time, buttons) instead of a two-line card
        val compact = maxWidth >= 560.dp
        Column(
            Modifier
                .fillMaxSize()
                .padding(horizontal = if (compact) 32.dp else 24.dp, vertical = 16.dp)
                .onPreviewKeyEvent { e ->
                    if (e.type != KeyEventType.KeyDown || confirmDiscard) return@onPreviewKeyEvent false
                    val cmd = e.isCtrlPressed || e.isMetaPressed
                    val current = plan.segments.indexOfFirst { it.id == focusedSegmentId }
                    when {
                        cmd && e.key == Key.S -> {
                            if (plan.segments.isNotEmpty()) app.saveActivePlan()
                            true
                        }
                        cmd && (e.key == Key.Enter || e.key == Key.NumPadEnter) -> {
                            addAfter(current.takeIf { it >= 0 })
                            true
                        }
                        e.isAltPressed && e.key == Key.DirectionUp && current > 0 -> {
                            app.moveSegment(current, current - 1)
                            true
                        }
                        e.isAltPressed && e.key == Key.DirectionDown && current >= 0 && current < plan.segments.lastIndex -> {
                            app.moveSegment(current, current + 1)
                            true
                        }
                        e.key == Key.Escape && keyboardFirst -> {
                            goBack()
                            true
                        }
                        else -> false
                    }
                }
        ) {
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
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next),
                keyboardActions = KeyboardActions(onNext = {
                    plan.segments.firstOrNull()?.let { focus(it.id, timeField = false) }
                }),
                modifier = Modifier.fillMaxWidth().padding(top = 16.dp),
            )
            Text(s.summaryOf(plan), color = AppTheme.colors.textSub, fontSize = 14.sp, modifier = Modifier.padding(vertical = 12.dp))

            LazyColumn(Modifier.weight(1f), state = listState) {
                // Stable keys: text fields keep focus/state when segments are reordered or deleted
                itemsIndexed(plan.segments, key = { _, seg -> seg.id }) { index, segment ->
                    val isLast = index == plan.segments.lastIndex
                    SegmentEditor(
                        index = index,
                        count = plan.segments.size,
                        segment = segment,
                        compact = compact,
                        focusRequest = focusRequest,
                        // After the last time field: a keyboard adds a new segment, a phone just closes the keyboard
                        lastAction = if (isLast && !keyboardFirst) ImeAction.Done else ImeAction.Next,
                        onTitleChange = { t -> app.updateSegment(segment.id) { it.copy(title = t) } },
                        onTargetChange = { secs -> app.updateSegment(segment.id) { it.copy(targetSeconds = secs) } },
                        onTimeDone = {
                            val next = plan.segments.getOrNull(index + 1)
                            when {
                                next != null -> focus(next.id, timeField = false)
                                keyboardFirst -> addAfter(index)
                                else -> focusManager.clearFocus()
                            }
                        },
                        onFocused = { focusedSegmentId = segment.id },
                        onMove = { to -> app.moveSegment(index, to) },
                        onDelete = {
                            if (focusedSegmentId == segment.id) focusedSegmentId = null
                            app.removeSegment(segment.id)
                        },
                    )
                }
                item {
                    TextButton(onClick = { addAfter(null) }, modifier = Modifier.padding(vertical = 8.dp)) {
                        Icon(Icons.Default.Add, contentDescription = null)
                        Text(" ${s.addSegment}")
                    }
                }
            }

            if (keyboardFirst) {
                Text(
                    s.editKeysHint,
                    color = AppTheme.colors.textSub,
                    fontSize = 12.sp,
                    modifier = Modifier.padding(bottom = 8.dp),
                )
            }
            Button(
                onClick = { app.saveActivePlan() },
                enabled = plan.segments.isNotEmpty(),
                modifier = Modifier.fillMaxWidth().height(56.dp),
            ) { FitText(s.saveAndReady, maxSize = 18.sp) }
        }
    }
}

@Composable
private fun SegmentEditor(
    index: Int,
    count: Int,
    segment: SpeechSegment,
    compact: Boolean,
    focusRequest: FocusRequest?,
    lastAction: ImeAction,
    onTitleChange: (String) -> Unit,
    onTargetChange: (Int) -> Unit,
    onTimeDone: () -> Unit,
    onFocused: () -> Unit,
    onMove: (Int) -> Unit,
    onDelete: () -> Unit,
) {
    val s = LocalStrings.current
    val c = AppTheme.colors
    val titleFocus = remember { FocusRequester() }
    val timeFocus = remember { FocusRequester() }
    // Field contents with selection, so tabbing into a field selects it (type to replace)
    var title by remember { mutableStateOf(TextFieldValue(segment.title)) }
    if (title.text != segment.title) title = TextFieldValue(segment.title, TextRange(segment.title.length))
    var time by remember { mutableStateOf(TextFieldValue(formatTimeForInput(segment.targetSeconds))) }

    LaunchedEffect(focusRequest) {
        val req = focusRequest ?: return@LaunchedEffect
        if (req.segmentId != segment.id) return@LaunchedEffect
        withFrameNanos { } // wait until the field is laid out
        runCatching { (if (req.timeField) timeFocus else titleFocus).requestFocus() }
    }

    val titleField = @Composable { modifier: Modifier ->
        OutlinedTextField(
            value = title,
            onValueChange = {
                title = it
                if (it.text != segment.title) onTitleChange(it.text)
            },
            label = { Text(s.segmentLabel(index + 1)) },
            singleLine = true,
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next),
            keyboardActions = KeyboardActions(onNext = { runCatching { timeFocus.requestFocus() } }),
            modifier = modifier
                .focusRequester(titleFocus)
                .onFocusChanged {
                    if (it.isFocused) {
                        onFocused()
                        title = title.copy(selection = TextRange(0, title.text.length))
                    }
                },
        )
    }
    val timeField = @Composable { modifier: Modifier ->
        OutlinedTextField(
            value = time,
            onValueChange = {
                time = it
                onTargetChange(parseTimeInput(it.text))
            },
            label = { Text(s.timeLabel, maxLines = 1) },
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal, imeAction = lastAction),
            keyboardActions = KeyboardActions(onNext = { onTimeDone() }, onDone = { onTimeDone() }),
            modifier = modifier
                .focusRequester(timeFocus)
                .onFocusChanged {
                    if (it.isFocused) {
                        onFocused()
                        time = time.copy(selection = TextRange(0, time.text.length))
                    }
                },
        )
    }
    // Shows how the input was understood, so "4.5" vs "4.05" is never a surprise
    val understood = @Composable {
        Text(
            "= ${formatTime(segment.targetSeconds)}",
            color = c.textSub,
            style = Tabular,
            maxLines = 1,
            modifier = Modifier.padding(horizontal = 8.dp),
        )
    }
    val moveButtons = @Composable {
        IconButton(onClick = { onMove(index - 1) }, enabled = index > 0) {
            Icon(Icons.Default.KeyboardArrowUp, contentDescription = s.moveUp)
        }
        IconButton(onClick = { onMove(index + 1) }, enabled = index < count - 1) {
            Icon(Icons.Default.KeyboardArrowDown, contentDescription = s.moveDown)
        }
    }
    val deleteButton = @Composable {
        IconButton(onClick = onDelete) {
            Icon(Icons.Default.Delete, contentDescription = s.deleteSegment, tint = c.textSub)
        }
    }

    Card(Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
        if (compact) {
            Row(Modifier.padding(start = 12.dp, end = 4.dp, top = 6.dp, bottom = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                titleField(Modifier.weight(1f))
                Spacer(Modifier.width(8.dp))
                timeField(Modifier.width(150.dp))
                understood()
                moveButtons()
                deleteButton()
            }
        } else {
            Column(Modifier.padding(12.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    titleField(Modifier.weight(1f))
                    deleteButton()
                }
                Spacer(Modifier.height(8.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    timeField(Modifier.weight(1f).widthIn(max = 170.dp))
                    understood()
                    moveButtons()
                }
            }
        }
    }
}
