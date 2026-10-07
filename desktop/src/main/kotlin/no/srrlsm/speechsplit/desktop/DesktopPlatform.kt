package no.srrlsm.speechsplit.desktop

import no.srrlsm.speechsplit.core.AppSettings
import no.srrlsm.speechsplit.core.DocBlock
import no.srrlsm.speechsplit.jvm.DocumentReader
import org.apache.pdfbox.Loader
import org.apache.pdfbox.text.PDFTextStripper
import no.srrlsm.speechsplit.core.AppStore
import no.srrlsm.speechsplit.core.PlatformServices
import no.srrlsm.speechsplit.core.ReleaseInfo
import no.srrlsm.speechsplit.jvm.UpdateClient
import no.srrlsm.speechsplit.core.PracticeRun
import no.srrlsm.speechsplit.core.SpeechPlan
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

    /** Writes to a temporary file first, so a crash mid-save never leaves a half-written file. */
    private fun write(file: File, text: String) {
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

    /** PDF text through Apache PDFBox; Word, OpenDocument and text through the shared reader. */
    override fun readDocument(fileName: String, bytes: ByteArray): List<DocBlock>? =
        DocumentReader.read(fileName, bytes) { pdf ->
            Loader.loadPDF(pdf).use { doc ->
                PDFTextStripper().apply {
                    sortByPosition = true
                    paragraphEnd = "\n\n"
                }.getText(doc)
            }
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

    override fun watchAlert(status: TimeStatus, title: String, text: String, strings: Strings) {}
    override fun hasDndAccess(): Boolean = false
    override fun setDnd(on: Boolean) {}
}
