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
import no.srrlsm.speechsplit.core.summaryOf
import no.srrlsm.speechsplit.ui.theme.AppTheme

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ReadyScreen(app: AppController) {
    val s = LocalStrings.current
    val c = AppTheme.colors
    val plan = app.activePlan ?: return
    val runCount = app.runsFor(plan.id).size
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

                Spacer(Modifier.height(if (shortPane) 24.dp else 56.dp))

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
                    TextButton(onClick = { app.backToLibrary() }) { Text(s.library, color = c.textSub) }
                }
            }
        }
    }
}
