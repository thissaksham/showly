package com.michaldrabik.ui_base.sync

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.michaldrabik.common.dispatchers.CoroutineDispatchers
import com.michaldrabik.ui_base.events.EventsManager
import com.michaldrabik.ui_base.events.ShowsMoviesSyncComplete
import com.michaldrabik.ui_base.sync.runners.MoviesSyncRunner
import com.michaldrabik.ui_base.sync.runners.ShowsSyncRunner
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.withContext
import timber.log.Timber
import java.util.concurrent.TimeUnit

@HiltWorker
class ShowsMoviesSyncWorker @AssistedInject constructor(
  @Assisted val context: Context,
  @Assisted workerParams: WorkerParameters,
  private val showsSyncRunner: ShowsSyncRunner,
  private val moviesSyncRunner: MoviesSyncRunner,
  private val eventsManager: EventsManager,
  private val dispatchers: CoroutineDispatchers,
) : CoroutineWorker(context, workerParams) {

  companion object {
    private const val TAG = "ShowsMoviesSyncWorker"
    private const val TAG_PERIODIC = "ShowsMoviesSyncWorker_periodic"
    private const val ARG_FORCE = "ARG_FORCE"

    fun schedule(workManager: WorkManager, force: Boolean = false) {
      val constraints = Constraints.Builder()
        .setRequiredNetworkType(NetworkType.CONNECTED)
        .build()

      val oneTimeRequest = OneTimeWorkRequestBuilder<ShowsMoviesSyncWorker>()
        .setConstraints(constraints)
        .addTag(TAG)
        .setInputData(androidx.work.Data.Builder().putBoolean(ARG_FORCE, force).build())
        .build()

      val periodicRequest = PeriodicWorkRequestBuilder<ShowsMoviesSyncWorker>(12, TimeUnit.HOURS)
        .setConstraints(constraints)
        .addTag(TAG_PERIODIC)
        .build()

      val policy = if (force) ExistingWorkPolicy.REPLACE else ExistingWorkPolicy.KEEP
      workManager.enqueueUniqueWork(TAG, policy, oneTimeRequest)
      workManager.enqueueUniquePeriodicWork(TAG_PERIODIC, ExistingPeriodicWorkPolicy.KEEP, periodicRequest)
      Timber.i("ShowsMoviesSyncWorker scheduled (OneTime + Periodic). Force: $force")
    }
  }

  override suspend fun doWork() =
    withContext(dispatchers.IO) {
      Timber.d("Doing work...")

      val force = inputData.getBoolean(ARG_FORCE, false)

      val showsAsync = async {
        try {
          Timber.d("Starting shows runner...")
          showsSyncRunner.run(force)
        } catch (error: Throwable) {
          Timber.e(error)
          0
        }
      }

      val moviesAsync = async {
        try {
          Timber.d("Starting movies runner...")
          moviesSyncRunner.run(force)
        } catch (error: Throwable) {
          Timber.e(error)
          0
        }
      }

      val (showsCount, moviesCount) = awaitAll(showsAsync, moviesAsync)
      eventsManager.sendEvent(ShowsMoviesSyncComplete(showsCount + moviesCount))

      Timber.d("Work finished. Shows: $showsCount Movies: $moviesCount")
      Result.success()
    }
}
