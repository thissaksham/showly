package com.michaldrabik.ui_movie.cases

import com.michaldrabik.common.dispatchers.CoroutineDispatchers
import com.michaldrabik.repository.movies.MoviesRepository
import com.michaldrabik.ui_model.IdTmdb
import com.michaldrabik.ui_model.IdTrakt
import dagger.hilt.android.scopes.ViewModelScoped
import kotlinx.coroutines.withContext
import timber.log.Timber
import javax.inject.Inject

@ViewModelScoped
class MovieDetailsMainCase @Inject constructor(
  private val dispatchers: CoroutineDispatchers,
  private val moviesRepository: MoviesRepository,
) {

  suspend fun loadDetails(idTrakt: IdTrakt) =
    withContext(dispatchers.IO) {
      moviesRepository.movieDetails.load(idTrakt)
    }

  suspend fun resolveTraktId(tmdbId: Long) =
    withContext(dispatchers.IO) {
      val local = moviesRepository.movieDetails.find(IdTmdb(tmdbId))
      if (local != null) {
        IdTrakt(local.traktId)
      } else {
        IdTrakt(-1) // Still -1 if not found, but we could add Trakt Search resolution here
      }
    }

  suspend fun removeMalformedMovie(idTrakt: IdTrakt) {
    withContext(dispatchers.IO) {
      with(moviesRepository) {
        myMovies.delete(idTrakt)
        watchlistMovies.delete(idTrakt)
        movieDetails.delete(idTrakt)
      }
    }
    Timber.d("Removing malformed movie...")
  }
}
