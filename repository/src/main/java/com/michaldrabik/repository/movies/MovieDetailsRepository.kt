package com.michaldrabik.repository.movies

import com.michaldrabik.common.Config
import com.michaldrabik.common.extensions.nowUtcMillis
import com.michaldrabik.data_local.LocalDataSource
import com.michaldrabik.data_local.database.model.MoviesSyncLog
import com.michaldrabik.data_remote.RemoteDataSource
import com.michaldrabik.repository.mappers.Mappers
import com.michaldrabik.repository.utilities.LocalIdResolver
import com.michaldrabik.ui_model.IdImdb
import com.michaldrabik.ui_model.IdSlug
import com.michaldrabik.ui_model.IdTmdb
import com.michaldrabik.ui_model.IdTrakt
import com.michaldrabik.ui_model.Movie
import javax.inject.Inject

class MovieDetailsRepository @Inject constructor(
  private val remoteSource: RemoteDataSource,
  private val localSource: LocalDataSource,
  private val mappers: Mappers,
) {

  suspend fun load(
    idTrakt: IdTrakt,
    force: Boolean = false,
  ): Movie {
    val local = localSource.movies.getById(idTrakt.id)
    if (force || local == null || nowUtcMillis() - local.updatedAt > Config.MOVIE_DETAILS_CACHE_DURATION) {
      // Details come from TMDB now. The stored row carries the TMDB id; a row minted
      // after the migration encodes it in the (negative) local id instead.
      val tmdbId = local
        ?.idTmdb
        ?.takeIf { it > 0 }
        ?: LocalIdResolver.tmdbIdOf(idTrakt.id)

      if (tmdbId != null) {
        val remote = remoteSource.tmdb.fetchMovieDetails(tmdbId)
        val movie = mappers.movie.fromTmdb(remote, localId = idTrakt.id)
        localSource.movies.upsert(listOf(mappers.movie.toDatabase(movie)))
        localSource.moviesSyncLog.upsert(MoviesSyncLog(movie.traktId, nowUtcMillis()))
        return movie
      }
      // No TMDB id to fetch with. Fall through to whatever is cached rather than
      // failing the screen outright.
    }
    return local
      ?.let { mappers.movie.fromDatabase(it) }
      ?: error("Movie ${idTrakt.id} is not cached and has no TMDB id to fetch with.")
  }

  /**
   * Fetches details straight from TMDB and stores them under [localId].
   *
   * Restoring a backup needs this. The row does not exist locally yet, and the id in
   * the backup is a Trakt one, which nothing can turn into a TMDB id on its own -
   * [load] would fail and the movie would be skipped. The backup stores the TMDB id
   * next to the Trakt id, so it is passed in directly and the original local id is
   * kept.
   */
  suspend fun loadByTmdbId(
    tmdbId: Long,
    localId: Long,
  ): Movie {
    val remote = remoteSource.tmdb.fetchMovieDetails(tmdbId)
    val movie = mappers.movie.fromTmdb(remote, localId = localId)
    localSource.movies.upsert(listOf(mappers.movie.toDatabase(movie)))
    localSource.moviesSyncLog.upsert(MoviesSyncLog(movie.traktId, nowUtcMillis()))
    return movie
  }

  suspend fun find(idImdb: IdImdb): Movie? {
    val localMovie = localSource.movies.getById(idImdb.id)
    if (localMovie != null) {
      return mappers.movie.fromDatabase(localMovie)
    }
    return null
  }

  suspend fun find(idTmdb: IdTmdb): Movie? {
    val localMovie = localSource.movies.getByTmdbId(idTmdb.id)
    if (localMovie != null) {
      return mappers.movie.fromDatabase(localMovie)
    }
    return null
  }

  suspend fun find(idSlug: IdSlug): Movie? {
    val localMovie = localSource.movies.getBySlug(idSlug.id)
    if (localMovie != null) {
      return mappers.movie.fromDatabase(localMovie)
    }
    return null
  }

  /**
   * Resolves a TMDB id to the local id. A movie already in the library keeps the id
   * its watch history is attached to; anything else gets a freshly minted one.
   *
   * This used to fall back to Trakt's id-search endpoint. That is gone, and returning
   * null here left callers holding IdTrakt() (-1), which then loaded the wrong movie.
   */
  suspend fun resolveTraktId(tmdbId: Long): IdTrakt? {
    if (tmdbId <= 0) return null
    val local = find(IdTmdb(tmdbId))
    if (local != null && local.ids.trakt.id > 0) return local.ids.trakt
    return IdTrakt(LocalIdResolver.newId(tmdbId))
  }

  suspend fun delete(idTrakt: IdTrakt) = localSource.movies.deleteById(idTrakt.id)
}
