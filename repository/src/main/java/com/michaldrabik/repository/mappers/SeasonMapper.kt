package com.michaldrabik.repository.mappers

import com.michaldrabik.data_local.database.model.Episode
import com.michaldrabik.data_remote.tmdb.model.TmdbSeasonDetails
import com.michaldrabik.ui_model.IdTmdb
import com.michaldrabik.ui_model.IdTrakt
import com.michaldrabik.ui_model.Ids
import com.michaldrabik.ui_model.Season
import java.time.ZonedDateTime
import javax.inject.Inject
import com.michaldrabik.data_local.database.model.Season as SeasonDb
import com.michaldrabik.data_remote.trakt.model.Season as SeasonNetwork

class SeasonMapper @Inject constructor(
  private val idsMapper: IdsMapper,
  private val episodeMapper: EpisodeMapper,
) {

  fun fromNetwork(season: SeasonNetwork) =
    Season(
      idsMapper.fromNetwork(season.ids),
      season.number ?: -1,
      season.episode_count ?: -1,
      season.aired_episodes ?: -1,
      season.title ?: "",
      if (season.first_aired.isNullOrBlank()) null else ZonedDateTime.parse(season.first_aired),
      season.overview ?: "",
      season.rating ?: -1F,
      season.episodes?.map { episodeMapper.fromNetwork(it) } ?: emptyList(),
    )

  /**
   * [localId] and [episodes] are resolved by the caller so that a season already in
   * the database keeps the id its watched episodes hang off.
   */
  fun fromTmdb(
    season: TmdbSeasonDetails,
    localId: Long,
    episodes: List<com.michaldrabik.ui_model.Episode>,
  ): Season {
    val now = ZonedDateTime.now()
    return Season(
      Ids.EMPTY.copy(
        trakt = IdTrakt(localId),
        tmdb = IdTmdb(season.id ?: -1),
      ),
      season.season_number ?: -1,
      episodes.size,
      episodes.count { it.firstAired?.isBefore(now) == true },
      season.name ?: "",
      season.air_date?.takeIf { it.isNotBlank() }?.let { ZonedDateTime.parse("${it}T00:00:00Z") },
      season.overview ?: "",
      season.vote_average ?: -1F,
      episodes,
    )
  }

  fun toNetwork(season: Season) =
    SeasonNetwork(
      ids = idsMapper.toNetwork(season.ids),
      number = season.number,
      episode_count = season.episodeCount,
      aired_episodes = season.airedEpisodes,
      title = season.title,
      first_aired = season.firstAired.toString(),
      overview = season.overview,
      rating = season.rating,
      episodes = season.episodes.map { episodeMapper.toNetwork(it) },
    )

  fun fromDatabase(
    seasonDb: SeasonDb,
    episodes: List<Episode> = emptyList(),
  ) = Season(
    Ids.EMPTY.copy(trakt = IdTrakt(seasonDb.idTrakt)),
    seasonDb.seasonNumber,
    seasonDb.episodesCount,
    seasonDb.episodesAiredCount,
    seasonDb.seasonTitle,
    seasonDb.seasonFirstAired,
    seasonDb.seasonOverview,
    seasonDb.rating ?: -1F,
    episodes.map { episodeMapper.fromDatabase(it) },
  )

  fun toDatabase(
    season: Season,
    showId: IdTrakt,
    isWatched: Boolean,
  ): SeasonDb =
    SeasonDb(
      season.ids.trakt.id,
      showId.id,
      season.number,
      season.title,
      season.overview,
      season.firstAired,
      season.episodeCount,
      season.airedEpisodes,
      season.rating,
      isWatched,
    )
}
