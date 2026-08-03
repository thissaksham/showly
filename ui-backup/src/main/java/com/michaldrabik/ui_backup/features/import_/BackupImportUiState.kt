package com.michaldrabik.ui_backup.features.import_

import com.michaldrabik.ui_backup.features.import_.model.BackupImportStatus
import com.michaldrabik.ui_backup.features.import_.model.BackupImportStatus.Idle

data class BackupImportUiState(
  val isImporting: BackupImportStatus = Idle,
  val isSuccess: Boolean = false,
  val isError: Throwable? = null,
  /** Items whose details could not be fetched. They are left out of the import. */
  val skippedCount: Int = 0,
)
