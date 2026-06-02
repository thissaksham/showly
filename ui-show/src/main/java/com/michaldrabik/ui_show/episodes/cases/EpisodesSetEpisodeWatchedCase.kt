package com.michaldrabik.ui_show.episodes.cases

import com.michaldrabik.repository.EpisodesManager
import com.michaldrabik.ui_model.EpisodeBundle
import com.michaldrabik.ui_show.sections.seasons.helpers.SeasonsCache
import java.time.ZonedDateTime
import javax.inject.Inject

class EpisodesSetEpisodeWatchedCase @Inject constructor(
  private val episodesManager: EpisodesManager,
  private val seasonsCache: SeasonsCache,
) {

  suspend fun setEpisodeWatched(
    episodeBundle: EpisodeBundle,
    isWatched: Boolean,
    customDate: ZonedDateTime?,
  ): Result {
    if (isWatched) {
      episodesManager.setEpisodeWatched(episodeBundle, customDate)
    } else {
      episodesManager.setEpisodeUnwatched(episodeBundle)
    }
    seasonsCache.clear(episodeBundle.show.ids.trakt)
    return Result.SUCCESS
  }

  enum class Result {
    SUCCESS,
  }
}
