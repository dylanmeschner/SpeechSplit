package no.srrlsm.speechsplit.platform

import android.annotation.SuppressLint
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.SystemClock
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import no.srrlsm.speechsplit.MainActivity
import no.srrlsm.speechsplit.R
import com.tom_roush.pdfbox.android.PDFBoxResourceLoader
import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.text.PDFTextStripper
import no.srrlsm.speechsplit.core.DocBlock
import no.srrlsm.speechsplit.core.PlatformServices
import no.srrlsm.speechsplit.jvm.DocumentReader
import no.srrlsm.speechsplit.core.ReleaseInfo
import no.srrlsm.speechsplit.jvm.UpdateClient
import no.srrlsm.speechsplit.core.Strings
import no.srrlsm.speechsplit.core.TimeStatus
import java.text.DateFormat
import java.util.Date
import java.util.Locale

/** Android versions of the device features the shared app logic uses. */
class AndroidPlatform(context: Context) : PlatformServices {
    private val app = context.applicationContext
    private val prefs = app.getSharedPreferences("SpeechTimerPlatform", Context.MODE_PRIVATE)
    private val notificationManager = app.getSystemService(NotificationManager::class.java)

    override val systemLanguage: String get() = Locale.getDefault().language

    override val platformName: String get() = "Android ${Build.VERSION.RELEASE} · ${Build.MANUFACTURER} ${Build.MODEL}"

    override val appVersion: String =
        try {
            @Suppress("DEPRECATION")
            app.packageManager.getPackageInfo(app.packageName, 0).versionName ?: "dev"
        } catch (e: Exception) {
            "dev"
        }

    override suspend fun fetchLatestRelease(): ReleaseInfo? = UpdateClient.fetchLatest()

    override suspend fun fetchReleases(): List<ReleaseInfo>? = UpdateClient.fetchReleases()

    // --- Reading speech documents ---------------------------------------------
    private var pdfReady = false

    override fun readDocument(fileName: String, bytes: ByteArray): List<DocBlock>? =
        DocumentReader.read(fileName, bytes, pdfToText = ::pdfText)

    /** Text of a PDF, with blank lines between paragraphs (PdfBox for Android). */
    private fun pdfText(bytes: ByteArray): String {
        if (!pdfReady) {
            PDFBoxResourceLoader.init(app)
            pdfReady = true
        }
        return PDDocument.load(bytes).use { doc ->
            PDFTextStripper().apply {
                sortByPosition = true
                paragraphEnd = "\n\n"
            }.getText(doc)
        }
    }

    override fun monotonicMs(): Long = SystemClock.elapsedRealtime()

    override fun epochMs(): Long = System.currentTimeMillis()

    override fun formatDateTime(epochMs: Long): String =
        DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT).format(Date(epochMs))

    // --- Vibration ------------------------------------------------------------
    private val vibrator: Vibrator? =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            app.getSystemService(VibratorManager::class.java)?.defaultVibrator
        } else {
            @Suppress("DEPRECATION")
            app.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
        }

    override val canVibrate: Boolean get() = vibrator?.hasVibrator() == true

    override fun vibrate(long: Boolean) {
        val v = vibrator ?: return
        if (!v.hasVibrator()) return
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                val effect = if (long) {
                    VibrationEffect.createWaveform(LONG_PATTERN, -1)
                } else {
                    VibrationEffect.createOneShot(180, VibrationEffect.DEFAULT_AMPLITUDE)
                }
                v.vibrate(effect)
            } else {
                @Suppress("DEPRECATION")
                v.vibrate(if (long) 700L else 180L)
            }
        } catch (e: SecurityException) {
            // VIBRATE permission missing: fail silently instead of crashing
        }
    }

    // --- Smartwatch -----------------------------------------------------------
    // There's no special watch app needed: phones forward notifications to a connected
    // watch (Wear OS, Galaxy Watch, and most watches that show phone notifications),
    // and the watch vibrates. The notifications are silent and remove themselves.
    @SuppressLint("MissingPermission") // checked with areNotificationsEnabled() + try/catch
    override fun watchAlert(status: TimeStatus, title: String, text: String, strings: Strings) {
        val nm = NotificationManagerCompat.from(app)
        if (!nm.areNotificationsEnabled()) return
        val over = status == TimeStatus.OVER
        ensureChannels(strings)

        val openApp = PendingIntent.getActivity(
            app, 0,
            Intent(app, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        val notification = NotificationCompat.Builder(app, if (over) CHANNEL_OVER else CHANNEL_WARNING)
            .setSmallIcon(R.mipmap.ic_launcher_foreground)
            .setContentTitle(title)
            .setContentText(text)
            .setCategory(NotificationCompat.CATEGORY_REMINDER)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setSound(null)
            .setVibrate(if (over) LONG_PATTERN else SHORT_PATTERN)
            .setContentIntent(openApp)
            .setAutoCancel(true)
            .setTimeoutAfter(8_000)
            .build()
        try {
            nm.notify(if (over) NOTIF_OVER else NOTIF_WARNING, notification)
        } catch (e: SecurityException) {
            // Notification permission was taken away: nothing to do
        }
    }

    private fun ensureChannels(strings: Strings) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        fun channel(id: String, name: String, pattern: LongArray) =
            NotificationChannel(id, name, NotificationManager.IMPORTANCE_DEFAULT).apply {
                setSound(null, null)          // never a sound in the middle of a speech
                enableVibration(true)
                vibrationPattern = pattern
                setBypassDnd(true)            // still reaches the watch when our Do Not Disturb is on
                setShowBadge(false)
            }
        notificationManager?.createNotificationChannels(
            listOf(
                channel(CHANNEL_WARNING, strings.channelWarning, SHORT_PATTERN),
                channel(CHANNEL_OVER, strings.channelOver, LONG_PATTERN),
            )
        )
    }

    // --- Do Not Disturb -------------------------------------------------------
    override fun hasDndAccess(): Boolean = notificationManager?.isNotificationPolicyAccessGranted == true

    /**
     * Turns DND on only if it was off, and remembers that we did it, so turning it back off
     * never overrides a Do Not Disturb the user had switched on themselves.
     */
    override fun setDnd(on: Boolean) {
        val nm = notificationManager ?: return
        if (!hasDndAccess()) return
        try {
            if (on) {
                if (nm.currentInterruptionFilter == NotificationManager.INTERRUPTION_FILTER_ALL) {
                    nm.setInterruptionFilter(NotificationManager.INTERRUPTION_FILTER_PRIORITY)
                    // commit(), not apply(): this must be on disk in case the app is killed right after
                    prefs.edit().putBoolean(KEY_DND_BY_APP, true).commit()
                    guard(true)
                }
            } else if (prefs.getBoolean(KEY_DND_BY_APP, false)) {
                nm.setInterruptionFilter(NotificationManager.INTERRUPTION_FILTER_ALL)
                prefs.edit().putBoolean(KEY_DND_BY_APP, false).commit()
                guard(false)
            }
        } catch (e: SecurityException) {
            // Access was revoked in the meantime
        }
    }

    /** Starts/stops [DndGuardService], which switches DND off if the app is swiped away mid-speech. */
    private fun guard(on: Boolean) {
        val intent = Intent(app, DndGuardService::class.java)
        try {
            if (on) app.startService(intent) else app.stopService(intent)
        } catch (e: Exception) {
            // Not allowed from the background: DND is still switched off the next time the app opens
        }
    }

    private companion object {
        const val CHANNEL_WARNING = "watch_warning"
        const val CHANNEL_OVER = "watch_over"
        const val NOTIF_WARNING = 1001
        const val NOTIF_OVER = 1002
        const val KEY_DND_BY_APP = "dnd_turned_on_by_app"
        val SHORT_PATTERN = longArrayOf(0, 250)
        val LONG_PATTERN = longArrayOf(0, 350, 150, 350)
    }
}
