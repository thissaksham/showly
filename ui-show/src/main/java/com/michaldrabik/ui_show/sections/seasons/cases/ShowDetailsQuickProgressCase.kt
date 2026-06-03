package com.michaldrabik.ui_show.sections.seasons.cases

import com.michaldrabik.common.extensions.toMillis
import com.michaldrabik.repository.EpisodesManager
import com.michaldrabik.repository.shows.ShowsRepository
import com.michaldrabik.ui_model.EpisodeBundle
import com.michaldrabik.ui_model.Show
import com.michaldrabik.ui_show.quicksetup.QuickSetupListItem
import com.michaldrabik.ui_show.sections.seasons.recycler.SeasonListItem
import java.time.ZonedDateTime
import javax.inject.Inject

class ShowDetailsQuickProgressCase @Inject constructor(
  private val showsRepository: ShowsRepository,
  private val episodesManager: EpisodesManager,
) {

  suspend fun setQuickProgress(
    item: QuickSetupListItem,
    seasons: List<SeasonListItem>,
    show: Show,
    customDate: ZonedDateTime?,
    useReleaseDate: Boolean = false,
  ) {
    val (targetEpisode, targetSeason) = item

    seasons
      .filter { it.season.number <= targetSeason.number }
      .forEach { seasonItem ->
        val episodes = seasonItem.season.episodes
        val isTargetSeason = seasonItem.season.number == targetSeason.number

        episodes
          .filter { if (isTargetSeason) it.number <= targetEpisode.number else true }
          .forEach { episode ->
            episodesManager.setEpisodeWatched(
              episodeBundle = EpisodeBundle(episode, seasonItem.season, show),
              customDate = customDate,
              useReleaseDate = useReleaseDate,
            )
          }
      }

    showsRepository.myShows.updateWatchedAt(show.traktId, customDate?.toMillis() ?: 0L)
  }
}
