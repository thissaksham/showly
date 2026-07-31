package com.michaldrabik.repository.utilities

import com.michaldrabik.data_local.LocalDataSource
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Maps a TMDB id onto the local primary key (`id_trakt`) used across the database.
 *
 * The column keeps its name for historical reasons - it is now just an opaque local
 * id. Rows created while Trakt was the metadata source keep the positive id they
 * already have, so no stored data has to be rewritten. Items first seen from TMDB
 * get a negative id, which cannot collide with those.
 */
@Singleton
class LocalIdResolver @Inject constructor(
  private val localSource: LocalDataSource,
) {

  suspend fun showId(tmdbId: Long): Long =
    localSource.shows.getByTmdbId(tmdbId)?.idTrakt ?: newId(tmdbId)

  suspend fun movieId(tmdbId: Long): Long =
    localSource.movies.getByTmdbId(tmdbId)?.idTrakt ?: newId(tmdbId)

  companion object {

    /**
     * Mints a local id for an item with no existing row. Negative by construction,
     * so it can never collide with a stored Trakt id.
     *
     * Rejects non-positive input on purpose: `id_tmdb` defaults to -1 in the schema,
     * and negating that would produce 1 - a valid Trakt id pointing at someone else's
     * show. Callers must drop items without a real TMDB id rather than persist them.
     */
    fun newId(tmdbId: Long): Long {
      require(tmdbId > 0) { "TMDB id must be positive, was $tmdbId" }
      // Offset by one so no minted id can ever be -1, which the app uses
      // everywhere as the "no id" sentinel (IdTrakt() defaults to it).
      return -(tmdbId + 1)
    }

    /**
     * Recovers the TMDB id from a local id, or null when there is nothing to
     * recover: pre-migration rows carry their TMDB id in the `id_tmdb` column,
     * and -1 means "unknown" rather than a real item.
     */
    fun tmdbIdOf(localId: Long): Long? = if (localId < UNKNOWN_ID) -localId - 1 else null

    private const val UNKNOWN_ID = -1L
  }
}
