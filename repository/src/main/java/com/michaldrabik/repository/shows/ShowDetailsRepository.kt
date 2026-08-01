package com.michaldrabik.repository.shows

import com.michaldrabik.common.Config
import com.michaldrabik.common.extensions.nowUtcMillis
import com.michaldrabik.data_local.LocalDataSource
import com.michaldrabik.data_local.utilities.TransactionsProvider
import com.michaldrabik.data_remote.RemoteDataSource
import com.michaldrabik.repository.mappers.Mappers
import com.michaldrabik.repository.utilities.LocalIdResolver
import com.michaldrabik.ui_model.AirTime
import com.michaldrabik.ui_model.IdImdb
import com.michaldrabik.ui_model.IdSlug
import com.michaldrabik.ui_model.IdTmdb
import com.michaldrabik.ui_model.IdTrakt
import com.michaldrabik.ui_model.Show
import timber.log.Timber
import javax.inject.Inject

class ShowDetailsRepository @Inject constructor(
  private val remoteSource: RemoteDataSource,
  private val localSource: LocalDataSource,
  private val transactions: TransactionsProvider,
  private val mappers: Mappers,
) {

  suspend fun load(
    idTrakt: IdTrakt,
    force: Boolean = false,
  ): Show {
    val localShow = localSource.shows.getById(idTrakt.id)
    if (force || localShow == null || nowUtcMillis() - localShow.updatedAt > Config.SHOW_DETAILS_CACHE_DURATION) {
      // Details come from TMDB now. The stored row carries the TMDB id; a row minted
      // after the migration encodes it in the (negative) local id instead.
      val tmdbId = localShow
        ?.idTmdb
        ?.takeIf { it > 0 }
        ?: LocalIdResolver.tmdbIdOf(idTrakt.id)

      if (tmdbId != null) {
        val remoteShow = remoteSource.tmdb.fetchShowDetails(tmdbId)
        val airTime = fetchAirTime(remoteShow.external_ids?.tvdb_id)
        val show = mappers.show.fromTmdb(remoteShow, localId = idTrakt.id, airTime = airTime)
        localSource.shows.upsert(listOf(mappers.show.toDatabase(show)))
        return show
      }
      // No TMDB id to fetch with. Fall through to whatever is cached rather than
      // failing the screen outright.
    }
    return localShow
      ?.let { mappers.show.fromDatabase(it) }
      ?: error("Show ${idTrakt.id} is not cached and has no TMDB id to fetch with.")
  }

  /**
   * Fetches details straight from TMDB and stores them under [localId].
   *
   * Restoring a backup needs this. The row does not exist locally yet, and the id in
   * the backup is a Trakt one, which nothing can turn into a TMDB id on its own -
   * [load] would fail and the show would be skipped. The backup stores the TMDB id
   * next to the Trakt id, so it is passed in directly and the original local id is
   * kept, which is what the restored watch history hangs off.
   */
  suspend fun loadByTmdbId(
    tmdbId: Long,
    localId: Long,
  ): Show {
    val remoteShow = remoteSource.tmdb.fetchShowDetails(tmdbId)
    val airTime = fetchAirTime(remoteShow.external_ids?.tvdb_id)
    val show = mappers.show.fromTmdb(remoteShow, localId = localId, airTime = airTime)
    localSource.shows.upsert(listOf(mappers.show.toDatabase(show)))
    return show
  }

  /**
   * TVDB is the only source with a time of day. It is a second network call on top of
   * the details fetch, and nothing else depends on it, so a failure here leaves the
   * show with an unknown slot instead of failing the screen.
   *
   * The id comes from TMDB's `external_ids`. TVDB's own remote-id search is not used:
   * it matches any entity whose id collides numerically and returns unrelated shows.
   */
  private suspend fun fetchAirTime(tvdbId: Long?): AirTime {
    if (tvdbId == null || tvdbId <= 0) return AirTime.EMPTY
    return try {
      mappers.show.airTimeFromTvdb(remoteSource.tvdb.fetchSeries(tvdbId))
    } catch (error: Throwable) {
      Timber.w(error, "TVDB air time lookup failed for $tvdbId")
      AirTime.EMPTY
    }
  }

  suspend fun find(idImdb: IdImdb): Show? {
    val localShow = localSource.shows.getById(idImdb.id)
    if (localShow != null) {
      return mappers.show.fromDatabase(localShow)
    }
    return null
  }

  suspend fun find(idTmdb: IdTmdb): Show? {
    val localShow = localSource.shows.getByTmdbId(idTmdb.id)
    if (localShow != null) {
      return mappers.show.fromDatabase(localShow)
    }
    return null
  }

  suspend fun find(idSlug: IdSlug): Show? {
    val localShow = localSource.shows.getBySlug(idSlug.id)
    if (localShow != null) {
      return mappers.show.fromDatabase(localShow)
    }
    return null
  }

  /**
   * Resolves a TMDB id to the local id. An item already in the library keeps the id
   * its watch history is attached to; anything else gets a freshly minted one.
   *
   * This used to fall back to Trakt's id-search endpoint. That is gone, and returning
   * null here left callers holding IdTrakt() (-1), which then loaded the wrong show.
   */
  suspend fun resolveTraktId(tmdbId: Long): IdTrakt? {
    if (tmdbId <= 0) return null
    val local = find(IdTmdb(tmdbId))
    if (local != null && local.ids.trakt.id > 0) return local.ids.trakt
    return IdTrakt(LocalIdResolver.newId(tmdbId))
  }

  suspend fun delete(idTrakt: IdTrakt) {
    with(localSource) {
      transactions.withTransaction {
        shows.deleteById(idTrakt.id)
        seasons.deleteAllForShow(idTrakt.id)
        episodes.deleteAllForShow(idTrakt.id)
      }
    }
  }
}
