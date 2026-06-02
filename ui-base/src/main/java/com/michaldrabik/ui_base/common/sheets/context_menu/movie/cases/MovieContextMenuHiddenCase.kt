package com.michaldrabik.ui_base.common.sheets.context_menu.movie.cases

import com.michaldrabik.common.dispatchers.CoroutineDispatchers
import com.michaldrabik.repository.PinnedItemsRepository
import com.michaldrabik.repository.movies.MoviesRepository
import com.michaldrabik.ui_model.IdTrakt
import com.michaldrabik.ui_model.Ids
import com.michaldrabik.ui_model.Movie
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class MovieContextMenuHiddenCase @Inject constructor(
  private val dispatchers: CoroutineDispatchers,
  private val moviesRepository: MoviesRepository,
  private val pinnedItemsRepository: PinnedItemsRepository,
) {

  suspend fun moveToHidden(traktId: IdTrakt) =
    withContext(dispatchers.IO) {
      val movie = Movie.EMPTY.copy(ids = Ids.EMPTY.copy(traktId))
      moviesRepository.hiddenMovies.insert(traktId)
      pinnedItemsRepository.removePinnedItem(movie)
    }

  suspend fun removeFromHidden(traktId: IdTrakt) =
    withContext(dispatchers.IO) {
      moviesRepository.hiddenMovies.delete(traktId)
    }
}
