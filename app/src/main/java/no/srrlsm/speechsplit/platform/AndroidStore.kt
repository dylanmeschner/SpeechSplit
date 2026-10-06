package no.srrlsm.speechsplit.platform

import android.content.Context
import no.srrlsm.speechsplit.core.AppLanguage
import no.srrlsm.speechsplit.core.AppSettings
import no.srrlsm.speechsplit.core.AppStore
import no.srrlsm.speechsplit.core.CelebrateOptions
import no.srrlsm.speechsplit.core.ClockMode
import no.srrlsm.speechsplit.core.PracticeRun
import no.srrlsm.speechsplit.core.SpeechPlan
import no.srrlsm.speechsplit.core.ThemeMode
import no.srrlsm.speechsplit.jvm.JsonCodec
import org.json.JSONObject

/**
 * Android storage: SharedPreferences holding JSON.
 * Same keys and plan format as before, so existing saved speeches still load.
 */
class AndroidStore(context: Context) : AppStore {
    private val planPrefs = context.getSharedPreferences("SpeechTimerPrefs", Context.MODE_PRIVATE)
    private val settingsPrefs = context.getSharedPreferences("SpeechTimerSettings", Context.MODE_PRIVATE)

    // --- Plans ----------------------------------------------------------------
    override fun loadPlans(): List<SpeechPlan> {
        val (plans, missingIds) = try {
            JsonCodec.plansFromJson(planPrefs.getString(KEY_PLANS, "[]") ?: "[]")
        } catch (e: Exception) {
            emptyList<SpeechPlan>() to false
        }
        // Older saves didn't store segment ids. Save once with ids, so practice history can
        // follow a segment even after it's renamed or moved.
        if (missingIds && plans.isNotEmpty()) savePlans(plans)
        return plans
    }

    override fun savePlans(plans: List<SpeechPlan>) {
        planPrefs.edit().putString(KEY_PLANS, JsonCodec.plansToJson(plans)).apply()
    }

    override fun exportPlan(plan: SpeechPlan): String = JsonCodec.planToJson(plan, includeIds = false).toString()

    override fun importPlan(text: String, fallbackTitle: String): SpeechPlan? = try {
        JsonCodec.planFromJson(JSONObject(text.trim()), fallbackTitle)
    } catch (e: Exception) {
        null
    }

    // --- Practice history -----------------------------------------------------
    override fun loadRuns(): List<PracticeRun> = try {
        JsonCodec.runsFromJson(planPrefs.getString(KEY_RUNS, "[]") ?: "[]")
    } catch (e: Exception) {
        emptyList()
    }

    override fun saveRuns(runs: List<PracticeRun>) {
        planPrefs.edit().putString(KEY_RUNS, JsonCodec.runsToJson(runs)).apply()
    }

    // --- Settings (kept as separate keys, like before) -------------------------
    override fun loadSettings(): AppSettings = with(settingsPrefs) {
        AppSettings(
            language = JsonCodec.enumOrDefault(getString("language", null), AppLanguage.SYSTEM),
            themeMode = JsonCodec.enumOrDefault(getString("themeMode", null), ThemeMode.SYSTEM),
            celebrateTolerance = getInt("celebrateTolerance", 0).let { if (it in CelebrateOptions) it else 0 },
            warningPercent = getInt("warningPercent", 10),
            clockMode = JsonCodec.enumOrDefault(getString("clockMode", null), ClockMode.REMAINING),
            showAdjusted = getBoolean("showAdjusted", true),
            flashAlerts = getBoolean("flashAlerts", true),
            vibrateAlerts = getBoolean("vibrateAlerts", false),
            keepScreenOn = getBoolean("keepScreenOn", true),
            watchAlerts = getBoolean("watchAlerts", false),
            dndWhileSpeaking = getBoolean("dndWhileSpeaking", false),
            lecternMode = getBoolean("lecternMode", false),
        )
    }

    override fun saveSettings(settings: AppSettings) {
        settingsPrefs.edit()
            .putString("language", settings.language.name)
            .putString("themeMode", settings.themeMode.name)
            .putInt("celebrateTolerance", settings.celebrateTolerance)
            .putInt("warningPercent", settings.warningPercent)
            .putString("clockMode", settings.clockMode.name)
            .putBoolean("showAdjusted", settings.showAdjusted)
            .putBoolean("flashAlerts", settings.flashAlerts)
            .putBoolean("vibrateAlerts", settings.vibrateAlerts)
            .putBoolean("keepScreenOn", settings.keepScreenOn)
            .putBoolean("watchAlerts", settings.watchAlerts)
            .putBoolean("dndWhileSpeaking", settings.dndWhileSpeaking)
            .putBoolean("lecternMode", settings.lecternMode)
            .apply()
    }

    private companion object {
        const val KEY_PLANS = "saved_plans"
        const val KEY_RUNS = "practice_runs"
    }
}
