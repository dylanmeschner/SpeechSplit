package no.srrlsm.speechsplit.core

fun Strings.summaryOf(plan: SpeechPlan): String =
    summary(plan.segments.size, formatTime(plan.totalTargetSeconds))

/**
 * A plain-text report of one run, for sharing by message or email:
 *
 *   Speech Split · Sunday talk
 *   05.10.2026 18:30
 *   Total 19:58 (plan 20:00)  -00:02
 *
 *   1. Introduction  04:10 (plan 04:00)  +00:10
 *   2. Main point    ...
 */
fun buildRunReport(run: PracticeRun, s: Strings, dateText: String): String = buildString {
    appendLine("Speech Split · ${run.planTitle}")
    appendLine(dateText)
    appendLine(
        "${s.reportTotal} ${formatTime(run.totalActualSeconds)} (${s.reportPlan(formatTime(run.totalTargetSeconds))})  ${formatDiff(run.diffSeconds)}"
    )
    appendLine()
    run.segments.forEachIndexed { i, seg ->
        appendLine(
            "${i + 1}. ${seg.title}  ${formatTime(seg.actualSeconds)} (${s.reportPlan(formatTime(seg.targetSeconds))})  ${formatDiff(seg.diffSeconds)}"
        )
    }
}.trimEnd()
