package com.michaldrabik.ui_backup.features.import_.runners

import com.michaldrabik.ui_backup.features.import_.model.BackupImportProgress
import com.michaldrabik.ui_backup.features.import_.model.BackupImportStatus
import com.michaldrabik.ui_backup.features.import_.model.BackupImportStatus.Importing

internal abstract class BackupImportRunner<T> {
  var statusListener: ((BackupImportStatus) -> Unit)? = null

  /** Shared across the runners so one count spans shows, movies and lists. */
  var progress: BackupImportProgress? = null

  /**
   * Titles left out because their details could not be fetched. Skipping and carrying
   * on is the right behaviour, but it used to happen with no trace at all, so a
   * restore that dropped most of a library looked exactly like one that worked.
   */
  val skipped = mutableListOf<String>()

  /** Counts this item and reports it, so the screen shows "45 / 233" and not a title alone. */
  protected fun reportProgress(title: String) {
    val counter = progress
    statusListener?.invoke(
      Importing(
        title = title,
        current = counter?.advance() ?: 0,
        total = counter?.total ?: 0,
      ),
    )
  }

  abstract suspend fun run(backup: T)
}
