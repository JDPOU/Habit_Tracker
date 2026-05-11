package com.example.habittracker

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.widget.Toast
import androidx.core.app.NotificationManagerCompat
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import java.text.SimpleDateFormat
import java.util.*

/**
 * BroadcastReceiver that handles the "Mark Completed" action button from a notification.
 * Updates the habit's status in Firestore without requiring the user to open the app.
 */
class CompletionActionReceiver : BroadcastReceiver() {
    /**
     * Called when the BroadcastReceiver is receiving an Intent broadcast from a notification action.
     * Marks the specified habit as completed in Firestore and dismisses the notification.
     *
     * @param context The Context in which the receiver is running.
     * @param intent The Intent being received, containing habit and user metadata.
     */
    override fun onReceive(context: Context, intent: Intent) {
        // Extract necessary IDs and names from the intent
        val habitId = intent.getStringExtra(Constants.EXTRA_HABIT_ID) ?: return
        val userId = intent.getStringExtra(Constants.EXTRA_USER_ID) ?: return
        val habitName = intent.getStringExtra(Constants.EXTRA_HABIT_NAME) ?: "Habit"

        val db = FirebaseFirestore.getInstance()
        // Standardized date format for storage
        val today = SimpleDateFormat(Constants.DATE_FORMAT_INTERNAL, Locale.US).format(Date())

        // Prepare updates for Firestore: toggle completed, add to history, and increment total count
        val updates = mapOf(
            "completed" to true,
            "completionHistory" to FieldValue.arrayUnion(today),
            "totalCompletions" to FieldValue.increment(1)
        )

        // Perform the update in Firestore
        db.collection(Constants.COLLECTION_USERS).document(userId)
            .collection(Constants.COLLECTION_HABITS)
            .document(habitId)
            .update(updates)
            .addOnSuccessListener {
                // Show confirmation to the user
                Toast.makeText(context, "$habitName marked as completed!", Toast.LENGTH_SHORT).show()
                // Cancel/Dismiss the notification once the action is successful
                NotificationManagerCompat.from(context).cancel(habitId.hashCode())
            }
            .addOnFailureListener {
                // Inform the user if the background update failed

                Toast.makeText(context, "Error updating $habitName", Toast.LENGTH_SHORT).show()
            }
    }
}
