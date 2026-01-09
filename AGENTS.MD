# Bossless Companion App – Agent & Coding Guide

**Architecture:** MVVM + Clean Architecture | **UI:** Jetpack Compose | **DI:** Hilt
**Native Android App (Kotlin + Jetpack Compose)**  
**Last Updated:** December 14, 2025

---

## ⚡ Core Principles

1.  **UI → ViewModel → Repository → (API / Local DB)**. This is the required data flow. No exceptions.
2.  **Single Source of Truth.** The `Repository` is the single source of truth. UI/ViewModels must not know whether data comes from the network or local cache.
3.  **Offline-First.** The UI must **always** load data from the local Room database first. Network calls are for syncing/updating the cache, not for direct UI display.
4.  **State Management.** `ViewModels` expose a single `StateFlow<UiState>` to the UI. The `UiState` data class represents all information the UI needs to render.
5.  **Handle All Errors.** Repository methods must return `Result<T>`. All failures must be propagated to the ViewModel and result in a user-visible message (e.g., a Snackbar). No silent failures.

---

## 🚀 Directory & Task Mapping

| Task To Perform                               | Primary Location(s) to Edit                       |
|-----------------------------------------------|---------------------------------------------------|
| **Change UI Layout or Style**                 | `ui/screens/` & `ui/components/` & `ui/theme/`    |
| **Add/Modify UI Logic & User Actions**        | `[Screen]ViewModel.kt`                            |
| **Fetch/Orchestrate Data** (Network or Cache) | `data/repository/`                                |
| **Add New API Endpoints**                     | `data/api/ApiService.kt`                          |
| **Define API Data Structures**                | `data/models/`                                    |
| **Define Local Database Schema/Queries**      | `data/local/db/` (DAOs & Entities)                |
| **Add New Dependencies (DI)**                 | `di/`                                             |

---

## ✅ Workflow for Adding a Feature (e.g., "Fetch Tasks")

1.  **`data/models/Task.kt`**: Create the Kotlin `data class` for the `Task` object from the API.
2.  **`data/api/ApiService.kt`**: Add the new Retrofit function: `suspend fun getTasks(): List<Task>`.
3.  **`data/local/db/TaskDao.kt`**: Add Room DAO methods (`@Insert`, `@Query("SELECT * ...")`).
4.  **`data/repository/TaskRepository.kt`**:
    -   Create `getTasks(): Flow<List<Task>>` that returns a `Flow` from the DAO.
    -   Create `suspend fun refreshTasks(): Result<Unit>`. Inside a `try/catch`, call the API, clear & save results to the Room DB, and return `Result.success(Unit)` or `Result.failure(e)`.
5.  **`ui/screens/tasks/TasksViewModel.kt`**:
    -   Inject `TaskRepository`.
    -   Expose `StateFlow` that collects the `getTasks()` flow from the repository.
    -   Create a function `onRefresh()` that calls `refreshTasks()` and updates the UI state with any loading status or errors.
6.  **`ui/screens/tasks/TasksScreen.kt`**:
    -   Collect the `UiState` from the `ViewModel`.
    -   Display the list of tasks.
    -   Call `viewModel.onRefresh()` when the user pulls to refresh.

---

## ⚡ Quick Reference

| What         | Where                                 |
|--------------|---------------------------------------|
| UI           | `app/src/main/java/.../ui/`           |
| Data Models  | `app/src/main/java/.../data/models/`  |
| API Layer    | `app/src/main/java/.../data/api/`     |
| Local Cache  | `app/src/main/java/.../data/local/db/`|
| DI           | `app/src/main/java/.../di/`           |
| Repository   | `app/src/main/java/.../data/repository/` |

---

## 🚀 Data Flow & Storage

- **API Interaction:**
  - Use Retrofit + OkHttp for all network calls.
  - Always use PostgREST filter syntax (e.g., `eq.` prefix) for queries.
  - All API calls are made via repositories, never directly from UI.

- **Local Storage:**
  - Use Room for structured caching of jobs, notifications, etc.
  - On successful API fetch, update Room cache; on failure, serve from cache.
  - Store auth tokens and small settings in EncryptedSharedPreferences or DataStore.
  - Expose cached data as Flow from DAOs for reactive UI updates.

- **Offline Support:**
  - UI always displays cached data if available.
  - Show "Last updated" timestamp for user awareness.
  - On refresh, if server is unavailable, show a toast/snackbar.

## 🔄 Data Synchronization Pattern

To ensure data consistency between the mobile app (offline-capable) and the web app, follow this synchronization pattern:

1.  **Local First (Immediate UI Feedback):**
    *   Create/Update data in the local Room database first.
    *   Mark these entries as `synced = false`.
    *   The UI observes the local database (Single Source of Truth).

2.  **Immediate Upstream Sync (Best Effort):**
    *   Immediately after local save, attempt to send data to the API.
    *   **Success:** Update the local entry with the server ID and set `synced = true`.
    *   **Failure (Offline/Error):** Leave as `synced = false`. The user can continue working.

3.  **Lazy Upstream Sync (Recovery):**
    *   When entering a relevant screen (e.g., Job Detail), check for any `synced = false` entries.
    *   Push them to the server (handling both active and completed states).
    *   Update local records to `synced = true` on success.

4.  **Downstream Sync (Refresh):**
    *   After the upstream sync attempt, fetch the latest data from the server.
    *   Upsert (Insert/Update) into the local database.
    *   This ensures the UI reflects changes made on other devices (Web App).

---

## 🧑‍💻 Coding Best Practices

- **Architecture:**
  - MVVM with Clean Architecture principles.
  - UI → ViewModel → Repository → API/DB.
  - No business logic in UI or ViewModel; keep it in repositories.

- **Dependency Injection:**
  - Use Hilt for all DI (ViewModels, repositories, DAOs, etc.).

- **Error Handling:**
  - Always return Result<T> from repository methods.
  - Never swallow exceptions; surface errors to ViewModel/UI.
  - Use sealed classes or state objects for UI state.

- **Code Cleanliness:**
  - Avoid unnecessary "Helpers" or utility classes—prefer clear, testable functions in the appropriate layer.
  - Keep extension functions close to their usage (e.g., mapping functions in the same file as entities/models).
  - Use consistent naming and file structure.
  - Remove unused code and imports regularly.

- **Testing:**
  - Write unit tests for repositories and ViewModels.
  - Use fake API/DAO implementations for testing business logic.

---

## 🔄 Consistent Problem Solving

- **Data Sync:**
  - Always update local cache after successful network fetch.
  - Never block UI on network; always show cached data first.

- **State Management:**
  - Use StateFlow for all ViewModel state.
  - Expose immutable StateFlow to UI; keep MutableStateFlow private.

- **No Fallbacks:**
  - Avoid "magic" fallback logic or silent failures.
  - Always inform the user of errors (e.g., via snackbar/toast).

---

## 🧹 Keeping the Codebase Clean

- Group related files by feature/module.
- Document non-obvious logic with concise comments.
- Prefer composition over inheritance.
- Review and refactor regularly—remove dead code.

---

## 📋 Checklist Before Merging

- [ ] No unused imports or files.
- [ ] All user-facing errors are handled and surfaced.
- [ ] Data is always loaded from cache first, then network.
- [ ] No direct API calls from UI or ViewModel.
- [ ] Code follows project structure and naming conventions.

---

## 🛑 Import Statement Policy

- **All import statements must be placed at the very top of each Kotlin file, immediately after the `package` declaration.**
- **Do not move imports into the body of a class, function, or after any top-level declaration.**
- This is required for the Kotlin compiler and to avoid build errors like `imports are only allowed in the beginning of file`.
- When editing or reviewing code, always check that imports are at the top and not duplicated or misplaced.
- [Android Architecture Guide](https://developer.android.com/jetpack/guide)
- [Room Persistence Library](https://developer.android.com/jetpack/androidx/releases/room)
- [Hilt DI](https://developer.android.com/training/dependency-injection/hilt-android)
- [Kotlin Coroutines & Flow](https://kotlinlang.org/docs/flow.html)

---

## Additional Guidelines

- **Do not insert new functions or logic at the top level of a Kotlin file unless it is a top-level declaration that is intentionally not part of a class or object.**
- **When adding new logic, always reference the correct class or object context.** For example, member functions that use `viewModelScope`, `this`, or class properties must be placed inside the relevant class (e.g., `class TimerViewModel`).
- Placing such code outside the class will cause unresolved reference errors and break the build.
