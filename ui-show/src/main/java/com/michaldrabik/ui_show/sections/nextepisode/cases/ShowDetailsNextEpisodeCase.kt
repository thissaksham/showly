package com.michaldrabik.ui_show.sections.nextepisode.cases

import com.michaldrabik.common.dispatchers.CoroutineDispatchers
import com.michaldrabik.repository.shows.ShowSeasonsRepository
import com.michaldrabik.ui_model.Episode
import com.michaldrabik.ui_model.IdTrakt
import dagger.hilt.android.scopes.ViewModelScoped
import kotlinx.coroutines.withContext
import javax.inject.Inject

@ViewModelScoped
class ShowDetailsNextEpisodeCase @Inject constructor(
  private val dispatchers: CoroutineDispatchers,
  private val showSeasonsRepository: ShowSeasonsRepository,
) {

  suspend fun loadNextEpisode(traktId: IdTrakt): Episode? =
    withContext(dispatchers.IO) {
      showSeasonsRepository.loadNextEpisode(traktId.id)
    }
}
