package com.michaldrabik.repository.mappers

import com.michaldrabik.common.extensions.toZonedDateTime
import com.michaldrabik.data_remote.tmdb.model.TmdbEpisode
import com.michaldrabik.ui_model.Episode
import com.michaldrabik.ui_model.IdImdb
import com.michaldrabik.ui_model.IdTmdb
import com.michaldrabik.ui_model.IdTrakt
import com.michaldrabik.ui_model.IdTvdb
import com.michaldrabik.ui_model.Ids
import com.michaldrabik.ui_model.Season
import java.time.ZonedDateTime
import javax.inject.Inject
import com.michaldrabik.data_local.database.model.Episode as EpisodeDb

class EpisodeMapper @Inject constructor() {

  /**
   * [localId] is resolved by the caller: an episode already in the database keeps
   * the id its watched flag is attached to.
   *
   * TMDB reports an air date but no air time, so everything lands at 00:00 UTC.
   */
  fun fromTmdb(
    episode: TmdbEpisode,
    localId: Long,
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
    firstAired = episode.air_date?.takeIf { it.isNotBlank() }?.let { "${it}T00:00:00Z" }.toZonedDateTime(),
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
