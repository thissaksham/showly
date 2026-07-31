package com.michaldrabik.repository.mappers

import com.michaldrabik.common.extensions.nowUtcMillis
import com.michaldrabik.data_remote.tmdb.model.TmdbDiscoveryItem
import com.michaldrabik.data_remote.tmdb.model.TmdbMovieDetails
import com.michaldrabik.data_remote.tmdb.model.TmdbSearchItem
import com.michaldrabik.ui_model.IdImdb
import com.michaldrabik.ui_model.IdSlug
import com.michaldrabik.ui_model.IdTmdb
import com.michaldrabik.ui_model.IdTrakt
import com.michaldrabik.ui_model.IdTvRage
import com.michaldrabik.ui_model.IdTvdb
import com.michaldrabik.ui_model.Ids
import com.michaldrabik.ui_model.Movie
import com.michaldrabik.ui_model.MovieStatus
import java.time.LocalDate
import javax.inject.Inject
import com.michaldrabik.data_local.database.model.Movie as MovieDb
import com.michaldrabik.data_remote.trakt.model.Movie as MovieNetwork

class MovieMapper @Inject constructor(
  private val idsMapper: IdsMapper,
) {

  fun fromNetwork(movie: MovieNetwork) =
    Movie(
      idsMapper.fromNetwork(movie.ids),
      movie.title ?: "",
      movie.year ?: -1,
      movie.overview ?: "",
      movie.released?.let { if (it.isNotBlank()) LocalDate.parse(it) else null },
      movie.runtime ?: -1,
      movie.country ?: "",
      movie.trailer ?: "",
      movie.homepage ?: "",
      movie.language ?: "",
      MovieStatus.fromKey(movie.status),
      movie.rating ?: -1F,
      movie.votes ?: -1,
      movie.comment_count ?: -1,
      movie.genres ?: emptyList(),
      nowUtcMillis(),
      nowUtcMillis(),
    )

  /**
   * Maps full movie details from TMDB.
   *
   * [localId] comes from LocalIdResolver: an existing row keeps the id it already
   * has, so watch history stays attached. Trailer needs an extra TMDB call
   * (`videos`) and is left blank for now.
   */
  fun fromTmdb(
    details: TmdbMovieDetails,
    localId: Long,
  ) = Movie(
    ids = Ids(
      trakt = IdTrakt(localId),
      slug = IdSlug(),
      tvdb = IdTvdb(details.external_ids?.tvdb_id ?: -1),
      imdb = IdImdb(details.imdb_id ?: details.external_ids?.imdb_id ?: ""),
      tmdb = IdTmdb(details.id ?: -1),
      tvrage = IdTvRage(),
    ),
    title = details.title ?: "",
    year = details.release_date?.take(4)?.toIntOrNull() ?: -1,
    overview = details.overview ?: "",
    released = details.release_date.toLocalDateOrNull(),
    runtime = details.runtime ?: -1,
    country = details.origin_country?.firstOrNull()?.lowercase() ?: "",
    trailer = details.videos?.trailerUrl ?: "",
    homepage = details.homepage ?: "",
    language = details.original_language ?: "",
    // TMDB reports "Released"; the app's keys are lowercase.
    status = MovieStatus.fromKey(details.status?.lowercase()),
    rating = details.vote_average ?: -1F,
    votes = details.vote_count ?: -1,
    commentCount = -1,
    genres = details.genres?.mapNotNull { it.name } ?: emptyList(),
    createdAt = nowUtcMillis(),
    updatedAt = nowUtcMillis(),
  )

  /**
   * Maps a `search/multi` hit. Search returns far less than the details endpoint,
   * so most fields stay empty until the movie is opened and fully fetched.
   */
  fun fromTmdbSearch(
    item: TmdbSearchItem,
    localId: Long,
  ) = Movie(
    ids = Ids.EMPTY.copy(
      trakt = IdTrakt(localId),
      tmdb = IdTmdb(item.id ?: -1),
    ),
    title = item.title ?: "",
    year = item.release_date?.take(4)?.toIntOrNull() ?: -1,
    overview = item.overview ?: "",
    released = item.release_date.toLocalDateOrNull(),
    runtime = -1,
    country = item.origin_country?.firstOrNull()?.lowercase() ?: "",
    trailer = "",
    homepage = "",
    language = "",
    status = MovieStatus.UNKNOWN,
    rating = item.vote_average ?: -1F,
    votes = item.vote_count ?: -1,
    commentCount = -1,
    genres = emptyList(),
    createdAt = nowUtcMillis(),
    updatedAt = nowUtcMillis(),
  )

  /**
   * TMDB omits release dates for unscheduled titles and sends "" for others.
   * Anything unparseable becomes null rather than throwing mid-list.
   */
  private fun String?.toLocalDateOrNull() =
    if (isNullOrBlank()) null else runCatching { LocalDate.parse(this) }.getOrNull()

  fun toNetwork(movie: Movie) =
    MovieNetwork(
      idsMapper.toNetwork(movie.ids),
      movie.title,
      movie.year,
      movie.overview,
      movie.released?.toString(),
      movie.runtime,
      movie.country,
      movie.trailer,
      movie.homepage,
      movie.status.key,
      movie.rating,
      movie.votes,
      movie.commentCount,
      movie.genres,
      movie.language,
    )

  fun fromTmdbDiscovery(item: TmdbDiscoveryItem) =
    Movie(
      Ids.EMPTY.copy(tmdb = IdTmdb(item.id)),
      item.title ?: "",
      item.release_date?.take(4)?.toIntOrNull() ?: -1,
      item.overview ?: "",
      item.release_date?.let { if (it.isNotBlank()) LocalDate.parse(it) else null },
      -1,
      "",
      "",
      "",
      "",
      MovieStatus.UNKNOWN,
      item.vote_average ?: -1F,
      item.vote_count ?: -1,
      -1,
      emptyList(),
      // Timestamps stay at 0: discover only returns summary fields, so the row must
      // read as stale and refetch full details when the movie is opened.
      0,
      0,
    )

  fun fromDatabase(movie: MovieDb) =
    Movie(
      idsMapper.fromDatabase(movie),
      movie.title,
      movie.year,
      movie.overview,
      if (movie.released.isBlank()) null else LocalDate.parse(movie.released),
      movie.runtime,
      movie.country,
      movie.trailer,
      movie.homepage,
      movie.language,
      MovieStatus.fromKey(movie.status),
      movie.rating,
      movie.votes,
      movie.commentCount,
      movie.genres.split(","),
      if (movie.updatedAt <= 0L) null else movie.updatedAt,
      movie.createdAt,
    )

  fun toDatabase(movie: Movie) =
    MovieDb(
      movie.ids.trakt.id,
      movie.ids.tmdb.id,
      movie.ids.imdb.id,
      movie.ids.slug.id,
      movie.title,
      movie.year,
      movie.overview,
      movie.released?.toString() ?: "",
      movie.runtime,
      movie.country,
      movie.trailer,
      movie.language,
      movie.homepage,
      movie.status.key,
      movie.rating,
      movie.votes,
      movie.commentCount,
      movie.genres.joinToString(","),
      movie.updatedAt ?: 0L,
      movie.createdAt,
    )
}
