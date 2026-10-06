package no.srrlsm.speechsplit.core

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlin.math.abs

enum class AppScreen { LIBRARY, SETTINGS, EDIT, READY, TIMER, HISTORY }

/** A one-off signal for the UI (e.g. flash the screen edge). A new id means a new event. */
data class AlertEvent(val id: Int, val status: TimeStatus)

/**
 * All app state and logic, independent of Android.
 * On Android it lives inside a ViewModel (see MainActivity.kt); a Windows or iPad version
 * would create it the same way with its own [AppStore] and [PlatformServices].
 */
class AppController(
    private val store: AppStore,
    private val platform: PlatformServices,
    private val scope: CoroutineScope,
) {
    var currentScreen by mutableStateOf(AppScreen.LIBRARY)
        private set

    val speechPlans = mutableStateListOf<SpeechPlan>().apply { addAll(store.loadPlans()) }

    /** All saved practice runs, newest first. */
    var runs by mutableStateOf(store.loadRuns().sortedByDescending { it.finishedAtEpochMs })
        private set

    /** The plan currently being edited, prepared or timed. */
    var activePlan by mutableStateOf<SpeechPlan?>(null)
        private set

    /** A quick timer isn't saved and isn't added to the history. */
    var isQuickTimer by mutableStateOf(false)
        private set

    /** A short message for the UI to show once (toast), then call [messageShown]. */
    var message by mutableStateOf<String?>(null)
        private set

    fun messageShown() {
        message = null
    }

    init {
        // If the app was closed while it had Do Not Disturb on, switch it back off.
        platform.setDnd(false)
    }

    // --- Settings -------------------------------------------------------------
    var settings by mutableStateOf(store.loadSettings())
        private set

    val strings: Strings get() = stringsFor(settings.language, platform.systemLanguage)

    val canVibrate: Boolean get() = platform.canVibrate
    val supportsWatchAlerts: Boolean get() = platform.supportsWatchAlerts
    val supportsDnd: Boolean get() = platform.supportsDnd
    val hasKeyboardShortcuts: Boolean get() = platform.hasKeyboardShortcuts

    fun updateSettings(transform: (AppSettings) -> AppSettings) {
        settings = transform(settings)
        store.saveSettings(settings)
        syncDnd()
    }

    fun testVibration() = platform.vibrate(long = false)

    fun hasDndAccess(): Boolean = platform.hasDndAccess()

    fun formatDate(epochMs: Long): String = platform.formatDateTime(epochMs)

    /** True while the screen should stay on (only while timing). */
    val keepScreenOn: Boolean get() = currentScreen == AppScreen.TIMER && settings.keepScreenOn

    // --- Navigation -----------------------------------------------------------
    // The plan as it was when editing started, and where "back" from the editor leads.
    private var editOriginal: SpeechPlan? = null
    private var editReturnScreen = AppScreen.LIBRARY

    val hasUnsavedEdits: Boolean
        get() = currentScreen == AppScreen.EDIT && activePlan != editOriginal

    fun createNewPlan() {
        startEditing(
            SpeechPlan(
                title = strings.defaultSpeechTitle,
                segments = listOf(SpeechSegment(title = strings.defaultFirstSegment, targetSeconds = 120)),
            ),
            returnTo = AppScreen.LIBRARY,
        )
    }

    fun openPlan(plan: SpeechPlan) {
        isQuickTimer = false
        activePlan = plan
        currentScreen = AppScreen.READY
    }

    fun editPlan(plan: SpeechPlan) = startEditing(plan, returnTo = AppScreen.LIBRARY)

    fun editActivePlan() {
        activePlan?.let { startEditing(it, returnTo = AppScreen.READY) }
    }

    private fun startEditing(plan: SpeechPlan, returnTo: AppScreen) {
        isQuickTimer = false
        activePlan = plan
        editOriginal = plan
        editReturnScreen = returnTo
        currentScreen = AppScreen.EDIT
    }

    /** Leaves the editor without saving: back to the Ready screen (old version) or the library. */
    fun cancelEditing() {
        val original = editOriginal
        editOriginal = null
        if (editReturnScreen == AppScreen.READY && original != null) {
            activePlan = original
            currentScreen = AppScreen.READY
        } else {
            backToLibrary()
        }
    }

    fun backToLibrary() {
        activePlan = null
        isQuickTimer = false
        currentScreen = AppScreen.LIBRARY
    }

    fun openSettings() {
        activePlan = null
        isQuickTimer = false
        currentScreen = AppScreen.SETTINGS
    }

    fun openHistory() {
        if (activePlan != null) currentScreen = AppScreen.HISTORY
    }

    fun closeHistory() {
        currentScreen = if (activePlan != null) AppScreen.READY else AppScreen.LIBRARY
    }

    // --- Editing --------------------------------------------------------------
    fun updateActivePlan(transform: (SpeechPlan) -> SpeechPlan) {
        activePlan = activePlan?.let(transform)
    }

    fun updateSegment(id: String, transform: (SpeechSegment) -> SpeechSegment) = updateActivePlan { p ->
        p.copy(segments = p.segments.map { if (it.id == id) transform(it) else it })
    }

    fun addSegment() = updateActivePlan {
        it.copy(segments = it.segments + SpeechSegment(title = strings.defaultNewSegment, targetSeconds = 120))
    }

    fun removeSegment(id: String) = updateActivePlan { p ->
        p.copy(segments = p.segments.filterNot { it.id == id })
    }

    fun moveSegment(from: Int, to: Int) = updateActivePlan { p ->
        if (from !in p.segments.indices || to !in p.segments.indices) p
        else p.copy(segments = p.segments.toMutableList().apply { add(to, removeAt(from)) })
    }

    fun saveActivePlan() {
        val plan = activePlan ?: return
        val cleaned = plan.copy(title = plan.title.trim().ifBlank { strings.untitledSpeech })
        val i = speechPlans.indexOfFirst { it.id == cleaned.id }
        if (i >= 0) speechPlans[i] = cleaned else speechPlans.add(cleaned)
        store.savePlans(speechPlans)
        activePlan = cleaned
        editOriginal = null
        currentScreen = AppScreen.READY
    }

    fun deletePlan(plan: SpeechPlan) {
        speechPlans.removeAll { it.id == plan.id }
        store.savePlans(speechPlans)
        clearHistory(plan.id)
    }

    fun exportPlan(plan: SpeechPlan): String = store.exportPlan(plan)

    fun importPlan(text: String): Boolean {
        val plan = store.importPlan(text, strings.importedSpeech) ?: return false
        if (plan.segments.isEmpty()) return false
        // Fresh ids, so importing the same speech twice gives two independent copies
        speechPlans.add(plan.copy(id = newId(), segments = plan.segments.map { it.copy(id = newId()) }))
        store.savePlans(speechPlans)
        return true
    }

    // --- History --------------------------------------------------------------
    fun runsFor(planId: String): List<PracticeRun> = runs.filter { it.planId == planId }

    fun deleteRun(runId: String) {
        runs = runs.filterNot { it.id == runId }
        store.saveRuns(runs)
    }

    fun clearHistory(planId: String) {
        if (runs.none { it.planId == planId }) return
        runs = runs.filterNot { it.planId == planId }
        store.saveRuns(runs)
    }

    /** The run as it stands now (for the finished screen's "Share report"). */
    fun currentRunSnapshot(): PracticeRun? {
        val plan = activePlan ?: return null
        if (!engine.isActive) return null
        val secs = engine.allElapsedSeconds()
        return PracticeRun(
            planId = plan.id,
            planTitle = plan.title,
            finishedAtEpochMs = platform.epochMs(),
            segments = plan.segments.mapIndexed { i, seg ->
                RunSegment(seg.id, seg.title, seg.targetSeconds, secs.getOrElse(i) { 0 })
            },
        )
    }

    private fun recordRun() {
        if (isQuickTimer || !engine.isFinished) return
        val run = currentRunSnapshot() ?: return
        val forPlan = (listOf(run) + runsFor(run.planId)).take(MaxRunsPerPlan)
        runs = (forPlan + runs.filterNot { it.planId == run.planId }).sortedByDescending { it.finishedAtEpochMs }
        store.saveRuns(runs)
    }

    // --- Timer ----------------------------------------------------------------
    val engine = TimerEngine(platform::monotonicMs)

    /** True right after finishing on target. Lives here so a screen rotation/resize doesn't replay it. */
    var celebrating by mutableStateOf(false)
        private set

    fun celebrationDone() {
        celebrating = false
    }

    /** Latest warning/overtime alert, for the screen-edge flash. */
    var alertEvent by mutableStateOf<AlertEvent?>(null)
        private set

    private val alerted = mutableSetOf<Pair<Int, TimeStatus>>()
    private var ticker: Job? = null

    /** Total spoken time minus total target, the same number the TOTAL clock shows. */
    fun finalDiffSeconds(plan: SpeechPlan): Int = engine.totalElapsedSeconds() - plan.totalTargetSeconds

    /** Whether the finished run landed on the target (within the tolerance chosen in Settings). */
    fun isPerfectFinish(): Boolean {
        val plan = activePlan ?: return false
        val tolerance = settings.celebrateTolerance
        return engine.isFinished && tolerance >= 0 && abs(finalDiffSeconds(plan)) <= tolerance
    }

    fun startTimer() {
        val plan = activePlan ?: return
        if (plan.segments.isEmpty()) return
        alerted.clear()
        celebrating = false
        dndWarned = false
        engine.start(plan.segments.size)
        currentScreen = AppScreen.TIMER
        afterChange()
    }

    /** A one-off countdown that isn't saved as a speech. */
    fun startQuickTimer(minutes: Int) {
        if (minutes <= 0) return
        activePlan = SpeechPlan(
            title = strings.quickTimerName(minutes),
            segments = listOf(SpeechSegment(title = strings.quickTimer, targetSeconds = minutes * 60)),
        )
        isQuickTimer = true
        startTimer()
    }

    fun togglePause() {
        engine.togglePause()
        afterChange()
    }

    fun nextSegment() {
        if (engine.next()) celebrating = isPerfectFinish()
        afterChange()
    }

    /** Undo an accidental NEXT (or FINISH). */
    fun previousSegment() {
        celebrating = false
        engine.previous()
        afterChange()
    }

    /** Leaves the timer. A finished run is saved to the history first. */
    fun stopTimer() {
        recordRun()
        engine.stop()
        celebrating = false
        afterChange()
        if (isQuickTimer) backToLibrary() else currentScreen = AppScreen.READY
    }

    /** Call when the app is closed for good. */
    fun dispose() {
        ticker?.cancel()
        if (dndOnByUs) platform.setDnd(false)
    }

    private fun afterChange() {
        if (engine.isRunning) startTicking() else stopTicking()
        syncDnd()
    }

    private fun startTicking() {
        if (ticker?.isActive == true) return
        ticker = scope.launch {
            while (isActive) {
                engine.tick()
                checkAlerts()
                // Wake up right when the next second ticks over, so the clock flips exactly on time
                delay(minOf(100L, engine.msToNextSecond() + 5))
            }
        }
    }

    private fun stopTicking() {
        ticker?.cancel()
        ticker = null
        engine.tick()
    }

    /** Vibration, watch buzz and screen flash, once per segment and level. */
    private fun checkAlerts() {
        if (!engine.isRunning) return
        val plan = activePlan ?: return
        val i = engine.currentIndex
        val seg = plan.segments.getOrNull(i) ?: return
        val remaining = seg.targetSeconds - engine.elapsedSeconds(i)
        val status = timeStatus(remaining, seg.targetSeconds, settings.warningPercent)
        if (status == TimeStatus.ON_TRACK) return
        if (!alerted.add(i to status)) return

        if (settings.vibrateAlerts) platform.vibrate(long = status == TimeStatus.OVER)
        if (settings.watchAlerts) {
            val s = strings
            if (status == TimeStatus.OVER) {
                platform.watchAlert(status, s.notifOverTitle(seg.title), s.notifOverText, s)
            } else {
                platform.watchAlert(status, s.notifWarningTitle(seg.title), s.notifWarningText(formatTime(remaining)), s)
            }
        }
        if (settings.flashAlerts) alertEvent = AlertEvent((alertEvent?.id ?: 0) + 1, status)
    }

    // --- Do Not Disturb -------------------------------------------------------
    private var dndOnByUs = false
    private var dndWarned = false

    /** DND is on while a run is in progress (not before, not after finishing). */
    private fun syncDnd() {
        val want = settings.dndWhileSpeaking && engine.isActive && !engine.isFinished
        if (want && !dndOnByUs) {
            if (platform.hasDndAccess()) {
                platform.setDnd(true)
                dndOnByUs = true
            } else if (!dndWarned) {
                dndWarned = true
                message = strings.dndMissing
            }
        } else if (!want && dndOnByUs) {
            platform.setDnd(false)
            dndOnByUs = false
        }
    }
}
