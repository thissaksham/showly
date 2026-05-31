# Walkthrough - Date Selection and History Sorting

I have modified the Date Selection bottom sheet and updated the sorting logic for History and Watched lists to support an "Unknown" watch date.

## Changes

### UI Modifications
- **Date Selection Layout**: The bottom sheet now has a more balanced 2x2 grid layout.
    - Row 1: **Release Day** | **Now**
    - Row 2: **Unknown** | **Select Date**
- **Strings**: Added "Unknown" string to `strings.xml`.

### Database and Models
- **Migration 42**: Added a migration to make `created_at` and `updated_at` nullable in the `movies_my_movies` table.
- **Nullable Dates**: Updated `MyMovie`, `MyShow`, and the `Movie` UI model to allow `Long?` and `ZonedDateTime?` for watch dates.

### Business Logic
- **Explicit "Now"**: Updated various ViewModels and Fragments to explicitly pass the current time (`nowUtc()`) when "Now" is selected. This allows `null` to represent the "Unknown" watch date.
- **Repository Updates**: `EpisodesManager`, `MyShowsRepository`, and `MyMoviesRepository` now correctly store `null` in the database when "Unknown" is selected.

### Sorting Logic
- **Episodes History**: In the History tab, episodes marked with an "Unknown" date are grouped at the bottom under an "Unknown" header. Within this group, they are sorted by their original release date (ascending).
- **Movies History**: In the My Movies section (when sorted by "Date Added"), movies with an "Unknown" date appear at the end of the list, also sorted by release date (ascending).

## Verification Summary

### Manual Verification
- Verified the layout of `view_date_selection.xml` matches the requirement.
- Verified `DateSelectionBottomSheet.kt` correctly emits `Result.Unknown`.
- Verified `HistoryItemsGrouper.kt` and `MyMoviesSorter.kt` handle null dates by placing them at the bottom.
- Verified that "Now" selection explicitly passes the current date from fragments to ensure it's not treated as "Unknown".
