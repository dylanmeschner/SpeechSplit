package no.srrlsm.speechsplit.core

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** Sorting, archive, and saving runs, with an in-memory store and a fake clock. */
class AppControllerTest {
    private class MemoryStore(var plans: List<SpeechPlan> = emptyList()) : AppStore {
        var runs = emptyList<PracticeRun>()
        var settings = AppSettings(autoUpdateCheck = false)
        override fun loadPlans() = plans
        override fun savePlans(plans: List<SpeechPlan>) { this.plans = plans.toList() }
        override fun loadRuns() = runs
        override fun saveRuns(runs: List<PracticeRun>) { this.runs = runs }
        override fun loadSettings() = settings
        override fun saveSettings(settings: AppSettings) { this.settings = settings }
        override fun exportPlan(plan: SpeechPlan) = ""
        override fun importPlan(text: String, fallbackTitle: String): SpeechPlan? = null
        var folders = emptyList<Folder>()
        override fun loadFolders() = folders
        override fun saveFolders(folders: List<Folder>) { this.folders = folders }
    }

    private class FakePlatform : PlatformServices {
        var mono = 0L
        var wall = 1_000_000L
        var dndOn = false
        override val systemLanguage = "en"
        override fun monotonicMs() = mono
        override fun epochMs() = wall
        override fun formatDateTime(epochMs: Long) = ""
        override val canVibrate = false
        override fun vibrate(long: Boolean) {}
        override fun watchAlert(status: TimeStatus, title: String, text: String, strings: Strings) {}
        override fun hasDndAccess() = true
        override fun setDnd(on: Boolean) { dndOn = on }
    }

    private fun plan(title: String) = SpeechPlan(title = title, segments = listOf(SpeechSegment(title = "A", targetSeconds = 60)))

    @Test
    fun oldSavesGetDatesThatKeepTheirOrder() {
        val store = MemoryStore(listOf(plan("First"), plan("Second"), plan("Third")))
        val app = AppController(store, FakePlatform(), CoroutineScope(Job()))
        app.setSort(SpeechSort.CREATED)
        assertEquals(listOf("Third", "Second", "First"), app.visiblePlans.map { it.title })
        assertTrue(store.plans.all { it.createdAtEpochMs > 0 })
    }

    @Test
    fun lastUsedComesFirst() {
        val p = FakePlatform()
        val app = AppController(MemoryStore(listOf(plan("A"), plan("B"), plan("C"))), p, CoroutineScope(Job()))
        p.wall += 10_000
        app.openPlan(app.speechPlans.first { it.title == "A" })
        app.startTimer()
        app.stopTimer()
        assertEquals("A", app.visiblePlans.first().title)
        app.setSort(SpeechSort.NAME)
        assertEquals(listOf("A", "B", "C"), app.visiblePlans.map { it.title })
    }

    @Test
    fun archiveHidesAndRestoreBringsBack() {
        val store = MemoryStore(listOf(plan("A"), plan("B")))
        val app = AppController(store, FakePlatform(), CoroutineScope(Job()))
        val a = app.speechPlans.first { it.title == "A" }
        app.archivePlan(a)
        assertEquals(listOf("B"), app.visiblePlans.map { it.title })
        assertEquals(listOf("A"), app.archivedPlans.map { it.title })
        assertTrue(store.plans.first { it.id == a.id }.archived)
        app.restorePlan(a)
        assertEquals(2, app.visiblePlans.size)
    }

    @Test
    fun runIsSavedOnFinishAndUndoTakesItBack() {
        val p = FakePlatform()
        val store = MemoryStore(listOf(plan("A")))
        val app = AppController(store, p, CoroutineScope(Job()))
        app.openPlan(app.speechPlans[0])
        app.startTimer()
        p.mono = 60_000
        app.nextSegment() // FINISH
        assertEquals(1, store.runs.size)       // saved right away, before leaving the timer
        app.previousSegment()                  // undo the finish
        assertEquals(0, store.runs.size)
        p.mono = 61_000
        app.nextSegment()
        app.stopTimer()
        assertEquals(1, store.runs.size)       // not saved twice
        assertEquals(61, store.runs[0].totalActualSeconds)
    }

    @Test
    fun doNotDisturbOnlyWhileRunning() {
        val p = FakePlatform()
        val store = MemoryStore(listOf(plan("A"))).apply { settings = settings.copy(dndWhileSpeaking = true) }
        val app = AppController(store, p, CoroutineScope(Job()))
        app.openPlan(app.speechPlans[0])
        app.startTimer()
        assertTrue(p.dndOn)
        app.nextSegment()
        assertEquals(false, p.dndOn)
    }

    @Test
    fun plainTextIsNoLongerImported() {
        // 3.0: documents and speech text aren't turned into speeches any more, only Speech Split files are
        val app = AppController(MemoryStore(), FakePlatform(), CoroutineScope(Job()))
        assertEquals(false, app.importText("Intro\nHello everyone, thank you all for coming here today."))
        app.importDocument("talk.pdf", "%PDF-1.7 something".encodeToByteArray())
        assertEquals(app.strings3.importOnlyFiles, app.message)
        assertEquals(0, app.speechPlans.size)
    }

    @Test
    fun foldersHoldSpeechesAndDeletingOneKeepsThem() {
        val store = MemoryStore(listOf(plan("A"), plan("B")))
        val app = AppController(store, FakePlatform(), CoroutineScope(Job()))
        val f = app.createFolder("  Talks  ")!!
        assertEquals("Talks", f.name)
        assertEquals(null, app.createFolder("   "))
        app.moveToFolder(app.speechPlans.first { it.title == "A" }, f.id)
        assertEquals(listOf("B"), app.plansIn(null).map { it.title })
        assertEquals(listOf("A"), app.plansIn(f.id).map { it.title })
        // A new speech made inside the folder goes there
        app.openFolder(f.id)
        app.createNewPlan()
        app.saveActivePlan()
        assertEquals(2, app.plansIn(f.id).size)
        assertEquals(listOf(f), store.folders)
        app.deleteFolder(f.id)
        assertEquals(3, app.plansIn(null).size)
        assertEquals(3, app.speechPlans.size)
        assertEquals(null, app.openFolderId)
    }

    @Test
    fun liveRunsAreMarked() {
        val p = FakePlatform()
        val app = AppController(MemoryStore(listOf(plan("A"))), p, CoroutineScope(Job()))
        app.openPlan(app.speechPlans[0])
        app.setRunLive(true)
        app.startTimer()
        p.mono += 61_000
        app.nextSegment()
        assertEquals(true, app.runs.single().live)
        app.stopTimer()
        app.setRunLive(false)
        app.startTimer(); p.mono += 59_000; app.nextSegment()
        assertEquals(false, app.runs.first().live)
    }

    @Test
    fun practisingOnePartIsNotSaved() {
        val p = FakePlatform()
        val two = SpeechPlan(title = "T", segments = listOf(SpeechSegment(title = "A", targetSeconds = 60), SpeechSegment(title = "B", targetSeconds = 120)))
        val app = AppController(MemoryStore(listOf(two)), p, CoroutineScope(Job()))
        app.openPlan(app.speechPlans[0])
        app.startDrill(1)
        assertEquals(listOf("B"), app.activePlan!!.segments.map { it.title })
        p.mono += 130_000; app.nextSegment()
        app.drillAgain()
        p.mono += 115_000; app.nextSegment()
        assertEquals(listOf(130, 115), app.drill!!.attempts)
        app.previousSegment() // undo the last finish
        assertEquals(listOf(130), app.drill!!.attempts)
        app.nextSegment()
        app.stopTimer()
        assertEquals(0, app.runs.size)
        assertEquals(AppScreen.READY, app.currentScreen)
        assertEquals("T", app.activePlan!!.title)
    }

    @Test
    fun midweekMeetingHasSongsCommentsAndSpareTime() {
        val week = MeetingWeek("2026-11-02", "Jeremia 49–50", listOf(1, 44, 33), listOf(
            MeetingPart(1, "Hjelp andre", 10), MeetingPart(2, "Åndelige perler", 10), MeetingPart(3, "Bibellesning", 4),
            MeetingPart(4, "Start en samtale", 3), MeetingPart(5, "Følg opp interessen", 4), MeetingPart(6, "Led bibelstudier", 5),
            MeetingPart(7, "Glem aldri", 15), MeetingPart(8, "Menighetsbibelstudiet", 30)), livingFrom = 7)
        val m = midweekMeeting(week, NorwegianStrings3, "x")
        assertEquals("Sang 1 og bønn", m.segments.first().title)
        assertEquals("Sang 44", m.segments[8].title)
        assertEquals("7. Glem aldri", m.segments[9].title)
        assertEquals("Sang 33 og bønn", m.segments.last().title)
        assertEquals(100 * 60, m.totalTargetSeconds)
        assertEquals(105 * 60, m.totalPlannedSeconds)
        assertEquals(105 * 60, weekendMeeting(NorwegianStrings3, "y").totalPlannedSeconds)
    }

    @Test
    fun meetingRunCountsSpareTime() {
        val p = FakePlatform()
        val app = AppController(MemoryStore(), p, CoroutineScope(Job()))
        app.setMeetingKind(MeetingKind.WEEKEND)
        app.startMeeting()
        assertEquals(AppScreen.TIMER, app.currentScreen)
        for (secs in listOf(300, 1800, 300, 3600, 300)) { p.mono += secs * 1000L; app.nextSegment() }
        val run = app.runs.single()
        assertEquals(WEEKEND_MEETING_ID, run.planId)
        assertEquals(MeetingKind.WEEKEND, run.meeting)
        assertEquals(0, run.diffSeconds)
        assertEquals(EnglishStrings3.meetingEndedOnTime, app.summaryOf(run).meetingLine)
        app.stopTimer()
        assertEquals(AppScreen.MEETING, app.currentScreen)
    }

    @Test
    fun speechBufferIsUsedBeforeTheSpeechIsOver() {
        fun run(actual: Int) = PracticeRun(planId = "p", planTitle = "T", finishedAtEpochMs = 1,
            segments = listOf(RunSegment("a", "A", 600, actual)), bufferSeconds = 120)
        assertEquals(-30, run(570).diffSeconds)   // ended before the planned time: under
        assertEquals(0, run(600).diffSeconds)
        assertEquals(0, run(690).diffSeconds)     // inside the spare time: on time
        assertEquals(90, run(690).bufferUsedSeconds)
        assertEquals(0, run(720).diffSeconds)
        assertEquals(45, run(765).diffSeconds)    // only what went beyond the spare time
        assertEquals(listOf(EnglishStrings3.praiseWithinBuffer("01:30")), buildSummary(run(690), null, 0, EnglishStrings3).praise)
        // Without spare time nothing changes
        assertEquals(90, run(690).copy(bufferSeconds = 0).diffSeconds)
    }

    @Test
    fun speechBufferIsSavedWithTheRun() {
        val p = FakePlatform()
        val speech = SpeechPlan(title = "Talk", segments = listOf(SpeechSegment(title = "A", targetSeconds = 60)), bufferSeconds = 30)
        val app = AppController(MemoryStore(listOf(speech)), p, CoroutineScope(Job()))
        assertEquals(90, app.speechPlans.first().totalPlannedSeconds)
        app.openPlan(app.speechPlans.first())
        app.startTimer()
        p.mono += 75_000L
        app.nextSegment()
        val run = app.runs.single()
        assertEquals(30, run.bufferSeconds)
        assertEquals(0, run.diffSeconds)
    }

    @Test
    fun subpartsReplaceTheirPartWhenTheMeetingStarts() {
        val meeting = weekendMeeting(EnglishStrings3, "Weekend")
        val talk = meeting.segments[1] // 30 minutes
        val subs = listOf(SpeechSegment("s1", "Intro", 120), SpeechSegment("s2", "", 600))
        val run = expandSubparts(meeting) { if (it.id == talk.id) subs else emptyList() }
        assertEquals(6, run.segments.size)
        assertEquals(listOf("we-talk/s1", "we-talk/s2"), run.segments.subList(1, 3).map { it.id })
        assertEquals("Intro", run.segments[1].title)
        assertEquals(talk.title, run.segments[2].title)             // no name: the part's own
        assertEquals(talk.title + " · Intro", run.segments[1].fullTitle)
        assertEquals(30 * 60 - 120, run.segments[2].targetSeconds)  // the rest goes to the last one
        assertEquals(meeting.totalPlannedSeconds, run.totalPlannedSeconds)
        // Longer than the part: used as they are
        val long = expandSubparts(meeting) { if (it.id == talk.id) listOf(SpeechSegment("s1", "All", 31 * 60)) else emptyList() }
        assertEquals(meeting.totalPlannedSeconds + 60, long.totalPlannedSeconds)
        assertEquals("weekend/we-talk", subpartsKey(meeting.meeting, "we-talk"))
        assertEquals("2026-11-02/mw-p1", subpartsKey(MeetingInfo(MeetingKind.MIDWEEK, 300, "2026-11-02"), "mw-p1"))
        val kept = pruneSubparts(mapOf("2026-08-03/mw-p1" to subs, "2026-11-02/mw-p1" to subs, "weekend/we-talk" to subs, "2026-11-09/mw-p2" to emptyList()), "2026-09-07")
        assertEquals(setOf("2026-11-02/mw-p1", "weekend/we-talk"), kept.keys)
    }

    @Test
    fun subpartsFromASpeechRunInsideTheMeeting() {
        val p = FakePlatform()
        val speech = SpeechPlan(title = "My talk", segments = listOf(
            SpeechSegment(title = "Intro", targetSeconds = 300), SpeechSegment(title = "Main", targetSeconds = 1200)))
        val app = AppController(MemoryStore(listOf(speech)), p, CoroutineScope(Job()))
        app.setMeetingKind(MeetingKind.WEEKEND)
        val meeting = app.chosenMeeting()!!
        val talk = meeting.segments[1]
        app.subpartsFromSpeech(meeting, talk, app.speechPlans.first())
        app.addSubpart(meeting, talk)                                  // gets the 5 minutes that are left
        assertEquals(listOf(300, 1200, 300), app.subpartsOf(meeting, talk).map { it.targetSeconds })
        app.removeSubpart(meeting, talk, app.subpartsOf(meeting, talk).last().id)
        app.startMeeting()
        assertEquals(listOf("Intro", "Main"), app.activePlan!!.segments.subList(1, 3).map { it.title })
        assertEquals(1500, app.activePlan!!.segments[2].targetSeconds) // 20 min + the 5 that were left
        assertEquals(6, app.engine.segmentCount)
        app.stopTimer()
        app.setSubparts(meeting, talk, emptyList())
        assertTrue(app.meetingSubparts.isEmpty())
    }

    @Test
    fun datesAndWeeks() {
        assertEquals("2026-11-02", mondayOf("2026-11-04"))
        assertEquals("2026-11-02", mondayOf("2026-11-02"))
        assertEquals("2026-11-02", mondayOf("2026-11-08"))
        assertEquals("2026-11-09", mondayOf("2026-11-04", 1))
        assertEquals("2026-12-28", mondayOf("2027-01-03"))
        assertEquals("2.–8. november", weekLabel("2026-11-02", "no"))
        assertEquals("30. November–6. Dezember", weekLabel("2026-11-30", "de"))
        assertEquals("November 2–8", weekLabel("2026-11-02", "en"))
        assertEquals("1:45:00", formatTime(6300))
    }

    @Test
    fun summaryPraisesAndNotes() {
        val run = PracticeRun(planId = "p", planTitle = "T", finishedAtEpochMs = 2, segments = listOf(
            RunSegment("a", "Intro", 60, 60), RunSegment("b", "Main", 300, 380), RunSegment("c", "End", 60, 58)))
        val prev = run.copy(id = "old", finishedAtEpochMs = 1, segments = run.segments.map { it.copy(actualSeconds = it.targetSeconds + 60) })
        val sm = buildSummary(run, prev, 0, EnglishStrings3)
        assertTrue(sm.praise.contains("2 of 3 parts were on time."))
        assertTrue(sm.praise.contains("“Intro” hit its time to the second."))
        assertEquals(listOf("“Main” ran 01:20 over time."), sm.notes)
        assertEquals("Last time +03:00 · this time +01:18", sm.compare)
        assertEquals(Verdict.BIT_OVER, verdict(14, 60))
        assertEquals(Verdict.ON_TIME, verdict(-5, 60))
        assertEquals(Verdict.UNDER, verdict(-100, 300))
    }

    @Test
    fun firstStartShowsTheIntroductionAnUpdateShowsWhatsNew() {
        val fresh = AppController(MemoryStore(), FakePlatform(), CoroutineScope(Job()))
        assertTrue(fresh.showTutorial)
        assertEquals(false, fresh.showWhatsNew)
        val old = AppController(MemoryStore(listOf(plan("A"))), FakePlatform(), CoroutineScope(Job()))
        assertEquals(false, old.showTutorial)
        assertTrue(old.showWhatsNew)
        assertTrue(old.settings.tutorialSeen)
    }

    @Test
    fun addSegmentGoesAfterTheGivenOne() {
        val app = AppController(MemoryStore(listOf(plan("A"))), FakePlatform(), CoroutineScope(Job()))
        app.editPlan(app.speechPlans.first())
        val first = app.addSegment()          // at the end
        val middle = app.addSegment(0)        // right after the first
        assertEquals(listOf(1, 2), listOf(middle, first).map { id -> app.activePlan!!.segments.indexOfFirst { it.id == id } })
    }

    @Test
    fun leavingTheEditorWithChangesAsksFirst() {
        val app = AppController(MemoryStore(listOf(plan("A"), plan("B"))), FakePlatform(), CoroutineScope(Job()))
        val b = app.speechPlans.first { it.title == "B" }
        app.editPlan(app.speechPlans.first { it.title == "A" })
        app.guardEdits { app.openPlan(b) }                 // nothing changed yet: goes right away
        assertEquals(AppScreen.READY, app.currentScreen)
        app.editActivePlan()
        app.updateActivePlan { it.copy(title = "B2") }
        app.guardEdits { app.backToLibrary() }
        assertEquals(AppScreen.EDIT, app.currentScreen)    // waits for an answer
        assertTrue(app.pendingLeave != null)
        app.cancelLeave()
        assertEquals(AppScreen.EDIT, app.currentScreen)
        app.guardEdits { app.backToLibrary() }
        app.confirmLeave()
        assertEquals(AppScreen.LIBRARY, app.currentScreen)
        assertEquals("B", app.speechPlans.first { it.id == b.id }.title) // not saved
    }

    @Test
    fun speechFileNamesAreSafe() {
        assertEquals("Wedding toast.speechsplit", speechFileName("Wedding toast"))
        assertEquals("A B C.speechsplit", speechFileName("A/B:C?"))
        assertEquals("Speech.speechsplit", speechFileName("  ...  "))
        assertEquals("Tale for Ørjan.speechsplit", speechFileName("Tale for Ørjan"))
    }
}
