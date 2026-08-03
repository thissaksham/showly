package com.michaldrabik.ui_backup.features.google

data class CloudBackupUiState(
  val isConnected: Boolean = false,
  val isBackingUp: Boolean = false,
  val isRestoring: Boolean = false,
  val lastBackupTimestamp: Long = 0L,
  val backups: List<CloudBackupFile> = emptyList(),
  val error: Throwable? = null,
  val successMessage: Int? = null,
  /** Raised when a backup is refused for holding far less than the stored one. */
  val shrinkWarning: BackupShrinkException? = null,
  /** Items the finished restore could not fetch. Null until a restore completes. */
  val restoreSkippedCount: Int? = null,
) {
  val isBusy: Boolean get() = isBackingUp || isRestoring
}
