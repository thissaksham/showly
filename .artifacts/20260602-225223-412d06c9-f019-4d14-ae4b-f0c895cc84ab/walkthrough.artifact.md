# Walkthrough - Renamed "Hide/Hidden" to "Drop/Dropped"

The "Hide" feature for movies and shows has been renamed to "Drop" across the entire application to better align with user behavior (marking shows/movies as dropped).

## Changes

### UI Strings
Updated user-facing text in all `strings.xml` files.
- "Hide" -> "Drop"
- "Hidden" -> "Dropped"
- "Add to Hidden" -> "Drop"
- "Move to Hidden" -> "Move to Dropped"

### UI Layouts
Renamed view IDs in XML layouts for consistency.
- `showDetailsHideLabel` -> `showDetailsDropLabel`
- `hiddenShowsLayout` -> `droppedShowsLayout`
- `hiddenMoviesLayout` -> `droppedMoviesLayout`

### Code Identifiers
Renamed classes, methods, and variables to reflect the new naming.
- `ShowDetailsHiddenCase` -> `ShowDetailsDroppedCase`
- `MovieDetailsHiddenCase` -> `MovieDetailsDroppedCase`
- `HiddenViewModel` -> `DroppedViewModel`
- `HiddenFragment` -> `DroppedFragment`
- `hiddenShows` repository property -> `droppedShows`
- `isHidden` state flag -> `isDropped`

### Packages and Files
Renamed `hidden` packages to `dropped` in `ui-my-shows` and `ui-my-movies` modules.
Renamed layout files like `fragment_hidden.xml` to `fragment_dropped.xml`.

## Verification Results

### Automated Tests
- Full project build was successful: `gradlew assembleDebug` finished successfully.
- Verified that all major modules (`ui-show`, `ui-movie`, `ui-my-shows`, `ui-my-movies`, `ui-base`, `repository`) compile correctly.

### Manual Verification
- All UI strings are now correctly using "Drop" or "Dropped".
- The list management logic remains unchanged, only the naming has been updated.
