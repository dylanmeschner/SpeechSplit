package no.srrlsm.speechsplit.core

// ============================================================================
// PLATFORM BOUNDARY
// Everything that differs between Android, Windows and iPad lives behind these two
// interfaces. The Android versions are in the `platform` package. A Windows or iPad
// version would only need its own implementations of these; the rest is shared.
// ============================================================================

/** Saving and loading. Android: SharedPreferences + JSON. */
interface AppStore {
    fun loadPlans(): List<SpeechPlan>
    fun savePlans(plans: List<SpeechPlan>)
    fun loadRuns(): List<PracticeRun>
    fun saveRuns(runs: List<PracticeRun>)
    fun loadSettings(): AppSettings
    fun saveSettings(settings: AppSettings)
    /** Plan as shareable text (no ids), and back. Returns null if the text isn't a plan. */
    fun exportPlan(plan: SpeechPlan): String
    fun importPlan(text: String, fallbackTitle: String): SpeechPlan?

    /** A .speechsplit file with one speech, and its practice history if [runs] isn't empty. */
    fun exportPlanFile(plan: SpeechPlan, runs: List<PracticeRun> = emptyList()): String = exportPlan(plan)

    /** A .speechsplit file with every speech (archived ones too) and all practice history. */
    fun exportLibraryFile(plans: List<SpeechPlan>, runs: List<PracticeRun>): String = ""

    /** Reads a .speechsplit file, an older export or pasted export text. Null if it isn't one. */
    fun readSpeechFile(text: String, fallbackTitle: String): SpeechFile? =
        importPlan(text, fallbackTitle)?.let { SpeechFile(listOf(it), emptyList(), isLibrary = false) }

    /** Writes anything still waiting to be saved. Called when the app closes. */
    fun flush() {}
}

/** What a .speechsplit file holds: one speech (maybe with its history), or a whole library backup. */
data class SpeechFile(
    val plans: List<SpeechPlan>,
    val runs: List<PracticeRun>,
    val isLibrary: Boolean,
)

/** What "Export" puts in the file. */
enum class ExportScope { SPEECH, SPEECH_WITH_HISTORY, LIBRARY }

/** "2026-10-21" (UTC) from epoch milliseconds, without any date library. */
fun isoDate(epochMs: Long): String {
    // Days since 1970 to year/month/day (Howard Hinnant's civil_from_days)
    val z = Math.floorDiv(epochMs, 86_400_000L) + 719_468
    val era = Math.floorDiv(z, 146_097L)
    val doe = z - era * 146_097
    val yoe = (doe - doe / 1_460 + doe / 36_524 - doe / 146_096) / 365
    val doy = doe - (365 * yoe + yoe / 4 - yoe / 100)
    val mp = (5 * doy + 2) / 153
    val d = doy - (153 * mp + 2) / 5 + 1
    val m = if (mp < 10) mp + 3 else mp - 9
    val y = yoe + era * 400 + if (m <= 2) 1 else 0
    return "$y-${m.toString().padStart(2, '0')}-${d.toString().padStart(2, '0')}"
}

/** Speeches saved as files ("Export") use this extension and file type. */
const val SPEECH_FILE_EXTENSION = "speechsplit"
const val SPEECH_FILE_MIME = "application/vnd.speechsplit+json"

/** A safe file name for a speech, e.g. "Wedding toast.speechsplit". */
fun speechFileName(title: String): String {
    val base = title.replace(Regex("""[\\/:*?"<>|\p{Cntrl}]"""), " ")
        .replace(Regex("""\s+"""), " ").trim().trimEnd('.').take(60).trim()
    return "${base.ifBlank { "Speech" }}.$SPEECH_FILE_EXTENSION"
}

/** Device features used by the app logic. Anything a platform lacks can simply do nothing. */
interface PlatformServices {
    /** This app's version, e.g. "2.0" ("dev" when run from the code). */
    val appVersion: String get() = "dev"

    /** Asks GitHub for the newest release. Null if offline or GitHub can't be reached. */
    suspend fun fetchLatestRelease(): ReleaseInfo? = null

    /** The latest releases with their notes, newest first (for "Patch notes"). Null if offline. */
    suspend fun fetchReleases(): List<ReleaseInfo>? = null

    /** e.g. "Android 15" or "Windows 11", for the feedback email. */
    val platformName: String get() = "unknown"

    /** Extra lines for the feedback email, e.g. where a freeze log was written. Null if none. */
    val diagnosticsNote: String? get() = null

    /**
     * Reads a speech document (PDF, Word, OpenDocument, text). Empty if it has no readable text,
     * null if the file type isn't supported. Called off the main thread.
     */
    fun readDocument(fileName: String, bytes: ByteArray): List<DocBlock>? = null

    /** Two-letter language code of the device, e.g. "nb", "de", "en". */
    val systemLanguage: String

    /** Monotonic milliseconds, for the timer (never jumps when the wall clock changes). */
    fun monotonicMs(): Long

    /** Wall-clock milliseconds, for dating saved runs. */
    fun epochMs(): Long

    /** Local, human-friendly date and time, e.g. "05.10.2026 18:30". */
    fun formatDateTime(epochMs: Long): String

    val canVibrate: Boolean
    fun vibrate(long: Boolean)

    /** Silent notification that makes a connected smartwatch buzz. */
    fun watchAlert(status: TimeStatus, title: String, text: String, strings: Strings)

    /** What this platform can do. The UI hides settings for things it can't. */
    val supportsWatchAlerts: Boolean get() = true
    val supportsDnd: Boolean get() = true
    /** Keyboard / presentation-clicker shortcuts (desktop). */
    val hasKeyboardShortcuts: Boolean get() = false

    /** Do Not Disturb (Focus on iPad / Windows). */
    fun hasDndAccess(): Boolean
    fun setDnd(on: Boolean)
}
