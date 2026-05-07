package com.example.habittracker

import android.graphics.Color
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.text.Editable
import android.text.TextWatcher
import android.view.View
import android.widget.Button
import android.widget.EditText
import android.widget.GridLayout
import android.widget.ProgressBar
import android.widget.TextView
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import com.google.firebase.Firebase
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.auth

import com.google.android.material.textfield.TextInputLayout

/**
 * Activity handling user registration.
 * Provides real-time password requirement feedback and validates all input fields
 * before creating a new account in Firebase Auth.
 */
class RegisterActivity : AppCompatActivity() {
    // UI component references
    private lateinit var etEmail: EditText
    private lateinit var etPassword: EditText
    private lateinit var etConfirm: EditText
    private lateinit var tilEmail: TextInputLayout
    private lateinit var tilPassword: TextInputLayout
    private lateinit var tilConfirm: TextInputLayout
    private lateinit var llPasswordRequirements: GridLayout
    private lateinit var tvReqLength: TextView
    private lateinit var tvReqUppercase: TextView
    private lateinit var tvReqLowercase: TextView
    private lateinit var tvReqNumber: TextView
    private lateinit var tvReqSpecial: TextView
    private lateinit var tvRegisterStatus: TextView
    private lateinit var pbRegister: ProgressBar
    private lateinit var btnRegister: Button
    
    // Firebase Authentication instance
    private lateinit var auth: FirebaseAuth

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // Enable edge-to-edge display
        enableEdgeToEdge()
        setContentView(R.layout.activity_register)

        // Initialize Firebase Auth
        auth = Firebase.auth

        // Initialize UI components by finding them in the layout
        etEmail = findViewById(R.id.et_register_email)
        etPassword = findViewById(R.id.et_register_password)
        etConfirm = findViewById(R.id.et_confirm)
        tilEmail = findViewById(R.id.til_register_email)
        tilPassword = findViewById(R.id.til_register_password)
        tilConfirm = findViewById(R.id.til_confirm)
        llPasswordRequirements = findViewById(R.id.ll_password_requirements)
        tvReqLength = findViewById(R.id.tv_req_length)
        tvReqUppercase = findViewById(R.id.tv_req_uppercase)
        tvReqLowercase = findViewById(R.id.tv_req_lowercase)
        tvReqNumber = findViewById(R.id.tv_req_number)
        tvReqSpecial = findViewById(R.id.tv_req_special)
        tvRegisterStatus = findViewById(R.id.tv_register_status)
        btnRegister = findViewById(R.id.btn_create)
        pbRegister = findViewById(R.id.pb_register)


        // Disable creation button until some text is entered in all required fields
        btnRegister.isEnabled = false

        // --- UI Interaction Logic ---

        // Only show the specific password requirements hint when the user is focused on the password field
        etPassword.setOnFocusChangeListener { _, hasFocus ->
            if (hasFocus) {
                llPasswordRequirements.visibility = View.VISIBLE
            }
        }

        // Real-time password requirement validation and button state management
        etPassword.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                updateRegisterButtonState()
            }
            override fun afterTextChanged(s: Editable?) {
                val password = s.toString()
                
                // Dynamically update requirement label colors (Green for pass, Red for fail)
                updateReqColor(tvReqLength, password.length >= 8)
                updateReqColor(tvReqUppercase, password.any { it.isUpperCase() })
                updateReqColor(tvReqLowercase, password.any { it.isLowerCase() })
                updateReqColor(tvReqNumber, password.any { it.isDigit() })
                updateReqColor(tvReqSpecial, password.any { !it.isLetterOrDigit() })
            }
        })

        // Watcher for email input to clear errors and update button state
        etEmail.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                updateRegisterButtonState()
            }
            override fun afterTextChanged(s: Editable?) {
                tilEmail.error = null
                tvRegisterStatus.visibility = View.GONE
            }
        })

        // Watcher for confirm password input to clear errors and update button state
        etConfirm.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                updateRegisterButtonState()
            }
            override fun afterTextChanged(s: Editable?) {
                tilConfirm.error = null
                tvRegisterStatus.visibility = View.GONE
            }
        })

        // --- Registration Execution ---
        btnRegister.setOnClickListener {
            val email = etEmail.text.toString().trim()
            val password = etPassword.text.toString().trim()
            val confirmPassword = etConfirm.text.toString().trim()

            tvRegisterStatus.visibility = View.GONE
            
            var isValid = true

            // 1. Email format validation (regex)
            val emailError = Validations.validateEmail(email)
            if (emailError != null) {
                tilEmail.error = emailError
                isValid = false
            }

            // 2. Comprehensive password requirement validation
            val passwordError = Validations.validatePassword(password)
            if (passwordError != null) {
                tilPassword.error = "Invalid password"
                llPasswordRequirements.visibility = View.VISIBLE
                isValid = false
            }
            
            // 3. Match validation: Password and Confirmation must be identical
            if (password != confirmPassword) {
                tilConfirm.error = "Passwords do not match"
                isValid = false
            }

            // Halt if any local validation failed
            if (!isValid) return@setOnClickListener

            // Show loading state (spinner)
            setLoadingState(true)

            // Attempt to create the user in Firebase Auth
            auth.createUserWithEmailAndPassword(email, password)
                .addOnCompleteListener(this) { task ->
                    // Exit loading state
                    setLoadingState(false)
                    
                    tvRegisterStatus.visibility = View.VISIBLE
                    if (task.isSuccessful) {
                        // Success: Show message and finish activity to return to Login
                        tvRegisterStatus.text = "Registration successful!"
                        tvRegisterStatus.setTextColor(Color.GREEN)
                        
                        Handler(Looper.getMainLooper()).postDelayed({
                            finish()
                        }, 2000)
                    } else {
                        // Failure: Show Firebase error message (e.g., email already exists)
                        tvRegisterStatus.text = "Registration failed: ${task.exception?.message}"
                        tvRegisterStatus.setTextColor(Color.RED)
                    }
                }
        }
    }

    /**
     * Toggles the UI state between loading (saving) and idle.
     * Disables all inputs to prevent user interference during the network call.
     */
    private fun setLoadingState(isLoading: Boolean) {
        pbRegister.visibility = if (isLoading) View.VISIBLE else View.GONE
        btnRegister.isEnabled = !isLoading
        etEmail.isEnabled = !isLoading
        etPassword.isEnabled = !isLoading
        etConfirm.isEnabled = !isLoading
    }

    /**
     * Toggles the Register button state based on whether all fields have some content.
     * Simple presence check to improve UX before final validation.
     */
    private fun updateRegisterButtonState() {
        val email = etEmail.text.toString().trim()
        val password = etPassword.text.toString().trim()
        val confirm = etConfirm.text.toString().trim()
        btnRegister.isEnabled = email.isNotEmpty() && password.isNotEmpty() && confirm.isNotEmpty()
    }

    /**
     * Updates the text color of a password requirement label based on its validity status.
     * @param textView The requirement TextView to update.
     * @param isValid Whether the requirement is currently met.
     */
    private fun updateReqColor(textView: TextView, isValid: Boolean) {
        textView.setTextColor(if (isValid) Color.GREEN else Color.RED)
    }
}
