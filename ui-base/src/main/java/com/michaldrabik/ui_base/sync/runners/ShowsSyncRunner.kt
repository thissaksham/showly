package com.michaldrabik.ui_base.sync.runners

import com.michaldrabik.common.ConfigVariant.SHOW_STATIC_SYNC_COOLDOWN
import com.michaldrabik.common.ConfigVariant.SHOW_SYNC_COOLDOWN
import com.michaldrabik.common.extensions.nowUtcMillis
import com.michaldrabik.data_local.LocalDataSource
import com.michaldrabik.data_remote.RemoteDataSource
import com.michaldrabik.repository.EpisodesManager
import com.michaldrabik.repository.mappers.Mappers
import com.michaldrabik.repository.shows.ShowsRepository
import com.michaldrabik.ui_model.ShowStatus.CANCELED
import com.michaldrabik.ui_model.ShowStatus.ENDED
import com.michaldrabik.ui_model.ShowStatus.UNKNOWN
import kotlinx.coroutines.delay
import timber.log.Timber
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
  }

  suspend fun run(): Int {
    Timber.i("Shows sync initialized.")

    val myShows = showsRepository.myShows.loadAll()
    val watchlistShows = showsRepository.watchlistShows.loadAll()

    val showsToSync = (myShows + watchlistShows)
      .filter { it.status != UNKNOWN }

    Timber.i("Shows to sync: ${showsToSync.size}.")
    if (showsToSync.isEmpty()) {
      Timber.i("Nothing to sync. Stopping...")
      return 0
    }

    var syncCount = 0
    val syncLog = localSource.episodesSyncLog.getAll()
    showsToSync.forEach { show ->
      val cooldown = if (show.status in arrayOf(ENDED, CANCELED)) SHOW_STATIC_SYNC_COOLDOWN else SHOW_SYNC_COOLDOWN
      val lastSync = syncLog.find { it.idTrakt == show.traktId }?.syncedAt ?: 0
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

    return syncCount
  }
}
