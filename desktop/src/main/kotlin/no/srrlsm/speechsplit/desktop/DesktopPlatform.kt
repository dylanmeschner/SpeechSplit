package no.srrlsm.speechsplit.desktop

import no.srrlsm.speechsplit.core.AppSettings
import no.srrlsm.speechsplit.core.FetchResult
import no.srrlsm.speechsplit.core.Folder
import no.srrlsm.speechsplit.core.MeetingProgram
import no.srrlsm.speechsplit.core.AppStore
import no.srrlsm.speechsplit.core.PlatformServices
import no.srrlsm.speechsplit.core.ReleaseInfo
import no.srrlsm.speechsplit.jvm.UpdateClient
import no.srrlsm.speechsplit.core.PracticeRun
import no.srrlsm.speechsplit.core.SpeechPlan
import no.srrlsm.speechsplit.core.SpeechSegment
import no.srrlsm.speechsplit.core.SpeechFile
import no.srrlsm.speechsplit.core.Strings
import no.srrlsm.speechsplit.core.TimeStatus
import no.srrlsm.speechsplit.jvm.JsonCodec
import org.json.JSONObject
import java.io.File
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.text.DateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

/** Where the app keeps its data: %APPDATA%\Speech Split on Windows. */
fun appDataDir(): File {
    val os = System.getProperty("os.name").lowercase()
    val home = System.getProperty("user.home")
    val dir = when {
        os.contains("win") -> File(System.getenv("APPDATA") ?: "$home\\AppData\\Roaming", "Speech Split")
        os.contains("mac") -> File(home, "Library/Application Support/Speech Split")
        else -> File(home, ".speechsplit")
    }
    dir.mkdirs()
    return dir
}

val isWindows: Boolean = System.getProperty("os.name").lowercase().contains("win")

/**
 * Desktop storage: three small JSON files. Same format as the Android app,
 * so a speech exported on the phone can be imported on the PC and the other way round.
 */
class DesktopStore(private val dir: File = appDataDir()) : AppStore {
    private val plansFile = File(dir, "speeches.json")
    private val runsFile = File(dir, "history.json")
    private val settingsFile = File(dir, "settings.json")
    private val foldersFile = File(dir, "folders.json")
    private fun meetingFile(language: String) = File(dir, "meetings-$language.json")

    override fun loadFolders(): List<Folder> = read(foldersFile)?.let { JsonCodec.foldersFromJson(it) } ?: emptyList()
    override fun saveFolders(folders: List<Folder>) = write(foldersFile, JsonCodec.foldersToJson(folders))
    override fun loadMeetingCache(language: String): String? = read(meetingFile(language))
    override fun saveMeetingCache(language: String, text: String) = write(meetingFile(language), text)
    override fun readMeetingProgram(text: String): MeetingProgram? = JsonCodec.meetingProgramFromJson(text)
    private val subpartsFile = File(dir, "meeting-subparts.json")
    override fun loadMeetingSubparts(): Map<String, List<SpeechSegment>> =
        read(subpartsFile)?.let { JsonCodec.subpartsFromJson(it) } ?: emptyMap()
    override fun saveMeetingSubparts(subparts: Map<String, List<SpeechSegment>>) = write(subpartsFile, JsonCodec.subpartsToJson(subparts))
    override fun hasSavedSettings(): Boolean = settingsFile.exists()

    override fun loadPlans(): List<SpeechPlan> =
        read(plansFile)?.let { runCatching { JsonCodec.plansFromJson(it).first }.getOrNull() } ?: emptyList()

    override fun savePlans(plans: List<SpeechPlan>) = write(plansFile, JsonCodec.plansToJson(plans))

    override fun loadRuns(): List<PracticeRun> =
        read(runsFile)?.let { runCatching { JsonCodec.runsFromJson(it) }.getOrNull() } ?: emptyList()

    override fun saveRuns(runs: List<PracticeRun>) = write(runsFile, JsonCodec.runsToJson(runs))

    override fun loadSettings(): AppSettings =
        read(settingsFile)?.let { runCatching { JsonCodec.settingsFromJson(it) }.getOrNull() } ?: AppSettings()

    override fun saveSettings(settings: AppSettings) = write(settingsFile, JsonCodec.settingsToJson(settings))

    override fun exportPlan(plan: SpeechPlan): String = JsonCodec.planToJson(plan, includeIds = false).toString()

    override fun exportPlanFile(plan: SpeechPlan, runs: List<PracticeRun>): String = JsonCodec.planFileText(plan, runs)

    override fun exportLibraryFile(plans: List<SpeechPlan>, runs: List<PracticeRun>, folders: List<Folder>): String =
        JsonCodec.libraryFileText(plans, runs, folders)

    override fun readSpeechFile(text: String, fallbackTitle: String): SpeechFile? =
        JsonCodec.readSpeechFile(text, fallbackTitle)

    override fun importPlan(text: String, fallbackTitle: String): SpeechPlan? = try {
        JsonCodec.planFromJson(JSONObject(text.trim()), fallbackTitle)
    } catch (e: Exception) {
        null
    }

    private fun read(file: File): String? = try {
        if (file.exists()) file.readText() else null
    } catch (e: Exception) {
        null
    }

    // Saving happens on its own thread, so a slow disk, antivirus scan or cloud-synced folder
    // can never make the window stop responding. Only the newest version of each file is written.
    private val pending = ConcurrentHashMap<File, String>()
    private val writer = Executors.newSingleThreadExecutor { r -> Thread(r, "SpeechSplit-save").apply { isDaemon = true } }

    private fun write(file: File, text: String) {
        if (pending.put(file, text) == null) {
            runCatching {
                writer.execute { pending.remove(file)?.let { writeNow(file, it) } }
            }.onFailure { pending.remove(file)?.let { writeNow(file, it) } } // closing: write right away
        }
    }

    /** Waits (briefly) until everything is on disk. Called when the app closes. */
    override fun flush() {
        writer.shutdown()
        runCatching { writer.awaitTermination(5, TimeUnit.SECONDS) }
        pending.keys.toList().forEach { f -> pending.remove(f)?.let { writeNow(f, it) } }
    }

    /** Writes to a temporary file first, so a crash mid-save never leaves a half-written file. */
    private fun writeNow(file: File, text: String) {
        try {
            val tmp = File(file.parentFile, file.name + ".tmp")
            tmp.writeText(text)
            Files.move(tmp.toPath(), file.toPath(), StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE)
        } catch (e: Exception) {
            runCatching { file.writeText(text) }
        }
    }
}

/** Desktop device features. A PC has no vibration, watch link or Do Not Disturb API we can use. */
class DesktopPlatform : PlatformServices {
    override val systemLanguage: String get() = Locale.getDefault().language

    /** Set by the build (see desktop/build.gradle.kts). "dev" when run from the code. */
    override val appVersion: String = System.getProperty("speechsplit.version") ?: "dev"

    override suspend fun fetchLatestRelease(): ReleaseInfo? = UpdateClient.fetchLatest()
    override suspend fun fetchReleases(): List<ReleaseInfo>? = UpdateClient.fetchReleases()

    override val platformName: String
        get() = "${System.getProperty("os.name")} ${System.getProperty("os.version")}"

    override val diagnosticsNote: String?
        get() = FreezeWatch.logFile.takeIf { it.exists() && it.length() > 0 }
            ?.let { "Freeze log (please attach): ${it.absolutePath}" }

    override suspend fun fetchText(url: String): FetchResult? = UpdateClient.fetchText(url)

    override fun localDateIso(epochMs: Long): String {
        val c = java.util.Calendar.getInstance()
        c.timeInMillis = epochMs
        return "%04d-%02d-%02d".format(Locale.ROOT, c.get(java.util.Calendar.YEAR), c.get(java.util.Calendar.MONTH) + 1, c.get(java.util.Calendar.DAY_OF_MONTH))
    }
    override fun monotonicMs(): Long = System.nanoTime() / 1_000_000
    override fun epochMs(): Long = System.currentTimeMillis()
    override fun formatDateTime(epochMs: Long): String =
        DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT).format(Date(epochMs))

    override val canVibrate: Boolean = false
    override fun vibrate(long: Boolean) {}

    override val supportsWatchAlerts: Boolean = false
    override val supportsDnd: Boolean = false
    override val hasKeyboardShortcuts: Boolean = true
    override val supportsSecondScreen: Boolean = true

    /** More than one monitor connected right now. */
    override fun hasSecondScreen(): Boolean =
        runCatching { java.awt.GraphicsEnvironment.getLocalGraphicsEnvironment().screenDevices.size > 1 }.getOrDefault(false)

    override fun watchAlert(status: TimeStatus, title: String, text: String, strings: Strings) {}
    override fun hasDndAccess(): Boolean = false
    override fun setDnd(on: Boolean) {}
}
