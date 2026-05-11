package com.example.habittracker

import android.content.Intent
import android.os.Bundle
import androidx.activity.OnBackPressedCallback
import androidx.appcompat.app.ActionBarDrawerToggle
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.Toolbar
import androidx.core.view.GravityCompat
import androidx.drawerlayout.widget.DrawerLayout
import com.google.android.material.navigation.NavigationView
import com.google.firebase.auth.FirebaseAuth

/**
 * Base activity to handle shared UI logic like the Navigation Drawer and Logout.
 */
abstract class BaseActivity : AppCompatActivity() {

    protected lateinit var drawerLayout: DrawerLayout

    /**
     * Called when the activity is first created.
     * Sets up the shared back press handling to close the navigation drawer if it's open.
     *
     * @param savedInstanceState If the activity is being re-initialized after
     * previously being shut down then this Bundle contains the data it most
     * recently supplied in onSaveInstanceState(Bundle).
     */
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        // Modern approach to handling back button with the OnBackPressedDispatcher
        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                if (::drawerLayout.isInitialized && drawerLayout.isDrawerOpen(GravityCompat.START)) {
                    drawerLayout.closeDrawer(GravityCompat.START)
                } else {
                    // Disable this callback and trigger the default back behavior
                    isEnabled = false
                    onBackPressedDispatcher.onBackPressed()
                    isEnabled = true
                }
            }
        })
    }

    /**
     * Configures the standard navigation drawer for activities.
     *
     * @param drawerLayout The DrawerLayout of the activity.
     * @param navigationView The NavigationView containing the menu items.
     * @param toolbar The Toolbar to use as the action bar.
     * @param activeItemId The ID of the menu item that should be marked as active.
     */
    protected fun setupNavigationDrawer(
        drawerLayout: DrawerLayout,
        navigationView: NavigationView,
        toolbar: Toolbar,
        activeItemId: Int
    ) {
        this.drawerLayout = drawerLayout
        setSupportActionBar(toolbar)

        val toggle = ActionBarDrawerToggle(
            this, drawerLayout, toolbar,
            R.string.navigation_drawer_open, R.string.navigation_drawer_close
        )
        drawerLayout.addDrawerListener(toggle)
        toggle.syncState()

        navigationView.setNavigationItemSelectedListener { menuItem ->
            if (menuItem.itemId != activeItemId) {
                when (menuItem.itemId) {
                    R.id.nav_home -> {
                        startActivity(Intent(this, HomeActivity::class.java))
                        if (activeItemId == R.id.ic_history) finish()
                    }
                    R.id.ic_history -> {
                        startActivity(Intent(this, History::class.java))
                        if (activeItemId == R.id.nav_home) finish()
                    }
                    R.id.menu_logout -> logout()
                }
            }
            drawerLayout.closeDrawer(GravityCompat.START)
            true
        }
        navigationView.setCheckedItem(activeItemId)
    }

    /**
     * Performs the standard logout procedure.
     * Clears local user preferences, signs out from Firebase, and redirects to the Login screen.
     */
    protected fun logout() {
        val sharedPref = getSharedPreferences(Constants.PREFS_NAME, MODE_PRIVATE)
        with(sharedPref.edit()) {
            remove(Constants.PREF_EMAIL)
            apply()
        }
        FirebaseAuth.getInstance().signOut()
        val intent = Intent(this, Login::class.java)
        intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        startActivity(intent)
        finish()
    }
}
