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
    fun pastedSpeechTextBecomesADraft() {
        val app = AppController(MemoryStore(), FakePlatform(), CoroutineScope(Job()))
        val ok = app.importText("Intro\nHello everyone, thank you all for coming here today.\n\nMain\nThis is the main point of the talk and it matters.")
        assertTrue(ok)
        val draft = app.importDraft!!
        val s = suggestSegments(draft, 130, null, null, "Intro") { "Part $it" }
        assertEquals(listOf("Intro", "Main"), s.segments.map { it.title })
        app.acceptImport(draft.title, s.segments, 130)
        assertEquals(AppScreen.EDIT, app.currentScreen)
        app.saveActivePlan()
        assertEquals(1, app.visiblePlans.size)
        assertTrue(app.visiblePlans[0].createdAtEpochMs > 0)
    }
}
