package no.srrlsm.speechsplit.desktop

import java.io.File
import java.lang.management.ManagementFactory
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import javax.swing.SwingUtilities

/**
 * Notices when the window stops responding and writes down what it was busy with.
 *
 * A small background thread "knocks" on the window's thread twice a second. If no answer
 * comes for [STALL_MS], it saves what that thread is doing (and whether the whole app was
 * paused, e.g. by memory clean-up or the PC sleeping) to freeze-log.txt in the app's data
 * folder. The feedback email points to that file, so a freeze can be pinned down exactly.
 * Nothing is sent anywhere by itself.
 */
object FreezeWatch {
    private const val STALL_MS = 2_000L
    private const val MAX_LOG_BYTES = 256 * 1024L

    val logFile: File by lazy { File(appDataDir(), "freeze-log.txt") }

    @Volatile private var lastBeatNs = System.nanoTime()

    fun start(version: String) {
        val t = Thread({
            // The watcher must never take the app down, nor quietly stop watching after a hiccup
            while (true) {
                try {
                    watch(version)
                    return@Thread
                } catch (e: Throwable) {
                    runCatching { Thread.sleep(1_000) }
                }
            }
        }, "SpeechSplit-freeze-watch")
        t.isDaemon = true
        t.priority = Thread.MAX_PRIORITY
        t.start()
    }

    private fun watch(version: String) {
        var stallStartNs = 0L
        var reported = false
        var lastLoopNs = System.nanoTime()
        while (true) {
            SwingUtilities.invokeLater { lastBeatNs = System.nanoTime() }
            try {
                Thread.sleep(500)
            } catch (e: InterruptedException) {
                return
            }
            val now = System.nanoTime()
            // If this thread itself overslept, the whole app was paused (GC, PC asleep, CPU starved)
            val loopGapMs = (now - lastLoopNs) / 1_000_000 - 500
            lastLoopNs = now
            if (loopGapMs > STALL_MS) {
                log(version, "Whole app paused for ~${loopGapMs} ms (memory clean-up, PC asleep or CPU busy elsewhere). ${memory()}")
                lastBeatNs = now // don't count the pause as a window freeze
                continue
            }
            val stalledMs = (now - lastBeatNs) / 1_000_000
            if (!reported && stalledMs > STALL_MS) {
                reported = true
                stallStartNs = lastBeatNs
                log(version, "Window not responding for ${stalledMs} ms. ${memory()}\n${uiThreadStack()}")
            } else if (reported && stalledMs < 1_000) {
                reported = false
                log(version, "Window responding again after ~${(lastBeatNs - stallStartNs) / 1_000_000} ms.")
            }
        }
    }

    private fun uiThreadStack(): String {
        val traces = Thread.getAllStackTraces()
        val ui = traces.entries.filter { it.key.name.startsWith("AWT-EventQueue") }
        val others = traces.entries.filter { (t, st) ->
            t.state == Thread.State.RUNNABLE && st.isNotEmpty() && !t.name.startsWith("AWT-EventQueue") &&
                t.name != Thread.currentThread().name && t.name != "Reference Handler" && t.name != "Signal Dispatcher"
        }
        return buildString {
            (ui + others).forEach { (t, st) ->
                append("  Thread \"${t.name}\" (${t.state})\n")
                st.take(if (t.name.startsWith("AWT-EventQueue")) 60 else 15).forEach { append("    at $it\n") }
            }
        }
    }

    private fun memory(): String {
        val rt = Runtime.getRuntime()
        val usedMb = (rt.totalMemory() - rt.freeMemory()) / 1_048_576
        val gcMs = runCatching {
            ManagementFactory.getGarbageCollectorMXBeans().sumOf { it.collectionTime.coerceAtLeast(0) }
        }.getOrNull()
        return "Memory ${usedMb}/${rt.maxMemory() / 1_048_576} MB" + (gcMs?.let { ", GC total $it ms." } ?: ".")
    }

    @Synchronized
    private fun log(version: String, text: String) {
        runCatching {
            val f = logFile
            if (f.length() > MAX_LOG_BYTES) {
                val old = File(f.parentFile, "freeze-log.old.txt")
                old.delete()
                f.renameTo(old)
            }
            val stamp = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.ROOT).format(Date())
            val os = "${System.getProperty("os.name")} ${System.getProperty("os.version")}"
            f.appendText("[$stamp] Speech Split $version · $os · Java ${System.getProperty("java.version")}\n$text\n\n")
        }
    }
}
