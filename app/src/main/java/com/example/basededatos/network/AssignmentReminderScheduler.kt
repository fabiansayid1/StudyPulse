package com.example.basededatos.network

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.net.Uri
import com.example.basededatos.view.AssignmentReminderReceiver

object AssignmentReminderScheduler {

    fun schedule(
        context: Context,
        email: String,
        assignmentId: Long,
        dueAt: Long
    ) {
        val now = System.currentTimeMillis()
        if (dueAt <= now) return
        val reminderAt = if (dueAt - now > ONE_HOUR_MS) {
            dueAt - ONE_HOUR_MS
        } else {
            dueAt - FIVE_MINUTES_MS
        }.coerceAtLeast(now + ONE_MINUTE_MS)
        if (reminderAt >= dueAt) return

        val pendingIntent = reminderPendingIntent(
            context,
            email,
            assignmentId,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        ) ?: return
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as? AlarmManager ?: return
        alarmManager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, reminderAt, pendingIntent)
    }

    fun cancel(context: Context, email: String, assignmentId: Long) {
        val pendingIntent = reminderPendingIntent(
            context,
            email,
            assignmentId,
            PendingIntent.FLAG_NO_CREATE or PendingIntent.FLAG_IMMUTABLE
        ) ?: return
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as? AlarmManager
        alarmManager?.cancel(pendingIntent)
        pendingIntent.cancel()
    }

    private fun reminderPendingIntent(
        context: Context,
        email: String,
        assignmentId: Long,
        flags: Int
    ): PendingIntent? {
        val normalizedEmail = email.trim().lowercase()
        val intent = Intent(context, AssignmentReminderReceiver::class.java).apply {
            action = AssignmentReminderReceiver.ACTION_SHOW_REMINDER
            data = Uri.parse("studypulse://reminder/$assignmentId/${Uri.encode(normalizedEmail)}")
            putExtra(AssignmentReminderReceiver.EXTRA_EMAIL, normalizedEmail)
            putExtra(AssignmentReminderReceiver.EXTRA_ASSIGNMENT_ID, assignmentId)
        }
        return PendingIntent.getBroadcast(
            context,
            assignmentId.hashCode(),
            intent,
            flags
        )
    }

    private const val ONE_HOUR_MS = 60 * 60 * 1000L
    private const val FIVE_MINUTES_MS = 5 * 60 * 1000L
    private const val ONE_MINUTE_MS = 60 * 1000L
}
