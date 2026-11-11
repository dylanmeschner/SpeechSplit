package no.srrlsm.speechsplit.core

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import no.srrlsm.speechsplit.jvm.JsonCodec
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** .speechsplit files: one speech, one speech with history, and whole-library backups. */
class SpeechFileTest {
    /** In-memory store using the real file format, like the Android and Windows stores. */
    private class JsonStore : AppStore {
        var plans = emptyList<SpeechPlan>()
        var runs = emptyList<PracticeRun>()
        override fun loadPlans() = plans
        override fun savePlans(plans: List<SpeechPlan>) { this.plans = plans.toList() }
        override fun loadRuns() = runs
        override fun saveRuns(runs: List<PracticeRun>) { this.runs = runs }
        override fun loadSettings() = AppSettings(autoUpdateCheck = false)
        override fun saveSettings(settings: AppSettings) {}
        override fun exportPlan(plan: SpeechPlan) = JsonCodec.planToJson(plan, includeIds = false).toString()
        override fun importPlan(text: String, fallbackTitle: String) = JsonCodec.planFromJson(JSONObject(text), fallbackTitle)
        override fun exportPlanFile(plan: SpeechPlan, runs: List<PracticeRun>) = JsonCodec.planFileText(plan, runs)
        override fun exportLibraryFile(plans: List<SpeechPlan>, runs: List<PracticeRun>, folders: List<Folder>) = JsonCodec.libraryFileText(plans, runs, folders)
        var folders = emptyList<Folder>()
        override fun loadFolders() = folders
        override fun saveFolders(folders: List<Folder>) { this.folders = folders }
        override fun readMeetingProgram(text: String) = JsonCodec.meetingProgramFromJson(text)
        override fun readSpeechFile(text: String, fallbackTitle: String) = JsonCodec.readSpeechFile(text, fallbackTitle)
    }

    private class Platform : PlatformServices {
        override val systemLanguage = "en"
        override fun monotonicMs() = 0L
        override fun epochMs() = 1_792_540_800_000L // 2026-10-21
        override fun formatDateTime(epochMs: Long) = ""
        override val canVibrate = false
        override fun vibrate(long: Boolean) {}
        override fun watchAlert(status: TimeStatus, title: String, text: String, strings: Strings) {}
        override fun hasDndAccess() = false
        override fun setDnd(on: Boolean) {}
    }

    private val intro = SpeechSegment(title = "Intro", targetSeconds = 90)
    private val end = SpeechSegment(title = "Slutt", targetSeconds = 45)
    private val talk = SpeechPlan(title = "Tale «test»", segments = listOf(intro, end), createdAtEpochMs = 5)
    private val toast = SpeechPlan(title = "Toast", segments = listOf(SpeechSegment(title = "A", targetSeconds = 60)), archived = true, createdAtEpochMs = 6)
    private fun run(plan: SpeechPlan, at: Long) = PracticeRun(
        planId = plan.id, planTitle = plan.title, finishedAtEpochMs = at,
        segments = plan.segments.map { RunSegment(it.id, it.title, it.targetSeconds, it.targetSeconds + 3) },
    )

    private fun app(store: JsonStore) = AppController(store, Platform(), CoroutineScope(Job()))

    @Test
    fun speechFileRoundTrip() {
        val text = JsonCodec.planFileText(talk)
        val back = JsonCodec.readSpeechFile(text, "x")!!
        assertFalse(back.isLibrary)
        assertEquals(talk.title, back.plans.single().title)
        assertEquals(talk.segments.map { it.title to it.targetSeconds }, back.plans.single().segments.map { it.title to it.targetSeconds })
        assertEquals("Speech Split", JSONObject(text).getString("app"))
    }

    @Test
    fun oldExportTextStillImports() {
        val store = JsonStore()
        assertTrue(app(store).importPlan("""{"title":"Old","segments":[{"title":"A","target":60}]}"""))
        assertEquals("Old", store.plans.single().title)
    }

    @Test
    fun speechWithHistoryKeepsItsHistoryLinkedToTheNewCopy() {
        val from = JsonStore().apply { plans = listOf(talk); runs = listOf(run(talk, 100), run(talk, 200)) }
        val (name, text) = app(from).exportFile(talk, ExportScope.SPEECH_WITH_HISTORY)
        assertEquals("Tale «test».speechsplit", name)

        val to = JsonStore()
        val target = app(to)
        assertTrue(target.importPlan(text))
        val copy = to.plans.single()
        assertTrue(copy.id != talk.id)
        assertEquals(2, to.runs.size)
        assertTrue(to.runs.all { it.planId == copy.id })
        // Each run's segments point at the copy's segments, so per-segment stats keep working
        assertEquals(copy.segments.map { it.id }, to.runs.first().segments.map { it.segmentId })
    }

    @Test
    fun libraryAddKeepsWhatIsHereAndAddsTheRest() {
        val shared = run(talk, 100)
        val from = JsonStore().apply { plans = listOf(talk, toast); runs = listOf(shared, run(toast, 50)) }
        val (name, text) = app(from).exportFile(null, ExportScope.LIBRARY)
        assertEquals("Speech Split 2026-10-21.speechsplit", name)

        val here = SpeechPlan(title = "Only here", segments = listOf(SpeechSegment(title = "X", targetSeconds = 30)), createdAtEpochMs = 7)
        val to = JsonStore().apply { plans = listOf(here, talk); runs = listOf(shared) }
        val target = app(to)
        assertTrue(target.importPlan(text))
        assertNotNull(target.pendingLibrary)          // waits for add / replace
        assertEquals(2, to.plans.size)
        target.applyLibrary(replace = false)
        assertNull(target.pendingLibrary)
        assertEquals(setOf("Only here", "Tale «test»", "Toast"), to.plans.map { it.title }.toSet())
        assertTrue(to.plans.first { it.id == toast.id }.archived) // the archive comes along
        assertEquals(2, to.runs.size)                              // the shared run isn't doubled
    }

    @Test
    fun libraryReplaceMakesThisDeviceTheBackup() {
        val from = JsonStore().apply { plans = listOf(talk, toast); runs = listOf(run(talk, 100)) }
        val text = app(from).exportFile(null, ExportScope.LIBRARY).second
        val to = JsonStore().apply { plans = listOf(SpeechPlan(title = "Gone", segments = listOf(intro))) }
        val target = app(to)
        target.importPlan(text)
        target.applyLibrary(replace = true)
        assertEquals(listOf(talk.id, toast.id), to.plans.map { it.id })
        assertEquals(1, to.runs.size)
        assertEquals(AppScreen.LIBRARY, target.currentScreen)
    }

    @Test
    fun libraryKeepsFoldersAndMeetingHistory() {
        val a = JsonStore()
        val app1 = app(a)
        val f = app1.createFolder("Foredrag")!!
        a.plans = listOf(talk.copy(folderId = f.id))
        val app2 = app(a)
        val meetingRun = PracticeRun(planId = MIDWEEK_MEETING_ID, planTitle = "M", finishedAtEpochMs = 9, segments = listOf(RunSegment("x", "Sang", 300, 290)),
            live = true, meeting = MeetingKind.MIDWEEK, bufferSeconds = 300)
        a.runs = listOf(meetingRun)
        val (_, text) = app(a).exportFile(null, ExportScope.LIBRARY)
        val b = JsonStore()
        val other = app(b)
        assertTrue(other.importPlan(text))
        other.applyLibrary(replace = true)
        assertEquals(listOf("Foredrag"), b.folders.map { it.name })
        assertEquals(f.id, b.plans.single().folderId)
        val r = b.runs.single()
        assertEquals(MeetingKind.MIDWEEK, r.meeting)
        assertEquals(300, r.bufferSeconds)
        assertEquals(-310, r.diffSeconds)
        assertTrue(r.live)
    }

    @Test
    fun bufferAndSubpartsSurviveSaving() {
        val withBuffer = talk.copy(bufferSeconds = 150)
        val back = JsonCodec.plansFromJson(JsonCodec.plansToJson(listOf(withBuffer, toast))).first
        assertEquals(listOf(150, 0), back.map { it.bufferSeconds })
        // A shared export keeps it too, and an old file without it reads as "none"
        assertEquals(150, JsonCodec.readSpeechFile(JsonCodec.planFileText(withBuffer), "x")!!.plans.single().bufferSeconds)
        assertEquals(0, JsonCodec.readSpeechFile("""{"title":"Old","segments":[{"title":"A","target":60}]}""", "x")!!.plans.single().bufferSeconds)
        val subs = mapOf("2026-11-02/mw-p1" to listOf(intro, end), "weekend/we-talk" to listOf(end))
        assertEquals(subs, JsonCodec.subpartsFromJson(JsonCodec.subpartsToJson(subs)))
        assertTrue(JsonCodec.subpartsFromJson("not json").isEmpty())
        assertTrue(JsonCodec.settingsFromJson(JsonCodec.settingsToJson(AppSettings(secondScreen = true))).secondScreen)
    }

    @Test
    fun libraryBackupKeepsSubpartsAndSettings() {
        val subs = mapOf("weekend/we-talk" to listOf(intro, end))
        val settings = AppSettings(warningPercent = 20, runLive = true, meetingKind = MeetingKind.WEEKEND, tutorialSeen = true, lastSeenVersion = "3.1")
        val text = JsonCodec.libraryFileText(listOf(talk), emptyList(), emptyList(), subs, settings)
        assertFalse(text.contains("lastSeenVersion"))   // belongs to the device, not to the backup
        val file = JsonCodec.readSpeechFile(text, "x")!!
        assertEquals(subs, file.subparts)
        assertEquals(20, file.settings!!.warningPercent)
        assertTrue(file.settings!!.runLive)
        assertEquals(MeetingKind.WEEKEND, file.settings!!.meetingKind)
        // An older backup has neither
        val old = JsonCodec.readSpeechFile(JsonCodec.libraryFileText(listOf(talk), emptyList()), "x")!!
        assertTrue(old.subparts.isEmpty())
        assertNull(old.settings)
    }

    @Test
    fun meetingProgramFileReads() {
        val text = """{"format":1,"lang":"no","weeks":[{"start":"2026-11-02","reading":"Jeremia 49–50","songs":[1,44,33],
            "parts":[{"n":1,"title":"Hjelp andre","min":10},{"n":7,"title":"Glem aldri","min":15}],"livingFrom":7}],"updated":"2026-11-04"}"""
        val p = JsonCodec.meetingProgramFromJson(text)!!
        assertEquals("2026-11-04", p.updated)
        assertEquals(listOf(1, 44, 33), p.weeks.single().songs)
        assertEquals(MeetingPart(7, "Glem aldri", 15), p.weeks.single().parts[1])
        assertNull(JsonCodec.meetingProgramFromJson("not json"))
    }

    @Test
    fun isoDates() {
        assertEquals("1970-01-01", isoDate(0))
        assertEquals("2024-02-29", isoDate(1_709_164_800_000L))
        assertEquals("2026-10-21", isoDate(1_792_540_800_000L))
    }
}

class PatchNotesLanguageTest {
    private val notes = """
        <!-- en -->
        ### New
        - Export files
        ---
        <!-- no -->
        ### Nytt
        - Eksporter filer
        ---
        <!-- de -->
        ### Neu
        - Dateien exportieren
    """.trimIndent()

    @Test
    fun picksTheLanguage() {
        org.junit.Assert.assertEquals("• Eksporter filer", plainReleaseNotes(notes, "no").lines().last())
        org.junit.Assert.assertEquals("Neu\n• Dateien exportieren", plainReleaseNotes(notes, "de"))
        org.junit.Assert.assertEquals("New\n• Export files", plainReleaseNotes(notes, "fr"))
        org.junit.Assert.assertEquals("Old\n• note", plainReleaseNotes("### Old\n- note", "no"))
    }
}
