package com.michaldrabik.ui_backup.features.import_.model

sealed interface BackupImportStatus {
  data object Idle : BackupImportStatus

  data object Initializing : BackupImportStatus

  /**
   * [current] and [total] count collection entries - shows, movies and lists - not
   * episodes. Each one costs a network fetch, so they are what the wait is actually
   * made of. [total] is 0 when it could not be worked out; show just the title then.
   */
  data class Importing(
    val title: String,
    val current: Int = 0,
    val total: Int = 0,
  ) : BackupImportStatus {
    val hasCount: Boolean get() = total > 0
  }
}
