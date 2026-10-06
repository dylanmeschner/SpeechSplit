package no.srrlsm.speechsplit.core

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

/**
 * The stopwatch logic, with no Android code. The clock is passed in, so the same engine can run
 * on Android, desktop or iOS (and in unit tests with a fake clock).
 * Uses Compose runtime state, which is multiplatform, so the UI updates by itself.
 *
 * HOW SECONDS ARE COUNTED
 * Every clock on screen is derived from ONE running total of milliseconds.
 * A segment's seconds = (whole seconds at its end) - (whole seconds at its start).
 * Before, each segment rounded its own time down, and the leftover fractions added up:
 * the segment clock could say 00:00 while the total was already a second over (or the
 * reverse). Now the segment clocks always add up exactly to the TOTAL clock.
 *
 * @param clockMs a monotonic clock in milliseconds (never jumps when the wall clock changes).
 */
class TimerEngine(private val clockMs: () -> Long) {
    var segmentCount by mutableIntStateOf(0)
        private set
    var currentIndex by mutableIntStateOf(0)
        private set
    var isRunning by mutableStateOf(false)
        private set

    private var recordedMs by mutableStateOf(emptyList<Long>()) // committed time per segment
    private var runStartedAt = 0L                                // clock value when the current run began
    private var now by mutableLongStateOf(0L)                    // refreshed by tick() to drive the UI
    private var finishedAt: Long? = null                         // for undoing an accidental "Finish"

    val isActive: Boolean get() = segmentCount > 0
    val isFinished: Boolean get() = segmentCount > 0 && currentIndex >= segmentCount

    private fun liveMs(): Long = if (isRunning) (now - runStartedAt).coerceAtLeast(0) else 0L

    fun totalMs(): Long = recordedMs.sum() + liveMs()

    /** Milliseconds from the start up to the end of segment [i] (or up to now, for the current one). */
    private fun msThrough(i: Int): Long {
        if (i < 0) return 0L
        var sum = 0L
        for (j in 0..minOf(i, recordedMs.lastIndex)) sum += recordedMs[j]
        if (i >= currentIndex) sum += liveMs()
        return sum
    }

    fun elapsedSeconds(index: Int): Int =
        ((msThrough(index) / 1000) - (msThrough(index - 1) / 1000)).toInt()

    fun totalElapsedSeconds(): Int = (totalMs() / 1000).toInt()

    /** Seconds per segment, adding up exactly to [totalElapsedSeconds]. */
    fun allElapsedSeconds(): List<Int> = (0 until segmentCount).map { elapsedSeconds(it) }

    /** How long until the next whole second ticks over, so the display can flip exactly on time. */
    fun msToNextSecond(): Long = 1000 - (totalMs() % 1000)

    fun tick() {
        now = clockMs()
    }

    fun start(segments: Int) {
        if (segments <= 0) return
        isRunning = false
        segmentCount = segments
        recordedMs = List(segments) { 0L }
        currentIndex = 0
        finishedAt = null
        resume()
    }

    fun togglePause() = if (isRunning) pause() else resume()

    /** Moves to the next segment. Returns true if that finished the run. */
    fun next(): Boolean {
        if (!isActive || isFinished) return false
        val wasRunning = isRunning
        if (wasRunning) commitCurrentRun()
        currentIndex++
        if (currentIndex >= segmentCount) {
            finishedAt = if (wasRunning) clockMs() else null
            isRunning = false
            return true
        }
        return false
    }

    /** Undo an accidental NEXT: time spent since then goes back to the previous segment. */
    fun previous() {
        if (currentIndex == 0) return
        if (isFinished) {
            currentIndex--
            finishedAt?.let { t ->
                addTo(currentIndex, clockMs() - t)
                resume()
            }
            finishedAt = null
            return
        }
        if (isRunning) commitCurrentRun()
        val moved = recordedMs[currentIndex]
        recordedMs = recordedMs.toMutableList().also {
            it[currentIndex] = 0L
            it[currentIndex - 1] += moved
        }
        currentIndex--
    }

    fun stop() {
        isRunning = false
        segmentCount = 0
        currentIndex = 0
        recordedMs = emptyList()
        finishedAt = null
    }

    private fun resume() {
        if (isRunning || isFinished || !isActive) return
        runStartedAt = clockMs()
        now = runStartedAt
        isRunning = true
    }

    private fun pause() {
        if (!isRunning) return
        commitCurrentRun()
        isRunning = false
    }

    private fun commitCurrentRun() {
        val t = clockMs()
        addTo(currentIndex, t - runStartedAt)
        runStartedAt = t
        now = t
    }

    private fun addTo(index: Int, ms: Long) {
        if (index !in recordedMs.indices) return
        recordedMs = recordedMs.toMutableList().also { it[index] += ms }
    }
}
