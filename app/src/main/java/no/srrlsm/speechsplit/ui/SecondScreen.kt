package no.srrlsm.speechsplit.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import no.srrlsm.speechsplit.core.AppController
import no.srrlsm.speechsplit.core.ClockMode
import no.srrlsm.speechsplit.core.formatDiff
import no.srrlsm.speechsplit.core.formatTime
import no.srrlsm.speechsplit.core.timeStatus

// ============================================================================
// SECOND SCREEN (Windows/desktop)
// What the extra screen shows while the timer runs: the name of the part and one huge
// clock for it, black with bright status colours like the big-clock (lectern) mode.
// Nothing to tap: the timer is controlled from the main window as usual.
// ============================================================================
@Composable
fun SecondScreenClock(app: AppController) {
    val plan = app.activePlan
    val engine = app.engine
    val settings = app.settings
    val index = engine.currentIndex
    val segment = plan?.segments?.getOrNull(index)

    Column(
        Modifier.fillMaxSize().background(LecternBg).padding(horizontal = 32.dp, vertical = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        if (segment == null) {
            // Finished (or nothing running): just say so, big and calm
            Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                FitText(app.strings.finished, maxSize = 160.sp, minSize = 24.sp, color = LecternSub)
            }
            return@Column
        }
        val elapsed = engine.elapsedSeconds(index)
        val remaining = segment.targetSeconds - elapsed
        val status = timeStatus(remaining, segment.targetSeconds, settings.warningPercent)
        val clockText = when (settings.clockMode) {
            ClockMode.REMAINING -> if (remaining < 0) formatDiff(-remaining) else formatTime(remaining)
            ClockMode.ELAPSED -> formatTime(elapsed)
        }
        FitText(
            text = segment.fullTitle,
            maxSize = 72.sp,
            minSize = 18.sp,
            fontWeight = FontWeight.Bold,
            color = LecternSub,
            modifier = Modifier.fillMaxWidth(),
        )
        // The clock fills whatever is left of the screen
        Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
            FitText(
                text = clockText,
                maxSize = 900.sp,
                minSize = 24.sp,
                fontWeight = FontWeight.Bold,
                color = status.lecternColor(),
                style = Tabular,
            )
        }
    }
}
