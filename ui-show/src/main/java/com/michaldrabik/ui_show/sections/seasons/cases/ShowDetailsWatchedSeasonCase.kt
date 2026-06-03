package com.michaldrabik.ui_show.sections.seasons.cases

import com.michaldrabik.repository.EpisodesManager
import com.michaldrabik.ui_model.Season
import com.michaldrabik.ui_model.SeasonBundle
import com.michaldrabik.ui_model.Show
import java.time.ZonedDateTime
import javax.inject.Inject

class ShowDetailsWatchedSeasonCase @Inject constructor(
  private val episodesManager: EpisodesManager,
) {

  suspend fun setSeasonWatched(
    show: Show,
    season: Season,
    isWatched: Boolean,
    customDate: ZonedDateTime?,
    useReleaseDate: Boolean = false,
  ): Result {
    val bundle = SeasonBundle(season, show)
    if (isWatched) {
      episodesManager.setSeasonWatched(bundle, customDate, useReleaseDate)
    } else {
      episodesManager.setSeasonUnwatched(bundle)
    }
    return Result.SUCCESS
  }

  enum class Result {
    SUCCESS,
  }
}
