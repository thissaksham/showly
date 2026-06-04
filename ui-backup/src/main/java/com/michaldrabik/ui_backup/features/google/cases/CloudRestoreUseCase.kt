package com.michaldrabik.ui_backup.features.google.cases

import com.michaldrabik.ui_backup.features.google.GoogleDriveManager
import com.michaldrabik.ui_backup.features.import_.workers.BackupImportWorker
import com.michaldrabik.ui_backup.features.export.cases.CreateBackupSchemeFromJsonUseCase
import javax.inject.Inject

class CloudRestoreUseCase @Inject constructor(
  private val googleDriveManager: GoogleDriveManager,
  private val createBackupSchemeFromJsonUseCase: CreateBackupSchemeFromJsonUseCase,
  private val backupImportWorker: BackupImportWorker,
) {

  companion object {
    private const val BACKUP_FILE_NAME = "showly_plus_cloud_backup.json"
  }

  suspend operator fun invoke(): Result<Unit> {
    return googleDriveManager.downloadBackup(BACKUP_FILE_NAME).mapCatching { json ->
      val scheme = createBackupSchemeFromJsonUseCase(json).getOrThrow()
        ?: throw Exception("Invalid backup data")
      backupImportWorker.run(scheme)
    }
  }
}
