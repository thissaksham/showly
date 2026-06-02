# Rename "Hide/Hidden" to "Drop/Dropped"

Renaming the "Hide" feature to "Drop" across the app to better reflect how users use it (marking shows/movies as dropped). This includes UI strings, layout IDs, and relevant code identifiers.

## Proposed Changes

### UI Strings

Update user-facing text to use "Drop" or "Dropped" instead of "Hide" or "Hidden" for the list management feature.

#### [strings.xml](file:///C:/Users/thiss/StudioProjects/showly/ui-base/src/main/res/values/strings.xml)

- `textHide`: "Hide" -> "Drop"
- `textAddToHidden`: "Add to Hidden" -> "Drop"
- `textInHidden`: "Hidden" -> "Dropped"
- `textAddedToHidden`: "Added to Hidden" -> "Dropped"
- `textMoveToHidden`: "Move to Hidden" -> "Move to Dropped"
- `textRemoveFromHidden`: "Remove from Hidden" -> "Remove from Dropped"

#### [strings.xml](file:///C:/Users/thiss/StudioProjects/showly/ui-show/src/main/res/values/strings.xml)

- `textInHidden`: "Hidden" -> "Dropped"

#### [strings.xml](file:///C:/Users/thiss/StudioProjects/showly/ui-movie/src/main/res/values/strings.xml)

- `textInHidden`: "Hidden" -> "Dropped"

#### [strings.xml](file:///C:/Users/thiss/StudioProjects/showly/ui-my-shows/src/main/res/values/strings.xml)

- `menuHidden`: "Hidden" -> "Dropped"
- `textHiddenEmpty`: Rename "Hidden" to "Dropped" in title and description.

#### [strings.xml](file:///C:/Users/thiss/StudioProjects/showly/ui-my-movies/src/main/res/values/strings.xml)

- `menuHidden`: "Hidden" -> "Dropped"
- `textHiddenEmpty`: Rename "Hidden" to "Dropped" in title and description.

#### [strings.xml](file:///C:/Users/thiss/StudioProjects/showly/ui-settings/src/main/res/values/strings.xml)

- Update all strings related to "Hidden" shows/movies in spoilers settings to use "Dropped".

---

### UI Layouts

#### [fragment_show_details.xml](file:///C:/Users/thiss/StudioProjects/showly/ui-show/src/main/res/layout/fragment_show_details.xml)
#### [fragment_movie_details.xml](file:///C:/Users/thiss/StudioProjects/showly/ui-movie/src/main/res/layout/fragment_movie_details.xml)

- `showDetailsHideLabel` -> `showDetailsDropLabel`
- `movieDetailsHideLabel` -> `movieDetailsDropLabel`

#### [sheet_spoilers_shows.xml](file:///C:/Users/thiss/StudioProjects/showly/ui-settings/src/main/res/layout/sheet_spoilers_shows.xml)
#### [sheet_spoilers_movies.xml](file:///C:/Users/thiss/StudioProjects/showly/ui-settings/src/main/res/layout/sheet_spoilers_movies.xml)

- `hiddenShowsLayout` -> `droppedShowsLayout`
- `hiddenMoviesLayout` -> `droppedMoviesLayout`
- And related child IDs.

---

### Code Identifiers

#### [AddToShowsButton.kt](file:///C:/Users/thiss/StudioProjects/showly/ui-show/src/main/java/com/michaldrabik/ui_show/views/AddToShowsButton.kt)

- `IN_HIDDEN` -> `IN_DROPPED`

#### [ShowDetailsUiState.kt](file:///C:/Users/thiss/StudioProjects/showly/ui-show/src/main/java/com/michaldrabik/ui_show/ShowDetailsUiState.kt)
#### [MovieDetailsUiState.kt](file:///C:/Users/thiss/StudioProjects/showly/ui-movie/src/main/java/com/michaldrabik/ui_movie/MovieDetailsUiState.kt)

- `isHidden` -> `isDropped`

#### [ShowsRepository.kt](file:///C:/Users/thiss/StudioProjects/showly/repository/src/main/java/com/michaldrabik/repository/shows/ShowsRepository.kt)
#### [MoviesRepository.kt](file:///C:/Users/thiss/StudioProjects/showly/repository/src/main/java/com/michaldrabik/repository/movies/MoviesRepository.kt)

- `hiddenShows` -> `droppedShows`
- `hiddenMovies` -> `droppedMovies`
- `skipHidden` argument -> `skipDropped`

#### UseCases and ViewModels

Rename classes and files:
- `ShowDetailsHiddenCase` -> `ShowDetailsDroppedCase`
- `MovieDetailsHiddenCase` -> `MovieDetailsDroppedCase`
- `ShowContextMenuHiddenCase` -> `ShowContextMenuDroppedCase`
- `MovieContextMenuHiddenCase` -> `MovieContextMenuDroppedCase`
- `HiddenLoadShowsCase` -> `DroppedLoadShowsCase`
- `HiddenLoadMoviesCase` -> `DroppedLoadMoviesCase`
- `HiddenViewModel` -> `DroppedViewModel`
- `HiddenFragment` -> `DroppedFragment`

---

## Verification Plan

### Automated Tests
- Run existing unit tests to ensure no regressions in logic.
- `gradlew :ui-show:test`
- `gradlew :ui-movie:test`
- `gradlew :repository:test`

### Manual Verification
- Deploy the app and verify:
    - Button label in Show Details changed from "HIDE" to "DROP".
    - Button label when show is in the list changed from "HIDDEN" to "DROPPED".
    - "Hidden" tab in "My Shows" and "History" renamed to "Dropped".
    - Spoilers settings for "Hidden" renamed to "Dropped".
