# Implementation Plan - Modify Date Selection and History Sorting

This plan outlines the changes needed to update the Date Selection bottom sheet layout, support marking items as watched with an "Unknown" date (null), and update the sorting logic in History/Watched lists.

## User Review Required

- **Database Migration**: A migration (v42) is required to make `created_at` and `updated_at` nullable in the `movies_my_movies` table.
- **"Now" Behavior Change**: To differentiate between "Now" and "Unknown", I will change several methods to no longer default to the current time when a `null` date is passed. Instead, "Now" will be passed explicitly as `nowUtc()` from the fragments.

## Proposed Changes

### [ui-base] - Date Selection UI and Core Models

#### [strings.xml](file:///C:/Users/thiss/StudioProjects/showly/ui-base/src/main/res/values/strings.xml)
- Add `textUnknown` string: `<string name="textUnknown">Unknown</string>`.

#### [view_date_selection.xml](file:///C:/Users/thiss/StudioProjects/showly/ui-base/src/main/res/layout/view_date_selection.xml)
- Add `dateUnknownButton`.
- Rearrange buttons:
    - Row 1: `dateReleaseButton` | `dateNowButton`
    - Row 2: `dateUnknownButton` | `dateCustomButton`

#### [DateSelectionBottomSheet.kt](file:///C:/Users/thiss/StudioProjects/showly/ui-base/src/main/java/com/michaldrabik/ui_base/common/sheets/date_selection/DateSelectionBottomSheet.kt)
- Add `Unknown` to `Result` interface.
- Update `setupView()` to handle click on `dateUnknownButton`.

---

### [data-local] - Database Models and Migrations

#### [MyMovie.kt](file:///C:/Users/thiss/StudioProjects/showly/data-local/src/main/java/com/michaldrabik/data_local/database/model/MyMovie.kt)
- Make `createdAt` and `updatedAt` nullable `Long?`.

#### [Migrations.kt](file:///C:/Users/thiss/StudioProjects/showly/data-local/src/main/java/com/michaldrabik/data_local/database/migrations/Migrations.kt)
- Increment `DATABASE_VERSION` to 42.
- Add `migration42` to handle nullable columns in `movies_my_movies`.

#### [MyShow.kt](file:///C:/Users/thiss/StudioProjects/showly/data-local/src/main/java/com/michaldrabik/data_local/database/model/MyShow.kt)
- Update `fromTraktId` to take `watchedAt: Long?`.

---

### [repository] - Business Logic for Marking Watched

#### [EpisodesManager.kt](file:///C:/Users/thiss/StudioProjects/showly/repository/src/main/java/com/michaldrabik/repository/EpisodesManager.kt)
- Update `setEpisodeWatched` and `setSeasonWatched` to NOT default to `nowUtc()` if `customDate` is null.
- Update `updateWatchedAt` in `showsRepository` calls.

#### [MyShowsRepository.kt](file:///C:/Users/thiss/StudioProjects/showly/repository/src/main/java/com/michaldrabik/repository/shows/MyShowsRepository.kt)
- Update `updateWatchedAt` to take `watchedAt: Long?`.

#### [MyMoviesRepository.kt](file:///C:/Users/thiss/StudioProjects/showly/repository/src/main/java/com/michaldrabik/repository/movies/MyMoviesRepository.kt)
- Update `insert` to handle null `customDate` as `null` timestamp (for "Unknown").

---

### [ui-progress] - Episodes History Sorting

#### [HistoryListItem.kt](file:///C:/Users/thiss/StudioProjects/showly/ui-progress/src/main/java/com/michaldrabik/ui_progress/history/entities/HistoryListItem.kt)
- Make `Header.date` nullable `LocalDateTime?`.

#### [HistoryHeaderView.kt](file:///C:/Users/thiss/StudioProjects/showly/ui-progress/src/main/java/com/michaldrabik/ui_progress/history/views/HistoryHeaderView.kt)
- Handle `item.date == null` by showing `R.string.textUnknown`.

#### [HistoryItemsGrouper.kt](file:///C:/Users/thiss/StudioProjects/showly/ui-progress/src/main/java/com/michaldrabik/ui_progress/history/utilities/groupers/HistoryItemsGrouper.kt)
- Update `groupByDay` to handle null `lastWatchedAt`.
- Place "Unknown" group at the bottom, sorted by `firstAired` ascending.

#### [ProgressMainFragment.kt](file:///C:/Users/thiss/StudioProjects/showly/ui-progress/src/main/java/com/michaldrabik/ui_progress/main/ProgressMainFragment.kt)
- Update `setFragmentResultListener` for `REQUEST_DATE_SELECTION` to pass `nowUtc()` for `Result.Now`.

---

### [ui-my-movies] - Movies History Sorting

#### [MyMoviesSorter.kt](file:///C:/Users/thiss/StudioProjects/showly/ui-my-movies/src/main/java/com/michaldrabik/ui_my_movies/mymovies/helpers/MyMoviesSorter.kt)
- Update `DATE_ADDED` sorting: items with `updatedAt` first (descending), then items with `null` `updatedAt` sorted by `released` ascending.

#### [MovieDetailsFragment.kt](file:///C:/Users/thiss/StudioProjects/showly/ui-movie/src/main/java/com/michaldrabik/ui_movie/MovieDetailsFragment.kt)
- Update `setFragmentResultListener` for `REQUEST_DATE_SELECTION` to pass `nowUtc()` for `Result.Now`.

---

### [Other Modules] - Updating Callers

- Update `setFragmentResultListener` in `ProgressMoviesMainFragment.kt`, `ShowDetailsEpisodesFragment.kt`, `ShowDetailsSeasonsFragment.kt`, and `MovieContextMenuBottomSheet.kt` to handle the new `Result.Unknown` and pass `nowUtc()` for `Result.Now`.

## Verification Plan

### Manual Verification
- **UI Check**: Open the Date Selection bottom sheet when marking an episode or movie as watched. Verify the new layout and "Unknown" button.
- **Marking as Watched**:
    - Select "Now": Verify item is marked as watched with current date.
    - Select "Unknown": Verify item is marked as watched with no date.
- **History List (Episodes)**:
    - Mark an episode as "Unknown". Verify it appears at the bottom of the History list under "Unknown" header.
    - Mark multiple episodes as "Unknown". Verify they are sorted by release date ascending within that group.
- **My Movies List (Movies)**:
    - Mark a movie as "Unknown". Verify it appears at the bottom when sorted by "Date Added".
    - Mark multiple movies as "Unknown". Verify they are sorted by release date ascending at the bottom.
- **Database Check**: Use Layout Inspector or similar (or temporary logging) to verify `null` values are stored in the database for "Unknown" dates.
