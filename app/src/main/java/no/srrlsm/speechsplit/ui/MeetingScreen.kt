package no.srrlsm.speechsplit.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ChevronLeft
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import no.srrlsm.speechsplit.core.AppController
import no.srrlsm.speechsplit.core.MIDWEEK_MEETING_ID
import no.srrlsm.speechsplit.core.MeetingKind
import no.srrlsm.speechsplit.core.MeetingLoad
import no.srrlsm.speechsplit.core.SpeechPlan
import no.srrlsm.speechsplit.core.SpeechSegment
import no.srrlsm.speechsplit.core.WEEKEND_MEETING_ID
import no.srrlsm.speechsplit.core.expandSubparts
import no.srrlsm.speechsplit.core.formatTime
import no.srrlsm.speechsplit.core.formatTimeForInput
import no.srrlsm.speechsplit.core.parseTimeInput
import no.srrlsm.speechsplit.core.weekLabel
import no.srrlsm.speechsplit.ui.theme.AppTheme

// ============================================================================
// MEETING MODE: pick the meeting (and the week), see the program, start.
// The timer is the same as for a speech, with the whole meeting as its parts.
// Tap a part to split it into sub-parts (or bring in a saved speech): the chairman
// can then follow his own part in detail while the meeting clock keeps going.
// ============================================================================
private val ProgramLanguages = listOf("no" to "Norsk", "en" to "English", "de" to "Deutsch")

@Composable
fun MeetingScreen(app: AppController) {
    val s = LocalStrings.current
    val s3 = LocalStrings3.current
    val c = AppTheme.colors
    val kind = app.settings.meetingKind
    val lang = app.meetingLanguage
    val load = app.meetingLoad[lang] ?: MeetingLoad.IDLE
    val haveSaved = app.meetingProgram(lang) != null
    val plan = app.chosenMeeting()
    val runs = app.runsFor(if (kind == MeetingKind.WEEKEND) WEEKEND_MEETING_ID else MIDWEEK_MEETING_ID).size
    // The part that's folded out (one at a time), and the part "From a speech" is picking for
    var expandedId by remember { mutableStateOf<String?>(null) }
    var pickFor by remember { mutableStateOf<SpeechSegment?>(null) }
    AppBackHandler { app.backToLibrary() }

    Column(Modifier.fillMaxSize().padding(horizontal = 16.dp, vertical = 8.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = { app.backToLibrary() }) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = s.back)
            }
            Text(s3.meetingMode, fontSize = 24.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
            // The language of the program, up here where it's easy to find
            ProgramLanguageButton(selected = lang, onSelect = { app.setMeetingLanguage(it) })
        }
        Column(
            Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = 4.dp),
        ) {
            Text(s3.meetingIntro, fontSize = 13.sp, color = c.textSub, modifier = Modifier.padding(bottom = 14.dp))
            SegmentedChoice(
                options = listOf(MeetingKind.MIDWEEK to s3.meetingMidweek, MeetingKind.WEEKEND to s3.meetingWeekend),
                selected = kind,
                onSelect = { app.setMeetingKind(it) },
                modifier = Modifier.fillMaxWidth(),
            )

            if (kind == MeetingKind.MIDWEEK) {
                // Week picker: this week by default, the arrows go back and forth
                val start = app.meetingWeekStart
                val week = plan?.meeting
                Card(Modifier.fillMaxWidth().padding(top = 14.dp)) {
                    Row(Modifier.padding(horizontal = 4.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                        IconButton(onClick = { app.changeMeetingWeek(-1) }) {
                            Icon(Icons.Default.ChevronLeft, contentDescription = s3.meetingPrevWeek)
                        }
                        Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(weekLabel(start, app.languageCode), fontWeight = FontWeight.Bold, fontSize = 17.sp, textAlign = TextAlign.Center)
                            val sub = listOfNotNull(
                                week?.reading?.takeIf { it.isNotBlank() },
                                s3.meetingThisWeek.takeIf { app.meetingWeekOffset == 0 },
                            ).joinToString(" · ")
                            if (sub.isNotBlank()) Text(sub, fontSize = 13.sp, color = c.textSub, textAlign = TextAlign.Center)
                            if (app.meetingWeekOffset != 0) {
                                TextButton(onClick = { app.changeMeetingWeek(0) }, contentPadding = PaddingValues(horizontal = 8.dp, vertical = 0.dp)) {
                                    Text(s3.meetingThisWeek, color = c.adjusted, fontSize = 13.sp)
                                }
                            }
                        }
                        IconButton(onClick = { app.changeMeetingWeek(1) }) {
                            Icon(Icons.Default.ChevronRight, contentDescription = s3.meetingNextWeek)
                        }
                    }
                }
                when {
                    load == MeetingLoad.LOADING && !haveSaved -> Row(Modifier.padding(top = 16.dp), verticalAlignment = Alignment.CenterVertically) {
                        CircularProgressIndicator(strokeWidth = 2.dp, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(10.dp))
                        Text(s3.meetingLoading, color = c.textSub)
                    }
                    load == MeetingLoad.MISSING && !haveSaved -> Text(s3.meetingNoLanguage, color = c.textSub, modifier = Modifier.padding(top = 16.dp))
                    load == MeetingLoad.FAILED && !haveSaved -> Column(Modifier.padding(top = 16.dp)) {
                        Text(s3.meetingNoData, color = c.textSub)
                        OutlinedButton(onClick = { app.loadMeetings(lang, force = true) }, modifier = Modifier.padding(top = 8.dp)) {
                            Text(s3.meetingRetry, color = c.textMain)
                        }
                    }
                    plan == null && load != MeetingLoad.LOADING -> {
                        // A gap between weeks that are there: no meeting that week (the Memorial, a convention)
                        val weeks = app.meetingProgram(lang)?.weeks.orEmpty()
                        val gap = weeks.any { it.start < start } && weeks.any { it.start > start }
                        Text(if (gap) s3.meetingNoneThisWeek else s3.meetingNoWeek, color = c.textSub, modifier = Modifier.padding(top = 16.dp))
                    }
                }
                if (load == MeetingLoad.FAILED && haveSaved) {
                    Text(s3.meetingOffline, fontSize = 13.sp, color = c.textSub, modifier = Modifier.padding(top = 8.dp))
                }
            } else {
                Text(s3.meetingWeekendNote, fontSize = 13.sp, color = c.textSub, modifier = Modifier.padding(top = 12.dp, start = 2.dp))
            }

            if (plan != null) {
                Text(s3.subpartsHint, fontSize = 13.sp, color = c.textSub, modifier = Modifier.padding(top = 14.dp, start = 2.dp))
                Card(Modifier.fillMaxWidth().padding(top = 8.dp)) {
                    Column(Modifier.padding(vertical = 4.dp)) {
                        plan.segments.forEachIndexed { i, seg ->
                            if (i > 0) HorizontalDivider(Modifier.padding(horizontal = 14.dp))
                            val subparts = app.subpartsOf(plan, seg)
                            val open = expandedId == seg.id
                            Row(
                                Modifier
                                    .fillMaxWidth()
                                    .clickable { expandedId = if (open) null else seg.id }
                                    .padding(start = 14.dp, end = 8.dp, top = 9.dp, bottom = 9.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Column(Modifier.weight(1f)) {
                                    Text(seg.title, fontSize = 15.sp)
                                    if (subparts.isNotEmpty()) {
                                        Text(s3.subpartsCount(subparts.size), fontSize = 12.sp, color = c.adjusted, fontWeight = FontWeight.SemiBold)
                                    }
                                }
                                Spacer(Modifier.width(12.dp))
                                Text(formatTime(seg.targetSeconds), fontWeight = FontWeight.Bold, style = Tabular)
                                Icon(
                                    if (open) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                                    contentDescription = null,
                                    tint = c.textSub,
                                    modifier = Modifier.padding(start = 4.dp).size(20.dp),
                                )
                            }
                            if (open) {
                                SubpartsEditor(app, plan, seg, subparts, onPickSpeech = { pickFor = seg })
                            }
                        }
                        HorizontalDivider(Modifier.padding(horizontal = 14.dp))
                        // The totals are those of the meeting as it will run (sub-parts longer than their part make it longer)
                        val running = expandSubparts(plan) { app.subpartsOf(plan, it) }
                        val buffer = running.spareSeconds
                        Row(Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 9.dp), verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                if (buffer > 0) s3.meetingTotal(formatTime(running.totalTargetSeconds), formatTime(buffer)) else s.reportTotal,
                                fontSize = 13.sp, color = c.textSub, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f),
                            )
                            Text(formatTime(running.totalPlannedSeconds), fontWeight = FontWeight.Bold, style = Tabular)
                        }
                    }
                }
            }
            Spacer(Modifier.height(16.dp))
        }

        Button(
            onClick = { app.startMeeting() },
            enabled = plan != null,
            modifier = Modifier.fillMaxWidth().padding(top = 8.dp).height(56.dp),
        ) {
            Icon(Icons.Default.PlayArrow, contentDescription = null)
            Spacer(Modifier.width(8.dp))
            FitText(s3.meetingStart, maxSize = 18.sp, fontWeight = FontWeight.Medium)
        }
        TextButton(onClick = { app.openMeetingHistory() }, modifier = Modifier.align(Alignment.CenterHorizontally)) {
            Text(if (runs > 0) "${s.history} ($runs)" else s.history, color = c.textSub)
        }
    }

    // "From a speech": the parts of a saved speech become the sub-parts
    val part = pickFor
    if (part != null && plan != null) {
        val speeches = app.visiblePlans
        AlertDialog(
            onDismissRequest = { pickFor = null },
            title = { ClosableTitle(s3.subpartFromSpeech) { pickFor = null } },
            text = {
                Column(Modifier.heightIn(max = 440.dp).verticalScroll(rememberScrollState())) {
                    Text(if (speeches.isEmpty()) s3.subpartNoSpeeches else s3.subpartPick, fontSize = 14.sp, color = c.textSub)
                    Spacer(Modifier.height(10.dp))
                    speeches.forEach { speech ->
                        Surface(
                            onClick = {
                                pickFor = null
                                app.subpartsFromSpeech(plan, part, speech)
                            },
                            color = c.surfaceHigh,
                            shape = MaterialTheme.shapes.medium,
                            modifier = Modifier.fillMaxWidth().padding(vertical = 3.dp),
                        ) {
                            Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                                Text(speech.title, fontWeight = FontWeight.Bold, maxLines = 2, modifier = Modifier.weight(1f))
                                Text(formatTime(speech.totalTargetSeconds), color = c.textSub, style = Tabular)
                            }
                        }
                    }
                }
            },
            confirmButton = {},
            dismissButton = { TextButton(onClick = { pickFor = null }) { Text(s.cancel, color = c.textMain) } },
        )
    }
}

/** The program's language as a small button with a menu, in the corner of the meeting screen. */
@Composable
private fun ProgramLanguageButton(selected: String, onSelect: (String) -> Unit) {
    val s3 = LocalStrings3.current
    val c = AppTheme.colors
    var open by remember { mutableStateOf(false) }
    Box {
        TextButton(onClick = { open = true }) {
            Icon(Icons.Default.Language, contentDescription = s3.meetingLanguage, tint = c.textMain, modifier = Modifier.size(20.dp))
            Spacer(Modifier.width(6.dp))
            Text(selected.uppercase(), color = c.textMain, fontWeight = FontWeight.Bold)
        }
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            Text(
                s3.meetingLanguage,
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold,
                color = c.textSub,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp),
            )
            ProgramLanguages.forEach { (code, name) ->
                DropdownMenuItem(
                    text = { Text(name) },
                    leadingIcon = { RadioButton(selected = code == selected, onClick = null) },
                    onClick = { open = false; onSelect(code) },
                )
            }
        }
    }
}

/** Under a folded-out part: its sub-parts (name and time), and the three things you can do with them. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun SubpartsEditor(
    app: AppController,
    plan: SpeechPlan,
    part: SpeechSegment,
    subparts: List<SpeechSegment>,
    onPickSpeech: () -> Unit,
) {
    val s3 = LocalStrings3.current
    val c = AppTheme.colors
    Column(
        Modifier
            .fillMaxWidth()
            .background(c.surfaceHigh.copy(alpha = 0.45f))
            .padding(start = 14.dp, end = 6.dp, top = 6.dp, bottom = 4.dp),
    ) {
        subparts.forEach { sub ->
            // Keyed by id, so each row keeps its own text while others are added or removed
            key(sub.id) {
                SubpartRow(
                    subpart = sub,
                    onChange = { app.updateSubpart(plan, part, it) },
                    onRemove = { app.removeSubpart(plan, part, sub.id) },
                )
            }
        }
        if (subparts.isNotEmpty()) {
            val sum = subparts.sumOf { it.targetSeconds }
            val left = part.targetSeconds - sum
            val note = when {
                left > 0 -> " · " + s3.subpartsLeft(formatTime(left))
                left < 0 -> " · " + s3.subpartsOver(formatTime(-left))
                else -> ""
            }
            Text(
                s3.subpartsSum(formatTime(sum), formatTime(part.targetSeconds)) + note,
                fontSize = 12.sp,
                color = if (left < 0) c.over else c.textSub,
                style = Tabular,
                modifier = Modifier.padding(top = 6.dp, end = 8.dp),
            )
        }
        FlowRow {
            TextButton(onClick = { app.addSubpart(plan, part) }) {
                Icon(Icons.Default.Add, contentDescription = null, tint = c.textMain, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(6.dp))
                Text(s3.subpartAdd, color = c.textMain)
            }
            TextButton(onClick = onPickSpeech) { Text(s3.subpartFromSpeech, color = c.textMain) }
            if (subparts.isNotEmpty()) {
                TextButton(onClick = { app.setSubparts(plan, part, emptyList()) }) { Text(s3.subpartClear, color = c.textSub) }
            }
        }
    }
}

@Composable
private fun SubpartRow(subpart: SpeechSegment, onChange: (SpeechSegment) -> Unit, onRemove: () -> Unit) {
    val s3 = LocalStrings3.current
    var title by remember { mutableStateOf(subpart.title) }
    var time by remember { mutableStateOf(formatTimeForInput(subpart.targetSeconds)) }
    Row(Modifier.fillMaxWidth().padding(vertical = 3.dp), verticalAlignment = Alignment.CenterVertically) {
        OutlinedTextField(
            value = title,
            onValueChange = {
                title = it
                onChange(subpart.copy(title = it))
            },
            label = { Text(s3.subpartName, maxLines = 1) },
            singleLine = true,
            modifier = Modifier.weight(1f),
        )
        Spacer(Modifier.width(8.dp))
        OutlinedTextField(
            value = time,
            onValueChange = {
                time = it
                onChange(subpart.copy(targetSeconds = parseTimeInput(it)))
            },
            label = { Text(s3.subpartTime, maxLines = 1) },
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
            modifier = Modifier.width(92.dp),
        )
        IconButton(onClick = onRemove) {
            Icon(Icons.Default.Close, contentDescription = s3.subpartRemove, tint = AppTheme.colors.textSub)
        }
    }
}
