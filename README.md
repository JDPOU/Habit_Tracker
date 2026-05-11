# Habit Tracker

A modern, intuitive Android application designed to help users build and maintain healthy habits through consistent tracking, reminders, and progress visualization.

## 🚀 Features

- **User Authentication:** Secure login and registration using Firebase Authentication.
- **Habit Management:** 
    - Create habits with custom names, notes, and colors.
    - Flexible scheduling (Daily, Weekly, Monthly).
    - Select specific days of the week for daily habits.
- **Real-time Synchronization:** Powered by Google Firebase Firestore for instant updates across devices.
- **Smart Reminders:** 
    - Personalized notifications for each habit at your preferred time.
    - **Mark Completed** directly from the notification without opening the app.
- **Progress Tracking:** 
    - Visual history of habit completions using an interactive calendar.
    - View total completion counts for each habit.
- **Persistence:** Habit data and reminders persist even after device reboots.

## 🛠 Tech Stack

- **Language:** [Kotlin](https://kotlinlang.org/)
- **Backend:** [Firebase Firestore](https://firebase.google.com/docs/firestore) (Database), [Firebase Auth](https://firebase.google.com/docs/auth) (Authentication)
- **UI Components:** 
    - [Material Design Components](https://material.io/develop/android)
    - [ConstraintLayout](https://developer.android.com/training/constraintlayout)
    - [RecyclerView](https://developer.android.com/guide/topics/ui/layout/recyclerview)
- **External Libraries:**
    - [Material Calendar View](https://github.com/Applandeo/Material-Calendar-View): For visualizing habit completion history.

## 📂 Project Structure

- `HomeActivity`: The main dashboard showing the list of habits for today.
- `AddHabit`: Activity for creating and editing habits.
- `History`: Calendar-based view to track progress over time.
- `Login/RegisterActivity`: Handles user onboarding and authentication.
- `ReminderReceiver`: Manages firing habit notifications.
- `CompletionActionReceiver`: Handles the background update when a habit is marked "Completed" from a notification.
- `BootReceiver`: Ensures all habit reminders are rescheduled when the device starts.

## 🛠 Technical Implementation

## 🛠 Technical Implementation

### Architecture & Backend
- **Architecture:** Follows a standard Android Activity-based architecture with specialized `BroadcastReceivers` for background operations. For a deep dive, see [Technical Overview](technical_overview.md).
- **Cloud Firestore Schema:** 
  - Data is structured under a `users` collection.
  - Each user has a `habits` sub-collection: `users/{userId}/habits/{habitId}`.
  - Habits store metadata including `completionHistory` (list of ISO dates) and `totalCompletions`.
- **Firebase Authentication:** Uses Email/Password provider for secure user management.

### Scheduling & Notifications
- **AlarmManager:** Utilizes `setExactAndAllowWhileIdle` to ensure reminders trigger even when the device is in Doze mode.
- **Notification Actions:** Implements `PendingIntent.getBroadcast` to allow users to mark habits as completed directly from the notification shade without launching the UI.
- **Boot Persistence:** A `BootReceiver` listens for `ACTION_BOOT_COMPLETED` to reschedule all active alarms stored in Firestore, ensuring reminders are never missed after a restart.

### UI & UX
- **Dynamic Theming:** Habits are color-coded based on user selection, with UI elements updating dynamically using custom XML drawables.
- **Streak Tracking:** Real-time calculation of current completion streaks displayed on the dashboard.
- **Interactive History:** Calendar-based visualization of past performance using `Material-Calendar-View`.

### Key Android Permissions
- `POST_NOTIFICATIONS`: Required for Android 13+ to show habit reminders.
- `SCHEDULE_EXACT_ALARM`: Ensures precise timing for user-defined habit reminders.
- `RECEIVE_BOOT_COMPLETED`: Necessary to restore alarms after a device reboot.

## ⚙️ Setup & Installation

1. **Clone the repository:**
   ```bash
   git clone https://github.com/yourusername/HabitTracker.git
   ```
2. **Firebase Setup:**
   - Create a new project in the [Firebase Console](https://console.firebase.google.com/).
   - Add an Android app with the package name `com.example.habittracker`.
   - Download the `google-services.json` file and place it in the `app/` directory.
   - Enable **Email/Password** authentication in the Firebase Auth section.
   - Create a **Cloud Firestore** database.
3. **Build the project:**
   - Open the project in **Android Studio**.
   - Sync Gradle files and run the app on an emulator or physical device.

## 📝 License

This project is open-source and available under the MIT License.
