package com.michaldrabik.repository.shows

import com.michaldrabik.common.Config
import com.michaldrabik.common.extensions.nowUtcMillis
import com.michaldrabik.data_local.LocalDataSource
import com.michaldrabik.data_local.database.model.RelatedShow
import com.michaldrabik.data_local.utilities.TransactionsProvider
import com.michaldrabik.data_remote.RemoteDataSource
import com.michaldrabik.data_remote.tmdb.model.TmdbDiscoveryItem
import com.michaldrabik.repository.mappers.Mappers
import com.michaldrabik.repository.utilities.LocalIdResolver
import com.michaldrabik.ui_model.IdTmdb
import com.michaldrabik.ui_model.IdTrakt
import com.michaldrabik.ui_model.Ids
import com.michaldrabik.ui_model.Show
import javax.inject.Inject

class RelatedShowsRepository @Inject constructor(
  private val remoteSource: RemoteDataSource,
  private val localSource: LocalDataSource,
  private val transactions: TransactionsProvider,
  private val mappers: Mappers,
) {

  suspend fun loadAll(
    show: Show,
    hiddenCount: Int,
  ): List<Show> {
    val relatedShows = localSource.relatedShows.getAllById(show.traktId)
    val latest = relatedShows.maxByOrNull { it.updatedAt }

    if (latest != null && nowUtcMillis() - latest.updatedAt < Config.RELATED_CACHE_DURATION) {
      val relatedShowsIds = relatedShows.map { it.idTrakt }
      return localSource.shows
        .getAll(relatedShowsIds)
        .map { mappers.show.fromDatabase(it) }
    }

    val tmdbId = show.ids.tmdb.id
      .takeIf { it > 0 }
      ?: LocalIdResolver.tmdbIdOf(show.traktId)
      ?: return emptyList()

    val remoteShows = toShows(remoteSource.tmdb.relatedShows(tmdbId))

    cacheRelatedShows(remoteShows, show.ids.trakt)

    return remoteShows
  }

  /**
   * A show already in the database keeps the id its watch history hangs off. Anything
   * else gets a minted id and the summary fields recommendations return - stored with
   * updatedAt = 0 so opening it refetches full details.
   */
  private suspend fun toShows(items: List<TmdbDiscoveryItem>): List<Show> {
    if (items.isEmpty()) return emptyList()
    val local = localSource.shows
      .getByTmdbIds(items.map { it.id })
      .associateBy { it.idTmdb }

    return items.map { item ->
      local[item.id]
        ?.let { mappers.show.fromDatabase(it) }
        ?: mappers.show.fromTmdbDiscovery(item).copy(
          ids = Ids.EMPTY.copy(
            tmdb = IdTmdb(item.id),
            trakt = IdTrakt(LocalIdResolver.newId(item.id)),
          ),
        )
    }
  }

  private suspend fun cacheRelatedShows(
    shows: List<Show>,
    showId: IdTrakt,
  ) {
    transactions.withTransaction {
      val timestamp = nowUtcMillis()
      localSource.shows.upsert(
        shows.map { show ->
          val row = mappers.show.toDatabase(show)
          if (show.createdAt == 0L) row.copy(updatedAt = 0) else row
        },
      )
      localSource.relatedShows.deleteById(showId.id)
      localSource.relatedShows.insert(
        shows.map {
          RelatedShow.fromTraktId(it.ids.trakt.id, showId.id, timestamp)
        },
      )
    }
  }
}
