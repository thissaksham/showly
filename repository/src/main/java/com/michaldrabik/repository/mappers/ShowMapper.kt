package com.michaldrabik.repository.mappers

import com.michaldrabik.common.extensions.nowUtcMillis
import com.michaldrabik.data_remote.tmdb.model.TmdbDiscoveryItem
import com.michaldrabik.ui_model.AirTime
import com.michaldrabik.ui_model.IdTmdb
import com.michaldrabik.ui_model.Ids
import com.michaldrabik.ui_model.Show
import com.michaldrabik.ui_model.ShowStatus
import javax.inject.Inject
import com.michaldrabik.data_local.database.model.Show as ShowDb
import com.michaldrabik.data_remote.trakt.model.AirTime as AirTimeNetwork
import com.michaldrabik.data_remote.trakt.model.Show as ShowNetwork

class ShowMapper @Inject constructor(
  private val idsMapper: IdsMapper,
) {

  fun fromNetwork(show: ShowNetwork) =
    Show(
      idsMapper.fromNetwork(show.ids),
      show.title ?: "",
      show.year ?: -1,
      show.overview ?: "",
      show.first_aired ?: "",
      show.runtime ?: -1,
      AirTime(
        show.airs?.day ?: "",
        show.airs?.time ?: "",
        show.airs?.timezone ?: "",
      ),
      show.certification ?: "",
      show.network ?: "",
      show.country ?: "",
      show.trailer ?: "",
      show.homepage ?: "",
      ShowStatus.fromKey(show.status),
      show.rating ?: -1F,
      show.votes ?: -1,
      show.comment_count ?: -1,
      show.genres ?: emptyList(),
      show.aired_episodes ?: -1,
      nowUtcMillis(),
      nowUtcMillis(),
    )

  fun toNetwork(show: Show) =
    ShowNetwork(
      idsMapper.toNetwork(show.ids),
      show.title,
      show.year,
      show.overview,
      show.firstAired,
      show.runtime,
      AirTimeNetwork(
        show.airTime.day,
        show.airTime.time,
        show.airTime.timezone,
      ),
      show.certification,
      show.network,
      show.country,
      show.trailer,
      show.homepage,
      show.status.key,
      show.rating,
      show.votes,
      show.commentCount,
      show.genres,
      show.airedEpisodes,
    )

  fun fromTmdbDiscovery(item: TmdbDiscoveryItem) =
    Show(
      ids = Ids.EMPTY.copy(tmdb = IdTmdb(item.id)),
      title = item.name ?: "",
      year = item.first_air_date?.take(4)?.toIntOrNull() ?: -1,
      overview = item.overview ?: "",
      firstAired = if (item.first_air_date.isNullOrBlank()) "" else "${item.first_air_date}T00:00:00Z",
      runtime = -1,
      airTime = AirTime(day = "", time = "", timezone = ""),
      certification = "",
      network = "",
      country = "",
      trailer = "",
      homepage = "",
      status = ShowStatus.UNKNOWN,
      rating = item.vote_average ?: 0.0f,
      votes = item.vote_count ?: 0,
      commentCount = 0,
      genres = listOf(),
      airedEpisodes = 0,
      createdAt = 0,
      updatedAt = 0,
    )

  fun fromDatabase(show: ShowDb) =
    Show(
      idsMapper.fromDatabase(show),
      show.title,
      show.year,
      show.overview,
      show.firstAired,
      show.runtime,
      AirTime(show.airtimeDay, show.airtimeTime, show.airtimeTimezone),
      show.certification,
      show.network,
      show.country,
      show.trailer,
      show.homepage,
      ShowStatus.fromKey(show.status),
      show.rating,
      show.votes,
      show.commentCount,
      show.genres.split(","),
      show.airedEpisodes,
      show.createdAt,
      show.updatedAt,
    )

  fun toDatabase(show: Show) =
    ShowDb(
      show.traktId,
      show.ids.tvdb.id,
      show.ids.tmdb.id,
      show.ids.imdb.id,
      show.ids.slug.id,
      show.ids.tvrage.id,
      show.title,
      show.year,
      show.overview,
      show.firstAired,
      show.runtime,
      show.airTime.day,
      show.airTime.time,
      show.airTime.timezone,
      show.certification,
      show.network,
      show.country,
      show.trailer,
      show.homepage,
      show.status.key,
      show.rating,
      show.votes,
      show.commentCount,
      show.genres.joinToString(","),
      show.airedEpisodes,
      show.createdAt,
      nowUtcMillis(),
    )
}
