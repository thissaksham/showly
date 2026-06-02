package com.michaldrabik.ui_base.common.sheets.ratings.cases

import com.michaldrabik.common.dispatchers.CoroutineDispatchers
import com.michaldrabik.repository.RatingsRepository
import com.michaldrabik.ui_model.Episode
import com.michaldrabik.ui_model.IdTrakt
import com.michaldrabik.ui_model.Ids
import com.michaldrabik.ui_model.TraktRating
import kotlinx.coroutines.withContext
import javax.inject.Inject

class RatingsEpisodeCase @Inject constructor(
  private val dispatchers: CoroutineDispatchers,
  private val ratingsRepository: RatingsRepository,
) {

  companion object {
    private val RATING_VALID_RANGE = 1..10
  }

  suspend fun loadRating(id: IdTrakt): TraktRating? =
    withContext(dispatchers.IO) {
      val episode = Episode.EMPTY.copy(ids = Ids.EMPTY.copy(trakt = id))
      ratingsRepository.shows.loadRating(episode)
    }

  suspend fun saveRating(
    id: IdTrakt,
    rating: Int,
    seasonNumber: Int,
    episodeNumber: Int,
  ) = withContext(dispatchers.IO) {
    if (rating !in RATING_VALID_RANGE) throw IllegalArgumentException("Rating must be between 1 and 10.")
    val episode = Episode.EMPTY.copy(
      ids = Ids.EMPTY.copy(trakt = id),
      season = seasonNumber,
      number = episodeNumber,
    )
    ratingsRepository.shows.addRating(
      episode = episode,
      rating = rating,
      withSync = false,
    )
  }

  suspend fun deleteRating(id: IdTrakt) =
    withContext(dispatchers.IO) {
      val episode = Episode.EMPTY.copy(ids = Ids.EMPTY.copy(trakt = id))
      ratingsRepository.shows.deleteRating(
        episode = episode,
        withSync = false,
      )
    }
}
