package no.srrlsm.speechsplit.jvm

import no.srrlsm.speechsplit.core.AppLanguage
import no.srrlsm.speechsplit.core.AppSettings
import no.srrlsm.speechsplit.core.CelebrateOptions
import no.srrlsm.speechsplit.core.ClockMode
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
        }
        put("title", plan.title)
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
        o.put("segments", planToJson(plan, includeIds = runs.isNotEmpty()).getJSONArray("segments"))
        if (runs.isNotEmpty()) o.put("history", JSONArray().apply { runs.forEach { put(runToJson(it)) } })
        return o.toString(2) + "\n"
    }

    /** A .speechsplit library backup: every speech (archived ones too) and all practice history. */
    fun libraryFileText(plans: List<SpeechPlan>, runs: List<PracticeRun>): String =
        JSONObject()
            .put("app", "Speech Split")
            .put("format", 1)
            .put("kind", "library")
            .put("speeches", JSONArray().apply { plans.forEach { put(planToJson(it, includeIds = true)) } })
            .put("history", JSONArray().apply { runs.forEach { put(runToJson(it)) } })
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
            return SpeechFile(plans, runs, isLibrary = true)
        }
        val plan = runCatching { planFromJson(o, fallbackTitle) }.getOrNull() ?: return null
        return SpeechFile(listOf(plan), runs, isLibrary = false)
    }

    fun runToJson(run: PracticeRun): JSONObject = JSONObject().apply {
        put("id", run.id)
        put("planId", run.planId)
        put("planTitle", run.planTitle)
        put("finishedAt", run.finishedAtEpochMs)
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
        .put("wordsPerMinute", s.wordsPerMinute)
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
            wordsPerMinute = o.optInt("wordsPerMinute", d.wordsPerMinute).coerceIn(60, 250),
        )
    }
}
