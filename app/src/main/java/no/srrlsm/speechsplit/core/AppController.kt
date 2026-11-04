package no.srrlsm.speechsplit.core

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.math.abs

enum class AppScreen { LIBRARY, ARCHIVE, SETTINGS, EDIT, READY, TIMER, HISTORY, MEETING }

/** "Practise one part": which speech and part, and the time of every attempt so far. */
data class DrillState(val planId: String, val index: Int, val attempts: List<Int> = emptyList())

/** Loading state of one language's meeting programs. */
enum class MeetingLoad { IDLE, LOADING, OK, FAILED, MISSING }

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

    /** All saved speeches, archived ones included, in the order they were added. */
    val speechPlans = mutableStateListOf<SpeechPlan>().apply { addAll(store.loadPlans()) }

    /** All saved practice runs, newest first. */
    var runs by mutableStateOf(store.loadRuns().sortedByDescending { it.finishedAtEpochMs })
        private set

    init {
        // Speeches saved before 2.2 have no dates. Give them dates that keep their old order,
        // and take "last used" from their practice history.
        if (speechPlans.any { it.createdAtEpochMs == 0L }) {
            val now = platform.epochMs()
            val lastRun = runs.groupBy { it.planId }.mapValues { (_, r) -> r.maxOf { it.finishedAtEpochMs } }
            for (i in speechPlans.indices) {
                val p = speechPlans[i]
                if (p.createdAtEpochMs == 0L) {
                    speechPlans[i] = p.copy(
                        createdAtEpochMs = now - (speechPlans.size - i) * 1000L,
                        lastUsedAtEpochMs = maxOf(p.lastUsedAtEpochMs, lastRun[p.id] ?: 0L),
                    )
                }
            }
            store.savePlans(speechPlans)
        }
    }

    /** The speech list as shown: not archived, in the order chosen in [AppSettings.speechSort]. */
    val visiblePlans: List<SpeechPlan>
        get() = sortPlans(speechPlans.filterNot { it.archived })

    // --- Folders --------------------------------------------------------------
    var folders by mutableStateOf(store.loadFolders())
        private set

    /** The folder open in the library, or null for the library itself. */
    var openFolderId by mutableStateOf<String?>(null)
        private set

    val openFolder: Folder? get() = folderById(openFolderId)

    fun folderById(id: String?): Folder? = if (id == null) null else folders.firstOrNull { it.id == id }

    val sortedFolders: List<Folder>
        get() = folders.sortedWith(compareBy(String.CASE_INSENSITIVE_ORDER) { it.name.trim() })

    /** Speeches in a folder, or (null) those in none. A speech whose folder is gone counts as in none. */
    fun plansIn(folderId: String?): List<SpeechPlan> =
        visiblePlans.filter { if (folderId != null) it.folderId == folderId else folderById(it.folderId) == null }

    fun openFolder(id: String?) {
        guardEdits {
            openFolderId = folderById(id)?.id
            backToLibrary()
        }
    }

    fun createFolder(name: String): Folder? {
        val clean = name.trim().take(60)
        if (clean.isBlank()) return null
        val folder = Folder(name = clean, createdAtEpochMs = platform.epochMs())
        folders = folders + folder
        store.saveFolders(folders)
        return folder
    }

    fun renameFolder(id: String, name: String) {
        val clean = name.trim().take(60)
        if (clean.isBlank()) return
        folders = folders.map { if (it.id == id) it.copy(name = clean) else it }
        store.saveFolders(folders)
    }

    /** The speeches in it go back to the library; nothing is deleted. */
    fun deleteFolder(id: String) {
        folders = folders.filterNot { it.id == id }
        store.saveFolders(folders)
        for (i in speechPlans.indices) {
            if (speechPlans[i].folderId == id) speechPlans[i] = speechPlans[i].copy(folderId = null)
        }
        store.savePlans(speechPlans)
        if (openFolderId == id) openFolderId = null
    }

    fun moveToFolder(plan: SpeechPlan, folderId: String?) {
        val target = folderById(folderId)
        replacePlan(plan.id) { it.copy(folderId = target?.id) }
        message = if (target != null) strings3.movedTo(target.name) else strings3.movedOut
    }

    val archivedPlans: List<SpeechPlan>
        get() = sortPlans(speechPlans.filter { it.archived })

    private fun sortPlans(plans: List<SpeechPlan>): List<SpeechPlan> = when (settings.speechSort) {
        SpeechSort.LAST_USED -> plans.sortedByDescending { it.recentActivityEpochMs }
        SpeechSort.CREATED -> plans.sortedByDescending { it.createdAtEpochMs }
        SpeechSort.NAME -> plans.sortedWith(compareBy(String.CASE_INSENSITIVE_ORDER) { it.title.trim() })
    }

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

    /** Texts added in 3.0 (Strings3.kt). */
    val strings3: Strings3 get() = strings3For(settings.language, platform.systemLanguage)

    /** "en", "de" or "no": which part of the patch notes to show. */
    val languageCode: String
        get() = when (settings.language.resolve(platform.systemLanguage)) {
            AppLanguage.GERMAN -> "de"
            AppLanguage.NORWEGIAN -> "no"
            else -> "en"
        }

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

    /**
     * Something the user asked for that would leave the editor with unsaved changes
     * (wide layout: the speech list stays visible next to the editor). The UI asks first.
     */
    var pendingLeave by mutableStateOf<(() -> Unit)?>(null)
        private set

    /** Runs [action] now, or after the user agrees to discard unsaved edits. */
    fun guardEdits(action: () -> Unit) {
        if (hasUnsavedEdits) pendingLeave = action else action()
    }

    fun confirmLeave() {
        val action = pendingLeave ?: return
        pendingLeave = null
        editOriginal = null
        action()
    }

    fun cancelLeave() {
        pendingLeave = null
    }

    fun setSort(sort: SpeechSort) = updateSettings { it.copy(speechSort = sort) }

    fun createNewPlan() {
        startEditing(
            SpeechPlan(
                title = strings.defaultSpeechTitle,
                segments = listOf(SpeechSegment(title = strings.defaultFirstSegment, targetSeconds = 120)),
                // Made inside a folder: it goes there
                folderId = openFolder?.id,
            ),
            returnTo = AppScreen.LIBRARY,
        )
    }

    fun openPlan(plan: SpeechPlan) {
        isQuickTimer = false
        drill = null
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
        drill = null
        currentScreen = AppScreen.LIBRARY
    }

    /** The Speeches tab: back to the library itself, out of any folder. */
    fun libraryTab() {
        openFolderId = null
        backToLibrary()
    }

    fun openArchive() {
        activePlan = null
        isQuickTimer = false
        currentScreen = AppScreen.ARCHIVE
    }

    fun openSettings() {
        activePlan = null
        isQuickTimer = false
        currentScreen = AppScreen.SETTINGS
    }

    fun openHistory() {
        historyFilter = null
        if (activePlan != null) currentScreen = AppScreen.HISTORY
    }

    fun closeHistory() {
        currentScreen = when {
            activePlan?.meeting != null -> { activePlan = null; AppScreen.MEETING }
            activePlan != null -> AppScreen.READY
            else -> AppScreen.LIBRARY
        }
    }

    /** History screen filter: all runs, only practice, or only real speeches. */
    var historyFilter by mutableStateOf<Boolean?>(null) // null all, false practice, true live
        private set

    fun filterHistory(live: Boolean?) {
        historyFilter = live
    }

    // --- Editing --------------------------------------------------------------
    fun updateActivePlan(transform: (SpeechPlan) -> SpeechPlan) {
        activePlan = activePlan?.let(transform)
    }

    fun updateSegment(id: String, transform: (SpeechSegment) -> SpeechSegment) = updateActivePlan { p ->
        p.copy(segments = p.segments.map { if (it.id == id) transform(it) else it })
    }

    /** Adds a segment after [afterIndex] (default: at the end) and returns its id, so the editor can focus it. */
    fun addSegment(afterIndex: Int? = null): String {
        val seg = SpeechSegment(title = strings.defaultNewSegment, targetSeconds = 120)
        updateActivePlan { p ->
            val at = ((afterIndex ?: p.segments.lastIndex) + 1).coerceIn(0, p.segments.size)
            p.copy(segments = p.segments.toMutableList().apply { add(at, seg) })
        }
        return seg.id
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
        val cleaned = plan.copy(
            title = plan.title.trim().ifBlank { strings.untitledSpeech },
            createdAtEpochMs = plan.createdAtEpochMs.takeIf { it > 0 } ?: platform.epochMs(),
        )
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

    /** Hides a speech from the list. Its history is kept, and it can be restored from the archive. */
    fun archivePlan(plan: SpeechPlan) {
        replacePlan(plan.id) { it.copy(archived = true) }
        // Wide layout: the archived speech may be open next to the list
        if (activePlan?.id == plan.id && (currentScreen == AppScreen.READY || currentScreen == AppScreen.HISTORY)) backToLibrary()
    }

    fun restorePlan(plan: SpeechPlan) = replacePlan(plan.id) { it.copy(archived = false) }

    private fun replacePlan(id: String, transform: (SpeechPlan) -> SpeechPlan) {
        val i = speechPlans.indexOfFirst { it.id == id }
        if (i < 0) return
        speechPlans[i] = transform(speechPlans[i])
        store.savePlans(speechPlans)
        if (activePlan?.id == id && currentScreen != AppScreen.EDIT) activePlan = speechPlans[i]
    }

    fun exportPlan(plan: SpeechPlan): String = store.exportPlan(plan)

    /**
     * File name and contents for "Export": one speech, one speech with its practice history,
     * or the whole library (all speeches, the archive and all history). [plan] may be null for LIBRARY.
     */
    fun exportFile(plan: SpeechPlan?, scope: ExportScope): Pair<String, String> = when {
        scope == ExportScope.LIBRARY || plan == null ->
            "Speech Split ${isoDate(platform.epochMs())}.$SPEECH_FILE_EXTENSION" to
                store.exportLibraryFile(speechPlans.toList(), runs, folders)
        scope == ExportScope.SPEECH_WITH_HISTORY -> speechFileName(plan.title) to store.exportPlanFile(plan, runsFor(plan.id))
        else -> speechFileName(plan.title) to store.exportPlanFile(plan)
    }

    /**
     * Imports a Speech Split file or export text (any version, any platform). A single speech is
     * added right away (with its history, if the file has it). A library backup waits in
     * [pendingLibrary] until the user picks "add" or "replace". Returns false if it isn't one.
     */
    fun importPlan(text: String): Boolean {
        val file = store.readSpeechFile(text, strings.importedSpeech) ?: return false
        if (file.isLibrary) {
            if (file.plans.isEmpty()) return false
            pendingLibrary = file
            return true
        }
        val plan = file.plans.firstOrNull()?.takeIf { it.segments.isNotEmpty() } ?: return false
        addCopy(plan, file.runs)
        message = strings.imported
        return true
    }

    /** Fresh ids, so importing the same speech twice gives two independent copies. History follows along. */
    private fun addCopy(plan: SpeechPlan, history: List<PracticeRun>) {
        val segIds = plan.segments.associate { it.id to newId() }
        val copy = plan.copy(
            id = newId(),
            segments = plan.segments.map { it.copy(id = segIds[it.id] ?: newId()) },
            createdAtEpochMs = platform.epochMs(),
            lastUsedAtEpochMs = history.maxOfOrNull { it.finishedAtEpochMs } ?: 0L,
            archived = false,
            folderId = null,
        )
        speechPlans.add(copy)
        store.savePlans(speechPlans)
        if (history.isNotEmpty()) {
            val moved = history.map { r ->
                r.copy(
                    id = newId(),
                    planId = copy.id,
                    planTitle = copy.title,
                    segments = r.segments.map { s -> s.copy(segmentId = segIds[s.segmentId] ?: s.segmentId) },
                )
            }
            runs = capRuns(runs + moved)
            store.saveRuns(runs)
        }
    }

    /** A library backup that was opened, waiting for "add" or "replace". */
    var pendingLibrary by mutableStateOf<SpeechFile?>(null)
        private set

    fun dismissLibrary() {
        pendingLibrary = null
    }

    /**
     * Add: speeches that aren't on this device yet are added with their history, plus any runs of
     * shared speeches this device doesn't have. Nothing here is changed or removed.
     * Replace: everything here (speeches, archive, history) becomes exactly the backup.
     */
    fun applyLibrary(replace: Boolean) {
        val file = pendingLibrary ?: return
        pendingLibrary = null
        val count: Int
        // Meeting history isn't tied to a speech, so it travels with the library too
        fun isMeeting(r: PracticeRun) = r.planId == MIDWEEK_MEETING_ID || r.planId == WEEKEND_MEETING_ID
        if (replace) {
            openFolderId = null
            backToLibrary()
            speechPlans.clear()
            speechPlans.addAll(file.plans)
            folders = file.folders
            val ids = file.plans.map { it.id }.toSet()
            runs = capRuns(file.runs.filter { it.planId in ids || isMeeting(it) })
            count = file.plans.size
        } else {
            val have = speechPlans.map { it.id }.toSet()
            val added = file.plans.filter { it.id !in have }
            speechPlans.addAll(added)
            val haveFolders = folders.map { it.id }.toSet()
            folders = folders + file.folders.filter { it.id !in haveFolders }
            val ids = speechPlans.map { it.id }.toSet()
            val haveRuns = runs.map { it.id }.toSet()
            runs = capRuns(runs + file.runs.filter { it.id !in haveRuns && (it.planId in ids || isMeeting(it)) })
            count = added.size
        }
        store.saveFolders(folders)
        store.savePlans(speechPlans)
        store.saveRuns(runs)
        message = strings.importedCount(count)
    }

    /** Newest first, at most [MaxRunsPerPlan] per speech. */
    private fun capRuns(list: List<PracticeRun>): List<PracticeRun> =
        list.distinctBy { it.id }
            .sortedByDescending { it.finishedAtEpochMs }
            .groupBy { it.planId }.values.flatMap { it.take(MaxRunsPerPlan) }
            .sortedByDescending { it.finishedAtEpochMs }

    // --- Importing a file or pasted text ------------------------------------------------
    // Only Speech Split files and exported text. (Reading PDF and Word documents was removed in 3.0:
    // everyone writes their notes differently, so the suggested parts were rarely right.)

    /** Pasted or shared text: an exported speech or library backup. False if it isn't one. */
    fun importText(text: String): Boolean = importPlan(text.removePrefix("\uFEFF").trim())

    /** A file dropped on the app, picked, or shared to it. */
    fun importDocument(fileName: String, bytes: ByteArray) {
        val text = bytes.takeIf { it.size <= MAX_IMPORT_BYTES }?.decodeToString()?.removePrefix("\uFEFF")?.trim()
        if (text == null || !text.startsWith("{") || !importPlan(text)) message = strings3.importOnlyFiles
    }

    // --- History --------------------------------------------------------------
    fun runsFor(planId: String): List<PracticeRun> = runs.filter { it.planId == planId }

    fun deleteRun(runId: String) {
        runs = runs.filterNot { it.id == runId }
        store.saveRuns(runs)
    }

    /** All runs of a speech that is deleted for good. */
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
            live = plan.meeting != null || settings.runLive,
            meeting = plan.meeting?.kind,
            bufferSeconds = plan.meeting?.bufferSeconds ?: 0,
        )
    }

    /** The run just finished (as saved), for the summary on the finished screen. */
    fun finishedRun(): PracticeRun? = recordedRunId?.let { id -> runs.firstOrNull { it.id == id } } ?: currentRunSnapshot()

    /** The run before [run] of the same speech or meeting, to compare with. */
    fun previousRun(run: PracticeRun): PracticeRun? =
        runsFor(run.planId).firstOrNull { it.id != run.id && it.finishedAtEpochMs < run.finishedAtEpochMs }

    fun summaryOf(run: PracticeRun): RunSummary =
        buildSummary(run, previousRun(run), settings.celebrateTolerance, strings3)

    /** Id of the run saved when FINISH was pressed, so an "undo" can take it back out. */
    private var recordedRunId: String? = null

    /**
     * Saved the moment FINISH is pressed (before, it was only saved when leaving the timer,
     * so closing the app on the finished screen lost the run).
     */
    private fun recordRun() {
        if (isQuickTimer || activePlan?.isDrill == true || !engine.isFinished || recordedRunId != null) return
        val run = currentRunSnapshot() ?: return
        recordedRunId = run.id
        val forPlan = (listOf(run) + runsFor(run.planId)).take(MaxRunsPerPlan)
        runs = (forPlan + runs.filterNot { it.planId == run.planId }).sortedByDescending { it.finishedAtEpochMs }
        store.saveRuns(runs)
    }

    private fun unrecordRun() {
        val id = recordedRunId ?: return
        recordedRunId = null
        deleteRun(id)
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
        if (plan.meeting != null) return false // a meeting is about ending in time, not on the second
        val tolerance = settings.celebrateTolerance
        return engine.isFinished && tolerance >= 0 && abs(finalDiffSeconds(plan)) <= tolerance
    }

    fun startTimer() {
        val plan = activePlan ?: return
        if (plan.segments.isEmpty()) return
        alerted.clear()
        celebrating = false
        dndWarned = false
        recordedRunId = null
        if (!isQuickTimer && plan.meeting == null && !plan.isDrill) {
            // For "last used" sorting
            replacePlan(plan.id) { it.copy(lastUsedAtEpochMs = platform.epochMs()) }
        }
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
        if (engine.next()) {
            celebrating = isPerfectFinish()
            recordRun()
            drill?.let { d -> if (activePlan?.isDrill == true) drill = d.copy(attempts = d.attempts + engine.totalElapsedSeconds()) }
        }
        afterChange()
    }

    /** Undo an accidental NEXT (or FINISH). */
    fun previousSegment() {
        if (!engine.isActive) return
        celebrating = false
        if (engine.isFinished) {
            unrecordRun()
            drill?.let { d -> if (activePlan?.isDrill == true) drill = d.copy(attempts = d.attempts.dropLast(1)) }
        }
        engine.previous()
        afterChange()
    }

    /** Leaves the timer. A finished run is already in the history. */
    fun stopTimer() {
        recordRun()
        recordedRunId = null
        engine.stop()
        celebrating = false
        afterChange()
        val plan = activePlan
        when {
            isQuickTimer -> backToLibrary()
            plan?.meeting != null -> { activePlan = null; currentScreen = AppScreen.MEETING }
            plan?.isDrill == true -> {
                val base = drill?.let { d -> speechPlans.firstOrNull { it.id == d.planId } }
                drill = null
                if (base != null) { activePlan = base; currentScreen = AppScreen.READY } else backToLibrary()
            }
            else -> currentScreen = AppScreen.READY
        }
    }

    // --- Practice: real speech or practice, and one part again and again ----------------
    fun setRunLive(live: Boolean) = updateSettings { it.copy(runLive = live) }

    /** Practising one part: which one, and the attempts so far. Null otherwise. */
    var drill by mutableStateOf<DrillState?>(null)
        private set

    /** Starts a one-segment run of part [index] of the open speech. Not saved in the history. */
    fun startDrill(index: Int) {
        val base = activePlan?.takeIf { !it.isDrill && it.meeting == null } ?: return
        startDrillOf(base, index, emptyList())
    }

    /** Another attempt at the same part. */
    fun drillAgain() {
        val d = drill ?: return
        val base = speechPlans.firstOrNull { it.id == d.planId } ?: return
        engine.stop()
        startDrillOf(base, d.index, d.attempts)
    }

    private fun startDrillOf(base: SpeechPlan, index: Int, attempts: List<Int>) {
        val seg = base.segments.getOrNull(index) ?: return
        drill = DrillState(base.id, index, attempts)
        activePlan = SpeechPlan(
            id = "drill",
            title = "${base.title} · ${strings3.drillTitle}",
            segments = listOf(seg.copy()),
            isDrill = true,
        )
        isQuickTimer = false
        startTimer()
    }

    // --- Meeting mode ------------------------------------------------------------------
    // The week's program comes from the website (docs/meetings/<lang>.json, made from the
    // meeting workbook with tools/workbook_to_json.py). The last copy is kept, so it works offline.

    /** Weeks from this week on the meeting screen (0 = this week). */
    var meetingWeekOffset by mutableStateOf(0)
        private set

    // Plain map (read during drawing, so it must not be Compose state); the counter tells the UI it changed
    private val meetingPrograms = HashMap<String, MeetingProgram?>()
    private var meetingProgramsVersion by mutableStateOf(0)
    var meetingLoad by mutableStateOf<Map<String, MeetingLoad>>(emptyMap())
        private set

    /** "no", "en" or "de": the language of the program (chosen on the meeting screen, or the app's). */
    val meetingLanguage: String
        get() = settings.meetingLanguage.takeIf { it in MEETING_LANGUAGES } ?: languageCode

    fun meetingProgram(lang: String): MeetingProgram? {
        meetingProgramsVersion // read, so screens update when a new program arrives
        if (lang !in meetingPrograms) meetingPrograms[lang] = store.loadMeetingCache(lang)?.let { store.readMeetingProgram(it) }
        return meetingPrograms[lang]
    }

    fun openMeeting() {
        guardEdits {
            activePlan = null
            isQuickTimer = false
            drill = null
            currentScreen = AppScreen.MEETING
            loadMeetings(meetingLanguage)
        }
    }

    /** Fetches the newest programs for [lang] (once per session, unless [force]). */
    fun loadMeetings(lang: String, force: Boolean = false) {
        val state = meetingLoad[lang] ?: MeetingLoad.IDLE
        if (state == MeetingLoad.LOADING || (state == MeetingLoad.OK && !force)) return
        meetingProgram(lang) // the saved copy, while the new one loads
        meetingLoad = meetingLoad + (lang to MeetingLoad.LOADING)
        scope.launch {
            val result = platform.fetchText(MEETINGS_URL + "$lang.json")
            val program = result?.text?.let { store.readMeetingProgram(it) }
            meetingLoad = meetingLoad + (lang to when {
                program != null -> MeetingLoad.OK
                result?.status == 404 -> MeetingLoad.MISSING
                else -> MeetingLoad.FAILED
            })
            if (program != null && result != null) {
                meetingPrograms[lang] = program
                meetingProgramsVersion++
                store.saveMeetingCache(lang, result.text)
            }
        }
    }

    fun setMeetingKind(kind: MeetingKind) = updateSettings { it.copy(meetingKind = kind) }

    fun setMeetingLanguage(lang: String) {
        updateSettings { it.copy(meetingLanguage = lang) }
        loadMeetings(lang)
    }

    fun changeMeetingWeek(by: Int) {
        meetingWeekOffset = if (by == 0) 0 else meetingWeekOffset + by
    }

    /** Monday of the week shown on the meeting screen, "2026-11-02". */
    val meetingWeekStart: String
        get() = mondayOf(platform.localDateIso(platform.epochMs()), meetingWeekOffset)

    /** The meeting picked on the meeting screen, or null when there's no program for that week. */
    fun chosenMeeting(): SpeechPlan? {
        val lang = meetingLanguage
        val program3 = strings3ForCode(lang)
        if (settings.meetingKind == MeetingKind.WEEKEND) return weekendMeeting(program3, strings3.meetingWeekendTitle)
        val week = meetingProgram(lang)?.weeks?.firstOrNull { it.start == meetingWeekStart && it.parts.isNotEmpty() } ?: return null
        return midweekMeeting(week, program3, strings3.meetingMidweekTitle(weekLabel(week.start, languageCode)))
    }

    fun startMeeting() {
        val plan = chosenMeeting() ?: return
        activePlan = plan
        isQuickTimer = false
        drill = null
        startTimer()
    }

    /** History of the meeting kind picked on the meeting screen. */
    fun openMeetingHistory() {
        val kind = settings.meetingKind
        activePlan = chosenMeeting() ?: SpeechPlan(
            id = if (kind == MeetingKind.WEEKEND) WEEKEND_MEETING_ID else MIDWEEK_MEETING_ID,
            title = strings3.meetingMidweek,
            meeting = MeetingInfo(kind, if (kind == MeetingKind.MIDWEEK) MIDWEEK_BUFFER_SECONDS else 0),
        )
        historyFilter = null
        currentScreen = AppScreen.HISTORY
    }

    // --- Introduction and "what's new" ---------------------------------------------------
    /** True on the very first start: show the introduction cards. */
    var showTutorial by mutableStateOf(false)
        private set

    /** True on the first start after an update: show what's new. */
    var showWhatsNew by mutableStateOf(false)
        private set

    fun openTutorial() { showTutorial = true }
    fun openWhatsNew() { showWhatsNew = true }

    fun tutorialDone() {
        showTutorial = false
        if (!settings.tutorialSeen) updateSettings { it.copy(tutorialSeen = true) }
    }

    fun whatsNewDone() { showWhatsNew = false }

    // --- Version and updates --------------------------------------------------
    val appVersion: String get() = platform.appVersion

    var updateState by mutableStateOf<UpdateState>(UpdateState.Idle)
        private set

    /** The "new version" banner can be closed for this session with "Later". */
    var updateBannerHidden by mutableStateOf(false)
        private set

    fun hideUpdateBanner() {
        updateBannerHidden = true
    }

    fun checkForUpdates() {
        if (updateState == UpdateState.Checking) return
        updateState = UpdateState.Checking
        scope.launch {
            val release = platform.fetchLatestRelease()
            updateState = when {
                release == null -> UpdateState.Failed
                isNewerVersion(release.version, appVersion) -> UpdateState.Available(release)
                else -> UpdateState.UpToDate
            }
        }
    }

    var notesState by mutableStateOf<NotesState>(NotesState.Idle)
        private set

    /** Loads the patch notes of the latest releases from GitHub (once per session, unless it failed). */
    fun loadReleaseNotes() {
        if (notesState == NotesState.Loading || notesState is NotesState.Loaded) return
        notesState = NotesState.Loading
        scope.launch {
            val list = platform.fetchReleases()
            notesState = if (list == null) NotesState.Failed else NotesState.Loaded(list)
        }
    }

    /** The feedback email: who it goes to, its subject and a short template with the version. */
    fun feedbackMail(kind: FeedbackKind): Triple<String, String, String> {
        val s = strings
        val label = when (kind) {
            FeedbackKind.BUG -> s.feedbackBug
            FeedbackKind.IDEA -> s.feedbackIdea
            FeedbackKind.OTHER -> s.feedbackOther
        }
        val subject = "Speech Split: $label"
        val diagnostics = platform.diagnosticsNote?.let { "\n$it" } ?: ""
        val body = "${s.feedbackDescribe}\n\n\n\n---\nSpeech Split $appVersion · ${platform.platformName}$diagnostics"
        return Triple(FEEDBACK_EMAIL, subject, body)
    }

    /** Call when the app is closed for good. */
    fun dispose() {
        ticker?.cancel()
        if (dndOnByUs) platform.setDnd(false)
        store.flush()
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

    init {
        // Runs last, after everything above is set up
        if (settings.autoUpdateCheck) checkForUpdates()

        // New users get the introduction; people updating from an older version get "what's new"
        val hadData = speechPlans.isNotEmpty() || runs.isNotEmpty() || store.hasSavedSettings()
        if (!hadData && !settings.tutorialSeen) {
            showTutorial = true
        } else if (settings.lastSeenVersion != appVersion) {
            showWhatsNew = true
        }
        if (settings.lastSeenVersion != appVersion || (hadData && !settings.tutorialSeen)) {
            updateSettings { it.copy(lastSeenVersion = appVersion, tutorialSeen = it.tutorialSeen || hadData) }
        }
    }
}
