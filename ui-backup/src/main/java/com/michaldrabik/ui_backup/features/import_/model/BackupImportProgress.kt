package com.michaldrabik.ui_backup.features.import_.model

import java.util.concurrent.atomic.AtomicInteger

/**
 * One counter shared by every runner, so a single "45 / 233" spans shows, movies and
 * lists rather than restarting at each stage.
 *
 * Atomic because the runners are free to work concurrently.
 */
class BackupImportProgress(
  val total: Int,
) {
  private val counter = AtomicInteger(0)

  fun advance(): Int = counter.incrementAndGet().coerceAtMost(total)
}
