package no.srrlsm.speechsplit.core

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

/** Loading state of one language's meeting programs. */
enum class MeetingLoad { IDLE, LOADING, OK, FAILED, MISSING }

/**
 * Meeting mode's own state: which meeting and week is picked, the programs, and the sub-parts.
 * Reached as `app.meetings`. Starting the meeting and opening its history are in [AppController],
 * next to the timer and the navigation they use.
 *
 * The week's program comes from the website (docs/meetings/<lang>.json, made from the meeting
 * workbook with tools/workbook_to_json.py). The last copy is kept, so it works offline.
 */
class MeetingController(
    private val app: AppController,
    private val store: AppStore,
    private val platform: PlatformServices,
    private val scope: CoroutineScope,
) {
    /** Weeks from this week on the meeting screen (0 = this week). */
    var weekOffset by mutableStateOf(0)
        private set

    // Plain map (read during drawing, so it must not be Compose state); the counter tells the UI it changed
    private val programs = HashMap<String, MeetingProgram?>()
    private var programsVersion by mutableStateOf(0)
    var loadState by mutableStateOf<Map<String, MeetingLoad>>(emptyMap())
        private set

    /** "no", "en" or "de": the language of the program (chosen on the meeting screen, or the app's). */
    val language: String
        get() = app.settings.meetingLanguage.takeIf { it in MEETING_LANGUAGES } ?: app.languageCode

    fun program(lang: String): MeetingProgram? {
        programsVersion // read, so screens update when a new program arrives
        if (lang !in programs) programs[lang] = store.loadMeetingCache(lang)?.let { store.readMeetingProgram(it) }
        return programs[lang]
    }

    /** Fetches the newest programs for [lang] (once per session, unless [force]). */
    fun loadPrograms(lang: String, force: Boolean = false) {
        val state = loadState[lang] ?: MeetingLoad.IDLE
        if (state == MeetingLoad.LOADING || (state == MeetingLoad.OK && !force)) return
        program(lang) // the saved copy, while the new one loads
        loadState = loadState + (lang to MeetingLoad.LOADING)
        scope.launch {
            val result = platform.fetchText(MEETINGS_URL + "$lang.json")
            val program = result?.text?.let { store.readMeetingProgram(it) }
            loadState = loadState + (lang to when {
                program != null -> MeetingLoad.OK
                result?.status == 404 -> MeetingLoad.MISSING
                else -> MeetingLoad.FAILED
            })
            if (program != null && result != null) {
                programs[lang] = program
                programsVersion++
                store.saveMeetingCache(lang, result.text)
            }
        }
    }

    fun setKind(kind: MeetingKind) = app.updateSettings { it.copy(meetingKind = kind) }

    fun setLanguage(lang: String) {
        app.updateSettings { it.copy(meetingLanguage = lang) }
        loadPrograms(lang)
    }

    fun changeWeek(by: Int) {
        weekOffset = if (by == 0) 0 else weekOffset + by
    }

    /** Monday of the week shown on the meeting screen, "2026-11-02". */
    val weekStart: String
        get() = mondayOf(platform.localDateIso(platform.epochMs()), weekOffset)

    /** The meeting picked on the meeting screen, or null when there's no program for that week. */
    fun chosen(): SpeechPlan? {
        val lang = language
        val program3 = strings3ForCode(lang)
        if (app.settings.meetingKind == MeetingKind.WEEKEND) return weekendMeeting(program3, app.strings3.meetingWeekendTitle)
        val week = program(lang)?.weeks?.firstOrNull { it.start == weekStart && it.parts.isNotEmpty() } ?: return null
        return midweekMeeting(week, program3, app.strings3.meetingMidweekTitle(weekLabel(week.start, app.languageCode)))
    }

    /** The picked meeting as the timer runs it: every split part replaced by its sub-parts. */
    fun chosenToRun(): SpeechPlan? = chosen()?.let { plan -> expandSubparts(plan) { subpartsOf(plan, it) } }

    // --- Sub-parts of a meeting part ----------------------------------------------------
    // Kept per week and part (see subpartsKey), so the meeting can be prepared in advance.

    /** All sub-parts, by [subpartsKey]. */
    var subparts by mutableStateOf(store.loadMeetingSubparts())
        private set

    fun subpartsOf(plan: SpeechPlan, segment: SpeechSegment): List<SpeechSegment> =
        subparts[subpartsKey(plan.meeting, segment.id)].orEmpty()

    /** Replaces a part's sub-parts (an empty list removes them) and saves. */
    fun setSubparts(plan: SpeechPlan, segment: SpeechSegment, list: List<SpeechSegment>) {
        val key = subpartsKey(plan.meeting, segment.id)
        replaceSubparts(if (list.isEmpty()) subparts - key else subparts + (key to list))
    }

    /** Replaces all sub-parts (a library backup was opened) and saves. Old weeks are dropped along the way. */
    fun replaceSubparts(all: Map<String, List<SpeechSegment>>) {
        val oldest = mondayOf(platform.localDateIso(platform.epochMs()), -8)
        subparts = pruneSubparts(all, oldest)
        store.saveMeetingSubparts(subparts)
    }

    /** A new sub-part at the end, with the time that's left of the part (at least a minute). */
    fun addSubpart(plan: SpeechPlan, segment: SpeechSegment) {
        val have = subpartsOf(plan, segment)
        val left = segment.targetSeconds - have.sumOf { it.targetSeconds }
        setSubparts(plan, segment, have + SpeechSegment(title = app.strings3.subpartNew, targetSeconds = if (left > 0) left else 60))
    }

    fun updateSubpart(plan: SpeechPlan, segment: SpeechSegment, subpart: SpeechSegment) =
        setSubparts(plan, segment, subpartsOf(plan, segment).map { if (it.id == subpart.id) subpart else it })

    fun removeSubpart(plan: SpeechPlan, segment: SpeechSegment, subpartId: String) =
        setSubparts(plan, segment, subpartsOf(plan, segment).filterNot { it.id == subpartId })

    /** The parts of a saved speech become the sub-parts (as copies: the speech isn't changed). */
    fun subpartsFromSpeech(plan: SpeechPlan, segment: SpeechSegment, speech: SpeechPlan) =
        setSubparts(plan, segment, speech.segments.map { SpeechSegment(title = it.title, targetSeconds = it.targetSeconds) })
}
