package com.michaldrabik.ui_backup.features.google

/**
 * A copy of the backup held in Drive. Two are kept: the latest, and the one taken
 * immediately before it, so a single bad overwrite is always recoverable.
 *
 * [counts] is null for a file written before counts were recorded on it - the list
 * still shows the date, which is enough to choose between two copies.
 */
data class CloudBackupFile(
  val fileName: String,
  val isLatest: Boolean,
  val modifiedAt: Long,
  val counts: BackupCounts?,
)
