package com.example.habittracker

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.card.MaterialCardView

import java.text.SimpleDateFormat
import java.util.Locale

/**
 * Adapter for the RecyclerView displaying habits.
 * Uses [ListAdapter] with [DiffUtil] for efficient list updates and smooth animations.
 * 
 * @param onItemClick Callback function triggered when a habit item is tapped (toggles completion).
 * @param onItemLongClick Callback function triggered when a habit item is long-pressed (shows delete dialog).
 */
class HabitAdapter(
    private val onItemClick: (Habit) -> Unit,
    private val onItemLongClick: (Habit) -> Unit
) : ListAdapter<Habit, HabitAdapter.HabitViewHolder>(HabitDiffCallback()) {

    // Date formats for internal logic and user-facing display
    private val sdfInternal = SimpleDateFormat("yyyy-MM-dd", Locale.US)
    private val sdfDisplay = SimpleDateFormat("MMM dd, yyyy", Locale.getDefault())

    /**
     * ViewHolder class for habit items. Caches view references to improve scroll performance.
     */
    class HabitViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val tvName: TextView = view.findViewById(R.id.tv_habit_name)
        val tvNotes: TextView = view.findViewById(R.id.tv_habit_notes)
        val tvCompleteDate: TextView = view.findViewById(R.id.tv_complete_date)
        val tvFrequency: TextView = view.findViewById(R.id.tv_habit_frequency)
        val tvTime: TextView = view.findViewById(R.id.tv_habit_time)
        val tvStreak: TextView = view.findViewById(R.id.tv_habit_streak)
        val card: MaterialCardView = itemView as MaterialCardView
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): HabitViewHolder {
        // Inflate the custom habit item layout
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_habit, parent, false)
        return HabitViewHolder(view)
    }

    override fun onBindViewHolder(holder: HabitViewHolder, position: Int) {
        val habit = getItem(position)
        holder.tvName.text = habit.name
        
        // --- 1. Display Frequency Info ---
        val freqText = when {
            habit.frequency == "Daily" -> {
                if (habit.selectedDays.size >= 7) {
                    "Daily"
                } else if (habit.selectedDays.isNotEmpty()) {
                    habit.selectedDays.joinToString(", ")
                } else {
                    "Daily"
                }
            }
            habit.frequency == "Weekly" && habit.selectedDays.isNotEmpty() -> {
                "Weekly (${habit.selectedDays.joinToString(", ")})"
            }
            habit.frequency == "Monthly" && habit.selectedDays.isNotEmpty() -> {
                val day = habit.selectedDays[0].toIntOrNull() ?: 0
                if (day > 0) {
                    val suffix = when {
                        day in 11..13 -> "th"
                        day % 10 == 1 -> "st"
                        day % 10 == 2 -> "nd"
                        day % 10 == 3 -> "rd"
                        else -> "th"
                    }
                    "Monthly (Every ${day}${suffix})"
                } else "Monthly"
            }
            else -> habit.frequency
        }
        holder.tvFrequency.text = freqText

        // --- 2. Display Scheduled Time ---
        if (habit.reminderTime.isNotEmpty()) {
            holder.tvTime.text = "Scheduled: ${habit.reminderTime}"
            holder.tvTime.visibility = View.VISIBLE
        } else {
            holder.tvTime.visibility = View.GONE
        }

        // --- 3. Calculate and Display Streak ---
        val streak = calculateCurrentStreak(habit.completionHistory)
        if (streak > 0) {
            holder.tvStreak.text = "Streak: $streak day${if (streak > 1) "s" else ""}"
            holder.tvStreak.visibility = View.VISIBLE
        } else {
            holder.tvStreak.visibility = View.GONE
        }

        // --- 4. Display Notes ---
        if (habit.notes.isNotEmpty()) {
            holder.tvNotes.text = habit.notes
            holder.tvNotes.visibility = View.VISIBLE
        } else {
            holder.tvNotes.visibility = View.GONE
        }

        // --- 5. Handle Completion Styling ---
        val today = sdfInternal.format(java.util.Date())
        
        if (habit.completed) {
            // If completed today: Apply strikethrough, fade the card, and hide the "Last completed" date
            holder.tvCompleteDate.visibility = View.GONE
            holder.tvName.paintFlags = holder.tvName.paintFlags or android.graphics.Paint.STRIKE_THRU_TEXT_FLAG
            holder.tvNotes.paintFlags = holder.tvNotes.paintFlags or android.graphics.Paint.STRIKE_THRU_TEXT_FLAG
            holder.card.alpha = 0.6f
            holder.card.cardElevation = 1f
        } else {
            // If NOT completed today: Show "Last completed" text if history exists
            val lastCompletionDate = habit.completionHistory.lastOrNull()

            if (lastCompletionDate != null && lastCompletionDate != today && streak == 0) {
                val displayDate = try {
                    val date = sdfInternal.parse(lastCompletionDate)
                    date?.let { sdfDisplay.format(it) } ?: lastCompletionDate
                } catch (e: Exception) {
                    lastCompletionDate
                }
                holder.tvCompleteDate.text = "Last completed: $displayDate"
                holder.tvCompleteDate.visibility = View.VISIBLE
            } else {
                holder.tvCompleteDate.visibility = View.GONE
            }

            // Reset styling to normal
            holder.tvName.paintFlags = holder.tvName.paintFlags and android.graphics.Paint.STRIKE_THRU_TEXT_FLAG.inv()
            holder.tvNotes.paintFlags = holder.tvNotes.paintFlags and android.graphics.Paint.STRIKE_THRU_TEXT_FLAG.inv()
            holder.card.alpha = 1.0f
            holder.card.cardElevation = 4f
        }

        // --- 6. Interaction Listeners ---
        
        // Single tap with a subtle scale animation
        holder.itemView.setOnClickListener {
            holder.card.animate()
                .scaleX(0.95f)
                .scaleY(0.95f)
                .setDuration(100)
                .withEndAction {
                    holder.card.animate()
                        .scaleX(1f)
                        .scaleY(1f)
                        .setDuration(100)
                        .start()
                    onItemClick(habit)
                }
                .start()
        }

        // Long click for deletion
        holder.itemView.setOnLongClickListener {
            onItemLongClick(habit)
            true
        }
    }

    /**
     * Calculates the current consecutive day streak.
     * A streak is active if the last completion was today or yesterday.
     */
    private fun calculateCurrentStreak(completionHistory: List<String>): Int {
        if (completionHistory.isEmpty()) return 0

        // Parse, normalize, and sort dates in descending order (newest first)
        val sortedDates = completionHistory.mapNotNull { 
            val date = sdfInternal.parse(it)
            if (date != null) {
                val cal = java.util.Calendar.getInstance()
                cal.time = date
                cal.set(java.util.Calendar.HOUR_OF_DAY, 0)
                cal.set(java.util.Calendar.MINUTE, 0)
                cal.set(java.util.Calendar.SECOND, 0)
                cal.set(java.util.Calendar.MILLISECOND, 0)
                cal.time
            } else null
        }.distinct().sortedDescending()
        
        if (sortedDates.isEmpty()) return 0

        // Reference dates for midnight today and yesterday
        val today = java.util.Calendar.getInstance().apply {
            set(java.util.Calendar.HOUR_OF_DAY, 0)
            set(java.util.Calendar.MINUTE, 0)
            set(java.util.Calendar.SECOND, 0)
            set(java.util.Calendar.MILLISECOND, 0)
        }.time

        val yesterday = java.util.Calendar.getInstance().apply {
            time = today
            add(java.util.Calendar.DATE, -1)
        }.time

        // If the most recent completion wasn't today or yesterday, the current streak is broken
        val lastDate = sortedDates[0]
        if (lastDate != today && lastDate != yesterday) {
            return 0
        }

        // Iterate through history to find consecutive days
        var streak = 1
        for (i in 0 until sortedDates.size - 1) {
            val diff = sortedDates[i].time - sortedDates[i + 1].time
            val diffInDays = java.util.concurrent.TimeUnit.MILLISECONDS.toDays(diff)

            if (diffInDays == 1L) {
                streak++
            } else {
                break
            }
        }

        return streak
    }

    /**
     * Callback for calculating the diff between two non-null items in a list.
     * Used by ListAdapter to animate only the items that changed.
     */
    class HabitDiffCallback : DiffUtil.ItemCallback<Habit>() {
        override fun areItemsTheSame(oldItem: Habit, newItem: Habit): Boolean {
            return oldItem.id == newItem.id
        }

        override fun areContentsTheSame(oldItem: Habit, newItem: Habit): Boolean {
            return oldItem == newItem
        }
    }
}
