package com.example.habittracker

import android.os.Bundle
import android.view.View
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.widget.Toolbar
import androidx.core.graphics.toColorInt
import com.applandeo.materialcalendarview.CalendarDay
import com.applandeo.materialcalendarview.CalendarView
import com.applandeo.materialcalendarview.listeners.OnCalendarDayClickListener
import com.applandeo.materialcalendarview.listeners.OnCalendarPageChangeListener
import com.google.android.material.navigation.NavigationView
import com.google.firebase.Firebase
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.auth
import com.google.firebase.firestore.FirebaseFirestore
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

/**
 * Activity for viewing the history of completed habits.
 * Uses a CalendarView to allow users to see which habits were completed on specific dates.
 * Displays monthly statistics and allows side-by-side comparison of daily and monthly data.
 */
class History : BaseActivity() {
    // UI components
    private lateinit var calendarView: CalendarView
    private lateinit var tvHistoryDate: TextView
    private lateinit var tvHistoryHabits: TextView
    private lateinit var tvHistoryEmpty: TextView
    private lateinit var tvMonthlyStats: TextView
    private lateinit var toolbar: Toolbar
    private lateinit var navigationView: NavigationView

    // Firebase and Data
    private val db = FirebaseFirestore.getInstance()
    private lateinit var auth: FirebaseAuth
    private var userId: String? = null
    
    // Cached list of all habits for the user to avoid multiple network calls
    private val allHabits = mutableListOf<Habit>()
    
    // Standardized date formats
    private val sdfInternal = SimpleDateFormat(Constants.DATE_FORMAT_INTERNAL, Locale.US)
    private val sdfDisplay = SimpleDateFormat("MMM dd, yyyy", Locale.getDefault())

    /**
     * Called when the activity is first created.
     * Initializes UI components, Firebase Auth, and sets up the calendar and navigation drawer.
     *
     * @param savedInstanceState If the activity is being re-initialized after
     * previously being shut down then this Bundle contains the data it most
     * recently supplied in onSaveInstanceState(Bundle).
     */
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.history_screen)

        // Initialize Firebase Auth and get current user
        auth = Firebase.auth
        userId = auth.currentUser?.uid

        // Link UI components to their XML counterparts
        calendarView = findViewById(R.id.calendarView)
        tvHistoryDate = findViewById(R.id.tv_history_date)
        tvHistoryHabits = findViewById(R.id.tv_history_habits)
        tvHistoryEmpty = findViewById(R.id.tv_history_empty)
        tvMonthlyStats = findViewById(R.id.tv_monthly_stats)
        toolbar = findViewById(R.id.toolbar)
        drawerLayout = findViewById(R.id.drawer_layout)
        navigationView = findViewById(R.id.nav_view)

        // Set up the shared Navigation Drawer
        setupNavigationDrawer(drawerLayout, navigationView, toolbar, R.id.ic_history)

        // Listener for selecting a specific day on the calendar
        calendarView.setOnCalendarDayClickListener(object : OnCalendarDayClickListener {
            override fun onClick(calendarDay: CalendarDay) {
                val clickedDayCalendar = calendarDay.calendar
                val internalDate = sdfInternal.format(clickedDayCalendar.time)
                showHabitsForDate(internalDate)
            }
        })

        // Listeners for month navigation to update monthly statistics
        calendarView.setOnForwardPageChangeListener(object : OnCalendarPageChangeListener {
            override fun onChange() {
                displayMonthlyStats(calendarView.currentPageDate)
            }
        })

        calendarView.setOnPreviousPageChangeListener(object : OnCalendarPageChangeListener {
            override fun onChange() {
                displayMonthlyStats(calendarView.currentPageDate)
            }
        })

        // Fetch user data if authenticated
        if (userId != null) {
            fetchAllHabits()
        }
    }

    /**
     * Fetches the user's entire habit collection from Firestore.
     * This data is used to populate the calendar indicators and calculate statistics.
     */
    private fun fetchAllHabits() {
        db.collection(Constants.COLLECTION_USERS).document(userId!!)
            .collection(Constants.COLLECTION_HABITS)
            .get()
            .addOnSuccessListener { result ->
                allHabits.clear()
                allHabits.addAll(result.toObjects(Habit::class.java))

                // Place visual indicators on the calendar for completed days
                updateCalendarIndicators()
                
                // Refresh statistics for the currently visible month
                displayMonthlyStats(calendarView.currentPageDate)

                // Default the daily view to show today's completions
                val today = sdfInternal.format(Date())
                showHabitsForDate(today)
            }
            .addOnFailureListener { e ->
                Toast.makeText(this, "Error fetching history: ${e.message}", Toast.LENGTH_SHORT).show()
            }
    }

    /**
     * Scans the completion history of all habits and adds a visual dot/indicator
     * to every date on the calendar where at least one habit was completed.
     */
    private fun updateCalendarIndicators() {
        val calendarDays = mutableListOf<CalendarDay>()
        
        // Find all unique dates across the completion history of all habits
        val completedDates = allHabits.flatMap { it.completionHistory }.distinct()

        for (dateStr in completedDates) {
            try {
                val date = sdfInternal.parse(dateStr)
                if (date != null) {
                    val calendar = Calendar.getInstance()
                    calendar.time = date
                    
                    // Assign an indicator image to each completed date
                    val calendarDay = CalendarDay(calendar)
                    val pill = androidx.core.content.ContextCompat.getDrawable(this, android.R.drawable.button_onoff_indicator_off)?.mutate()

                    pill?.setTint("#1F998E".toColorInt())

                    calendarDay.imageDrawable = pill
                    calendarDays.add(calendarDay)
                }
            } catch (_: Exception) {
                // Ignore parsing errors for individual dates to prevent crashes
            }
        }
        // Update the calendar UI component with the new list of indicators
        calendarView.setCalendarDays(calendarDays)
    }

    /**
     * Tallies how many times each habit was completed during the visible calendar month.
     * @param calendar A Calendar object representing any day within the target month.
     */
    private fun displayMonthlyStats(calendar: Calendar) {
        // Create a prefix (e.g., "2023-10") to filter history date strings
        val targetMonthPrefix = SimpleDateFormat("yyyy-MM", Locale.US).format(calendar.time)
        val targetMonthYearLabel = SimpleDateFormat("MMM yyyy", Locale.getDefault()).format(calendar.time)
        
        val habitCounts = mutableMapOf<String, Int>()

        // Count occurrences of the month prefix in each habit's history
        for (habit in allHabits) {
            val count = habit.completionHistory.count { 
                it.startsWith(targetMonthPrefix)
            }
            if (count > 0) {
                habitCounts[habit.name] = count
            }
        }

        // Sort habits by most completions to least
        val sortedStats = habitCounts.toList().sortedByDescending { it.second }
        
        if (sortedStats.isNotEmpty()) {
            val statsText = StringBuilder()
            statsText.append("Stats for $targetMonthYearLabel:\n")
            sortedStats.forEach { (name, count) ->
                statsText.append("• $name: $count time(s)\n")
            }
            tvMonthlyStats.text = statsText.toString().trim()
        } else {
            tvMonthlyStats.text = "No habits completed in $targetMonthYearLabel."
        }
    }

    /**
     * Filters the cached habit list for completions on a specific date and displays them.
     * @param dateStr The date in internal storage format (yyyy-MM-dd).
     */
    private fun showHabitsForDate(dateStr: String) {
        // Format the date for a more user-friendly header (e.g., Oct 25, 2023)
        val displayDate = try {
            val date = sdfInternal.parse(dateStr)
            date?.let { sdfDisplay.format(it) } ?: dateStr
        } catch (e: Exception) {
            dateStr
        }
        
        tvHistoryDate.text = displayDate
        tvHistoryDate.visibility = View.VISIBLE

        // Filter habits where the selected date is in the completion history
        val completedHabits = allHabits.filter { habit ->
            habit.completionHistory.contains(dateStr)
        }

        // Update UI visibility based on whether habits were found
        if (completedHabits.isNotEmpty()) {
            val habitsText = completedHabits.joinToString("\n") { "• ${it.name}" }
            tvHistoryHabits.text = habitsText
            tvHistoryHabits.visibility = View.VISIBLE
            tvHistoryEmpty.visibility = View.GONE
        } else {
            tvHistoryHabits.visibility = View.GONE
            tvHistoryEmpty.visibility = View.VISIBLE
        }
    }
}
