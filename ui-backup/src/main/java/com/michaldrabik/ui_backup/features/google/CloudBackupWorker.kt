package com.michaldrabik.ui_backup.features.google

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.michaldrabik.repository.settings.SettingsRepository
import com.michaldrabik.ui_backup.features.google.cases.CloudBackupUseCase
import com.michaldrabik.ui_base.Logger
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import timber.log.Timber
import java.util.concurrent.TimeUnit

@HiltWorker
class CloudBackupWorker @AssistedInject constructor(
  @Assisted appContext: Context,
  @Assisted workerParams: WorkerParameters,
  private val cloudBackupUseCase: CloudBackupUseCase,
  private val googleAuthManager: GoogleAuthManager,
  private val settingsRepository: SettingsRepository,
) : CoroutineWorker(appContext, workerParams) {

  companion object {
    private const val TAG = "CLOUD_BACKUP_WORK"

    fun schedule(context: Context) {
      val constraints = Constraints.Builder()
        .setRequiredNetworkType(NetworkType.CONNECTED)
        .build()

      val request = PeriodicWorkRequestBuilder<CloudBackupWorker>(15, TimeUnit.MINUTES)
        .setConstraints(constraints)
        .addTag(TAG)
        .build()

      WorkManager.getInstance(context).enqueueUniquePeriodicWork(
        TAG,
        ExistingPeriodicWorkPolicy.KEEP,
        request
      )
    }

    fun cancel(context: Context) {
      WorkManager.getInstance(context).cancelUniqueWork(TAG)
    }
  }

  override suspend fun doWork(): Result {
    if (!googleAuthManager.hasDrivePermission()) {
      return Result.success() // Cannot backup without permission
    }

    return try {
      Timber.i("Starting periodic cloud backup")
      cloudBackupUseCase().fold(
        onSuccess = {
          settingsRepository.cloudBackupTimestamp = System.currentTimeMillis()
          Result.success()
        },
        onFailure = { throw it }
      )
    } catch (e: Exception) {
      Timber.e(e, "Periodic cloud backup failed")
      Logger.record(e, "CloudBackupWorker::doWork")
      Result.retry()
    }
  }
}
