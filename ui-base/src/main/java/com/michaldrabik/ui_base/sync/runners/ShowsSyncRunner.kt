package com.michaldrabik.ui_base.sync.runners

import com.michaldrabik.common.ConfigVariant.SHOW_SYNC_COOLDOWN
import com.michaldrabik.common.extensions.nowUtcMillis
import com.michaldrabik.data_local.LocalDataSource
import com.michaldrabik.data_local.database.model.EpisodesSyncLog
import com.michaldrabik.data_remote.RemoteDataSource
import com.michaldrabik.repository.EpisodesManager
import com.michaldrabik.repository.mappers.Mappers
import com.michaldrabik.repository.shows.ShowsRepository
import com.michaldrabik.ui_model.ShowStatus.CANCELED
import com.michaldrabik.ui_model.ShowStatus.ENDED
import com.michaldrabik.ui_model.ShowStatus.UNKNOWN
import kotlinx.coroutines.delay
import timber.log.Timber
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

/**
 * This class is responsible for fetching and syncing missing/updated episodes data for current progress shows.
 */
@Singleton
class ShowsSyncRunner @Inject constructor(
  private val remoteSource: RemoteDataSource,
  private val localSource: LocalDataSource,
  private val mappers: Mappers,
  private val episodesManager: EpisodesManager,
  private val showsRepository: ShowsRepository,
) {

  companion object {
    private const val DELAY_MS = 10L
    private val ENDED_SHOW_SYNC_COOLDOWN = TimeUnit.DAYS.toMillis(3)
  }

  suspend fun run(): Int {
    Timber.i("Shows sync initialized.")

    val myShows = showsRepository.myShows.loadAll()
    val watchlistShows = showsRepository.watchlistShows.loadAll()
    val watchlistShowsIds = watchlistShows.map { it.traktId }

    // We sync all shows in My Shows (to catch new seasons) and only non-ended shows in Watchlist.
    val showsToSync = (myShows + watchlistShows)

    Timber.i("Shows to sync: ${showsToSync.size}.")
    if (showsToSync.isEmpty()) {
      Timber.i("Nothing to sync. Stopping...")
      return 0
    }

    var syncCount = 0
    val syncLog = localSource.episodesSyncLog.getAll()
    showsToSync.forEach { show ->
      val isInWatchlist = show.traktId in watchlistShowsIds
      val isEnded = show.status in arrayOf(ENDED, CANCELED, UNKNOWN)

      val lastSync = syncLog.find { it.idTrakt == show.traktId }?.syncedAt ?: 0
      val cooldown = if (isEnded) ENDED_SHOW_SYNC_COOLDOWN else SHOW_SYNC_COOLDOWN
      
      if (nowUtcMillis() - lastSync < cooldown) {
        Timber.i("${show.title} is on cooldown. No need to sync.")
        return@forEach
      }

      try {
        Timber.i("Syncing ${show.title}(${show.ids.trakt}) details...")
        showsRepository.detailsShow.load(show.ids.trakt, force = true)
        syncCount++
        Timber.i("${show.title}(${show.ids.trakt}) show synced.")
      } catch (t: Throwable) {
        Timber.e("${show.title}(${show.ids.trakt}) show sync error. Skipping... \n$t")
      }

      if (isInWatchlist) {
        localSource.episodesSyncLog.upsert(EpisodesSyncLog(show.traktId, nowUtcMillis()))
      } else {
        try {
          Timber.i("Syncing ${show.title}(${show.ids.trakt}) episodes...")

          val remoteSeasons = remoteSource.trakt
            .fetchSeasons(show.traktId)
            .map { mappers.season.fromNetwork(it) }
          episodesManager.invalidateSeasons(show, remoteSeasons)
          syncCount++

          Timber.i("${show.title}(${show.ids.trakt}) episodes synced.")
        } catch (t: Throwable) {
          Timber.e("${show.title}(${show.ids.trakt}) episodes sync error. Skipping... \n$t")
        } finally {
          delay(DELAY_MS)
        }
      }
    }

    return syncCount
  }
}
