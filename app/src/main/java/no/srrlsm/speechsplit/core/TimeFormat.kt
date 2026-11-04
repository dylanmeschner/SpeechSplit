package no.srrlsm.speechsplit.core

import kotlin.math.abs

/** "04:30", "-00:05", and "1:45:00" from an hour up (a whole meeting). Plain Kotlin (no String.format). */
fun formatTime(seconds: Int): String {
    val a = abs(seconds)
    val sign = if (seconds < 0) "-" else ""
    if (a >= 3600) {
        return sign + (a / 3600) + ":" + (a % 3600 / 60).toString().padStart(2, '0') + ":" + (a % 60).toString().padStart(2, '0')
    }
    return sign + (a / 60).toString().padStart(2, '0') + ":" + (a % 60).toString().padStart(2, '0')
}

/** "+00:10" for over, "-00:10" for under, "00:00" for exact. */
fun formatDiff(diffSeconds: Int): String =
    if (diffSeconds > 0) "+" + formatTime(diffSeconds) else formatTime(diffSeconds)

/** "4.30", "4,30" and "4:30" all mean 4 min 30 s. "4.5" means 4:50. */
fun parseTimeInput(input: String): Int {
    val parts = input.trim().replace(",", ".").replace(":", ".").split(".")
    val m = parts.getOrNull(0)?.toIntOrNull() ?: 0
    val s = parts.getOrNull(1)?.take(2)?.padEnd(2, '0')?.toIntOrNull() ?: 0
    return (m * 60 + s).coerceAtLeast(0)
}

fun formatTimeForInput(seconds: Int): String {
    val m = seconds / 60
    val s = seconds % 60
    return if (s == 0) "$m" else "$m." + s.toString().padStart(2, '0')
}

/** Rounds to the nearest 5 seconds, for suggested times that are easy to type in. */
fun roundTo5(seconds: Int): Int = ((seconds + 2) / 5) * 5
