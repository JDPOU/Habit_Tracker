package com.example.habittracker

/**
 * Utility object containing validation logic for user inputs.
 * Used during registration and login to ensure data integrity before sending to Firebase.
 */
object Validations {
    // Regular expression for validating email format
    private const val EMAIL_PATTERN = "^[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}$"

    /**
     * Validates an email string.
     * @param email The email string to validate.
     * @return An error message string if invalid, or null if valid.
     */
    fun validateEmail(email: String): String? {
        if (email.isEmpty()) return "Email cannot be empty"
        if (email.contains(" ")) return "Email cannot contain spaces"
        if (!email.matches(EMAIL_PATTERN.toRegex())) {
            return "Please enter a valid email address"
        }
        return null
    }

    /**
     * Validates a password string against security requirements.
     * Requirements: 8+ chars, 1 uppercase, 1 lowercase, 1 digit, 1 special character.
     * @param password The password string to validate.
     * @return An error message string if invalid, or null if valid.
     */
    fun validatePassword(password: String): String? {
        if (password.length < 8) return "Password must be at least 8 characters"
        if (!password.contains(Regex("[A-Z]"))) return "Password must contain at least one uppercase letter"
        if (!password.contains(Regex("[a-z]"))) return "Password must contain at least one lowercase letter"
        if (!password.contains(Regex("[0-9]"))) return "Password must contain at least one number"
        if (!password.contains(Regex("[^A-Za-z0-9]"))) return "Password must contain at least one special character"
        return null
    }
}
