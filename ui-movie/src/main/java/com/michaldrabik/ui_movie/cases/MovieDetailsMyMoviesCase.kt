package com.michaldrabik.ui_movie.cases

import com.michaldrabik.common.dispatchers.CoroutineDispatchers
import com.michaldrabik.repository.PinnedItemsRepository
import com.michaldrabik.repository.movies.MoviesRepository
import com.michaldrabik.ui_base.notifications.AnnouncementManager
import com.michaldrabik.ui_model.Movie
import dagger.hilt.android.scopes.ViewModelScoped
import kotlinx.coroutines.withContext
import java.time.ZonedDateTime
import javax.inject.Inject

@ViewModelScoped
class MovieDetailsMyMoviesCase @Inject constructor(
  private val dispatchers: CoroutineDispatchers,
  private val moviesRepository: MoviesRepository,
  private val pinnedItemsRepository: PinnedItemsRepository,
  private val announcementManager: AnnouncementManager,
) {

  suspend fun getAllIds() =
    withContext(dispatchers.IO) {
      val myMoviesIds = moviesRepository.myMovies.loadAllIds()
      val watchlistMoviesIds = moviesRepository.watchlistMovies.loadAllIds()
      myMoviesIds to watchlistMoviesIds
    }

  suspend fun getMyMovie(movie: Movie) =
    withContext(dispatchers.IO) {
      moviesRepository.myMovies.load(movie.ids.trakt)
    }

  suspend fun addToMyMovies(
    movie: Movie,
    customDate: ZonedDateTime?,
  ) = withContext(dispatchers.IO) {
    moviesRepository.myMovies.insert(movie.ids.trakt, customDate)
    pinnedItemsRepository.removePinnedItem(movie)
    announcementManager.refreshMoviesAnnouncements()
  }

  suspend fun removeFromMyMovies(movie: Movie) =
    withContext(dispatchers.IO) {
      moviesRepository.myMovies.delete(movie.ids.trakt)
      pinnedItemsRepository.removePinnedItem(movie)
    }
}
