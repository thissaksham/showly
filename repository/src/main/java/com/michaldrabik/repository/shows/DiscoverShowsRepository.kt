package com.michaldrabik.repository.shows

import com.michaldrabik.common.Config
import com.michaldrabik.common.extensions.nowUtcDay
import com.michaldrabik.common.extensions.nowUtcMillis
import com.michaldrabik.data_local.LocalDataSource
import com.michaldrabik.data_local.database.model.DiscoverShow
import com.michaldrabik.data_local.utilities.TransactionsProvider
import com.michaldrabik.data_remote.Config.TMDB_ANTICIPATED_PAGES
import com.michaldrabik.data_remote.Config.TMDB_DISCOVER_PAGES
import com.michaldrabik.data_remote.RemoteDataSource
import com.michaldrabik.data_remote.tmdb.model.TmdbDiscoveryItem
import com.michaldrabik.repository.mappers.Mappers
import com.michaldrabik.repository.utilities.LocalIdResolver
import com.michaldrabik.repository.utilities.TmdbFilters
import com.michaldrabik.ui_model.DiscoverFeed
import com.michaldrabik.ui_model.DiscoverFeed.ANTICIPATED
import com.michaldrabik.ui_model.DiscoverFeed.POPULAR
import com.michaldrabik.ui_model.DiscoverFeed.RECENT
import com.michaldrabik.ui_model.DiscoverFeed.TRENDING
import com.michaldrabik.ui_model.Genre
import com.michaldrabik.ui_model.IdTmdb
import com.michaldrabik.ui_model.IdTrakt
import com.michaldrabik.ui_model.Ids
import com.michaldrabik.ui_model.Network
import com.michaldrabik.ui_model.Show
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import javax.inject.Inject

class DiscoverShowsRepository @Inject constructor(
  private val remoteSource: RemoteDataSource,
  private val localSource: LocalDataSource,
  private val transactions: TransactionsProvider,
  private val mappers: Mappers,
) {

  private companion object {
    const val SORT_POPULAR = "popularity.desc"
  }

  suspend fun isCacheValid(): Boolean {
    val stamp = localSource.discoverShows.getMostRecent()?.createdAt ?: 0
    return nowUtcMillis() - stamp < Config.DISCOVER_SHOWS_CACHE_DURATION
  }

  suspend fun loadAllCached(): List<Show> {
    val cachedShows = localSource.discoverShows.getAll().map { it.idTrakt }
    val shows = localSource.shows.getAll(cachedShows)

    return cachedShows
      .map { id -> shows.first { it.idTrakt == id } }
      .map { mappers.show.fromDatabase(it) }
  }

  suspend fun loadAllRemote(
    order: DiscoverFeed,
    showCollection: Boolean,
    collectionSize: Int,
    genres: List<Genre>,
    networks: List<Network>,
  ): List<Show> {
    val shows = when (order) {
      TRENDING, RECENT -> loadRemoteTrending(genres, networks, showCollection, collectionSize)
      POPULAR -> loadRemotePopular(genres, networks)
      ANTICIPATED -> loadRemoteAnticipated(genres, networks)
    }
    cacheDiscoverShows(shows)
    return shows
  }

  /**
   * Trending has no filters on TMDB, so a filtered request falls back to discover
   * sorted by popularity. Genres still narrow the unfiltered feed client-side -
   * trending results carry their genre ids.
   */
  private suspend fun loadRemoteTrending(
    genres: List<Genre>,
    networks: List<Network>,
    showCollection: Boolean,
    collectionSize: Int,
  ): List<Show> {
    return coroutineScope {
      val resultShows = mutableListOf<Show>()
      val genreIds = TmdbFilters.showGenreIds(genres)
      val networkIds = TmdbFilters.networkIds(networks)

      val trendingAsync = async {
        runCatching {
          if (networkIds.isEmpty()) {
            remoteSource.tmdb
              .trendingShows(TMDB_DISCOVER_PAGES)
              .filter { item ->
                genreIds.isEmpty() || item.genre_ids.orEmpty().any { it in genreIds }
              }
          } else {
            remoteSource.tmdb.discoverShows(
              sortBy = SORT_POPULAR,
              genres = TmdbFilters.query(genreIds),
              networks = TmdbFilters.query(networkIds),
              airedAfter = null,
              pages = TMDB_DISCOVER_PAGES,
            )
          }
        }.getOrDefault(emptyList())
      }

      val anticipatedAsync = async {
        runCatching { fetchAnticipated(genreIds, networkIds) }.getOrDefault(emptyList())
      }

      val trendingShows = toShows(trendingAsync.await())
      val anticipatedShows = toShows(anticipatedAsync.await()).toMutableList()

      trendingShows.forEachIndexed { index, trendingShow ->
        addIfMissing(resultShows, trendingShow)
        if (index != 0 && index % 6 == 0 && anticipatedShows.isNotEmpty()) {
          val anticipatedShow = anticipatedShows.removeAt(0)
          addIfMissing(resultShows, anticipatedShow)
        }
      }

      return@coroutineScope resultShows
    }
  }

  private suspend fun loadRemotePopular(
    genres: List<Genre>,
    networks: List<Network>,
  ): List<Show> =
    toShows(
      remoteSource.tmdb.discoverShows(
        sortBy = SORT_POPULAR,
        genres = TmdbFilters.query(TmdbFilters.showGenreIds(genres)),
        networks = TmdbFilters.query(TmdbFilters.networkIds(networks)),
        airedAfter = null,
        pages = TMDB_DISCOVER_PAGES,
      ),
    )

  private suspend fun loadRemoteAnticipated(
    genres: List<Genre>,
    networks: List<Network>,
  ): List<Show> =
    toShows(
      fetchAnticipated(
        TmdbFilters.showGenreIds(genres),
        TmdbFilters.networkIds(networks),
        pages = TMDB_DISCOVER_PAGES,
      ),
    )

  /** Anticipated == not aired yet, most popular first. */
  private suspend fun fetchAnticipated(
    genreIds: List<Long>,
    networkIds: List<Long>,
    pages: Int = TMDB_ANTICIPATED_PAGES,
  ) = remoteSource.tmdb.discoverShows(
    sortBy = SORT_POPULAR,
    genres = TmdbFilters.query(genreIds),
    networks = TmdbFilters.query(networkIds),
    airedAfter = nowUtcDay().toString(),
    pages = pages,
  )

  /**
   * A show already in the database keeps its id and its full details; anything else
   * gets a minted id and the summary fields discover returns. Those summaries are
   * stored with updatedAt = 0 on purpose, so opening one refetches full details
   * instead of serving a half-empty row for the details cache window.
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

  suspend fun cacheDiscoverShows(shows: List<Show>) {
    transactions.withTransaction {
      val timestamp = nowUtcMillis()
      localSource.shows.upsert(
        shows.map { show ->
          val row = mappers.show.toDatabase(show)
          // toDatabase always stamps updatedAt with "now". A discover summary carries
          // only a handful of fields, so it must instead read as stale and be
          // refetched in full when opened. createdAt == 0 marks those summaries.
          if (show.createdAt == 0L) row.copy(updatedAt = 0) else row
        },
      )
      localSource.discoverShows.replace(
        shows.map {
          DiscoverShow(
            idTrakt = it.ids.trakt.id,
            createdAt = timestamp,
            updatedAt = timestamp,
          )
        },
      )
    }
  }

  private fun addIfMissing(
    shows: MutableList<Show>,
    show: Show,
  ) {
    if (shows.any { it.ids.trakt == show.ids.trakt }) return
    shows.add(show)
  }
}
