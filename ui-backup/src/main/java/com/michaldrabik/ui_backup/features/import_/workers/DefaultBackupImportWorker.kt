package com.michaldrabik.ui_backup.features.import_.workers

import com.michaldrabik.ui_backup.features.import_.model.BackupImportProgress
import com.michaldrabik.ui_backup.features.import_.model.BackupImportResult
import com.michaldrabik.ui_backup.features.import_.model.BackupImportStatus
import com.michaldrabik.ui_backup.features.import_.runners.BackupImportListsRunner
import com.michaldrabik.ui_backup.features.import_.runners.BackupImportMoviesRunner
import com.michaldrabik.ui_backup.features.import_.runners.BackupImportRunner
import com.michaldrabik.ui_backup.features.import_.runners.BackupImportShowsRunner
import com.michaldrabik.ui_backup.model.BackupScheme
import kotlinx.coroutines.coroutineScope
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
internal class DefaultBackupImportWorker @Inject constructor(
  private val importShowsRunner: BackupImportShowsRunner,
  private val importMoviesRunner: BackupImportMoviesRunner,
  private val importListsRunner: BackupImportListsRunner,
) : BackupImportWorker {

  override var statusListener: ((BackupImportStatus) -> Unit)? = null
    set(value) {
      field = value
      importShowsRunner.statusListener = field
      importMoviesRunner.statusListener = field
      importListsRunner.statusListener = field
    }

  override suspend fun run(backup: BackupScheme): BackupImportResult {
    val runners: List<BackupImportRunner<*>> =
      listOf(importShowsRunner, importMoviesRunner, importListsRunner)

    val progress = BackupImportProgress(total = countItems(backup))
    runners.forEach {
      // The runners are singletons, so a previous run's skips would otherwise be
      // reported again by the next one.
      it.skipped.clear()
      it.progress = progress
    }

    coroutineScope {
      importShowsRunner.run(backup.shows)
      importMoviesRunner.run(backup.movies)
      importListsRunner.run(backup.lists)
    }

    return BackupImportResult(skippedTitles = runners.flatMap { it.skipped })
  }

  /**
   * Collection entries only. Each one may cost a network fetch, so they are what the
   * wait is made of - episodes and seasons ride along with their show and would make
   * the count jump in ways that do not match the time passing.
   */
  private fun countItems(backup: BackupScheme): Int =
    with(backup) {
      shows.collectionHistory.size +
        shows.collectionWatchlist.size +
        shows.collectionDropped.size +
        movies.collectionHistory.size +
        movies.collectionWatchlist.size +
        movies.collectionDropped.size +
        lists.lists.size
    }
}
