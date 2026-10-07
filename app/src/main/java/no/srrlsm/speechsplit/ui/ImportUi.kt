package no.srrlsm.speechsplit.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
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
import no.srrlsm.speechsplit.core.ImportDraft
import no.srrlsm.speechsplit.core.PaceOptions
import no.srrlsm.speechsplit.core.formatTime
import no.srrlsm.speechsplit.core.formatTimeForInput
import no.srrlsm.speechsplit.core.parseTimeInput
import no.srrlsm.speechsplit.core.roundTo5
import no.srrlsm.speechsplit.core.suggestSegments
import no.srrlsm.speechsplit.ui.theme.AppTheme
import kotlin.math.roundToInt

private val PartOptions = listOf(0, 2, 3, 4, 5, 6, 8) // 0 = automatic

/**
 * Shows the segments suggested for an imported document. The user can pick how the times are
 * worked out (speaking pace or a fixed total) and, without headings, how many parts.
 * "Continue" opens the normal editor; nothing is saved before that.
 */
@Composable
fun ImportSuggestDialog(app: AppController, draft: ImportDraft) {
    val s = LocalStrings.current
    val c = AppTheme.colors
    var title by remember(draft) { mutableStateOf(draft.title) }
    var pace by remember(draft) { mutableIntStateOf(app.settings.wordsPerMinute) }
    var useTotal by remember(draft) { mutableStateOf(false) }
    // Start the total at what the pace gives, so switching over doesn't jump
    val paceSeconds = roundTo5((draft.totalWords * 60.0 / pace).roundToInt()).coerceAtLeast(60)
    var totalText by remember(draft) { mutableStateOf(formatTimeForInput(paceSeconds - paceSeconds % 60)) }
    var parts by remember(draft) { mutableIntStateOf(0) }
    val totalSeconds = if (useTotal) parseTimeInput(totalText).takeIf { it > 0 } else null

    val suggestion = remember(draft, pace, totalSeconds, parts, s) {
        suggestSegments(
            draft = draft,
            wordsPerMinute = pace,
            totalSeconds = totalSeconds,
            parts = parts.takeIf { it > 0 },
            introTitle = s.defaultFirstSegment,
            partTitle = s.partTitle,
        )
    }

    AlertDialog(
        onDismissRequest = { app.dismissImportDraft() },
        icon = { Icon(Icons.Default.AutoAwesome, contentDescription = null) },
        title = { Text(s.suggestTitle) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                OutlinedTextField(
                    value = title,
                    onValueChange = { title = it },
                    label = { Text(s.speechTitleLabel) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(Modifier.height(10.dp))
                Text(
                    (if (suggestion.fromHeadings) s.suggestFromHeadings else s.suggestEvenly) + " " + s.suggestEditNote,
                    fontSize = 13.sp,
                    color = c.textSub,
                )

                Spacer(Modifier.height(14.dp))
                SectionLabel(s.timesFrom.uppercase())
                Spacer(Modifier.height(6.dp))
                ChoiceChips(
                    options = listOf(false, true),
                    selected = useTotal,
                    label = { if (it) s.timesFromTotal else s.timesFromPace },
                    onSelect = { useTotal = it },
                )
                Spacer(Modifier.height(8.dp))
                if (useTotal) {
                    OutlinedTextField(
                        value = totalText,
                        onValueChange = { totalText = it.take(6) },
                        label = { Text(s.timeLabel) },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        supportingText = { Text("= ${formatTime(parseTimeInput(totalText))}") },
                        modifier = Modifier.fillMaxWidth(),
                    )
                } else {
                    ChoiceChips(
                        options = (PaceOptions + pace).distinct().sorted(),
                        selected = pace,
                        label = { s.paceLabel(it) },
                        onSelect = { pace = it },
                    )
                    Text(s.paceNote, fontSize = 12.sp, color = c.textSub, modifier = Modifier.padding(top = 6.dp))
                }

                if (!suggestion.fromHeadings) {
                    Spacer(Modifier.height(14.dp))
                    SectionLabel(s.partsTitle.uppercase())
                    Spacer(Modifier.height(6.dp))
                    ChoiceChips(
                        options = PartOptions,
                        selected = parts,
                        label = { if (it == 0) s.partsAuto else "$it" },
                        onSelect = { parts = it },
                    )
                }

                Spacer(Modifier.height(16.dp))
                HorizontalDivider()
                suggestion.segments.forEachIndexed { i, seg ->
                    Row(Modifier.fillMaxWidth().padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text("${i + 1}.", color = c.textSub, fontSize = 14.sp, modifier = Modifier.width(28.dp))
                        Column(Modifier.weight(1f)) {
                            Text(seg.title, fontWeight = FontWeight.Medium, maxLines = 2, overflow = TextOverflow.Ellipsis)
                            Text(s.wordsCount(seg.words), fontSize = 12.sp, color = c.textSub)
                        }
                        Text(formatTime(seg.seconds), fontWeight = FontWeight.Bold, style = Tabular, modifier = Modifier.padding(start = 8.dp))
                    }
                    HorizontalDivider()
                }
                Row(Modifier.fillMaxWidth().padding(top = 10.dp)) {
                    Text(s.reportTotal, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                    Text(s.wordsCount(suggestion.segments.sumOf { it.words }), color = c.textSub, fontSize = 13.sp)
                    Spacer(Modifier.width(10.dp))
                    Text(formatTime(suggestion.totalSeconds), fontWeight = FontWeight.Bold, style = Tabular)
                }
            }
        },
        confirmButton = {
            Button(
                enabled = suggestion.segments.isNotEmpty(),
                onClick = { app.acceptImport(title, suggestion.segments, pace) },
            ) { Text(s.continueLabel) }
        },
        dismissButton = {
            TextButton(onClick = { app.dismissImportDraft() }) { Text(s.cancel, color = c.textMain) }
        },
    )
}
