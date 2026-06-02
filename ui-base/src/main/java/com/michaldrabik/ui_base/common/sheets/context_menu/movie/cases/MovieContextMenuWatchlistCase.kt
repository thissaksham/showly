package com.michaldrabik.ui_base.common.sheets.context_menu.movie.cases

import com.michaldrabik.common.dispatchers.CoroutineDispatchers
import com.michaldrabik.repository.PinnedItemsRepository
import com.michaldrabik.repository.movies.MoviesRepository
import com.michaldrabik.ui_base.notifications.AnnouncementManager
import com.michaldrabik.ui_model.IdTrakt
import com.michaldrabik.ui_model.Ids
import com.michaldrabik.ui_model.Movie
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class MovieContextMenuWatchlistCase @Inject constructor(
  private val dispatchers: CoroutineDispatchers,
  private val moviesRepository: MoviesRepository,
  private val pinnedItemsRepository: PinnedItemsRepository,
  private val announcementManager: AnnouncementManager,
) {

  suspend fun moveToWatchlist(traktId: IdTrakt) =
    withContext(dispatchers.IO) {
      val movie = Movie.EMPTY.copy(ids = Ids.EMPTY.copy(traktId))
      moviesRepository.watchlistMovies.insert(traktId)
      pinnedItemsRepository.removePinnedItem(movie)
      announcementManager.refreshMoviesAnnouncements()
    }

  suspend fun removeFromWatchlist(traktId: IdTrakt) =
    withContext(dispatchers.IO) {
      moviesRepository.watchlistMovies.delete(traktId)
    }
}
