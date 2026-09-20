package com.ergrm.trainer.backup

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent

private const val REQUEST_CODE = 100
private val INTERVAL_MILLIS = 7L * 24 * 60 * 60 * 1000

/** Schedules the weekly "back up your data" reminder — a plain (non-exact) [AlarmManager] alarm,
 *  since a reminder firing a few minutes late is fine and avoids needing the exact-alarm
 *  permission. Alarms don't survive a reboot, so [scheduleIfNeeded] is called on every app
 *  launch and only actually schedules when nothing is already pending — cheap to call often, and
 *  self-healing after a reboot without a dedicated boot receiver. */
object BackupReminderScheduler {

    fun scheduleIfNeeded(context: Context) {
        val existing = PendingIntent.getBroadcast(
            context,
            REQUEST_CODE,
            Intent(context, BackupReminderReceiver::class.java),
            PendingIntent.FLAG_NO_CREATE or PendingIntent.FLAG_IMMUTABLE,
        )
        if (existing != null) return
        scheduleNext(context)
    }

    /** Called by [BackupReminderReceiver] itself right after showing the notification, to queue
     *  the next one — a self-rescheduling alarm rather than a repeating one, so each firing can
     *  freely decide the next delay with no drift accumulating across firings. */
    fun scheduleNext(context: Context) {
        val pendingIntent = PendingIntent.getBroadcast(
            context,
            REQUEST_CODE,
            Intent(context, BackupReminderReceiver::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val alarmManager = context.getSystemService(AlarmManager::class.java)
        alarmManager.setAndAllowWhileIdle(
            AlarmManager.RTC_WAKEUP,
            System.currentTimeMillis() + INTERVAL_MILLIS,
            pendingIntent,
        )
    }
}
