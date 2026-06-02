package com.michaldrabik.ui_progress_movies.progress.cases

import com.michaldrabik.repository.PinnedItemsRepository
import com.michaldrabik.ui_model.Movie
import javax.inject.Inject

class ProgressMoviesPinnedCase @Inject constructor(
  private val pinnedItemsRepository: PinnedItemsRepository,
) {

  fun togglePinned(movie: Movie) {
    if (pinnedItemsRepository.isItemPinned(movie)) {
      pinnedItemsRepository.removePinnedItem(movie)
    } else {
      pinnedItemsRepository.addPinnedItem(movie)
    }
  }
}
