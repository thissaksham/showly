package com.michaldrabik.repository.movies

import com.michaldrabik.common.Config
import com.michaldrabik.common.extensions.nowUtcDay
import com.michaldrabik.common.extensions.nowUtcMillis
import com.michaldrabik.data_local.LocalDataSource
import com.michaldrabik.data_local.database.model.DiscoverMovie
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
import com.michaldrabik.ui_model.Movie
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import javax.inject.Inject

class DiscoverMoviesRepository @Inject constructor(
  private val remoteSource: RemoteDataSource,
  private val localSource: LocalDataSource,
  private val transactions: TransactionsProvider,
  private val mappers: Mappers,
) {

  private companion object {
    const val SORT_POPULAR = "popularity.desc"
  }

  suspend fun isCacheValid(): Boolean {
    val stamp = localSource.discoverMovies.getMostRecent()?.createdAt ?: 0
    return nowUtcMillis() - stamp < Config.DISCOVER_MOVIES_CACHE_DURATION
  }

  suspend fun loadAllCached(): List<Movie> {
    val cachedMovies = localSource.discoverMovies.getAll().map { it.idTrakt }
    val movies = localSource.movies.getAll(cachedMovies)

    return cachedMovies
      .map { id -> movies.first { it.idTrakt == id } }
      .map { mappers.movie.fromDatabase(it) }
  }

  suspend fun loadAllRemote(
    order: DiscoverFeed,
    showCollection: Boolean,
    collectionSize: Int,
    genres: List<Genre>,
  ): List<Movie> {
    val movies = when (order) {
      TRENDING, RECENT -> loadRemoteTrending(genres, showCollection, collectionSize)
      POPULAR -> loadRemotePopular(genres)
      ANTICIPATED -> loadRemoteAnticipated(genres)
    }
    cacheDiscoverMovies(movies)
    return movies
  }

  /**
   * Trending has no genre filter on TMDB, so it is applied client-side - trending
   * results carry their genre ids.
   */
  private suspend fun loadRemoteTrending(
    genres: List<Genre>,
    showCollection: Boolean,
    collectionSize: Int,
  ): List<Movie> {
    return coroutineScope {
      val resultMovies = mutableListOf<Movie>()
      val genreIds = TmdbFilters.movieGenreIds(genres)

      val trendingAsync = async {
        runCatching {
          remoteSource.tmdb
            .trendingMovies(TMDB_DISCOVER_PAGES)
            .filter { item ->
              genreIds.isEmpty() || item.genre_ids.orEmpty().any { it in genreIds }
            }
        }.getOrDefault(emptyList())
      }

      val anticipatedAsync = async {
        runCatching { fetchAnticipated(genreIds) }.getOrDefault(emptyList())
      }

      val trendingMovies = toMovies(trendingAsync.await())
      val anticipatedMovies = toMovies(anticipatedAsync.await()).toMutableList()

      trendingMovies.forEachIndexed { index, trendingMovie ->
        addIfMissing(resultMovies, trendingMovie)
        if (index != 0 && index % 6 == 0 && anticipatedMovies.isNotEmpty()) {
          val anticipatedMovie = anticipatedMovies.removeAt(0)
          addIfMissing(resultMovies, anticipatedMovie)
        }
      }

      return@coroutineScope resultMovies
    }
  }

  private suspend fun loadRemotePopular(genres: List<Genre>): List<Movie> =
    toMovies(
      remoteSource.tmdb.discoverMovies(
        sortBy = SORT_POPULAR,
        genres = TmdbFilters.query(TmdbFilters.movieGenreIds(genres)),
        releasedAfter = null,
        pages = TMDB_DISCOVER_PAGES,
      ),
    )

  private suspend fun loadRemoteAnticipated(genres: List<Genre>): List<Movie> =
    toMovies(fetchAnticipated(TmdbFilters.movieGenreIds(genres), pages = TMDB_DISCOVER_PAGES))

  /** Anticipated == not released yet, most popular first. */
  private suspend fun fetchAnticipated(
    genreIds: List<Long>,
    pages: Int = TMDB_ANTICIPATED_PAGES,
  ) = remoteSource.tmdb.discoverMovies(
    sortBy = SORT_POPULAR,
    genres = TmdbFilters.query(genreIds),
    releasedAfter = nowUtcDay().toString(),
    pages = pages,
  )

  /**
   * A movie already in the database keeps its id and its full details; anything else
   * gets a minted id and the summary fields discover returns. Those summaries are
   * stored with updatedAt = 0 on purpose, so opening one refetches full details
   * instead of serving a half-empty row for the details cache window.
   */
  private suspend fun toMovies(items: List<TmdbDiscoveryItem>): List<Movie> {
    if (items.isEmpty()) return emptyList()
    val local = localSource.movies
      .getByTmdbIds(items.map { it.id })
      .associateBy { it.idTmdb }

    return items.map { item ->
      local[item.id]
        ?.let { mappers.movie.fromDatabase(it) }
        ?: mappers.movie.fromTmdbDiscovery(item).copy(
          ids = Ids.EMPTY.copy(
            tmdb = IdTmdb(item.id),
            trakt = IdTrakt(LocalIdResolver.newId(item.id)),
          ),
        )
    }
  }

  suspend fun cacheDiscoverMovies(movies: List<Movie>) {
    transactions.withTransaction {
      val timestamp = nowUtcMillis()
      localSource.movies.upsert(
        movies.map { movie ->
          val row = mappers.movie.toDatabase(movie)
          // toDatabase always stamps updatedAt with "now". A discover summary carries
          // only a handful of fields, so it must instead read as stale and be
          // refetched in full when opened. createdAt == 0 marks those summaries.
          if (movie.createdAt == 0L) row.copy(updatedAt = 0) else row
        },
      )
      localSource.discoverMovies.replace(
        movies.map {
          DiscoverMovie(
            idTrakt = it.ids.trakt.id,
            createdAt = timestamp,
            updatedAt = timestamp,
          )
        },
      )
    }
  }

  private fun addIfMissing(
    movies: MutableList<Movie>,
    movie: Movie,
  ) {
    if (movies.any { it.ids.trakt == movie.ids.trakt }) {
      return
    }
    movies.add(movie)
  }
}
