package com.michaldrabik.ui_backup.features.google.cases

import com.michaldrabik.ui_backup.features.export.cases.CreateBackupJsonUseCase
import com.michaldrabik.ui_backup.features.google.GoogleDriveManager
import javax.inject.Inject

class CloudBackupUseCase @Inject constructor(
  private val createBackupJsonUseCase: CreateBackupJsonUseCase,
  private val googleDriveManager: GoogleDriveManager,
) {

  companion object {
    private const val BACKUP_FILE_NAME = "showly_plus_cloud_backup.json"
  }

  suspend operator fun invoke(): Result<Unit> {
    val json = createBackupJsonUseCase()
    return googleDriveManager.uploadBackup(json, BACKUP_FILE_NAME)
  }
}
