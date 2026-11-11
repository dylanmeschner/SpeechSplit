package no.srrlsm.speechsplit.jvm

import no.srrlsm.speechsplit.core.AppLanguage
import no.srrlsm.speechsplit.core.AppSettings
import no.srrlsm.speechsplit.core.CelebrateOptions
import no.srrlsm.speechsplit.core.ClockMode
import no.srrlsm.speechsplit.core.Folder
import no.srrlsm.speechsplit.core.MeetingKind
import no.srrlsm.speechsplit.core.MeetingPart
import no.srrlsm.speechsplit.core.MeetingProgram
import no.srrlsm.speechsplit.core.MeetingWeek
import no.srrlsm.speechsplit.core.PracticeRun
import no.srrlsm.speechsplit.core.RunSegment
import no.srrlsm.speechsplit.core.SpeechPlan
import no.srrlsm.speechsplit.core.SpeechSegment
import no.srrlsm.speechsplit.core.SpeechFile
import no.srrlsm.speechsplit.core.SpeechSort
import no.srrlsm.speechsplit.core.ThemeMode
import no.srrlsm.speechsplit.core.WarningOptions
import no.srrlsm.speechsplit.core.newId
import org.json.JSONArray
import org.json.JSONObject

/**
 * Converts speeches, runs and settings to and from JSON.
 * Shared by the Android app and the Windows/desktop app (both run on the JVM),
 * so a speech exported on one can be imported on the other.
 */
object JsonCodec {
    inline fun <reified T : Enum<T>> enumOrDefault(name: String?, default: T): T =
        enumValues<T>().firstOrNull { it.name == name } ?: default

    fun hasSegmentIds(obj: JSONObject): Boolean {
        val segs = obj.optJSONArray("segments") ?: return true
        return (0 until segs.length()).all { segs.optJSONObject(it)?.has("id") == true }
    }

    /** [includeIds] = the app's own save (ids, dates, archive). Without it: a clean, shareable export. */
    fun planToJson(plan: SpeechPlan, includeIds: Boolean): JSONObject = JSONObject().apply {
        if (includeIds) {
            put("id", plan.id)
            if (plan.createdAtEpochMs > 0) put("createdAt", plan.createdAtEpochMs)
            if (plan.lastUsedAtEpochMs > 0) put("lastUsedAt", plan.lastUsedAtEpochMs)
            if (plan.archived) put("archived", true)
            plan.folderId?.let { put("folderId", it) }
        }
        put("title", plan.title)
        // Spare time (buffer): part of the speech, so it's in a shared export too
        if (plan.bufferSeconds > 0) put("buffer", plan.bufferSeconds)
        put("segments", JSONArray().apply {
            plan.segments.forEach { seg ->
                val o = JSONObject().put("title", seg.title).put("target", seg.targetSeconds)
                if (includeIds) o.put("id", seg.id)
                put(o)
            }
        })
    }

    /** Returns null if the JSON isn't a plan. Throws on malformed segment entries. */
    fun planFromJson(obj: JSONObject, fallbackTitle: String): SpeechPlan? {
        val segArray = obj.optJSONArray("segments") ?: return null
        val segments = (0 until segArray.length()).map { j ->
            val s = segArray.getJSONObject(j)
            SpeechSegment(
                id = s.optString("id").ifBlank { newId() },
                title = s.optString("title"),
                targetSeconds = s.optInt("target").coerceAtLeast(0),
            )
        }
        return SpeechPlan(
            id = obj.optString("id").ifBlank { newId() },
            title = obj.optString("title").ifBlank { fallbackTitle },
            segments = segments,
            createdAtEpochMs = obj.optLong("createdAt", 0L).coerceAtLeast(0L),
            lastUsedAtEpochMs = obj.optLong("lastUsedAt", 0L).coerceAtLeast(0L),
            archived = obj.optBoolean("archived", false),
            folderId = obj.optString("folderId").ifBlank { null },
            bufferSeconds = obj.optInt("buffer", 0).coerceAtLeast(0),
        )
    }

    /**
     * A .speechsplit file with one speech: the shareable export, indented, with a marker saying
     * what it is. With history, the ids are kept so each run can find its segments again.
     */
    fun planFileText(plan: SpeechPlan, runs: List<PracticeRun> = emptyList()): String {
        val o = JSONObject().put("app", "Speech Split").put("format", 1)
        if (runs.isNotEmpty()) o.put("id", plan.id)
        o.put("title", plan.title)
        if (plan.bufferSeconds > 0) o.put("buffer", plan.bufferSeconds)
        o.put("segments", planToJson(plan, includeIds = runs.isNotEmpty()).getJSONArray("segments"))
        if (runs.isNotEmpty()) o.put("history", JSONArray().apply { runs.forEach { put(runToJson(it)) } })
        return o.toString(2) + "\n"
    }

    /** A .speechsplit library backup: every speech (archived ones too), the folders and all practice history. */
    fun libraryFileText(
        plans: List<SpeechPlan>,
        runs: List<PracticeRun>,
        folders: List<Folder> = emptyList(),
        subparts: Map<String, List<SpeechSegment>> = emptyMap(),
        settings: AppSettings? = null,
    ): String =
        JSONObject()
            .put("app", "Speech Split")
            .put("format", 1)
            .put("kind", "library")
            .put("folders", foldersToJsonArray(folders))
            .put("speeches", JSONArray().apply { plans.forEach { put(planToJson(it, includeIds = true)) } })
            .put("history", JSONArray().apply { runs.forEach { put(runToJson(it)) } })
            .apply {
                if (subparts.isNotEmpty()) put("subparts", JSONObject(subpartsToJson(subparts)))
                // Only the choices a person makes, not what belongs to one device (version seen, introduction shown)
                if (settings != null) put("settings", JSONObject(settingsToJson(settings)).apply {
                    remove("tutorialSeen")
                    remove("lastSeenVersion")
                })
            }
            .toString(2) + "\n"

    /** Reads any Speech Split file or export text. Null if it isn't one. */
    fun readSpeechFile(text: String, fallbackTitle: String): SpeechFile? {
        val o = runCatching { JSONObject(text.trim()) }.getOrNull() ?: return null
        val runs = o.optJSONArray("history")?.let { arr ->
            (0 until arr.length()).mapNotNull { i -> runCatching { runFromJson(arr.getJSONObject(i)) }.getOrNull() }
        } ?: emptyList()
        if (o.optString("kind") == "library" || o.has("speeches")) {
            val arr = o.optJSONArray("speeches") ?: return null
            val plans = (0 until arr.length()).mapNotNull { i ->
                runCatching { planFromJson(arr.getJSONObject(i), fallbackTitle) }.getOrNull()
            }.distinctBy { it.id }
            val folders = o.optJSONArray("folders")?.let { foldersFromJsonArray(it) } ?: emptyList()
            val subparts = o.optJSONObject("subparts")?.let { subpartsFromJson(it.toString()) } ?: emptyMap()
            val settings = o.optJSONObject("settings")?.let { runCatching { settingsFromJson(it.toString()) }.getOrNull() }
            return SpeechFile(plans, runs, isLibrary = true, folders = folders, subparts = subparts, settings = settings)
        }
        val plan = runCatching { planFromJson(o, fallbackTitle) }.getOrNull() ?: return null
        return SpeechFile(listOf(plan), runs, isLibrary = false)
    }

    fun runToJson(run: PracticeRun): JSONObject = JSONObject().apply {
        put("id", run.id)
        put("planId", run.planId)
        put("planTitle", run.planTitle)
        put("finishedAt", run.finishedAtEpochMs)
        // Same names as the web version uses
        if (run.live) put("live", true)
        run.meeting?.let { put("meeting", if (it == MeetingKind.WEEKEND) "weekend" else "midweek") }
        if (run.bufferSeconds > 0) put("buffer", run.bufferSeconds)
        put("segments", JSONArray().apply {
            run.segments.forEach { s ->
                put(
                    JSONObject()
                        .put("segmentId", s.segmentId)
                        .put("title", s.title)
                        .put("target", s.targetSeconds)
                        .put("actual", s.actualSeconds)
                )
            }
        })
    }

    fun runFromJson(obj: JSONObject): PracticeRun {
        val segs = obj.getJSONArray("segments")
        return PracticeRun(
            id = obj.optString("id").ifBlank { newId() },
            planId = obj.getString("planId"),
            planTitle = obj.optString("planTitle"),
            finishedAtEpochMs = obj.getLong("finishedAt"),
            live = obj.optBoolean("live", false),
            meeting = when (obj.optString("meeting")) {
                "midweek" -> MeetingKind.MIDWEEK
                "weekend" -> MeetingKind.WEEKEND
                else -> null
            },
            bufferSeconds = obj.optInt("buffer", 0).coerceAtLeast(0),
            segments = (0 until segs.length()).map { j ->
                val s = segs.getJSONObject(j)
                RunSegment(
                    segmentId = s.optString("segmentId"),
                    title = s.optString("title"),
                    targetSeconds = s.optInt("target"),
                    actualSeconds = s.optInt("actual"),
                )
            },
        )
    }

    fun plansToJson(plans: List<SpeechPlan>): String =
        JSONArray().apply { plans.forEach { put(planToJson(it, includeIds = true)) } }.toString()

    /** Second value is true when some segments had no stored id (older saves). */
    fun plansFromJson(text: String): Pair<List<SpeechPlan>, Boolean> {
        var missingIds = false
        val array = JSONArray(text)
        val plans = (0 until array.length()).mapNotNull { i ->
            runCatching {
                val obj = array.getJSONObject(i)
                if (!hasSegmentIds(obj)) missingIds = true
                planFromJson(obj, "Speech")
            }.getOrNull()
        }.distinctBy { it.id }
        return plans to missingIds
    }

    fun runsToJson(runs: List<PracticeRun>): String =
        JSONArray().apply { runs.forEach { put(runToJson(it)) } }.toString()

    fun runsFromJson(text: String): List<PracticeRun> {
        val array = JSONArray(text)
        return (0 until array.length()).mapNotNull { i -> runCatching { runFromJson(array.getJSONObject(i)) }.getOrNull() }
    }

    fun settingsToJson(s: AppSettings): String = JSONObject()
        .put("language", s.language.name)
        .put("themeMode", s.themeMode.name)
        .put("celebrateTolerance", s.celebrateTolerance)
        .put("warningPercent", s.warningPercent)
        .put("clockMode", s.clockMode.name)
        .put("showAdjusted", s.showAdjusted)
        .put("flashAlerts", s.flashAlerts)
        .put("vibrateAlerts", s.vibrateAlerts)
        .put("keepScreenOn", s.keepScreenOn)
        .put("watchAlerts", s.watchAlerts)
        .put("dndWhileSpeaking", s.dndWhileSpeaking)
        .put("lecternMode", s.lecternMode)
        .put("autoUpdateCheck", s.autoUpdateCheck)
        .put("speechSort", s.speechSort.name)
        .put("tutorialSeen", s.tutorialSeen)
        .put("lastSeenVersion", s.lastSeenVersion)
        .put("runLive", s.runLive)
        .put("meetingKind", s.meetingKind.name)
        .put("meetingLanguage", s.meetingLanguage)
        .put("secondScreen", s.secondScreen)
        .toString(2)

    fun settingsFromJson(text: String): AppSettings {
        val o = JSONObject(text)
        val d = AppSettings()
        return AppSettings(
            language = enumOrDefault(o.optString("language"), d.language),
            themeMode = enumOrDefault(o.optString("themeMode"), d.themeMode),
            celebrateTolerance = o.optInt("celebrateTolerance", d.celebrateTolerance).let { if (it in CelebrateOptions) it else 0 },
            warningPercent = o.optInt("warningPercent", d.warningPercent).let { if (it in WarningOptions) it else d.warningPercent },
            clockMode = enumOrDefault(o.optString("clockMode"), d.clockMode),
            showAdjusted = o.optBoolean("showAdjusted", d.showAdjusted),
            flashAlerts = o.optBoolean("flashAlerts", d.flashAlerts),
            vibrateAlerts = o.optBoolean("vibrateAlerts", d.vibrateAlerts),
            keepScreenOn = o.optBoolean("keepScreenOn", d.keepScreenOn),
            watchAlerts = o.optBoolean("watchAlerts", d.watchAlerts),
            dndWhileSpeaking = o.optBoolean("dndWhileSpeaking", d.dndWhileSpeaking),
            lecternMode = o.optBoolean("lecternMode", d.lecternMode),
            autoUpdateCheck = o.optBoolean("autoUpdateCheck", d.autoUpdateCheck),
            speechSort = enumOrDefault(o.optString("speechSort"), d.speechSort),
            tutorialSeen = o.optBoolean("tutorialSeen", d.tutorialSeen),
            lastSeenVersion = o.optString("lastSeenVersion", d.lastSeenVersion),
            runLive = o.optBoolean("runLive", d.runLive),
            meetingKind = enumOrDefault(o.optString("meetingKind"), d.meetingKind),
            meetingLanguage = o.optString("meetingLanguage", d.meetingLanguage),
            secondScreen = o.optBoolean("secondScreen", d.secondScreen),
        )
    }

    // --- Folders ------------------------------------------------------------------
    fun foldersToJsonArray(folders: List<Folder>): JSONArray = JSONArray().apply {
        folders.forEach { f ->
            put(JSONObject().put("id", f.id).put("name", f.name).apply { if (f.createdAtEpochMs > 0) put("createdAt", f.createdAtEpochMs) })
        }
    }

    fun foldersFromJsonArray(array: JSONArray): List<Folder> = (0 until array.length()).mapNotNull { i ->
        val o = array.optJSONObject(i) ?: return@mapNotNull null
        val id = o.optString("id")
        val name = o.optString("name").trim()
        if (id.isBlank() || name.isBlank()) null else Folder(id, name, o.optLong("createdAt", 0L))
    }.distinctBy { it.id }

    fun foldersToJson(folders: List<Folder>): String = foldersToJsonArray(folders).toString()

    fun foldersFromJson(text: String): List<Folder> = runCatching { foldersFromJsonArray(JSONArray(text)) }.getOrDefault(emptyList())

    // --- Sub-parts of meeting parts: { "2026-11-02/mw-p1": [ { id, title, target }, ... ] } ---
    fun subpartsToJson(subparts: Map<String, List<SpeechSegment>>): String = JSONObject().apply {
        subparts.forEach { (key, list) ->
            put(key, JSONArray().apply {
                list.forEach { put(JSONObject().put("id", it.id).put("title", it.title).put("target", it.targetSeconds)) }
            })
        }
    }.toString()

    fun subpartsFromJson(text: String): Map<String, List<SpeechSegment>> = runCatching {
        val o = JSONObject(text)
        val out = LinkedHashMap<String, List<SpeechSegment>>()
        for (key in o.keys()) {
            val arr = o.optJSONArray(key) ?: continue
            val list = (0 until arr.length()).mapNotNull { i ->
                val s = arr.optJSONObject(i) ?: return@mapNotNull null
                SpeechSegment(
                    id = s.optString("id").ifBlank { newId() },
                    title = s.optString("title"),
                    targetSeconds = s.optInt("target").coerceAtLeast(0),
                )
            }
            if (list.isNotEmpty()) out[key] = list
        }
        out
    }.getOrDefault(emptyMap())

    // --- Meeting programs (docs/meetings/<lang>.json) ---------------------------------
    fun meetingProgramFromJson(text: String): MeetingProgram? = runCatching {
        val o = JSONObject(text.trim())
        val weeks = o.getJSONArray("weeks")
        MeetingProgram(
            language = o.optString("lang"),
            updated = o.optString("updated"),
            weeks = (0 until weeks.length()).mapNotNull { i ->
                val w = weeks.optJSONObject(i) ?: return@mapNotNull null
                val parts = w.optJSONArray("parts") ?: return@mapNotNull null
                val songs = w.optJSONArray("songs")
                MeetingWeek(
                    start = w.optString("start"),
                    reading = w.optString("reading"),
                    songs = if (songs == null) emptyList() else (0 until songs.length()).map { songs.optInt(it) }.filter { it > 0 },
                    parts = (0 until parts.length()).mapNotNull { j ->
                        val p = parts.optJSONObject(j) ?: return@mapNotNull null
                        MeetingPart(p.optInt("n"), p.optString("title"), p.optInt("min"))
                    },
                    livingFrom = w.optInt("livingFrom", 7),
                )
            }.filter { it.start.length == 10 },
        )
    }.getOrNull()
}
