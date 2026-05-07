package com.example.habittracker

import android.app.TimePickerDialog
import android.graphics.Color
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.text.Editable
import android.text.TextWatcher
import android.view.View
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.TextView
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.Toolbar
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import com.google.android.material.button.MaterialButtonToggleGroup
import com.google.android.material.chip.Chip
import com.google.android.material.chip.ChipGroup
import com.google.firebase.Firebase
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.auth
import com.google.firebase.firestore.FirebaseFirestore
import java.util.Calendar
import java.util.Locale

/**
 * Activity for adding a new habit to the user's profile.
 * Handles frequency selection (Daily/Weekly/Monthly), day selection, and optional reminders.
 * Saves validated habit data to Firebase Firestore.
 */
class AddHabit : AppCompatActivity() {
    // UI components
    private lateinit var etHabitName: EditText
    private lateinit var etHabitNotes: EditText
    private lateinit var tvAddStatus: TextView
    private lateinit var btnSaveHabit: Button
    private lateinit var toolbar: Toolbar
    private lateinit var toggleFrequency: MaterialButtonToggleGroup
    private lateinit var layoutDaySelection: LinearLayout
    private lateinit var chipGroupDays: ChipGroup
    private lateinit var btnSelectTime: Button
    private lateinit var tvSelectedTime: TextView
    
    // State variables
    private var selectedFrequency: String = "Daily"
    private var selectedTime: String = ""
    private var selectedDayOfMonth: Int = 0
    private val db = FirebaseFirestore.getInstance()
    private lateinit var auth: FirebaseAuth
    private var userId: String? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContentView(R.layout.add_habit)

        // Set up edge-to-edge layout padding to account for system bars (status/navigation)
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main_add)) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }

        // Initialize Firebase and retrieve User ID from intent
        auth = Firebase.auth
        userId = intent.getStringExtra("USER_ID")

        // Configure the top toolbar with a back navigation button
        toolbar = findViewById(R.id.toolbar_add)
        setSupportActionBar(toolbar)
        toolbar.setNavigationOnClickListener { finish() }

        // Initialize UI component references
        etHabitName = findViewById(R.id.et_habit_name)
        etHabitNotes = findViewById(R.id.et_habit_notes)
        tvAddStatus = findViewById(R.id.tv_add_status)
        btnSaveHabit = findViewById(R.id.btn_save_habit)
        toggleFrequency = findViewById(R.id.toggle_frequency)
        layoutDaySelection = findViewById(R.id.layout_day_selection)
        chipGroupDays = findViewById(R.id.chip_group_days)
        btnSelectTime = findViewById(R.id.btn_select_time)
        tvSelectedTime = findViewById(R.id.tv_selected_time)

        // Initialize default UI state for "Daily" selection
        setAllDaysChecked(true)

        // --- Frequency Selection Logic ---
        toggleFrequency.addOnButtonCheckedListener { _, checkedId, isChecked ->
            if (isChecked) {
                when (checkedId) {
                    R.id.btn_daily -> {
                        selectedFrequency = "Daily"
                        layoutDaySelection.visibility = View.VISIBLE
                        setAllDaysChecked(true) // Auto-check all days for Daily
                    }
                    R.id.btn_weekly -> {
                        selectedFrequency = "Weekly"
                        layoutDaySelection.visibility = View.VISIBLE
                        setAllDaysChecked(false) // Clear for custom selection
                    }
                    R.id.btn_monthly -> {
                        selectedFrequency = "Monthly"
                        layoutDaySelection.visibility = View.GONE
                        btnSelectTime.text = "Select Day and Time"
                    }
                }
            }
        }

        // --- Time/Day Selection Logic ---
        btnSelectTime.setOnClickListener {
            val calendar = Calendar.getInstance()
            
            if (selectedFrequency == "Monthly") {
                // For Monthly habits, first show a DatePicker to choose the day of the month
                val datePickerDialog = android.app.DatePickerDialog(this, { _, _, _, day ->
                    selectedDayOfMonth = day
                    // Once day is selected, immediately show the TimePicker
                    showTimePickerDialog()
                }, calendar.get(Calendar.YEAR), calendar.get(Calendar.MONTH), calendar.get(Calendar.DAY_OF_MONTH))
                datePickerDialog.show()
            } else {
                // For Daily/Weekly, just show the TimePicker
                showTimePickerDialog()
            }
            
            // --- Notification Permission Handling (Android 13+) ---
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
                if (androidx.core.content.ContextCompat.checkSelfPermission(
                        this,
                        android.Manifest.permission.POST_NOTIFICATIONS
                    ) != android.content.pm.PackageManager.PERMISSION_GRANTED
                ) {
                    androidx.core.app.ActivityCompat.requestPermissions(
                        this,
                        arrayOf(android.Manifest.permission.POST_NOTIFICATIONS),
                        101
                    )
                }
            }

            // --- Exact Alarm Check (Android 12+) ---
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.S) {
                val alarmManager = getSystemService(ALARM_SERVICE) as android.app.AlarmManager
                if (!alarmManager.canScheduleExactAlarms()) {
                    // Note: In a production app, we would prompt the user to enable this in settings
                }
            }
        }

        // Disable the save button until at least a habit name is provided
        btnSaveHabit.isEnabled = false

        // Monitor habit name input to toggle save button state
        etHabitName.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                btnSaveHabit.isEnabled = s.toString().trim().isNotEmpty()
                tvAddStatus.visibility = View.GONE
            }
            override fun afterTextChanged(s: Editable?) {}
        })

        // Execute save when button is clicked
        btnSaveHabit.setOnClickListener {
            saveHabit()
        }
    }

    /**
     * Shows the TimePickerDialog and stores the user's choice.
     */
    private fun showTimePickerDialog() {
        val calendar = Calendar.getInstance()
        val hour = calendar.get(Calendar.HOUR_OF_DAY)
        val minute = calendar.get(Calendar.MINUTE)

        val timePickerDialog = TimePickerDialog(this, { _, selectedHour, selectedMinute ->
            val amPm = if (selectedHour < 12) "AM" else "PM"
            val hour12 = if (selectedHour % 12 == 0) 12 else selectedHour % 12
            
            // Store time in standardized Locale.US format for internal consistency
            selectedTime = String.format(Locale.US, "%02d:%02d %s", hour12, selectedMinute, amPm)
            
            // Format time for UI display
            val displayTime = String.format(Locale.getDefault(), "%02d:%02d %s", hour12, selectedMinute, amPm)
            if (selectedFrequency == "Monthly" && selectedDayOfMonth > 0) {
                val suffix = getDayOfMonthSuffix(selectedDayOfMonth)
                tvSelectedTime.text = "Reminder set for the $selectedDayOfMonth$suffix at $displayTime"
            } else {
                tvSelectedTime.text = "Reminder set for $displayTime"
            }
        }, hour, minute, false)
        timePickerDialog.show()
    }

    /**
     * Helper to check or uncheck all day chips in the chip group.
     */
    private fun setAllDaysChecked(checked: Boolean) {
        for (i in 0 until chipGroupDays.childCount) {
            (chipGroupDays.getChildAt(i) as? Chip)?.isChecked = checked
        }
    }

    /**
     * Helper function to return the correct ordinal suffix (st, nd, rd, th) for a day number.
     */
    private fun getDayOfMonthSuffix(n: Int): String {
        if (n in 11..13) return "th"
        return when (n % 10) {
            1 -> "st"
            2 -> "nd"
            3 -> "rd"
            else -> "th"
        }
    }

    /**
     * Gathers all user input, validates it, and uploads the new habit to Firestore.
     */
    private fun saveHabit() {
        val habitName = etHabitName.text.toString().trim()
        val notes = etHabitNotes.text.toString().trim()

        // Safety check for authentication
        if (userId == null) {
            tvAddStatus.text = "Error: User not authenticated"
            tvAddStatus.setTextColor(Color.RED)
            tvAddStatus.visibility = View.VISIBLE
            return
        }

        // --- Collect Selected Days ---
        val selectedDays = mutableListOf<String>()
        if (selectedFrequency == "Daily" || selectedFrequency == "Weekly") {
            for (i in 0 until chipGroupDays.childCount) {
                val chip = chipGroupDays.getChildAt(i) as Chip
                if (chip.isChecked) {
                    selectedDays.add(chip.text.toString())
                }
            }
            
            // Default to all days if "Daily" is selected but no specific days are checked
            if (selectedDays.isEmpty() && selectedFrequency == "Daily") {
                selectedDays.addAll(listOf("Mon", "Tue", "Wed", "Thu", "Fri", "Sat", "Sun"))
            } else if (selectedDays.isEmpty() && selectedFrequency == "Weekly") {
                tvAddStatus.text = "Please select at least one day"
                tvAddStatus.setTextColor(Color.RED)
                tvAddStatus.visibility = View.VISIBLE
                return
            }
        } else if (selectedFrequency == "Monthly") {
            // Ensure a specific day of the month was chosen
            if (selectedDayOfMonth == 0) {
                tvAddStatus.text = "Please select a day of the month"
                tvAddStatus.setTextColor(Color.RED)
                tvAddStatus.visibility = View.VISIBLE
                return
            }
            selectedDays.add(selectedDayOfMonth.toString())
        }

        // Generate a new unique document reference in the user's habits subcollection
        val habitRef = db.collection("users").document(userId!!).collection("habits").document()

        // Create the Habit data object
        val habit = Habit(
            id = habitRef.id,
            name = habitName,
            notes = notes,
            frequency = selectedFrequency,
            selectedDays = selectedDays,
            reminderTime = selectedTime
        )

        // Upload to Firestore
        habitRef.set(habit)
            .addOnSuccessListener {
                // If a reminder time was set, schedule the notification alarm locally
                if (habit.reminderTime.isNotEmpty()) {
                    ReminderManager.scheduleReminder(this, habit)
                }

                tvAddStatus.text = "Habit added successfully!"
                tvAddStatus.visibility = View.VISIBLE
                
                // Navigate back to the home screen after a short delay for feedback
                Handler(Looper.getMainLooper()).postDelayed({
                    finish()
                }, 1500)
            }
            .addOnFailureListener { e ->
                // Provide specific error feedback on failure
                val errorMsg = e.message ?: "Unknown error"
                tvAddStatus.text = "Error adding habit: $errorMsg"
                tvAddStatus.setTextColor(Color.RED)
                tvAddStatus.visibility = View.VISIBLE
            }
    }
}
