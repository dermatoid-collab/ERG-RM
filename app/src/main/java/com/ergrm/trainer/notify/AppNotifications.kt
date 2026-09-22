package com.ergrm.trainer.notify

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat
import com.ergrm.trainer.MainActivity
import com.ergrm.trainer.R

private const val CHANNEL_ID = "backup_reminder"
private const val NOTIFICATION_ID_BACKUP_EXPORTED = 44
private const val NOTIFICATION_ID_BACKUP_IMPORTED = 45
private const val NOTIFICATION_ID_WORKOUT_SAVED = 46
private const val NOTIFICATION_ID_AUTO_BACKUP_NEEDED = 47

/** Passive confirmations for actions that already show a snackbar while the app is open — these
 *  also land in the notification shade, so there's a record even if the rider backgrounds the
 *  app right after Stop or an export. Tapping one does nothing but dismiss it; there's no linked
 *  action, except [notifyAutoBackupNeeded], which opens straight to Settings' backup section —
 *  a silent miss there is exactly the kind of thing a rider should find out about immediately,
 *  not by discovering a ride is gone. Shares [com.ergrm.trainer.backup.BackupReminderReceiver]'s
 *  channel and icon, with its own notification ids so none of these ever overwrite each other. */
object AppNotifications {

    fun notifyBackupExported(context: Context, fileName: String) =
        post(context, NOTIFICATION_ID_BACKUP_EXPORTED, "Settings backup exported", fileName)

    fun notifySettingsImported(context: Context) =
        post(context, NOTIFICATION_ID_BACKUP_IMPORTED, "Settings imported", "Backup applied")

    fun notifyWorkoutSaved(context: Context, summary: String) =
        post(context, NOTIFICATION_ID_WORKOUT_SAVED, "Workout saved", summary)

    /** Posted whenever a just-saved ride didn't make it into the auto-backup folder — either no
     *  folder is set yet, or writing to it failed (deleted, permission revoked). Tapping it opens
     *  Settings' backup section, where picking (or re-picking) a folder is the same "Choose/Change
     *  auto-backup folder" button already there, so this is a direct path to fixing it, not just
     *  a heads-up. */
    fun notifyAutoBackupNeeded(context: Context, title: String, text: String) {
        ensureChannel(context)
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
            .setContentTitle(title)
            .setContentText(text)
            .setSmallIcon(R.drawable.ic_notification_bolt)
            .setAutoCancel(true)
            .setContentIntent(contentIntent)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .build()
        context.getSystemService(NotificationManager::class.java).notify(NOTIFICATION_ID_AUTO_BACKUP_NEEDED, notification)
    }

    private fun post(context: Context, id: Int, title: String, text: String) {
        ensureChannel(context)
        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setContentTitle(title)
            .setContentText(text)
            .setSmallIcon(R.drawable.ic_notification_bolt)
            .setAutoCancel(true)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .build()
        context.getSystemService(NotificationManager::class.java).notify(id, notification)
    }

    /** Idempotent — safe to call before every post, since a channel must exist first on API 26+
     *  and this can fire before the weekly reminder ever has. */
    fun ensureChannel(context: Context) {
        val channel = NotificationChannel(
            CHANNEL_ID,
            "Backup & workout updates",
            NotificationManager.IMPORTANCE_DEFAULT,
        )
        context.getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
    }
}
