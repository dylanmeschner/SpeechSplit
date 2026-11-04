package no.srrlsm.speechsplit.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.ChevronLeft
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import no.srrlsm.speechsplit.core.AppController
import no.srrlsm.speechsplit.core.MIDWEEK_MEETING_ID
import no.srrlsm.speechsplit.core.MeetingKind
import no.srrlsm.speechsplit.core.MeetingLoad
import no.srrlsm.speechsplit.core.WEEKEND_MEETING_ID
import no.srrlsm.speechsplit.core.formatTime
import no.srrlsm.speechsplit.core.weekLabel
import no.srrlsm.speechsplit.ui.theme.AppTheme

// ============================================================================
// MEETING MODE: pick the meeting (and the week), see the program, start.
// The timer is the same as for a speech, with the whole meeting as its parts.
// ============================================================================
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
    AppBackHandler { app.backToLibrary() }

    Column(Modifier.fillMaxSize().padding(horizontal = 16.dp, vertical = 8.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = { app.backToLibrary() }) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = s.back)
            }
            Text(s3.meetingMode, fontSize = 24.sp, fontWeight = FontWeight.Bold)
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
                    plan == null && load != MeetingLoad.LOADING -> Text(s3.meetingNoWeek, color = c.textSub, modifier = Modifier.padding(top = 16.dp))
                }
                if (load == MeetingLoad.FAILED && haveSaved) {
                    Text(s3.meetingOffline, fontSize = 13.sp, color = c.textSub, modifier = Modifier.padding(top = 8.dp))
                }
            } else {
                Text(s3.meetingWeekendNote, fontSize = 13.sp, color = c.textSub, modifier = Modifier.padding(top = 12.dp, start = 2.dp))
            }

            if (plan != null) {
                Card(Modifier.fillMaxWidth().padding(top = 12.dp)) {
                    Column(Modifier.padding(horizontal = 14.dp, vertical = 4.dp)) {
                        plan.segments.forEachIndexed { i, seg ->
                            if (i > 0) HorizontalDivider()
                            Row(Modifier.fillMaxWidth().padding(vertical = 9.dp), verticalAlignment = Alignment.CenterVertically) {
                                Text(seg.title, fontSize = 15.sp, modifier = Modifier.weight(1f))
                                Spacer(Modifier.width(12.dp))
                                Text(formatTime(seg.targetSeconds), fontWeight = FontWeight.Bold, style = Tabular)
                            }
                        }
                        HorizontalDivider()
                        val buffer = plan.meeting?.bufferSeconds ?: 0
                        Row(Modifier.fillMaxWidth().padding(vertical = 9.dp), verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                if (buffer > 0) s3.meetingTotal(formatTime(plan.totalTargetSeconds), formatTime(buffer)) else s.reportTotal,
                                fontSize = 13.sp, color = c.textSub, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f),
                            )
                            Text(formatTime(plan.totalPlannedSeconds), fontWeight = FontWeight.Bold, style = Tabular)
                        }
                    }
                }
            }

            if (kind == MeetingKind.MIDWEEK) {
                SectionLabel(s3.meetingLanguage.uppercase(), Modifier.padding(top = 24.dp, bottom = 8.dp, start = 4.dp))
                SegmentedChoice(
                    options = listOf("no" to "Norsk", "en" to "English", "de" to "Deutsch"),
                    selected = lang,
                    onSelect = { app.setMeetingLanguage(it) },
                    modifier = Modifier.fillMaxWidth(),
                )
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
}
