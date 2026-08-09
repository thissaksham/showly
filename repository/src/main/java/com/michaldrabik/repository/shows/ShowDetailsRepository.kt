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

        // Stricter resolution: ensure we aren't creating a duplicate of a show we already have
        // (e.g. from the Trakt era) but under a different local ID.
        val existingShow = findByDetails(
          tmdbId = tmdbId,
          imdbId = remoteShow.external_ids?.imdb_id,
          title = remoteShow.name,
          year = remoteShow.first_air_date?.take(4)?.toIntOrNull(),
        )

        val resolvedLocalId = if (existingShow != null && existingShow.traktId != idTrakt.id) {
          Timber.i("Resolved TMDB ID $tmdbId to existing local show ${existingShow.traktId} instead of ${idTrakt.id}")
          // If the current request is for a negative ID but we found a better one, use it.
          existingShow.traktId
        } else {
          idTrakt.id
        }

        val show = mappers.show.fromTmdb(remoteShow, localId = resolvedLocalId, airTime = airTime)
        localSource.shows.upsert(listOf(mappers.show.toDatabase(show)))

        // If we switched IDs, we should also merge history from the requested ID to the resolved ID.
        if (resolvedLocalId != idTrakt.id && idTrakt.id != -1L) {
          mergeShows(idTrakt.id, resolvedLocalId)
        }

        return show
      }
      // No TMDB id to fetch with. Fall through to whatever is cached rather than
      // failing the screen outright.
    }
    return localShow
      ?.let { mappers.show.fromDatabase(it) }
      ?: error("Show ${idTrakt.id} is not cached and has no TMDB id to fetch with.")
  }

  private suspend fun mergeShows(duplicateId: Long, mainId: Long) {
    transactions.withTransaction {
      localSource.shows.moveSeasons(duplicateId, mainId)
      localSource.shows.moveEpisodes(duplicateId, mainId)
      localSource.shows.moveMyShow(duplicateId, mainId)
      localSource.shows.moveWatchlistShow(duplicateId, mainId)
      localSource.shows.moveArchiveShow(duplicateId, mainId)
      localSource.shows.deleteById(duplicateId)
    }
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

  suspend fun findByDetails(
    tmdbId: Long,
    imdbId: String?,
    title: String?,
    year: Int?,
  ): Show? {
    // 1. Try TMDB ID
    find(IdTmdb(tmdbId))?.let { return it }

    // 2. Try IMDB ID
    imdbId?.takeIf { it.isNotBlank() }?.let { id ->
      localSource.shows.getById(id)?.let { return mappers.show.fromDatabase(it) }
    }

    // 3. Try Title and Year (if exact match)
    if (!title.isNullOrBlank() && year != null && year > 0) {
      localSource.shows.getAll()
        .find { it.title.equals(title, ignoreCase = true) && it.year == year }
        ?.let { return mappers.show.fromDatabase(it) }
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
    if (local != null) return local.ids.trakt
    return IdTrakt(LocalIdResolver.newId(tmdbId))
  }

  /**
   * Finds and merges shows with the same TMDB ID.
   *
   * This is a one-time cleanup to fix the progress loss caused by duplicates
   * fighting over the same Season/Episode records.
   */
  suspend fun reconcileDuplicates() {
    val duplicateTmdbIds = localSource.shows.getDuplicatesByTmdbId()
    if (duplicateTmdbIds.isEmpty()) return

    Timber.i("Found ${duplicateTmdbIds.size} shows with duplicate TMDB IDs. Starting reconciliation...")

    duplicateTmdbIds.forEach { tmdbId ->
      val duplicates = localSource.shows.getByTmdbIds(listOf(tmdbId))
      if (duplicates.size < 2) return@forEach

      // Prefer the positive ID (Trakt) or the one that is in My Shows.
      val mainShow = duplicates.find { it.idTrakt > 0 }
        ?: duplicates.find { localSource.myShows.checkExists(it.idTrakt) }
        ?: duplicates.sortedBy { it.idTrakt }.last() // Arbitrary but stable

      val others = duplicates.filter { it.idTrakt != mainShow.idTrakt }

      others.forEach { duplicate ->
        Timber.i("Merging show ${duplicate.idTrakt} into ${mainShow.idTrakt} (TMDB ID $tmdbId)")
        transactions.withTransaction {
          localSource.shows.moveSeasons(duplicate.idTrakt, mainShow.idTrakt)
          localSource.shows.moveEpisodes(duplicate.idTrakt, mainShow.idTrakt)
          localSource.shows.moveMyShow(duplicate.idTrakt, mainShow.idTrakt)
          localSource.shows.moveWatchlistShow(duplicate.idTrakt, mainShow.idTrakt)
          localSource.shows.moveArchiveShow(duplicate.idTrakt, mainShow.idTrakt)
          localSource.shows.deleteById(duplicate.idTrakt)
        }
      }
    }
    Timber.i("Reconciliation complete.")
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
