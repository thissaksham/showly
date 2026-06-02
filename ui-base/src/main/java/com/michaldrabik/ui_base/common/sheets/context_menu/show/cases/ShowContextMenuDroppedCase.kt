package com.michaldrabik.ui_base.common.sheets.context_menu.show.cases

import com.michaldrabik.common.dispatchers.CoroutineDispatchers
import com.michaldrabik.data_local.LocalDataSource
import com.michaldrabik.data_local.database.model.Season
import com.michaldrabik.data_local.utilities.TransactionsProvider
import com.michaldrabik.repository.PinnedItemsRepository
import com.michaldrabik.repository.shows.ShowsRepository
import com.michaldrabik.ui_base.notifications.AnnouncementManager
import com.michaldrabik.ui_model.IdTrakt
import com.michaldrabik.ui_model.Ids
import com.michaldrabik.ui_model.Show
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ShowContextMenuDroppedCase @Inject constructor(
  private val dispatchers: CoroutineDispatchers,
  private val localSource: LocalDataSource,
  private val transactions: TransactionsProvider,
  private val showsRepository: ShowsRepository,
  private val pinnedItemsRepository: PinnedItemsRepository,
  private val announcementManager: AnnouncementManager,
) {

  suspend fun moveToDropped(
    traktId: IdTrakt,
    removeLocalData: Boolean,
  ) = withContext(dispatchers.IO) {
    val show = Show.EMPTY.copy(ids = Ids.EMPTY.copy(traktId))
    transactions.withTransaction {
      showsRepository.droppedShows.insert(show.ids.trakt)

      if (removeLocalData) {
        localSource.episodes.deleteAllUnwatchedForShow(show.traktId)
        val seasons = localSource.seasons.getAllByShowId(show.traktId)
        val episodes = localSource.episodes.getAllByShowId(show.traktId)
        val toDelete = mutableListOf<Season>()
        seasons.forEach { season ->
          if (episodes.none { it.idSeason == season.idTrakt }) {
            toDelete.add(season)
          }
        }
        localSource.seasons.delete(toDelete)
      }
    }
    pinnedItemsRepository.removePinnedItem(show)
    announcementManager.refreshShowsAnnouncements()
  }

  suspend fun removeFromDropped(traktId: IdTrakt) =
    withContext(dispatchers.IO) {
      showsRepository.droppedShows.delete(traktId)
      announcementManager.refreshShowsAnnouncements()
    }
}
