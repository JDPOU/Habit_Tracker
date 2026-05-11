package com.example.habittracker

/**
 * Centralized constants for the application to prevent typos and ease maintenance.
 */
object Constants {
    // Firestore Collections
    const val COLLECTION_USERS = "users"
    const val COLLECTION_HABITS = "habits"

    // Intent Extras
    const val EXTRA_USER_ID = "USER_ID"
    const val EXTRA_EDIT_HABIT = "EDIT_HABIT"
    const val EXTRA_HABIT_ID = "HABIT_ID"
    const val EXTRA_HABIT_NAME = "HABIT_NAME"

    // Shared Preferences
    const val PREFS_NAME = "MyPrefs"
    const val PREF_EMAIL = "email"

    // Date Formats
    const val DATE_FORMAT_INTERNAL = "yyyy-MM-dd"
}
