package no.srrlsm.speechsplit.core

import kotlin.random.Random

// ============================================================================
// CORE MODELS
// Everything in the `core` package is plain Kotlin: no Android imports.
// That keeps it ready to share with a future Windows (desktop) or iPad version
// via Kotlin Multiplatform / Compose Multiplatform.
// ============================================================================

/** Random UUID-style id, without java.util.UUID (which doesn't exist on iOS). */
fun newId(): String {
    val hex = "0123456789abcdef"
    val chars = CharArray(36)
    for (i in chars.indices) {
        chars[i] = if (i == 8 || i == 13 || i == 18 || i == 23) '-' else hex[Random.nextInt(16)]
    }
    return chars.concatToString()
}

data class SpeechSegment(
    val id: String = newId(),
    val title: String = "",
    val targetSeconds: Int = 0,
)

data class SpeechPlan(
    val id: String = newId(),
    val title: String = "",
    val segments: List<SpeechSegment> = emptyList(),
    /** When the speech was first saved (0 = unknown, older saves). */
    val createdAtEpochMs: Long = 0L,
    /** When the timer was last started for this speech (0 = never). */
    val lastUsedAtEpochMs: Long = 0L,
    /** Archived speeches are hidden from the list, but kept with their history and can be restored. */
    val archived: Boolean = false,
    /** The folder it's in, or null for the library itself. */
    val folderId: String? = null,
    /** Set for a meeting (meeting mode). A meeting isn't saved as a speech: it's made from the week's program. */
    val meeting: MeetingInfo? = null,
    /** "Practise one part": a one-segment copy of a speech's part, run again and again. Not saved. */
    val isDrill: Boolean = false,
) {
    val totalTargetSeconds: Int get() = segments.sumOf { it.targetSeconds }

    /** What the run is measured against at the end: the parts, plus a meeting's spare minutes. */
    val totalPlannedSeconds: Int get() = totalTargetSeconds + (meeting?.bufferSeconds ?: 0)

    /** For "last used" sorting: a new speech counts as used when it was created. */
    val recentActivityEpochMs: Long get() = maxOf(lastUsedAtEpochMs, createdAtEpochMs)
}

/** A folder in the library. Speeches point to it with [SpeechPlan.folderId]. */
data class Folder(
    val id: String = newId(),
    val name: String,
    val createdAtEpochMs: Long = 0L,
)

enum class MeetingKind { MIDWEEK, WEEKEND }

/**
 * @param bufferSeconds time to spare at the end (the midweek meeting has 5 minutes). Parts that run
 *   late use it up first; the meeting is only late once it's gone.
 * @param week Monday of the meeting's week, "2026-11-02" (midweek only).
 */
data class MeetingInfo(
    val kind: MeetingKind,
    val bufferSeconds: Int = 0,
    val week: String = "",
    val reading: String = "",
)

/** How the speech list is sorted. */
enum class SpeechSort { LAST_USED, CREATED, NAME }

/** One segment of a finished practice run. Title and target are copied, so old runs still read right after the plan is edited. */
data class RunSegment(
    val segmentId: String,
    val title: String,
    val targetSeconds: Int,
    val actualSeconds: Int,
) {
    val diffSeconds: Int get() = actualSeconds - targetSeconds
}

/** A finished practice run, saved to the speech's history. */
data class PracticeRun(
    val id: String = newId(),
    val planId: String,
    val planTitle: String,
    val finishedAtEpochMs: Long,
    val segments: List<RunSegment>,
    /** A real speech (chosen on the Ready screen), not practice. Meetings are always real. */
    val live: Boolean = false,
    /** Set for a meeting's run. */
    val meeting: MeetingKind? = null,
    /** A meeting's spare minutes: counted as planned time at the end. */
    val bufferSeconds: Int = 0,
) {
    val totalTargetSeconds: Int get() = segments.sumOf { it.targetSeconds }
    val totalActualSeconds: Int get() = segments.sumOf { it.actualSeconds }
    /** Over (+) or under (-) the plan. A meeting is only over once its spare minutes are used up. */
    val diffSeconds: Int get() = totalActualSeconds - totalTargetSeconds - bufferSeconds
}

// ============================================================================
// SETTINGS
// ============================================================================
enum class AppLanguage { SYSTEM, ENGLISH, GERMAN, NORWEGIAN }

enum class ClockMode { REMAINING, ELAPSED }

enum class ThemeMode { SYSTEM, LIGHT, DARK }

data class AppSettings(
    val language: AppLanguage = AppLanguage.SYSTEM,
    val themeMode: ThemeMode = ThemeMode.SYSTEM,
    /** -1 = off, 0 = only an exact hit, n = within ±n seconds of the target. */
    val celebrateTolerance: Int = 0,
    val warningPercent: Int = 10,          // 0 = no warning
    val clockMode: ClockMode = ClockMode.REMAINING,
    val showAdjusted: Boolean = true,
    val flashAlerts: Boolean = true,
    val vibrateAlerts: Boolean = false,
    val keepScreenOn: Boolean = true,
    /** Buzz a connected smartwatch via a silent notification. */
    val watchAlerts: Boolean = false,
    /** Turn on Do Not Disturb while the timer runs. */
    val dndWhileSpeaking: Boolean = false,
    /** Big, high-contrast clock for reading from a distance. */
    val lecternMode: Boolean = false,
    /** Ask GitHub for a newer version when the app starts. */
    val autoUpdateCheck: Boolean = true,
    /** Order of the speech list. */
    val speechSort: SpeechSort = SpeechSort.LAST_USED,
    /** The introduction cards were shown (or skipped). */
    val tutorialSeen: Boolean = false,
    /** The version the app last started as, to show "what's new" once after an update. */
    val lastSeenVersion: String = "",
    /** Ready screen choice: a real speech (true) or practice. */
    val runLive: Boolean = false,
    /** Meeting mode: the meeting picked last time. */
    val meetingKind: MeetingKind = MeetingKind.MIDWEEK,
    /** Meeting mode: language of the program ("no", "en", "de"), or "" for the app's language. */
    val meetingLanguage: String = "",
)

val WarningOptions = listOf(0, 5, 10, 15, 20)
val CelebrateOptions = listOf(-1, 0, 2, 5)
val QuickTimerOptions = listOf(5, 10, 15, 20, 30, 45)

/** Largest file that's read for importing (a whole library with years of history is far smaller). */
const val MAX_IMPORT_BYTES = 20 * 1024 * 1024

enum class TimeStatus { ON_TRACK, WARNING, OVER }

/** One shared rule for "warning" and "overtime" everywhere in the app. */
fun timeStatus(remainingSeconds: Int, targetSeconds: Int, warningPercent: Int): TimeStatus = when {
    remainingSeconds < 0 -> TimeStatus.OVER
    warningPercent > 0 && targetSeconds > 0 && remainingSeconds <= targetSeconds * warningPercent / 100.0 -> TimeStatus.WARNING
    else -> TimeStatus.ON_TRACK
}
