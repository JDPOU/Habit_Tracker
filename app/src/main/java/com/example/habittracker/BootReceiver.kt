package com.example.habittracker

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore

/**
 * BroadcastReceiver that listens for system boot completion.
 * Responsible for rescheduling all active habit reminders after a device restart,
 * as AlarmManager alarms are cleared on reboot.
 */
class BootReceiver : BroadcastReceiver() {
    /**
     * Called when the BroadcastReceiver is receiving an Intent broadcast.
     * Triggers the rescheduling of habit alarms if the system has finished booting.
     *
     * @param context The Context in which the receiver is running.
     * @param intent The Intent being received.
     */
    override fun onReceive(context: Context, intent: Intent) {
        // Only proceed if the action is boot completed
        if (intent.action == Intent.ACTION_BOOT_COMPLETED) {
            // Get current user ID; if not logged in, we can't fetch habits
            val userId = FirebaseAuth.getInstance().currentUser?.uid ?: return
            val db = FirebaseFirestore.getInstance()

            // Fetch all habits for the user from Firestore
            db.collection(Constants.COLLECTION_USERS).document(userId)
                .collection(Constants.COLLECTION_HABITS)
                .get()
                .addOnSuccessListener { result ->
                    val habitList = result.toObjects(Habit::class.java)
                    // Reschedule reminders for any habit that has a reminder time set
                    for (habit in habitList) {
                        if (habit.reminderTime.isNotEmpty()) {
                            ReminderManager.scheduleReminder(context, habit)
                        }
                    }
                }
        }
    }
}
