package com.example.basededatos.view

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.BroadcastReceiver
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.example.basededatos.R
import com.example.basededatos.network.AssignmentReminderRecord
import com.example.basededatos.network.AssignmentReminderScheduler
import com.example.basededatos.network.UserStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

class AssignmentReminderReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent?) {
        val appContext = context.applicationContext
        val action = intent?.action ?: return
        if (action != ACTION_SHOW_REMINDER && action != ACTION_RESTORE_REMINDERS &&
            action != Intent.ACTION_BOOT_COMPLETED
        ) return

        val pendingResult = goAsync()
        CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
            try {
                val userStore = UserStore(appContext)
                if (action == ACTION_RESTORE_REMINDERS || action == Intent.ACTION_BOOT_COMPLETED) {
                    userStore.getAllReminderAssignments().forEach { reminder ->
                        AssignmentReminderScheduler.schedule(
                            appContext,
                            reminder.email,
                            reminder.id,
                            reminder.dueAt
                        )
                    }
                } else {
                    val email = intent.getStringExtra(EXTRA_EMAIL).orEmpty()
                    val assignmentId = intent.getLongExtra(EXTRA_ASSIGNMENT_ID, -1L)
                    val reminder = userStore.getAssignmentForReminder(email, assignmentId)
                    if (reminder != null && !reminder.isCompleted && reminder.reminderEnabled) {
                        showReminder(appContext, reminder)
                    }
                }
            } catch (_: Exception) {
                // A reminder must never crash the app process.
            } finally {
                pendingResult.finish()
            }
        }
    }

    private fun showReminder(context: Context, assignment: AssignmentReminderRecord) {
        createNotificationChannel(context)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) !=
            PackageManager.PERMISSION_GRANTED
        ) {
            return
        }

        val openApp = PendingIntent.getActivity(
            context,
            assignment.id.hashCode(),
            Intent(context, MainActivity::class.java).addFlags(
                Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            ),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val details = "${assignment.subject}: ${assignment.title}"
        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle("Se acerca una entrega")
            .setContentText(details)
            .setStyle(NotificationCompat.BigTextStyle().bigText(details))
            .setContentIntent(openApp)
            .setAutoCancel(true)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .build()
        NotificationManagerCompat.from(context).notify(assignment.id.toInt(), notification)
    }

    private fun createNotificationChannel(context: Context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val channel = NotificationChannel(
            CHANNEL_ID,
            "Recordatorios de entregas",
            NotificationManager.IMPORTANCE_HIGH
        ).apply {
            description = "Avisos antes de las fechas límite de tus asignaciones."
        }
        context.getSystemService(NotificationManager::class.java)?.createNotificationChannel(channel)
    }

    companion object {
        const val ACTION_SHOW_REMINDER = "com.example.basededatos.action.SHOW_ASSIGNMENT_REMINDER"
        const val ACTION_RESTORE_REMINDERS = "com.example.basededatos.action.RESTORE_ASSIGNMENT_REMINDERS"
        const val EXTRA_EMAIL = "assignment_reminder_email"
        const val EXTRA_ASSIGNMENT_ID = "assignment_reminder_id"
        private const val CHANNEL_ID = "assignment_deadlines"
    }
}
