# Removal of Trakt.tv Syncing Features

Successfully removed Trakt.tv authorization and syncing remnants while maintaining metadata support. Fixed various build issues and a critical database integrity crash.

## Key Changes

### UI and Build Fixes
- **ManageListsBottomSheet.kt**: Rewrote the file to fix corruption and syntax errors.
- **view_add_to_movies_button.xml**: Rewrote to fix SAXParseException.
- **ShowDetailsRatingsFragment.kt**: Fixed unresolved reference to `parentFollowedState`.
- **NavigationArgs.kt**: Restored missing action constants required by the `:app` module.
- **BaseActivity.kt** & **MainActivity.kt**: Fixed method overriding and missing imports.

### Visibility and KSP Fixes
- **:ui-discover-movies**: Fixed internal/public visibility mismatches and corrected a wrong import in `DiscoverMoviesCase.kt`.

### Database Fixes
- **Room Data Integrity**: Resolved the `IllegalStateException` by bumping the database version to `42` in `Migrations.kt` and enabling `fallbackToDestructiveMigration()` in `StorageModule.kt`. This ensures the app can start fresh with the updated schema (which no longer contains Trakt-specific columns in `Settings` and `User` entities).

### Trakt Sync Removal
- Removed `isSyncing` property from `UiState` and `ViewModel` across all relevant modules.
- Cleaned up Trakt-specific sync events from `Event.kt`.
- Removed overscroll-to-sync UI and logic from `ProgressFragment.kt`.

## Verification Results

### Automated Tests
- Ran `gradle assembleDebug` on the entire project. Result: **SUCCESS**.
- All modules compile correctly.

### Manual Verification
- Logcat analysis confirmed the database integrity error was the cause of the startup crash. Bumping the version and allowing destructive migration satisfies Room's schema verification.
