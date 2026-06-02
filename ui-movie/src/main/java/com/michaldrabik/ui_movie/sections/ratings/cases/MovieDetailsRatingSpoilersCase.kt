package com.michaldrabik.ui_movie.sections.ratings.cases

import com.michaldrabik.common.dispatchers.CoroutineDispatchers
import com.michaldrabik.repository.settings.SettingsSpoilersRepository
import com.michaldrabik.ui_model.Movie
import com.michaldrabik.ui_model.Ratings
import com.michaldrabik.ui_movie.MovieDetailsUiState.FollowedState
import com.michaldrabik.ui_movie.cases.MovieDetailsDroppedCase
import com.michaldrabik.ui_movie.cases.MovieDetailsMyMoviesCase
import com.michaldrabik.ui_movie.cases.MovieDetailsWatchlistCase
import dagger.hilt.android.scopes.ViewModelScoped
import kotlinx.coroutines.async
import kotlinx.coroutines.withContext
import javax.inject.Inject

@ViewModelScoped
class MovieDetailsRatingSpoilersCase @Inject constructor(
  private val dispatchers: CoroutineDispatchers,
  private val myMoviesCase: MovieDetailsMyMoviesCase,
  private val watchlistCase: MovieDetailsWatchlistCase,
  private val droppedCase: MovieDetailsDroppedCase,
  private val settingsSpoilersRepository: SettingsSpoilersRepository,
) {

  suspend fun hideSpoilerRatings(
    movie: Movie,
    ratings: Ratings,
  ): Ratings =
    withContext(dispatchers.IO) {
      val spoilers = settingsSpoilersRepository.getAll()

      val isMy = async { myMoviesCase.getMyMovie(movie) }
      val isWatchlist = async { watchlistCase.isWatchlist(movie) }
      val isDropped = async { droppedCase.isDropped(movie) }

      val state = FollowedState(
        isMyMovie = isMy.await() != null,
        isWatchlist = isWatchlist.await(),
        isDropped = isDropped.await(),
        withAnimation = false,
      )

      val isMyHidden = spoilers.isMyMoviesRatingsHidden && state.isMyMovie
      val isWatchlistHidden = spoilers.isWatchlistMoviesRatingsHidden && state.isWatchlist
      val isDroppedHidden = spoilers.isDroppedMoviesRatingsHidden && state.isDropped
      val isNotCollectedHidden = spoilers.isNotCollectedMoviesRatingsHidden && !state.isInCollection()

      return@withContext ratings.copy(
        isHidden = isMyHidden || isWatchlistHidden || isDroppedHidden || isNotCollectedHidden,
      )
    }
}
