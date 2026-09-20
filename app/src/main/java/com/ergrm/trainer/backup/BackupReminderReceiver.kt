package com.ergrm.trainer.backup

import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat
import com.ergrm.trainer.MainActivity
import com.ergrm.trainer.R
import com.ergrm.trainer.notify.AppNotifications

private const val CHANNEL_ID = "backup_reminder"
private const val NOTIFICATION_ID = 43

/** Fires weekly (see [BackupReminderScheduler]): nudges the rider to export a backup, since
 *  nothing here uploads one automatically. Tapping it opens the app straight to Settings, where
 *  the Export backup button already is. */
class BackupReminderReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        AppNotifications.ensureChannel(context)
        val contentIntent = PendingIntent.getActivity(
            context,
            0,
            Intent(context, MainActivity::class.java).apply {
                putExtra(MainActivity.EXTRA_OPEN_BACKUP_SETTINGS, true)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
            },
            PendingIntent.FLAG_IMMUTABLE,
        )
        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setContentTitle("Time for a backup")
            .setContentText("Export your settings and ride history to keep them safe")
            .setSmallIcon(R.drawable.ic_notification_bolt)
            .setAutoCancel(true)
            .setContentIntent(contentIntent)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .build()
        context.getSystemService(NotificationManager::class.java).notify(NOTIFICATION_ID, notification)

        BackupReminderScheduler.scheduleNext(context)
    }
}
