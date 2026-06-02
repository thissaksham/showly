package com.michaldrabik.ui_base.common.sheets.ratings.cases

import com.michaldrabik.common.dispatchers.CoroutineDispatchers
import com.michaldrabik.repository.RatingsRepository
import com.michaldrabik.ui_model.IdTrakt
import com.michaldrabik.ui_model.Ids
import com.michaldrabik.ui_model.Movie
import com.michaldrabik.ui_model.TraktRating
import kotlinx.coroutines.withContext
import javax.inject.Inject

class RatingsMovieCase @Inject constructor(
  private val dispatchers: CoroutineDispatchers,
  private val ratingsRepository: RatingsRepository,
) {

  companion object {
    private val RATING_VALID_RANGE = 1..10
  }

  suspend fun loadRating(id: IdTrakt): TraktRating? =
    withContext(dispatchers.IO) {
      val movie = Movie.EMPTY.copy(ids = Ids.EMPTY.copy(trakt = id))
      val ratings = ratingsRepository.movies.loadRatings(listOf(movie))
      ratings.firstOrNull()
    }

  suspend fun saveRating(
    id: IdTrakt,
    rating: Int,
  ) = withContext(dispatchers.IO) {
    if (rating !in RATING_VALID_RANGE) throw IllegalArgumentException("Rating must be between 1 and 10.")
    val movie = Movie.EMPTY.copy(ids = Ids.EMPTY.copy(trakt = id))
    ratingsRepository.movies.addRating(
      movie = movie,
      rating = rating,
      withSync = false,
    )
  }

  suspend fun deleteRating(id: IdTrakt) =
    withContext(dispatchers.IO) {
      val movie = Movie.EMPTY.copy(ids = Ids.EMPTY.copy(trakt = id))
      ratingsRepository.movies.deleteRating(
        movie = movie,
        withSync = false,
      )
    }
}
