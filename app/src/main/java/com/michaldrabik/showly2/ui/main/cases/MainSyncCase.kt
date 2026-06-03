package com.michaldrabik.showly2.ui.main.cases

import androidx.work.WorkManager
import com.michaldrabik.ui_base.sync.ShowsMoviesSyncWorker
import dagger.hilt.android.scopes.ViewModelScoped
import javax.inject.Inject

@ViewModelScoped
class MainSyncCase @Inject constructor(
  private val workManager: WorkManager,
) {

  fun refreshSyncSchedule() {
    ShowsMoviesSyncWorker.schedule(workManager)
    ShowsMoviesSyncWorker.schedulePeriodic(workManager)
  }
}
