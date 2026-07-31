package com.michaldrabik.repository.movies

import com.michaldrabik.common.Config
import com.michaldrabik.common.extensions.nowUtcMillis
import com.michaldrabik.data_local.LocalDataSource
import com.michaldrabik.data_local.database.model.RelatedMovie
import com.michaldrabik.data_local.utilities.TransactionsProvider
import com.michaldrabik.data_remote.RemoteDataSource
import com.michaldrabik.data_remote.tmdb.model.TmdbDiscoveryItem
import com.michaldrabik.repository.mappers.Mappers
import com.michaldrabik.repository.utilities.LocalIdResolver
import com.michaldrabik.ui_model.IdTmdb
import com.michaldrabik.ui_model.IdTrakt
import com.michaldrabik.ui_model.Ids
import com.michaldrabik.ui_model.Movie
import javax.inject.Inject

class RelatedMoviesRepository @Inject constructor(
  private val remoteSource: RemoteDataSource,
  private val localSource: LocalDataSource,
  private val transactions: TransactionsProvider,
  private val mappers: Mappers,
) {

  suspend fun loadAll(movie: Movie): List<Movie> {
    val related = localSource.relatedMovies.getAllById(movie.ids.trakt.id)
    val latest = related.maxByOrNull { it.updatedAt }

    if (latest != null && nowUtcMillis() - latest.updatedAt < Config.RELATED_CACHE_DURATION) {
      val relatedIds = related.map { it.idTrakt }
      return localSource.movies
        .getAll(relatedIds)
        .map { mappers.movie.fromDatabase(it) }
    }

    val tmdbId = movie.ids.tmdb.id
      .takeIf { it > 0 }
      ?: LocalIdResolver.tmdbIdOf(movie.ids.trakt.id)
      ?: return emptyList()

    val remote = toMovies(remoteSource.tmdb.relatedMovies(tmdbId))

    cacheRelated(remote, movie.ids.trakt)

    return remote
  }

  /**
   * A movie already in the database keeps the id its watch history hangs off. Anything
   * else gets a minted id and the summary fields recommendations return - stored with
   * updatedAt = 0 so opening it refetches full details.
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

  private suspend fun cacheRelated(
    movies: List<Movie>,
    movieId: IdTrakt,
  ) {
    transactions.withTransaction {
      val timestamp = nowUtcMillis()
      localSource.movies.upsert(
        movies.map { movie ->
          val row = mappers.movie.toDatabase(movie)
          if (movie.createdAt == 0L) row.copy(updatedAt = 0) else row
        },
      )
      localSource.relatedMovies.deleteById(movieId.id)
      localSource.relatedMovies.insert(
        movies.map {
          RelatedMovie.fromTraktId(it.ids.trakt.id, movieId.id, timestamp)
        },
      )
    }
  }
}
