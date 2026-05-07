package com.example.habittracker

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat

/**
 * BroadcastReceiver triggered by AlarmManager at a habit's scheduled time.
 * Responsible for showing the reminder notification and scheduling the next alarm.
 */
class ReminderReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val habitId = intent.getStringExtra("HABIT_ID") ?: return
        val habitName = intent.getStringExtra("HABIT_NAME") ?: "Habit Reminder"
        val userId = intent.getStringExtra("USER_ID")
        
        // Display the notification to the user
        showNotification(context, habitId, habitName, userId)

        // Reschedule the alarm for the next occurrence (e.g., tomorrow)
        // This ensures the notification system is recurring.
        if (userId != null) {
            val db = com.google.firebase.firestore.FirebaseFirestore.getInstance()
            db.collection("users").document(userId).collection("habits")
                .document(habitId)
                .get()
                .addOnSuccessListener { document ->
                    val habit = document.toObject(Habit::class.java)
                    if (habit != null && habit.reminderTime.isNotEmpty()) {
                        ReminderManager.scheduleReminder(context, habit)
                    }
                }
        }
    }

    /**
     * Builds and displays a notification for the habit reminder.
     */
    private fun showNotification(context: Context, habitId: String, habitName: String, userId: String?) {
        val channelId = "habit_reminders"
        
        // Create the notification channel for Android 8.0+
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val name = "Habit Reminders"
            val descriptionText = "Notifications for habit reminders"
            val importance = NotificationManager.IMPORTANCE_HIGH
            val channel = NotificationChannel(channelId, name, importance).apply {
                description = descriptionText
            }
            val notificationManager: NotificationManager =
                context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            notificationManager.createNotificationChannel(channel)
        }

        // Intent to open the HomeActivity when the notification body is clicked
        val intent = Intent(context, HomeActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        }
        val pendingIntent: PendingIntent = PendingIntent.getActivity(
            context, habitId.hashCode(), intent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        // Intent for the "Mark Completed" action button
        val completeIntent = Intent(context, CompletionActionReceiver::class.java).apply {
            putExtra("HABIT_ID", habitId)
            putExtra("HABIT_NAME", habitName)
            putExtra("USER_ID", userId)
        }
        val completePendingIntent: PendingIntent = PendingIntent.getBroadcast(
            context, habitId.hashCode() + 1, completeIntent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        // Build the notification
        val builder = NotificationCompat.Builder(context, channelId)
            .setSmallIcon(android.R.drawable.ic_lock_idle_alarm)
            .setContentTitle("Time for your habit!")
            .setContentText("Don't forget to: $habitName")
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)
            .addAction(
                android.R.drawable.ic_menu_edit, 
                "Mark Completed", 
                completePendingIntent
            )

        // Show the notification if permission is granted
        with(NotificationManagerCompat.from(context)) {
            if (androidx.core.content.ContextCompat.checkSelfPermission(
                    context,
                    android.Manifest.permission.POST_NOTIFICATIONS
                ) == android.content.pm.PackageManager.PERMISSION_GRANTED
            ) {
                notify(habitId.hashCode(), builder.build())
            }
        }
    }
}
