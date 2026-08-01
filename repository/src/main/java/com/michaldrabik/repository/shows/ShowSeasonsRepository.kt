package com.michaldrabik.repository.shows

import com.michaldrabik.data_local.LocalDataSource
import com.michaldrabik.data_remote.RemoteDataSource
import com.michaldrabik.repository.mappers.Mappers
import com.michaldrabik.repository.utilities.LocalIdResolver
import com.michaldrabik.ui_model.AirTime
import com.michaldrabik.ui_model.Episode
import com.michaldrabik.ui_model.Season
import javax.inject.Inject
import javax.inject.Singleton
import com.michaldrabik.data_local.database.model.Show as ShowDb

/**
 * Seasons and episodes from TMDB, keyed so that watch history survives the move
 * off Trakt.
 *
 * Watched flags hang off `id_trakt` on the season and episode rows. TMDB issues
 * its own ids, so re-keying blindly would orphan every episode the user has
 * already watched. Instead an existing row is matched by its season/episode
 * number and keeps the id it already has; only genuinely new episodes get a
 * freshly minted one.
 */
@Singleton
class ShowSeasonsRepository @Inject constructor(
  private val remoteSource: RemoteDataSource,
  private val localSource: LocalDataSource,
  private val mappers: Mappers,
) {

  /**
   * The next episode to air, or null when the show has none scheduled. TMDB reports
   * it on the show endpoint, so this costs one request rather than a season sweep.
   */
  suspend fun loadNextEpisode(showLocalId: Long): Episode? {
    val show = localSource.shows.getById(showLocalId)
    val tmdbId = resolveTmdbId(showLocalId, show) ?: return null
    val next = remoteSource.tmdb.fetchShowDetails(tmdbId).next_episode_to_air ?: return null
    val tmdbEpisodeId = next.id ?: return null

    val existing = localSource.episodes
      .getAllByShowId(showLocalId)
      .find { it.seasonNumber == next.season_number && it.episodeNumber == next.episode_number }

    return mappers.episode.fromTmdb(
      next,
      existing?.idTrakt ?: LocalIdResolver.newId(tmdbEpisodeId),
      airTimeOf(show),
    )
  }

  private fun resolveTmdbId(
    showLocalId: Long,
    show: ShowDb?,
  ): Long? = show?.idTmdb?.takeIf { it > 0 } ?: LocalIdResolver.tmdbIdOf(showLocalId)

  /**
   * The show's air slot, stored by ShowDetailsRepository when it fetched the details.
   * Blank until that has happened, in which case episodes keep their date-only time.
   */
  private fun airTimeOf(show: ShowDb?): AirTime =
    show?.let { AirTime(it.airtimeDay, it.airtimeTime, it.airtimeTimezone) } ?: AirTime.EMPTY

  suspend fun loadRemote(showLocalId: Long): List<Season> {
    val show = localSource.shows.getById(showLocalId)
    val tmdbId = resolveTmdbId(showLocalId, show) ?: return emptyList()
    val airTime = airTimeOf(show)

    val localSeasons = localSource.seasons
      .getAllByShowId(showLocalId)
      .associateBy { it.seasonNumber }
    val localEpisodes = localSource.episodes
      .getAllByShowId(showLocalId)
      .associateBy { it.seasonNumber to it.episodeNumber }

    return remoteSource.tmdb
      .fetchSeasons(tmdbId)
      .mapNotNull { season ->
        val seasonTmdbId = season.id ?: return@mapNotNull null
        val seasonNumber = season.season_number ?: return@mapNotNull null

        val episodes = season.episodes
          .orEmpty()
          .mapNotNull { episode ->
            val episodeTmdbId = episode.id ?: return@mapNotNull null
            val existing = localEpisodes[seasonNumber to (episode.episode_number ?: -1)]
            val localId = existing?.idTrakt ?: LocalIdResolver.newId(episodeTmdbId)
            mappers.episode.fromTmdb(episode, localId, airTime)
          }

        val seasonLocalId = localSeasons[seasonNumber]?.idTrakt ?: LocalIdResolver.newId(seasonTmdbId)
        mappers.season.fromTmdb(season, seasonLocalId, episodes)
      }
  }
}
