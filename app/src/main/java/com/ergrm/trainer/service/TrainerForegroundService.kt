package com.ergrm.trainer.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.os.Binder
import android.os.IBinder
import androidx.core.app.NotificationCompat
import com.ergrm.trainer.MainActivity
import com.ergrm.trainer.R

/**
 * A minimal foreground service whose only job is to keep this app's process alive at a high
 * priority — with an ongoing notification — for as long as a trainer is connected.
 *
 * It does not own the BLE connection or the workout itself (those stay in [com.ergrm.trainer.ui.MainViewModel],
 * scoped to the app's single Activity): a foreground service's real job here is just to stop
 * Android from reclaiming the whole process while the app is backgrounded mid-ride, which would
 * otherwise silently kill the BLE connection along with everything else. [updateStatus] lets the
 * ViewModel push live connection/target-power text into the notification.
 */
class TrainerForegroundService : Service() {

    private val binder = LocalBinder()

    inner class LocalBinder : Binder() {
        fun getService(): TrainerForegroundService = this@TrainerForegroundService
    }

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
        startForeground(NOTIFICATION_ID, buildNotification("Connecting to trainer…", null))
    }

    override fun onBind(intent: Intent): IBinder = binder

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int = START_NOT_STICKY

    /** Called by the ViewModel whenever the connection or workout state changes. */
    fun updateStatus(title: String, targetWatts: Int?) {
        val manager = getSystemService(NotificationManager::class.java)
        manager.notify(NOTIFICATION_ID, buildNotification(title, targetWatts))
    }

    private fun buildNotification(title: String, targetWatts: Int?): Notification {
        val contentIntent = PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE,
        )
        val text = if (targetWatts != null) "ERG target: $targetWatts W" else "Waiting for a workout"
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle(title)
            .setContentText(text)
            .setSmallIcon(R.drawable.ic_notification_bolt)
            .setContentIntent(contentIntent)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()
    }

    private fun createNotificationChannel() {
        val channel = NotificationChannel(
            CHANNEL_ID,
            "Trainer connection",
            NotificationManager.IMPORTANCE_LOW,
        )
        getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
    }

    companion object {
        private const val NOTIFICATION_ID = 42
        private const val CHANNEL_ID = "trainer_connection"
    }
}
