package no.srrlsm.speechsplit.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.random.Random

/**
 * Plain JVM tests for the shared core (no phone or emulator needed).
 * Run them in Android Studio: right-click this file > Run.
 */
class TimerEngineTest {
    private var t = 0L
    private fun engine() = TimerEngine { t }

    /** The reported bug: 00:00 on the segment clock gave "1 s over" in the total. */
    @Test
    fun finishingAtZeroIsExactlyOnTarget() {
        val e = engine()
        t = 0; e.start(2)
        t = 60_700; e.tick()
        assertEquals(60, e.elapsedSeconds(0))
        e.next()
        // Wait until segment 2's clock shows its full 60 s, then finish
        while (e.elapsedSeconds(1) < 60) { t += 10; e.tick() }
        e.next()
        assertEquals(120, e.totalElapsedSeconds())
    }

    @Test
    fun segmentsAlwaysAddUpToTotal() {
        val r = Random(1)
        repeat(500) {
            val e = engine(); t = 0; e.start(4)
            repeat(4) {
                repeat(r.nextInt(1, 30)) {
                    t += r.nextLong(1, 7000); e.tick()
                    assertEquals(e.totalElapsedSeconds(), e.allElapsedSeconds().sum())
                    if (r.nextInt(10) == 0) { e.togglePause(); t += r.nextLong(0, 5000); e.togglePause(); e.tick() }
                }
                e.next()
            }
            assertEquals(e.totalElapsedSeconds(), e.allElapsedSeconds().sum())
        }
    }

    @Test
    fun undoMovesTimeBack() {
        val e = engine()
        t = 0; e.start(2)
        t = 30_000; e.tick(); e.next()
        t = 40_000; e.tick(); e.previous(); e.tick()
        assertEquals(0, e.currentIndex)
        assertEquals(40, e.elapsedSeconds(0))
    }

    @Test
    fun undoFinishResumes() {
        val e = engine()
        t = 0; e.start(2)
        t = 30_000; e.next()
        t = 50_000; e.next()
        assertTrue(e.isFinished)
        t = 55_000; e.previous(); e.tick()
        assertTrue(e.isRunning)
        assertEquals(25, e.elapsedSeconds(1))
    }

    @Test
    fun suggestionsOnlyForClearPatterns() {
        val a = SpeechSegment(title = "A", targetSeconds = 60)
        val b = SpeechSegment(title = "B", targetSeconds = 60)
        val plan = SpeechPlan(title = "Talk", segments = listOf(a, b))
        val runs = listOf(0, 20, 40, 60).map { over ->
            PracticeRun(planId = plan.id, planTitle = "Talk", finishedAtEpochMs = 0, segments = listOf(
                RunSegment(a.id, "A", 60, 60),
                RunSegment(b.id, "B", 60, 60 + over),
            ))
        }
        val stats = planStats(plan, runs, tolerance = 0)
        assertEquals(listOf("B"), stats.suggestions.map { it.segment.title })
        assertEquals(90, stats.suggestions.first().suggestedSeconds)
        assertEquals(1, stats.onTargetCount)
    }

    @Test
    fun timeFormatting() {
        assertEquals("-00:05", formatTime(-5))
        assertEquals("+01:00", formatDiff(60))
        assertEquals("4.30", formatTimeForInput(270))
        assertEquals(270, parseTimeInput("4:30"))
        assertEquals(290, parseTimeInput("4.5"))
    }

    @Test
    fun versionComparison() {
        assertTrue(isNewerVersion("2.1", "2.0"))
        assertTrue(isNewerVersion("v2.10", "2.9"))
        assertTrue(isNewerVersion("3", "2.9.9"))
        assertTrue(!isNewerVersion("2.0", "2.0"))
        assertTrue(!isNewerVersion("2.0", "2.0.0"))
        assertTrue(!isNewerVersion("1.9", "2.0"))
        assertTrue(!isNewerVersion("2.1", "dev"))
    }
}
