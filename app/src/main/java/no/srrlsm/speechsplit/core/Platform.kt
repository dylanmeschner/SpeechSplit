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
