package com.michaldrabik.ui_base.common.sheets.context_menu.movie.cases

import com.michaldrabik.repository.PinnedItemsRepository
import com.michaldrabik.ui_model.IdTrakt
import com.michaldrabik.ui_model.Movie
import com.michaldrabik.ui_model.Ids
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class MovieContextMenuPinnedCase @Inject constructor(
  private val pinnedItemsRepository: PinnedItemsRepository,
) {

  fun addToTopPinned(traktId: IdTrakt) {
    pinnedItemsRepository.addMoviePinnedItem(traktId)
  }

  fun removeFromTopPinned(traktId: IdTrakt) {
    pinnedItemsRepository.removePinnedItem(Movie.EMPTY.copy(ids = Ids.EMPTY.copy(trakt = traktId)))
  }
}
