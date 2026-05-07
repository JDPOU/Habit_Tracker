package com.example.habittracker

import android.content.Intent
import android.graphics.Color
import android.graphics.Paint
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.View
import android.widget.Button
import android.widget.CheckBox
import android.widget.EditText
import android.widget.ProgressBar
import android.widget.TextView
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import com.google.firebase.Firebase
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.auth

import com.google.android.material.textfield.TextInputLayout

/**
 * Activity handling user login.
 * Checks for existing sessions (Remember Me) and validates credentials against Firebase Auth.
 * Allows users to reset forgotten passwords and navigate to registration.
 */
class Login : AppCompatActivity() {

    // UI component references
    private lateinit var etEmail: EditText
    private lateinit var etPassword: EditText
    private lateinit var tilEmail: TextInputLayout
    private lateinit var tilPassword: TextInputLayout
    private lateinit var tvLoginStatus: TextView
    private lateinit var btnLogin: Button
    private lateinit var btnRegister: Button
    private lateinit var pbLogin: ProgressBar
    private lateinit var tvForgotPW: TextView
    private lateinit var cbRememberMe: CheckBox

    // Firebase Authentication instance
    private lateinit var auth: FirebaseAuth

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // Enable edge-to-edge display for a modern look
        enableEdgeToEdge()
        setContentView(R.layout.login_screen)

        // Initialize Firebase Auth
        auth = Firebase.auth

        // Initialize UI components by finding them in the layout
        etEmail = findViewById(R.id.et_login_email)
        etPassword = findViewById(R.id.et_login_password)
        tilEmail = findViewById(R.id.til_login_email)
        tilPassword = findViewById(R.id.til_password)
        tvLoginStatus = findViewById(R.id.tv_login_status)
        btnLogin = findViewById(R.id.btn_login)
        btnRegister = findViewById(R.id.btn_register)
        pbLogin = findViewById(R.id.pb_login)
        tvForgotPW = findViewById(R.id.tv_forgot_password)
        cbRememberMe = findViewById(R.id.cb_remember_me)

        // --- Auto-Login Logic (Remember Me) ---
        val sharedPref = getSharedPreferences("MyPrefs", MODE_PRIVATE)
        val rememberedEmail = sharedPref.getString("email", null)
        val rememberedPassword = sharedPref.getString("password", null)

        if (rememberedEmail != null && rememberedPassword != null) {
            // If credentials exist in local storage, attempt automatic sign-in
            setLoadingState(true)
            auth.signInWithEmailAndPassword(rememberedEmail, rememberedPassword)
                .addOnCompleteListener(this) { task ->
                    if (task.isSuccessful) {
                        // Success: redirect to Home
                        goToHome(auth.currentUser?.uid)
                    } else {
                        // Failure: clear stored credentials as they may be outdated/invalid
                        setLoadingState(false)
                        with(sharedPref.edit()) {
                            remove("email")
                            remove("password")
                            apply()
                        }
                    }
                }
        } else {
            // Ensure no stale Firebase session exists if "Remember Me" wasn't active
            auth.signOut()
        }

        // Add visual underline to "Forgot Password" to indicate it's clickable
        tvForgotPW.paintFlags = tvForgotPW.paintFlags or Paint.UNDERLINE_TEXT_FLAG

        // Keep login button disabled until the user provides some input
        btnLogin.isEnabled = false

        // --- Real-time Input Validation ---
        val textWatcher = object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                // Enable button only if both email and password fields have text
                val email = etEmail.text.toString().trim()
                val password = etPassword.text.toString().trim()
                btnLogin.isEnabled = email.isNotEmpty() && password.isNotEmpty()
            }
            override fun afterTextChanged(s: Editable?) {
                // Clear error messages as the user starts typing again
                if (etEmail.hasFocus()) {
                    tilEmail.error = null
                }
                if (etPassword.hasFocus()) {
                    tilPassword.error = null
                }
                tvLoginStatus.visibility = View.GONE
            }
        }

        etEmail.addTextChangedListener(textWatcher)
        etPassword.addTextChangedListener(textWatcher)

        // --- Login Action ---
        btnLogin.setOnClickListener {
            val email = etEmail.text.toString().trim()
            val password = etPassword.text.toString().trim()

            // Perform basic validation before calling Firebase
            val emailError = Validations.validateEmail(email)
            val passwordError = if (password.isEmpty()) "Password cannot be empty" else null

            if (emailError != null || passwordError != null) {
                tilEmail.error = emailError
                tilPassword.error = passwordError
                return@setOnClickListener
            }

            // Enter loading state (show progress bar, disable buttons)
            setLoadingState(true)

            // Attempt Firebase sign-in
            auth.signInWithEmailAndPassword(email, password)
                .addOnCompleteListener(this) { task ->
                    // Exit loading state regardless of outcome
                    setLoadingState(false)
                    
                    if (task.isSuccessful) {
                        // Handle "Remember Me" storage based on checkbox state
                        if (cbRememberMe.isChecked) {
                            with(sharedPref.edit()) {
                                putString("email", email)
                                putString("password", password)
                                apply()
                            }
                        } else {
                            with(sharedPref.edit()) {
                                remove("email")
                                remove("password")
                                apply()
                            }
                        }
                        
                        // Proceed to main screen
                        goToHome(auth.currentUser?.uid)
                    } else {
                        // Best practice: show a generic failure message for security
                        tvLoginStatus.text = "Login failed: Invalid email or password."
                        tvLoginStatus.setTextColor(Color.RED)
                        tvLoginStatus.visibility = View.VISIBLE
                    }
                }
        }

        // --- Navigation to Registration ---
        btnRegister.setOnClickListener {
            val intent = Intent(this, RegisterActivity::class.java)
            startActivity(intent)
        }

        // --- Password Reset Action ---
        tvForgotPW.setOnClickListener {
            val email = etEmail.text.toString().trim()
            if (email.isNotEmpty()) {
                auth.sendPasswordResetEmail(email)
                    .addOnCompleteListener { task ->
                        if (task.isSuccessful) {
                            tvLoginStatus.text = "Password reset email sent."
                            tvLoginStatus.setTextColor(Color.GREEN)
                            tvLoginStatus.visibility = View.VISIBLE
                        } else {
                            tvLoginStatus.text = "Failed to send reset email. Check your email address."
                            tvLoginStatus.setTextColor(Color.RED)
                            tvLoginStatus.visibility = View.VISIBLE
                        }
                    }
            } else {
                tilEmail.error = "Please enter your email"
            }
        }
    }

    /**
     * Toggles the UI state between loading and idle.
     * Shows/hides progress bar and enables/disables inputs to prevent duplicate submissions.
     */
    private fun setLoadingState(isLoading: Boolean) {
        pbLogin.visibility = if (isLoading) View.VISIBLE else View.GONE
        btnLogin.isEnabled = !isLoading
        btnRegister.isEnabled = !isLoading
        etEmail.isEnabled = !isLoading
        etPassword.isEnabled = !isLoading
    }

    /**
     * Navigates to the HomeActivity and clears the current activity task.
     * @param userId The unique ID of the logged-in user.
     */
    private fun goToHome(userId: String?) {
        val intent = Intent(this, HomeActivity::class.java)
        intent.putExtra("USER_ID", userId)
        // Ensure user cannot navigate back to the login screen after successful entry
        intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        startActivity(intent)
        finish()
    }
}
