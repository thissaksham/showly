package com.michaldrabik.ui_backup.features.google

import android.app.Notification
import android.content.Context
import android.content.pm.ServiceInfo
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.hilt.work.HiltWorker
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingWorkPolicy
import androidx.work.ForegroundInfo
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkInfo
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import com.michaldrabik.ui_backup.R
import com.michaldrabik.ui_backup.features.google.cases.CloudRestoreUseCase
import com.michaldrabik.ui_backup.features.import_.model.BackupImportStatus
import com.michaldrabik.ui_base.Logger
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import timber.log.Timber

/**
 * Restoring re-fetches every show and movie from TMDB and takes many minutes.
 *
 * It used to run in the settings screen's own scope, so navigating away, switching
 * apps or an aggressive battery manager killed it part way and left a half imported
 * library with no error shown. Here it is a foreground service instead: it keeps
 * running with the app in the background, and it survives the screen being closed.
 */
@HiltWorker
class CloudRestoreWorker @AssistedInject constructor(
  @Assisted private val appContext: Context,
  @Assisted workerParams: WorkerParameters,
  private val cloudRestoreUseCase: CloudRestoreUseCase,
) : CoroutineWorker(appContext, workerParams) {

  companion object {
    const val TAG = "CLOUD_RESTORE_WORK"
    const val KEY_FILE_NAME = "file_name"
    const val KEY_SKIPPED_COUNT = "skipped_count"
    const val KEY_ERROR = "error_message"

    private const val NOTIFICATION_ID = 4821
    private const val CHANNEL = "CLOUD_RESTORE"

    fun start(
      context: Context,
      fileName: String = GoogleDriveManager.BACKUP_FILE_NAME,
    ) {
      val request = OneTimeWorkRequestBuilder<CloudRestoreWorker>()
        .setInputData(workDataOf(KEY_FILE_NAME to fileName))
        .setConstraints(
          Constraints
            .Builder()
            .setRequiredNetworkType(NetworkType.CONNECTED)
            .build(),
        ).addTag(TAG)
        .build()

      // KEEP, not REPLACE: a second tap must not restart an import that is already
      // half way through.
      WorkManager.getInstance(context).enqueueUniqueWork(TAG, ExistingWorkPolicy.KEEP, request)
    }

    fun workInfo(context: Context) = WorkManager.getInstance(context).getWorkInfosForUniqueWorkLiveData(TAG)

    /**
     * Blocking, so only call it from a background thread. Used by the backup worker
     * to stay out of the way while a restore is in flight.
     */
    fun isPending(context: Context): Boolean =
      try {
        WorkManager
          .getInstance(context)
          .getWorkInfosForUniqueWork(TAG)
          .get()
          .any { it.state == WorkInfo.State.RUNNING || it.state == WorkInfo.State.ENQUEUED }
      } catch (error: Throwable) {
        Timber.w(error, "Could not read cloud restore work state.")
        false
      }
  }

  override suspend fun doWork(): Result {
    val fileName = inputData.getString(KEY_FILE_NAME) ?: GoogleDriveManager.BACKUP_FILE_NAME
    goToForeground(appContext.getString(R.string.textCloudRestoreNotificationStarting))

    return cloudRestoreUseCase(fileName) { status ->
      if (status is BackupImportStatus.Importing) {
        updateNotification(
          if (status.hasCount) {
            appContext.getString(
              R.string.textCloudRestoreNotificationProgress,
              status.current,
              status.total,
              status.title,
            )
          } else {
            status.title
          },
          status,
        )
      }
    }.fold(
      onSuccess = { result ->
        Timber.i("Cloud restore finished. Skipped ${result.skippedCount} items.")
        Result.success(workDataOf(KEY_SKIPPED_COUNT to result.skippedCount))
      },
      onFailure = { error ->
        Timber.e(error, "Cloud restore failed.")
        Logger.record(error, "CloudRestoreWorker::doWork")
        Result.failure(workDataOf(KEY_ERROR to error.message.orEmpty()))
      },
    )
  }

  /**
   * Only promoted on the first attempt.
   *
   * A rejected foreground service does not fail this coroutine - it throws on the
   * main thread inside WorkManager's own service and takes the process with it, so
   * the catch below cannot save it. WorkManager then retries on next launch and the
   * app dies again, which is an unrecoverable loop for the user.
   *
   * Retrying without the promotion breaks that loop: the restore runs as ordinary
   * background work, which is less durable but never fatal.
   */
  private suspend fun goToForeground(text: String) {
    if (runAttemptCount > 0) {
      Timber.w("Restore attempt $runAttemptCount: staying in the background.")
      return
    }
    try {
      setForeground(foregroundInfo(text))
    } catch (error: Throwable) {
      Timber.w(error, "Could not promote the restore to the foreground.")
    }
  }

  private fun updateNotification(
    text: String,
    status: BackupImportStatus.Importing?,
  ) {
    if (runAttemptCount > 0) return
    try {
      setForegroundAsync(foregroundInfo(text, status))
    } catch (error: Throwable) {
      Timber.w(error, "Could not update the restore notification.")
    }
  }

  private fun foregroundInfo(
    text: String,
    status: BackupImportStatus.Importing? = null,
  ): ForegroundInfo {
    val notification = buildNotification(text, status)
    return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
      ForegroundInfo(NOTIFICATION_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC)
    } else {
      ForegroundInfo(NOTIFICATION_ID, notification)
    }
  }

  private fun buildNotification(
    text: String,
    status: BackupImportStatus.Importing?,
  ): Notification =
    NotificationCompat
      .Builder(appContext, CHANNEL)
      .setContentTitle(appContext.getString(R.string.textCloudRestoreNotificationTitle))
      .setContentText(text)
      .setStyle(NotificationCompat.BigTextStyle().bigText(text))
      .setSubText(appContext.getString(R.string.textCloudRestoreKeepOpen))
      .setSmallIcon(android.R.drawable.stat_sys_download)
      .setOngoing(true)
      .setOnlyAlertOnce(true)
      .apply {
        // A real bar once the total is known; a spinner while downloading.
        if (status != null && status.hasCount) {
          setProgress(status.total, status.current, false)
        } else {
          setProgress(0, 0, true)
        }
      }.build()
}
