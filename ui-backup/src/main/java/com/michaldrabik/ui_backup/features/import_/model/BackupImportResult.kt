package com.michaldrabik.ui_backup.features.import_.model

/**
 * What an import actually managed to do. Items whose details cannot be fetched are
 * skipped so one bad title does not abort the whole restore, but the caller has to
 * be able to say so - a restore that quietly dropped most of a library used to look
 * identical to one that worked.
 */
data class BackupImportResult(
  val skippedTitles: List<String>,
) {
  val skippedCount: Int get() = skippedTitles.size
  val isComplete: Boolean get() = skippedTitles.isEmpty()

  companion object {
    val EMPTY = BackupImportResult(skippedTitles = emptyList())
  }
}
