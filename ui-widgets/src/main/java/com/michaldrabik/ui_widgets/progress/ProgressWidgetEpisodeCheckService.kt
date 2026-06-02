package com.michaldrabik.ui_widgets.progress

import android.content.Context
import android.content.Intent
import androidx.core.app.JobIntentService
import com.michaldrabik.repository.EpisodesManager
import com.michaldrabik.ui_base.common.WidgetsProvider
import com.michaldrabik.ui_model.IdTrakt
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import javax.inject.Inject
import kotlin.coroutines.CoroutineContext

@AndroidEntryPoint
class ProgressWidgetEpisodeCheckService : JobIntentService(), CoroutineScope {

  companion object {
    private const val JOB_ID = 1000
    private const val EXTRA_EPISODE_ID = "EXTRA_EPISODE_ID"
    private const val EXTRA_SEASON_ID = "EXTRA_SEASON_ID"
    private const val EXTRA_SHOW_ID = "EXTRA_SHOW_ID"

    fun initialize(
      context: Context,
      episodeId: Long,
      seasonId: Long,
      showId: IdTrakt,
    ) {
      val intent = Intent(context, ProgressWidgetEpisodeCheckService::class.java).apply {
        putExtra(EXTRA_EPISODE_ID, episodeId)
        putExtra(EXTRA_SEASON_ID, seasonId)
        putExtra(EXTRA_SHOW_ID, showId)
      }
      enqueueWork(context, ProgressWidgetEpisodeCheckService::class.java, JOB_ID, intent)
    }
  }

  override val coroutineContext: CoroutineContext = Dispatchers.IO + SupervisorJob()

  @Inject lateinit var episodesManager: EpisodesManager

  override fun onHandleWork(intent: Intent) {
    val episodeId = intent.getLongExtra(EXTRA_EPISODE_ID, -1L)
    val seasonId = intent.getLongExtra(EXTRA_SEASON_ID, -1L)
    val showId = intent.getParcelableExtra<IdTrakt>(EXTRA_SHOW_ID)!!

    launch {
      episodesManager.setEpisodeWatched(
        episodeId = episodeId,
        seasonId = seasonId,
        showId = showId,
        customDate = null,
      )

      (applicationContext as? WidgetsProvider)?.requestShowsWidgetsUpdate()
    }
  }

  override fun onDestroy() {
    super.onDestroy()
  }
}
