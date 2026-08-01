package com.michaldrabik.repository.mappers

import com.michaldrabik.data_remote.tmdb.model.TmdbEpisode
import com.michaldrabik.ui_model.AirTime
import com.michaldrabik.ui_model.Episode
import com.michaldrabik.ui_model.IdImdb
import com.michaldrabik.ui_model.IdTmdb
import com.michaldrabik.ui_model.IdTrakt
import com.michaldrabik.ui_model.IdTvdb
import com.michaldrabik.ui_model.Ids
import com.michaldrabik.ui_model.Season
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZoneOffset
import java.time.ZonedDateTime
import javax.inject.Inject
import com.michaldrabik.data_local.database.model.Episode as EpisodeDb

class EpisodeMapper @Inject constructor() {

  /**
   * TMDB reports an air date but never a time, so an episode used to land at 00:00
   * UTC - 5:30am in India, for a show that airs in the evening. [airTime] carries
   * the show's slot from TVDB and puts the episode at the right moment.
   *
   * Falls back to the old midnight-UTC behaviour when the show has no known slot,
   * so a missing lookup shifts nothing.
   */
  private fun airedAt(
    airDate: String?,
    airTime: AirTime,
  ): ZonedDateTime? {
    val date = airDate?.takeIf { it.isNotBlank() }?.let { runCatching { LocalDate.parse(it) }.getOrNull() }
      ?: return null
    val time = airTime.time.takeIf { it.isNotBlank() }?.let { runCatching { LocalTime.parse(it) }.getOrNull() }
    val zone = airTime.timezone.takeIf { it.isNotBlank() }?.let { runCatching { ZoneId.of(it) }.getOrNull() }
    if (time == null || zone == null) {
      return date.atStartOfDay(ZoneOffset.UTC)
    }
    return date.atTime(time).atZone(zone)
  }

  /**
   * [localId] is resolved by the caller: an episode already in the database keeps
   * the id its watched flag is attached to.
   */
  fun fromTmdb(
    episode: TmdbEpisode,
    localId: Long,
    airTime: AirTime = AirTime.EMPTY,
  ) = Episode(
    season = episode.season_number ?: -1,
    number = episode.episode_number ?: -1,
    title = episode.name ?: "",
    ids = Ids.EMPTY.copy(
      trakt = IdTrakt(localId),
      tmdb = IdTmdb(episode.id ?: -1),
    ),
    overview = episode.overview ?: "",
    rating = episode.vote_average ?: 0F,
    votes = episode.vote_count ?: 0,
    commentCount = 0,
    firstAired = airedAt(episode.air_date, airTime),
    runtime = episode.runtime ?: -1,
    numberAbs = null,
    lastWatchedAt = null,
  )

  fun toDatabase(
    episode: Episode,
    season: Season,
    showId: IdTrakt,
    isWatched: Boolean,
    lastExportedAt: ZonedDateTime?,
    lastWatchedAt: ZonedDateTime?,
  ): EpisodeDb =
    EpisodeDb(
      idTrakt = episode.ids.trakt.id,
      idSeason = season.ids.trakt.id,
      idShowTrakt = showId.id,
      idShowTvdb = episode.ids.tvdb.id,
      idShowImdb = episode.ids.imdb.id,
      idShowTmdb = episode.ids.tmdb.id,
      seasonNumber = season.number,
      episodeNumber = episode.number,
      episodeNumberAbs = episode.numberAbs,
      episodeOverview = episode.overview,
      title = episode.title,
      firstAired = episode.firstAired,
      commentsCount = episode.commentCount,
      rating = episode.rating,
      runtime = episode.runtime,
      votesCount = episode.votes,
      isWatched = isWatched,
      lastExportedAt = lastExportedAt,
      lastWatchedAt = lastWatchedAt,
    )

  fun fromDatabase(episodeDb: EpisodeDb) =
    Episode(
      ids = Ids.EMPTY.copy(
        trakt = IdTrakt(episodeDb.idTrakt),
        tvdb = IdTvdb(episodeDb.idShowTvdb),
        imdb = IdImdb(episodeDb.idShowImdb),
        tmdb = IdTmdb(episodeDb.idShowTmdb),
      ),
      title = episodeDb.title,
      number = episodeDb.episodeNumber,
      numberAbs = episodeDb.episodeNumberAbs,
      season = episodeDb.seasonNumber,
      overview = episodeDb.episodeOverview,
      commentCount = episodeDb.commentsCount,
      firstAired = episodeDb.firstAired,
      rating = episodeDb.rating,
      runtime = episodeDb.runtime,
      votes = episodeDb.votesCount,
      lastWatchedAt = episodeDb.lastWatchedAt,
    )
}
