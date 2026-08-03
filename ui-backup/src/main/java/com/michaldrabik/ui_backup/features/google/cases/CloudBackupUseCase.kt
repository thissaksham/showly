package com.michaldrabik.ui_backup.features.google.cases

import com.michaldrabik.ui_backup.features.export.cases.CreateBackupJsonUseCase
import com.michaldrabik.ui_backup.features.google.GoogleDriveManager
import javax.inject.Inject

class CloudBackupUseCase @Inject constructor(
  private val createBackupJsonUseCase: CreateBackupJsonUseCase,
  private val googleDriveManager: GoogleDriveManager,
) {

  /**
   * [force] bypasses the guard that refuses to replace a large backup with a much
   * smaller one. Pass it only once the user has been shown both sizes and agreed.
   */
  suspend operator fun invoke(force: Boolean = false): Result<Unit> {
    val json = createBackupJsonUseCase()
    return googleDriveManager.uploadBackup(jsonContent = json, force = force)
  }
}
