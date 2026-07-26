package com.michaldrabik.ui_base.sync.runners

import com.michaldrabik.common.ConfigVariant.MOVIE_STATIC_SYNC_COOLDOWN
import com.michaldrabik.common.ConfigVariant.MOVIE_SYNC_COOLDOWN
import com.michaldrabik.common.extensions.nowUtcMillis
import com.michaldrabik.data_local.LocalDataSource
import com.michaldrabik.repository.movies.MoviesRepository
import com.michaldrabik.repository.settings.SettingsRepository
import com.michaldrabik.ui_model.MovieStatus.IN_PRODUCTION
import com.michaldrabik.ui_model.MovieStatus.PLANNED
import com.michaldrabik.ui_model.MovieStatus.POST_PRODUCTION
import com.michaldrabik.ui_model.MovieStatus.RELEASED
import com.michaldrabik.ui_model.MovieStatus.RUMORED
import kotlinx.coroutines.delay
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton

/**
 * This class is responsible for fetching and syncing missing/updated movies data.
 */
@Singleton
class MoviesSyncRunner @Inject constructor(
  private val localSource: LocalDataSource,
  private val moviesRepository: MoviesRepository,
  private val settingsRepository: SettingsRepository,
) {

  companion object {
    private const val DELAY_MS = 10L
  }

  suspend fun run(): Int {
    Timber.i("Movies sync initialized.")

    if (!settingsRepository.isMoviesEnabled) {
      Timber.i("Movies are disabled. Exiting...")
      return 0
    }

    val moviesToSync = moviesRepository.loadCollectionSyncInfo()
      .filter { it.status in arrayOf(PLANNED.name, IN_PRODUCTION.name, POST_PRODUCTION.name, RUMORED.name, RELEASED.name) }
    
    if (moviesToSync.isEmpty()) {
      Timber.i("Nothing to process. Exiting...")
      return 0
    }
    Timber.i("Movies to sync count: ${moviesToSync.size}.")

    var syncCount = 0
    val syncLog = localSource.moviesSyncLog.getAll()
    moviesToSync.forEach { movie ->
      val traktId = com.michaldrabik.ui_model.IdTrakt(movie.idTrakt)
      val cooldown = if (movie.status == RELEASED.name) MOVIE_STATIC_SYNC_COOLDOWN else MOVIE_SYNC_COOLDOWN
      val lastSync = syncLog.find { it.idTrakt == movie.idTrakt }?.syncedAt ?: 0
      if (nowUtcMillis() - lastSync < cooldown) {
        Timber.i("${movie.title} is on cooldown. No need to sync.")
        return@forEach
      }

      try {
        Timber.i("Syncing ${movie.title}(${movie.idTrakt}) details...")
        moviesRepository.movieDetails.load(traktId, force = true)
        syncCount++
        Timber.i("${movie.title}(${movie.idTrakt}) movie synced.")
      } catch (t: Throwable) {
        Timber.e("${movie.title}(${movie.idTrakt}) movie sync error. Skipping... \n$t")
      } finally {
        delay(DELAY_MS)
      }
    }

    return syncCount
  }
}
