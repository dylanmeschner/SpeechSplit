package no.srrlsm.speechsplit.desktop

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.ImageComposeScene
import androidx.compose.ui.unit.Density
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import no.srrlsm.speechsplit.core.AppController
import no.srrlsm.speechsplit.core.AppSettings
import no.srrlsm.speechsplit.core.AppStore
import no.srrlsm.speechsplit.core.PlatformServices
import no.srrlsm.speechsplit.core.PracticeRun
import no.srrlsm.speechsplit.core.RunSegment
import no.srrlsm.speechsplit.core.SpeechPlan
import no.srrlsm.speechsplit.core.SpeechSegment
import no.srrlsm.speechsplit.core.Strings
import no.srrlsm.speechsplit.core.ThemeMode
import no.srrlsm.speechsplit.core.TimeStatus
import no.srrlsm.speechsplit.jvm.JsonCodec
import no.srrlsm.speechsplit.ui.LocalUiActions
import no.srrlsm.speechsplit.ui.SpeechSplitApp
import no.srrlsm.speechsplit.ui.UiActions
import no.srrlsm.speechsplit.ui.theme.SpeechSplitTheme
import org.jetbrains.skia.EncodedImageFormat
import java.io.File

// ============================================================================
// DEVELOPER TOOL: renders the main screens to PNG files (no window needed).
// Run: ./gradlew :desktop:screenshots   → desktop/build/screenshots/
// Useful for checking the design, and for the download page / store listings.
// ============================================================================

private class MemoryStore(var settings: AppSettings) : AppStore {
    var plans = emptyList<SpeechPlan>()
    var runs = emptyList<PracticeRun>()
    override fun loadPlans() = plans
    override fun savePlans(plans: List<SpeechPlan>) { this.plans = plans.toList() }
    override fun loadRuns() = runs
    override fun saveRuns(runs: List<PracticeRun>) { this.runs = runs }
    override fun loadSettings() = settings
    override fun saveSettings(settings: AppSettings) { this.settings = settings }
    override fun exportPlan(plan: SpeechPlan) = JsonCodec.planToJson(plan, false).toString()
    override fun importPlan(text: String, fallbackTitle: String): SpeechPlan? = null
}

private class FakeClockPlatform : PlatformServices {
    var now = 0L
    override val systemLanguage = "en"
    override fun monotonicMs() = now
    override fun epochMs() = 1_791_300_000_000L
    override fun formatDateTime(epochMs: Long) = "6 Oct 2026, 18:30"
    override val canVibrate = false
    override fun vibrate(long: Boolean) {}
    override val supportsWatchAlerts = false
    override val supportsDnd = false
    override val hasKeyboardShortcuts = true
    override fun watchAlert(status: TimeStatus, title: String, text: String, strings: Strings) {}
    override fun hasDndAccess() = false
    override fun setDnd(on: Boolean) {}
}

private object NoUi : UiActions {
    override fun share(text: String, chooserTitle: String) {}
    override fun toast(text: String) {}
    override fun animationsEnabled() = false
    override fun hasNotificationPermission() = false
    override fun requestNotificationPermission(onResult: (Boolean) -> Unit) = onResult(false)
    override fun openDndSettings() {}
}

fun main(args: Array<String>) {
    val out = File(args.firstOrNull() ?: "build/screenshots").apply { mkdirs() }
    val intro = SpeechSegment(title = "Introduction", targetSeconds = 120)
    val main = SpeechSegment(title = "Main point", targetSeconds = 420)
    val story = SpeechSegment(title = "Story", targetSeconds = 240)
    val close = SpeechSegment(title = "Conclusion", targetSeconds = 120)
    val plan = SpeechPlan(title = "Sunday talk", segments = listOf(intro, main, story, close))

    for (dark in listOf(false, true)) {
        val mode = if (dark) "dark" else "light"
        fun shot(name: String, setup: (AppController, FakeClockPlatform) -> Unit) {
            val store = MemoryStore(AppSettings(themeMode = if (dark) ThemeMode.DARK else ThemeMode.LIGHT))
            store.plans = listOf(plan, SpeechPlan(title = "Wedding toast", segments = listOf(intro, close)))
            store.runs = listOf(30, 45, 12, 50).mapIndexed { i, over ->
                PracticeRun(planId = plan.id, planTitle = plan.title, finishedAtEpochMs = 1_791_300_000_000L - i * 86_400_000L,
                    segments = listOf(
                        RunSegment(intro.id, intro.title, 120, 118 + i),
                        RunSegment(main.id, main.title, 420, 420 + over),
                        RunSegment(story.id, story.title, 240, 236),
                        RunSegment(close.id, close.title, 120, 121),
                    ))
            }
            val platform = FakeClockPlatform()
            val app = AppController(store, platform, CoroutineScope(Job()))
            setup(app, platform)
            val scene = ImageComposeScene(width = 880, height = 1640, density = Density(2f)) {
                SpeechSplitTheme(darkTheme = dark) {
                    CompositionLocalProvider(LocalUiActions provides NoUi) { SpeechSplitApp(app) }
                }
            }
            scene.render(0)
            val image = scene.render(1_000_000_000)
            File(out, "$name-$mode.png").writeBytes(image.encodeToData(EncodedImageFormat.PNG)!!.bytes)
            scene.close()
            println("Saved $name-$mode.png")
        }
        shot("1-library") { _, _ -> }
        shot("2-ready") { app, _ -> app.openPlan(app.speechPlans[0]) }
        shot("3-timer") { app, p ->
            app.openPlan(app.speechPlans[0]); app.startTimer()
            p.now = 125_000; app.nextSegment()          // intro took 2:05
            p.now = 125_000 + 380_000; app.engine.tick() // 6:20 into the main point (warning zone)
        }
        shot("4-bigclock") { app, p ->
            app.openPlan(app.speechPlans[0]); app.updateSettings { it.copy(lecternMode = true) }; app.startTimer()
            p.now = 95_000; app.engine.tick()
        }
        shot("5-history") { app, _ -> app.openPlan(app.speechPlans[0]); app.openHistory() }
        shot("6-settings") { app, _ -> app.openSettings() }
    }
}
