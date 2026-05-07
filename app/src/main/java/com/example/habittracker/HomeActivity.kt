package com.example.habittracker

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.ActionBarDrawerToggle
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.Toolbar
import androidx.core.view.GravityCompat
import androidx.drawerlayout.widget.DrawerLayout
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.floatingactionbutton.FloatingActionButton
import com.google.android.material.navigation.NavigationView
import com.google.firebase.Firebase
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.auth
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Main activity showing the list of habits for the logged-in user.
 * Handles displaying, adding, completing, and deleting habits.
 * It also includes logic to reset habit completion status at the start of a new day.
 */
class HomeActivity : AppCompatActivity() {
    // UI components
    private lateinit var btnAddHabit: FloatingActionButton
    private lateinit var rvHabits: RecyclerView
    private lateinit var adapter: HabitAdapter
    private lateinit var toolbar: Toolbar
    private lateinit var drawerLayout: DrawerLayout
    private lateinit var navigationView: NavigationView
    private lateinit var tvEmptyState: TextView

    // Firebase and Data
    private val db = FirebaseFirestore.getInstance()
    private lateinit var auth: FirebaseAuth
    private var userId: String? = null

    // Standardized date format for internal logic and storage (ISO 8601)
    private val sdfInternal = SimpleDateFormat("yyyy-MM-dd", Locale.US)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.home_screen)

        // Initialize Firebase Auth and get current user
        auth = Firebase.auth
        userId = auth.currentUser?.uid

        // Initialize UI components
        drawerLayout = findViewById(R.id.drawer_layout)
        navigationView = findViewById(R.id.nav_view)
        toolbar = findViewById(R.id.toolbar)
        btnAddHabit = findViewById(R.id.btn_addhabit)
        rvHabits = findViewById(R.id.rv_habits)
        tvEmptyState = findViewById(R.id.tv_empty_state)

        // Set up the top toolbar
        setSupportActionBar(toolbar)

        // Set up the Navigation Drawer (Hamburger Menu)
        val toggle = ActionBarDrawerToggle(
            this, drawerLayout, toolbar,
            R.string.navigation_drawer_open, R.string.navigation_drawer_close
        )
        drawerLayout.addDrawerListener(toggle)
        toggle.syncState()

        // Handle navigation menu item selections
        navigationView.setNavigationItemSelectedListener { menuItem ->
            when (menuItem.itemId) {
                R.id.nav_home -> {
                    // Already on Home, do nothing
                }
                R.id.ic_history -> {
                    startActivity(Intent(this, History::class.java))
                }
                R.id.menu_logout -> {
                    logout()
                }
            }
            drawerLayout.closeDrawer(GravityCompat.START)
            true
        }
        navigationView.setCheckedItem(R.id.nav_home)

        // Initialize the RecyclerView for habits
        setupRecyclerView()

        // Floating Action Button (FAB) to navigate to the AddHabit screen
        btnAddHabit.setOnClickListener {
            val intent = Intent(this, AddHabit::class.java)
            intent.putExtra("USER_ID", userId)
            startActivity(intent)
        }
    }

    override fun onResume() {
        super.onResume()
        // Refresh the habits list from Firestore whenever the activity returns to focus
        if (userId != null) {
            loadHabits()
        }
    }

    /**
     * Signs out the user, clears remembered credentials from local storage,
     * and redirects the user back to the Login screen.
     */
    private fun logout() {
        // Clear remembered credentials from SharedPreferences
        val sharedPref = getSharedPreferences("MyPrefs", Context.MODE_PRIVATE)
        with(sharedPref.edit()) {
            remove("email")
            remove("password")
            apply()
        }

        // Sign out from Firebase
        auth.signOut()

        // Navigate back to LoginActivity and clear the activity task stack
        val intent = Intent(this, Login::class.java)
        intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        startActivity(intent)
        finish()
    }

    /**
     * Configures the RecyclerView with its adapter and click listeners.
     */
    private fun setupRecyclerView() {
        adapter = HabitAdapter( 
            onItemClick = { habit ->
                // Toggle habit completion when an item is tapped
                toggleHabitCompletion(habit)
            },
            onItemLongClick = { habit ->
                // Show a deletion confirmation dialog when an item is long-pressed
                showDeleteConfirmationDialog(habit)
            }
        )
        
        rvHabits.adapter = adapter
    }

    /**
     * Toggles the completion status of a habit in Firestore and updates the history.
     * @param habit The habit to toggle.
     */
    private fun toggleHabitCompletion(habit: Habit) {
        val currentUserId = userId ?: return
        val newStatus = !habit.completed
        val today = sdfInternal.format(Date())

        val updates = mutableMapOf<String, Any?>(
            "completed" to newStatus
        )

        if (newStatus) {
            // If marking as completed: add today's date to history and increment total completions
            updates["completionHistory"] = FieldValue.arrayUnion(today)
            updates["totalCompletions"] = FieldValue.increment(1)
        } else {
            // If un-completing: remove today's date from history and decrement total completions
            updates["completionHistory"] = FieldValue.arrayRemove(today)
            updates["totalCompletions"] = FieldValue.increment(-1)
        }

        // Update the habit document in Firestore
        db.collection("users").document(currentUserId).collection("habits")
            .document(habit.id)
            .update(updates)
            .addOnSuccessListener {
                // Reload list to reflect changes immediately
                loadHabits()
            }
            .addOnFailureListener { e ->
                Toast.makeText(this, "Error updating habit: ${e.message}", Toast.LENGTH_SHORT).show()
            }
    }

    /**
     * Displays a confirmation dialog before permanently deleting a habit.
     */
    private fun showDeleteConfirmationDialog(habit: Habit) {
        AlertDialog.Builder(this)
            .setTitle("Delete Habit")
            .setMessage("Are you sure you want to delete \"${habit.name}\"?")
            .setPositiveButton("Delete") { _, _ ->
                deleteHabit(habit)
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    /**
     * Deletes a habit document from Firestore and cancels its scheduled reminder.
     * @param habit The habit entity to delete.
     */
    private fun deleteHabit(habit: Habit) {
        val currentUserId = userId ?: return
        db.collection("users").document(currentUserId).collection("habits")
            .document(habit.id)
            .delete()
            .addOnSuccessListener {
                // Successfully deleted; now cancel any pending reminders for this habit
                ReminderManager.cancelReminder(this, habit.id)
                loadHabits()
            }
            .addOnFailureListener { e ->
                Toast.makeText(this, "Error deleting habit: ${e.message}", Toast.LENGTH_SHORT).show()
            }
    }

    /**
     * Fetches all habits for the current user from Firestore and updates the UI list.
     * Also checks if habits need their daily "completed" status reset based on the date.
     */
    private fun loadHabits() {
        val currentUserId = userId ?: return
        db.collection("users").document(currentUserId).collection("habits")
            .get()
            .addOnSuccessListener { result ->
                val habitList = result.toObjects(Habit::class.java)
                
                // Get today's date string for comparison
                val today = sdfInternal.format(Date())
                
                // --- Habit Reset Logic ---
                // If a habit was completed on a previous day, its 'completed' status must be reset to false for today.
                var needsRefresh = false
                for (habit in habitList) {
                    val lastCompletedDate = habit.completionHistory.lastOrNull()
                    if (habit.completed && lastCompletedDate != today) {
                        resetHabit(habit)
                        needsRefresh = true
                    }
                }
                
                // Only submit the list to the adapter if no resets were triggered (to prevent double UI updates)
                if (!needsRefresh) {
                    adapter.submitList(habitList)
                }

                // Show a helpful message if the list is empty
                tvEmptyState.visibility = if (habitList.isEmpty()) View.VISIBLE else View.GONE
            }
            .addOnFailureListener { e ->
                Toast.makeText(this, "Error loading habits: ${e.message}", Toast.LENGTH_SHORT).show()
            }
    }

    /**
     * Resets a single habit's 'completed' status in Firestore for the new day.
     */
    private fun resetHabit(habit: Habit) {
        val currentUserId = userId ?: return
        db.collection("users").document(currentUserId).collection("habits")
            .document(habit.id)
            .update(mapOf(
                "completed" to false
            ))
            .addOnSuccessListener {
                // Re-load all habits once the reset is acknowledged by the server
                loadHabits()
            }
    }

    /**
     * Handles the back button to close the side drawer if it's currently open.
     */
    @Deprecated("Deprecated in Java")
    override fun onBackPressed() {
        if (drawerLayout.isDrawerOpen(GravityCompat.START)) {
            drawerLayout.closeDrawer(GravityCompat.START)
        } else {
            super.onBackPressed()
        }
    }
}
