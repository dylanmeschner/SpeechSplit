package no.srrlsm.speechsplit.core

import kotlin.math.abs
import kotlin.math.roundToInt

/** How many of the latest runs the averages and suggestions look at. */
const val RecentRuns = 5

/** Suggestions only appear once there are at least this many runs to go on. */
const val MinRunsForSuggestion = 3

/** History kept per speech (oldest runs are dropped beyond this). */
const val MaxRunsPerPlan = 50

data class SegmentStats(
    val segment: SpeechSegment,
    /** Number of recent runs that included this segment. */
    val runs: Int,
    /** Average over (+) or under (-) the target, in seconds, over the recent runs. */
    val avgDiffSeconds: Int,
    /** A suggested new target, or null when the segment is already on track. Never applied automatically. */
    val suggestedSeconds: Int?,
)

data class PlanStats(
    val runCount: Int,
    val onTargetCount: Int,
    val avgTotalDiffSeconds: Int,
    /** The run closest to the planned total. */
    val bestRun: PracticeRun?,
    val segments: List<SegmentStats>,
) {
    val suggestions: List<SegmentStats> get() = segments.filter { it.suggestedSeconds != null }
}

/** Finds this segment in an old run: by id first, then by title (for runs saved before ids were stored). */
fun PracticeRun.find(segment: SpeechSegment): RunSegment? =
    segments.firstOrNull { it.segmentId == segment.id }
        ?: segments.firstOrNull { it.title.isNotBlank() && it.title.equals(segment.title, ignoreCase = true) }

/**
 * @param runs this plan's runs, newest first.
 * @param tolerance seconds counted as "on target" (the celebration setting; at least 0).
 */
fun planStats(plan: SpeechPlan, runs: List<PracticeRun>, tolerance: Int): PlanStats {
    val recent = runs.take(RecentRuns)
    val segmentStats = plan.segments.map { seg ->
        val diffs = recent.mapNotNull { it.find(seg)?.diffSeconds }
        val avg = if (diffs.isEmpty()) 0 else diffs.average().roundToInt()
        // Only suggest when the pattern is clear: enough runs, and off by 10 s or 10 %, whichever is bigger
        val threshold = maxOf(10, seg.targetSeconds / 10)
        val suggested = if (diffs.size >= MinRunsForSuggestion && abs(avg) >= threshold) {
            roundTo5(seg.targetSeconds + avg).coerceAtLeast(5)
        } else null
        SegmentStats(seg, diffs.size, avg, suggested?.takeIf { it != seg.targetSeconds })
    }
    return PlanStats(
        runCount = runs.size,
        onTargetCount = runs.count { abs(it.diffSeconds) <= tolerance.coerceAtLeast(0) },
        avgTotalDiffSeconds = if (recent.isEmpty()) 0 else recent.map { it.diffSeconds }.average().roundToInt(),
        bestRun = runs.minByOrNull { abs(it.diffSeconds) },
        segments = segmentStats,
    )
}
