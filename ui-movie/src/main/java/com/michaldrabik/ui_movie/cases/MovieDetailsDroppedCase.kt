package com.michaldrabik.ui_movie.cases

import com.michaldrabik.common.dispatchers.CoroutineDispatchers
import com.michaldrabik.repository.PinnedItemsRepository
import com.michaldrabik.repository.movies.MoviesRepository
import com.michaldrabik.ui_model.Movie
import dagger.hilt.android.scopes.ViewModelScoped
import kotlinx.coroutines.withContext
import javax.inject.Inject

@ViewModelScoped
class MovieDetailsDroppedCase @Inject constructor(
  private val dispatchers: CoroutineDispatchers,
  private val moviesRepository: MoviesRepository,
  private val pinnedItemsRepository: PinnedItemsRepository,
) {

  suspend fun isDropped(movie: Movie) =
    withContext(dispatchers.IO) {
      moviesRepository.droppedMovies.exists(movie.ids.trakt)
    }

  suspend fun addToDropped(movie: Movie) =
    withContext(dispatchers.IO) {
      moviesRepository.droppedMovies.insert(movie.ids.trakt)
      pinnedItemsRepository.removePinnedItem(movie)
    }

  suspend fun removeFromDropped(movie: Movie) =
    withContext(dispatchers.IO) {
      moviesRepository.droppedMovies.delete(movie.ids.trakt)
    }
}
