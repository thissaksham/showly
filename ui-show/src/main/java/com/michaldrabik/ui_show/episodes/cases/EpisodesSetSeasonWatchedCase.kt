package com.michaldrabik.ui_show.episodes.cases

import com.michaldrabik.repository.EpisodesManager
import com.michaldrabik.ui_model.Season
import com.michaldrabik.ui_model.SeasonBundle
import com.michaldrabik.ui_model.Show
import com.michaldrabik.ui_show.sections.seasons.helpers.SeasonsCache
import java.time.ZonedDateTime
import javax.inject.Inject

class EpisodesSetSeasonWatchedCase @Inject constructor(
  private val episodesManager: EpisodesManager,
  private val seasonsCache: SeasonsCache,
) {

  suspend fun setSeasonWatched(
    show: Show,
    season: Season,
    isWatched: Boolean,
    customDate: ZonedDateTime?,
  ): Result {
    val bundle = SeasonBundle(season, show)
    if (isWatched) {
      episodesManager.setSeasonWatched(bundle, customDate)
    } else {
      episodesManager.setSeasonUnwatched(bundle)
    }
    seasonsCache.clear(show.ids.trakt)
    return Result.SUCCESS
  }

  enum class Result {
    SUCCESS,
  }
}
