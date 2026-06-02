# Remove Trakt.tv Authorization and Syncing

Remove all features related to Trakt.tv user authorization, account syncing, and user-specific data management (watchlist, progress, comments, etc. that require authentication). Trakt will remain as a metadata source for show/movie information using its public (unauthorized) API.

## User Review Required

> [!IMPORTANT]
> This change will permanently remove the ability for users to log in to Trakt.tv and sync their data. Existing users will lose their Trakt connection and the app will operate in a local-only mode for user data.

- **Feature Removal**: Initial sync, periodic sync, quick sync, and Trakt-specific settings will be removed.
- **UI Changes**: The "Trakt" section in Settings will be removed, and login prompts will be gone.
- **Data Persistence**: Watchlist and progress will remain local-only.

### Database Fixes
- **Migrations.kt**: Bump `DATABASE_VERSION` to 42.
- **StorageModule.kt**: Enable `fallbackToDestructiveMigration()` to handle schema changes in `Settings` and `User` entities.

### [ui-trakt-sync]

---
#### [DELETE] [ui-trakt-sync](file:///C:/Users/thiss/StudioProjects/showly/ui-trakt-sync)
- Delete the entire module as it's dedicated to Trakt sync UI.

### [data-remote]

---
#### [TraktModule.kt](file:///C:/Users/thiss/StudioProjects/showly/data-remote/src/main/java/com/michaldrabik/data_remote/di/module/TraktModule.kt)
- Remove `providesAuthorizedTraktApi` and `providesTraktTokenProvider`.
- Remove `providesTraktApi` auth token related logic if any.

#### [DELETE] [AuthorizedTraktApi.kt](file:///C:/Users/thiss/StudioProjects/showly/data-remote/src/main/java/com/michaldrabik/data_remote/trakt/api/AuthorizedTraktApi.kt)
#### [DELETE] [AuthorizedTraktRemoteDataSource.kt](file:///C:/Users/thiss/StudioProjects/showly/data-remote/src/main/java/com/michaldrabik/data_remote/trakt/AuthorizedTraktRemoteDataSource.kt)
#### [DELETE] [TraktAuthorizationInterceptor.kt](file:///C:/Users/thiss/StudioProjects/showly/data-remote/src/main/java/com/michaldrabik/data_remote/trakt/interceptors/TraktAuthorizationInterceptor.kt)
#### [DELETE] [TokenProvider.kt](file:///C:/Users/thiss/StudioProjects/showly/data-remote/src/main/java/com/michaldrabik/data_remote/token/TokenProvider.kt)
#### [DELETE] [TraktTokenProvider.kt](file:///C:/Users/thiss/StudioProjects/showly/data-remote/src/main/java/com/michaldrabik/data_remote/token/TraktTokenProvider.kt)

### [repository]

---
#### [DELETE] [UserTraktManager.kt](file:///C:/Users/thiss/StudioProjects/showly/repository/src/main/java/com/michaldrabik/repository/UserTraktManager.kt)

#### [CommentsRepository.kt](file:///C:/Users/thiss/StudioProjects/showly/repository/src/main/java/com/michaldrabik/repository/CommentsRepository.kt)
- Remove `AuthorizedTraktRemoteDataSource` dependency.
- Remove `postComment` and `deleteComment` methods.

#### [ShowsRatingsRepository.kt](file:///C:/Users/thiss/StudioProjects/showly/repository/src/main/java/com/michaldrabik/repository/shows/ratings/ShowsRatingsRepository.kt)
#### [MoviesRatingsRepository.kt](file:///C:/Users/thiss/StudioProjects/showly/repository/src/main/java/com/michaldrabik/repository/movies/ratings/MoviesRatingsRepository.kt)
- Remove `AuthorizedTraktRemoteDataSource` dependency.
- Remove remote sync logic from `preloadRatings`, `addRating`, `deleteRating`.

#### [SettingsRepository.kt](file:///C:/Users/thiss/StudioProjects/showly/repository/src/main/java/com/michaldrabik/repository/settings/SettingsRepository.kt)
#### [SettingsSyncRepository.kt](file:///C:/Users/thiss/StudioProjects/showly/repository/src/main/java/com/michaldrabik/repository/settings/SettingsSyncRepository.kt)
- Remove Trakt-specific settings keys and properties.

### [ui-base]

---
#### [DELETE] [ui_base/trakt/](file:///C:/Users/thiss/StudioProjects/showly/ui-base/src/main/java/com/michaldrabik/ui_base/trakt)
- Delete all files in this directory including `TraktSyncWorker`, `TraktSyncRunner`, `TraktNotificationWorker`, and `quicksync` package.

#### [DELETE] [ui_base/common/sheets/remove_trakt/](file:///C:/Users/thiss/StudioProjects/showly/ui-base/src/main/java/com/michaldrabik/ui_base/common/sheets/remove_trakt)
- Delete all Trakt-specific removal bottom sheets.

### [ui-settings]

---
#### [DELETE] [ui_settings/sections/trakt/](file:///C:/Users/thiss/StudioProjects/showly/ui-settings/src/main/java/com/michaldrabik/ui_settings/sections/trakt)
- Delete the Trakt settings section.

#### [fragment_settings.xml](file:///C:/Users/thiss/StudioProjects/showly/ui-settings/src/main/res/layout/fragment_settings.xml)
- Remove `settingsCategoryTrakt` view.

#### [SettingsFiltersView.kt](file:///C:/Users/thiss/StudioProjects/showly/ui-settings/src/main/java/com/michaldrabik/ui_settings/views/SettingsFiltersView.kt)
- Remove `TRAKT` from `SettingsFilter` enum and UI.

### [app]

---
#### [MainViewModel.kt](file:///C:/Users/thiss/StudioProjects/showly/app/src/main/java/com/michaldrabik/showly2/ui/main/MainViewModel.kt)
- Remove `MainTraktCase` dependency and `refreshTraktSyncSchedule` calls.

#### [DELETE] [MainTraktCase.kt](file:///C:/Users/thiss/StudioProjects/showly/app/src/main/java/com/michaldrabik/showly2/ui/main/cases/MainTraktCase.kt)

#### [BaseActivity.kt](file:///C:/Users/thiss/StudioProjects/showly/app/src/main/java/com/michaldrabik/showly2/ui/BaseActivity.kt)
- Remove `handleTraktAuthorization` method.

#### [AndroidManifest.xml](file:///C:/Users/thiss/StudioProjects/showly/app/src/main/AndroidManifest.xml)
- Remove Trakt deep link intent filters and receivers.

### [Navigation]

---
#### [navigation_graph.xml](file:///C:/Users/thiss/StudioProjects/showly/ui-navigation/src/main/res/navigation/navigation_graph.xml)
- Remove `traktSyncFragment` and all `removeTrakt*` dialogs.
- Remove all actions pointing to these destinations.

## Verification Plan

### Automated Tests
- Run `./gradlew assembleDebug` to ensure the project builds successfully.
- Run `./gradlew testDebugUnitTest` to ensure all tests pass (some tests might need to be deleted if they were Trakt-specific).

### Manual Verification
- Verify that the Trakt section is gone from Settings.
- Verify that there are no login prompts.
- Verify that Watchlist and Progress still work (locally).
- Verify that Search still works (using unauthorized Trakt API).
