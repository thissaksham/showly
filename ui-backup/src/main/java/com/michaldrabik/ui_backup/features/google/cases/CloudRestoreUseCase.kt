package com.michaldrabik.ui_backup.features.google.cases

import com.michaldrabik.ui_backup.features.export.cases.CreateBackupSchemeFromJsonUseCase
import com.michaldrabik.ui_backup.features.google.GoogleDriveManager
import com.michaldrabik.ui_backup.features.import_.model.BackupImportResult
import com.michaldrabik.ui_backup.features.import_.model.BackupImportStatus
import com.michaldrabik.ui_backup.features.import_.workers.BackupImportWorker
import javax.inject.Inject

class CloudRestoreUseCase @Inject constructor(
  private val googleDriveManager: GoogleDriveManager,
  private val createBackupSchemeFromJsonUseCase: CreateBackupSchemeFromJsonUseCase,
  private val backupImportWorker: BackupImportWorker,
) {

  /**
   * [fileName] selects which copy to restore. The previous one is kept so a bad
   * overwrite can still be rolled back from the device.
   *
   * [onProgress] reports the title currently being imported, for the notification.
   */
  suspend operator fun invoke(
    fileName: String = GoogleDriveManager.BACKUP_FILE_NAME,
    onProgress: ((BackupImportStatus) -> Unit)? = null,
  ): Result<BackupImportResult> =
    googleDriveManager.downloadBackup(fileName).mapCatching { json ->
      val scheme = createBackupSchemeFromJsonUseCase(json).getOrThrow()
        ?: throw Exception("Invalid backup data")
      backupImportWorker.statusListener = onProgress
      try {
        backupImportWorker.run(scheme)
      } finally {
        backupImportWorker.statusListener = null
      }
    }
}
