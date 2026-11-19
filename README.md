# SmartTodo – Native Android Task Manager

SmartTodo is a full-stack native Android to-do and productivity application built with **Kotlin**, **Jetpack Compose**, **Material 3**, **Firebase Authentication (Google Sign-In via Credential Manager)**, and **Cloud Firestore** with offline persistence.

## Features

- **Google Sign-In & Per-User Isolation**: Authenticates users with Jetpack Credential Manager (`GetSignInWithGoogleOption` + silent auto sign-in for returning users) and scopes all Cloud Firestore profiles and tasks strictly to the signed-in user.
- **Dashboard (Home)**: Time-aware greeting (*Good morning / Good afternoon / Good evening*), today's date, animated task completion progress card, interactive stat cards (**Total, Completed, Pending, Overdue**), and Today's task list.
- **Full Task CRUD**: Create, read, edit, complete, reopen, and delete (with confirmation dialog) tasks with title, optional description, due date, optional due time, priority (`Low`, `Medium`, `High`), and category (`Personal`, `Work`, `Study`, `Other`).
- **Search, Filters & Sorting**:
  - Filters: **All**, **Today**, **Upcoming**, **Pending**, **Overdue**, **Completed**.
  - Search tasks by title in real time.
  - Sort by **Due date**, **Priority**, **Recently created**, or **Alphabetical**.
- **Offline Persistence & Sync**: Uses Cloud Firestore's `PersistentCacheSettings` offline cache and real-time `ConnectivityManager` monitoring to show an offline indicator and automatically synchronize changes when connectivity returns.
- **Local Reminders**: Schedules local notifications before task due times using `AlarmManager` (`setAndAllowWhileIdle`), handles Android 13+ `POST_NOTIFICATIONS` runtime permissions gracefully, supports configurable reminder lead times, and cancels reminders automatically when tasks are completed or deleted.
- **Settings**: User profile card, **Light / Dark / System Default** theme switcher persisted via Jetpack DataStore, notification controls, and safe sign-out.

## Architecture

Clean Architecture + MVVM under `app/src/main/java/com/example/`:

- `domain/model/` — Canonical Firestore-compatible `Task` domain model, `TaskPriority`, and `TaskCategory`
- `model/` — App models (`UserProfile`, `DashboardStats`, `UiState`, filter/sort/theme enums)
- `domain/` — `TaskValidator` and `TaskFilterSorter`
- `data/` — `FirestoreErrorHandler` (structured JSON logging) and `SettingsDataStore`
- `repository/` — `TaskRepository` interface & `FirestoreTaskRepository`
- `authentication/` — `GoogleAuthManager` (Credential Manager interactive, silent, and sign-out flows)
- `notifications/` — `TaskReminderScheduler` & `TaskReminderReceiver`
- `viewmodel/` — `TaskViewModel` & `SettingsViewModel`
- `navigation/` — Auth-gated `SmartTodoNavigation` with adaptive `NavigationBar` / `NavigationRail`
- `ui/theme/` — Material 3 color schemes, shapes, and bundled `Plus Jakarta Sans` + `Inter` typography
- `ui/components/` — `TaskCard`, offline banner, empty/loading/error views, delete dialog
- `ui/screens/` — `AuthScreen`, `HomeScreen`, `TasksScreen`, `CompletedScreen`, `TaskEditorScreen`, `SettingsScreen`
- `utils/` — `DateTimeUtils` & `NetworkMonitor`

## Firestore Database Schema

```text
users/
  {userId}/
    profile/
      info
        userId: string
        name: string
        email: string
        createdAt: timestamp (serverTimestamp)
        updatedAt: timestamp (serverTimestamp)

    tasks/
      {taskId}
        userId: string
        title: string (1..120 chars)
        description: string? (optional, up to 1000 chars)
        dueDate: integer? (optional, UTC start-of-day epoch millis)
        dueTime: string? ("HH:mm", omitted if not set)
        priority: "Low" | "Medium" | "High"
        category: "Personal" | "Work" | "Study" | "Other"
        isCompleted: boolean
        createdAt: timestamp (serverTimestamp, immutable)
        updatedAt: timestamp (serverTimestamp)
```

Security rules live in `/firestore.rules` (enforcing zero-trust per-user ownership, schema validation, immutable `createdAt`/`userId`, and strict allowed-field lists) and are verified by `/firestore.test.js`.

## Setup, Building & Testing

### Prerequisites
- Android Studio (Ladybug or newer) or Gradle with JDK 17+
- Firebase project configured with Google Sign-In and Cloud Firestore (`app/google-services.json` and `app/src/main/res/values/firebase_applet_config.xml` are already provisioned in this project).

### Run Unit & Robolectric Tests
```bash
gradle :app:testDebugUnitTest
```

### Run Firestore Security Rules Tests (Local Emulator)
```bash
firebase emulators:exec --only firestore --project demo-no-project "node --test firestore.test.js"
```

### Build Debug APK
```bash
gradle :app:assembleDebug
```
The debug APK is generated at `app/build/outputs/apk/debug/app-debug.apk`.

### Build Release AAB (Google Play)
Configure your release signing environment variables (`KEYSTORE_PATH`, `STORE_PASSWORD`, `KEY_PASSWORD`) or use the AI Studio export/build menu, then run:
```bash
gradle :app:bundleRelease
```
The signed App Bundle is generated at `app/build/outputs/bundle/release/app-release.aab`.
