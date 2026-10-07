package no.srrlsm.speechsplit.platform

import android.app.Service
import android.content.Intent
import android.os.IBinder

/**
 * Runs (invisibly, doing nothing) only while Speech Split has Do Not Disturb switched on.
 * Its one job: when the app is swiped away from the recent apps in the middle of a speech,
 * Android calls [onTaskRemoved], and Do Not Disturb is switched back off right away,
 * instead of staying on until the app is opened again.
 */
class DndGuardService : Service() {
    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int = START_NOT_STICKY

    override fun onTaskRemoved(rootIntent: Intent?) {
        AndroidPlatform(applicationContext).setDnd(false)
        stopSelf()
        super.onTaskRemoved(rootIntent)
    }
}
