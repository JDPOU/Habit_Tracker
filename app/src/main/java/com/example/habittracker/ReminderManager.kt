package com.example.habittracker

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import com.google.firebase.auth.FirebaseAuth
import java.text.SimpleDateFormat
import java.util.*

/**
 * Utility object for managing habit reminders using AlarmManager.
 * Handles scheduling, updating, and canceling of alarms.
 */
object ReminderManager {

    /**
     * Schedules a single exact alarm for a habit.
     * @param context Application context.
     * @param habit The habit entity containing reminder information.
     */
    fun scheduleReminder(context: Context, habit: Habit) {
        // Only schedule if a reminder time is set
        if (habit.reminderTime.isEmpty()) return

        val appContext = context.applicationContext
        val alarmManager = appContext.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        
        // Intent to trigger the ReminderReceiver
        val intent = Intent(appContext, ReminderReceiver::class.java).apply {
            putExtra(Constants.EXTRA_HABIT_ID, habit.id)
            putExtra(Constants.EXTRA_HABIT_NAME, habit.name)
            putExtra(Constants.EXTRA_USER_ID, FirebaseAuth.getInstance().currentUser?.uid)
        }

        // PendingIntent for the broadcast receiver
        val pendingIntent = PendingIntent.getBroadcast(
            appContext,
            habit.id.hashCode(),
            intent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        // Parse the habit's reminder time (e.g., "08:30 AM") into a Calendar object
        val calendar = Calendar.getInstance()
        val timeFormat = SimpleDateFormat("hh:mm a", Locale.US) // Use Locale.US for consistent parsing
        val date = timeFormat.parse(habit.reminderTime) ?: return
        
        val timeCalendar = Calendar.getInstance().apply {
            time = date
        }

        // Set the alarm time to the parsed values, ensuring it's relative to today
        calendar.set(Calendar.HOUR_OF_DAY, timeCalendar.get(Calendar.HOUR_OF_DAY))
        calendar.set(Calendar.MINUTE, timeCalendar.get(Calendar.MINUTE))
        calendar.set(Calendar.SECOND, 0)

        // If the calculated time has already passed today, schedule for tomorrow
        if (calendar.timeInMillis <= System.currentTimeMillis()) {
            calendar.add(Calendar.DAY_OF_YEAR, 1)
        }

        // Android 12+ (API 31) exact alarm permission check
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && !alarmManager.canScheduleExactAlarms()) {
            // Fallback to non-exact alarm if permission is missing
            alarmManager.setAndAllowWhileIdle(
                AlarmManager.RTC_WAKEUP,
                calendar.timeInMillis,
                pendingIntent
            )
        } else {
            try {
                // Attempt to set an exact alarm that triggers even in Doze mode
                alarmManager.setExactAndAllowWhileIdle(
                    AlarmManager.RTC_WAKEUP,
                    calendar.timeInMillis,
                    pendingIntent
                )
            } catch (_: SecurityException) {
                // Final fallback if permission was revoked between the check and execution
                alarmManager.setAndAllowWhileIdle(
                    AlarmManager.RTC_WAKEUP,
                    calendar.timeInMillis,
                    pendingIntent
                )
            }
        }
    }

    /**
     * Cancels any pending reminders for a specific habit.
     * @param context Application context.
     * @param habitId The unique ID of the habit whose reminder should be canceled.
     */
    fun cancelReminder(context: Context, habitId: String) {
        val appContext = context.applicationContext
        val alarmManager = appContext.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val intent = Intent(appContext, ReminderReceiver::class.java)
        
        // Find the existing PendingIntent (if any)
        val pendingIntent = PendingIntent.getBroadcast(
            appContext,
            habitId.hashCode(),
            intent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_NO_CREATE
        )
        
        // If found, cancel the alarm and the PendingIntent itself
        if (pendingIntent != null) {
            alarmManager.cancel(pendingIntent)
            pendingIntent.cancel()
        }
    }
}
