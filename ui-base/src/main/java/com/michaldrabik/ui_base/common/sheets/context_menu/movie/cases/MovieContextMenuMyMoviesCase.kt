package com.michaldrabik.ui_base.common.sheets.context_menu.movie.cases

import com.michaldrabik.common.dispatchers.CoroutineDispatchers
import com.michaldrabik.repository.PinnedItemsRepository
import com.michaldrabik.repository.movies.MoviesRepository
import com.michaldrabik.ui_base.notifications.AnnouncementManager
import com.michaldrabik.ui_model.IdTrakt
import com.michaldrabik.ui_model.Ids
import com.michaldrabik.ui_model.Movie
import kotlinx.coroutines.withContext
import java.time.ZonedDateTime
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class MovieContextMenuMyMoviesCase @Inject constructor(
  private val dispatchers: CoroutineDispatchers,
  private val moviesRepository: MoviesRepository,
  private val pinnedItemsRepository: PinnedItemsRepository,
  private val announcementManager: AnnouncementManager,
) {

  suspend fun moveToMyMovies(
    traktId: IdTrakt,
    customDate: ZonedDateTime?,
  ) = withContext(dispatchers.IO) {
    val movie = Movie.EMPTY.copy(ids = Ids.EMPTY.copy(traktId))
    moviesRepository.myMovies.insert(traktId, customDate)
    pinnedItemsRepository.removePinnedItem(movie)
    announcementManager.refreshMoviesAnnouncements()
  }

  suspend fun removeFromMyMovies(traktId: IdTrakt) =
    withContext(dispatchers.IO) {
      val movie = Movie.EMPTY.copy(ids = Ids.EMPTY.copy(traktId))
      moviesRepository.myMovies.delete(traktId)
      pinnedItemsRepository.removePinnedItem(movie)
    }
}
