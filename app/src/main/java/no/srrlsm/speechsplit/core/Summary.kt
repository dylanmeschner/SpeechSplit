package no.srrlsm.speechsplit.core

import kotlin.math.abs
import kotlin.math.roundToInt

// ============================================================================
// SUMMARY AFTER A RUN
// What went well, plain observations ("Part 3 ran 1:20 over time"), and how it compares
// with the run before. Shown on the finished screen and for every run in the history.
// ============================================================================

/** How one part went: within 5 s or 5 % is on time; within 15 s or 15 % is "a bit". */
enum class Verdict { ON_TIME, BIT_OVER, OVER, BIT_UNDER, UNDER }

fun verdict(diffSeconds: Int, targetSeconds: Int): Verdict {
    val a = abs(diffSeconds)
    val near = maxOf(5, (targetSeconds * 0.05).roundToInt())
    val bit = maxOf(15, (targetSeconds * 0.15).roundToInt())
    return when {
        a <= near -> Verdict.ON_TIME
        diffSeconds > 0 -> if (a <= bit) Verdict.BIT_OVER else Verdict.OVER
        else -> if (a <= bit) Verdict.BIT_UNDER else Verdict.UNDER
    }
}

fun Strings3.verdictLabel(v: Verdict): String = when (v) {
    Verdict.ON_TIME -> verdictOnTime
    Verdict.BIT_OVER -> verdictBitOver
    Verdict.OVER -> verdictOver
    Verdict.BIT_UNDER -> verdictBitUnder
    Verdict.UNDER -> verdictUnder
}

data class RunSummary(
    val praise: List<String>,
    val notes: List<String>,
    /** "Last time +1:10 · this time +0:05", or null for the first run. */
    val compare: String?,
    /** For a meeting: "The meeting ended 3:10 early." Null for a speech. */
    val meetingLine: String?,
)

/**
 * @param prev the run before this one (same speech or meeting), or null.
 * @param tolerance the celebration setting (seconds that count as "on target").
 */
fun buildSummary(run: PracticeRun, prev: PracticeRun?, tolerance: Int, s: Strings3): RunSummary {
    val praise = mutableListOf<String>()
    val total = run.diffSeconds
    val meeting = run.meeting != null
    // A speech with spare time that ended inside it: on time, without having hit the second
    val usedBuffer = if (meeting) 0 else run.bufferUsedSeconds
    when {
        usedBuffer > 0 && total == 0 -> praise += s.praiseWithinBuffer(formatTime(usedBuffer))
        total == 0 -> praise += s.praisePerfect
        abs(total) <= maxOf(10, tolerance) -> praise += s.praiseTotalClose(formatTime(abs(total)))
        total < 0 && meeting -> praise += s.praiseUnder(formatTime(-total))
    }
    val segs = run.segments
    val onTime = segs.count { verdict(it.diffSeconds, it.targetSeconds) == Verdict.ON_TIME }
    if (segs.size > 1 && onTime * 2 >= segs.size) praise += s.praiseSegments(onTime.toString(), segs.size.toString())
    segs.firstOrNull { it.targetSeconds > 0 && it.actualSeconds == it.targetSeconds }?.let {
        if (segs.size > 1) praise += s.praiseExact(it.title)
    }
    if (prev != null) {
        val gain = abs(prev.diffSeconds) - abs(total)
        if (gain >= 5) praise += s.praiseBetter(formatTime(gain))
    }

    val off = segs
        .map { it to verdict(it.diffSeconds, it.targetSeconds) }
        .filter { (_, v) -> v == Verdict.OVER || v == Verdict.BIT_OVER || v == Verdict.UNDER }
        .sortedByDescending { (seg, _) -> abs(seg.diffSeconds) }
    val notes = off.take(3).map { (seg, _) ->
        if (seg.diffSeconds > 0) s.noteOver(seg.title, formatTime(seg.diffSeconds))
        else s.noteUnder(seg.title, formatTime(-seg.diffSeconds))
    }.toMutableList()
    if (off.size > 3) notes += s.noteMore(off.size - 3)

    return RunSummary(
        praise = praise.take(3),
        notes = notes,
        compare = prev?.let { s.compareLast(formatDiff(it.diffSeconds), formatDiff(total)) },
        meetingLine = if (!meeting) null else when {
            total < 0 -> s.meetingEndedEarly(formatTime(-total))
            total > 0 -> s.meetingEndedLate(formatTime(total))
            else -> s.meetingEndedOnTime
        },
    )
}
