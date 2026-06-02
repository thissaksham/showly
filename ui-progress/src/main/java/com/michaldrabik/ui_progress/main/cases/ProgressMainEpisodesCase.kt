package com.michaldrabik.ui_progress.main.cases

import com.michaldrabik.common.dispatchers.CoroutineDispatchers
import com.michaldrabik.data_local.sources.EpisodesLocalDataSource
import com.michaldrabik.repository.EpisodesManager
import com.michaldrabik.repository.settings.SettingsSpoilersRepository
import com.michaldrabik.repository.shows.ShowsRepository
import com.michaldrabik.ui_model.Episode
import com.michaldrabik.ui_model.EpisodeBundle
import com.michaldrabik.ui_model.Show
import kotlinx.coroutines.withContext
import java.time.ZonedDateTime
import javax.inject.Inject

class ProgressMainEpisodesCase @Inject constructor(
  private val dispatchers: CoroutineDispatchers,
  private val episodesManager: EpisodesManager,
  private val showsRepository: ShowsRepository,
  private val spoilersSettings: SettingsSpoilersRepository,
  private val localDataSource: EpisodesLocalDataSource,
) {

  suspend fun setEpisodeWatched(
    episodeBundle: EpisodeBundle,
    customDate: ZonedDateTime?,
  ) = withContext(dispatchers.IO) {
    episodesManager.setEpisodeWatched(episodeBundle, customDate)
  }

  suspend fun isWatched(
    show: Show,
    episode: Episode,
  ) = withContext(dispatchers.IO) {
    val localEpisode = localDataSource.getById(
      show.traktId,
      episode.ids.trakt.id,
    )
    val isFollowed = showsRepository.myShows.exists(show.ids.trakt)
    val isWatchlist = showsRepository.watchlistShows.exists(show.ids.trakt)
    val isDropped = showsRepository.droppedShows.exists(show.ids.trakt)

    val areSpoilersHidden = when {
      isFollowed -> spoilersSettings.isMyShowsHidden
      isWatchlist -> spoilersSettings.isWatchlistShowsHidden
      isDropped -> spoilersSettings.isDroppedShowsHidden
      else -> spoilersSettings.isUncollectedShowsHidden
    }
    localEpisode?.isWatched == true || !areSpoilersHidden
  }
}
