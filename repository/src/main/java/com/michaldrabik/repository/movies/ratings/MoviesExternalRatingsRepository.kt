package com.michaldrabik.repository.movies.ratings

import com.michaldrabik.common.ConfigVariant
import com.michaldrabik.common.extensions.nowUtcMillis
import com.michaldrabik.data_local.LocalDataSource
import com.michaldrabik.data_remote.RemoteDataSource
import com.michaldrabik.repository.mappers.Mappers
import com.michaldrabik.ui_model.Movie
import com.michaldrabik.ui_model.Ratings
import java.util.Locale
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class MoviesExternalRatingsRepository @Inject constructor(
  private val remoteSource: RemoteDataSource,
  private val localSource: LocalDataSource,
  private val mappers: Mappers,
) {

  suspend fun loadRatings(movie: Movie): Ratings {
    val localRatings = localSource.movieRatings.getById(movie.traktId)
    localRatings?.let {
      if (nowUtcMillis() - it.updatedAt < ConfigVariant.RATINGS_CACHE_DURATION) {
        return mappers.ratings.fromDatabase(it).copy(
          moctaleUrl = movie.generateMoctaleId(),
          parentalGuideUrl = movie.generateParentalGuideUrl()
        )
      }
    }

    val remoteRatings = remoteSource.omdb
      .fetchOmdbData(movie.ids.imdb.id)
      .let { mappers.ratings.fromNetwork(it) }
      .copy(
        trakt = Ratings.Value(String.format(Locale.ENGLISH, "%.1f", movie.rating), false),
        moctaleUrl = movie.generateMoctaleId(),
        parentalGuideUrl = movie.generateParentalGuideUrl()
      )

    val dbRatings = mappers.ratings.toMovieDatabase(movie.ids.trakt, remoteRatings)
    localSource.movieRatings.upsert(dbRatings)

    return remoteRatings
  }

  private fun Movie.generateMoctaleId(): String {
    val slug = title.lowercase()
      .replace("'", "") // Remove apostrophes (e.g. Widow's -> widows)
      .replace(Regex("[^a-z0-9]"), "-")
      .replace(Regex("-+"), "-")
      .trim('-')
    return "https://www.moctale.in/content/$slug-$year"
  }

  private fun Movie.generateParentalGuideUrl(): String? {
    val imdbId = ids.imdb.id
    return if (imdbId.isNotBlank()) "https://www.imdb.com/title/$imdbId/parentalguide/" else null
  }
}
