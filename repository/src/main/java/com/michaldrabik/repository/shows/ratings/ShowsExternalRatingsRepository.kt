package com.michaldrabik.repository.shows.ratings

import com.michaldrabik.common.ConfigVariant
import com.michaldrabik.common.extensions.nowUtcMillis
import com.michaldrabik.data_local.LocalDataSource
import com.michaldrabik.data_remote.RemoteDataSource
import com.michaldrabik.repository.mappers.Mappers
import com.michaldrabik.ui_model.Ratings
import com.michaldrabik.ui_model.Show
import java.util.Locale
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ShowsExternalRatingsRepository @Inject constructor(
  private val remoteSource: RemoteDataSource,
  private val localSource: LocalDataSource,
  private val mappers: Mappers,
) {

  suspend fun loadRatings(show: Show): Ratings {
    val localRatings = localSource.showRatings.getById(show.traktId)
    localRatings?.let {
      if (nowUtcMillis() - it.updatedAt < ConfigVariant.RATINGS_CACHE_DURATION) {
        return mappers.ratings.fromDatabase(it).copy(
          moctaleUrl = show.generateMoctaleId(),
          parentalGuideUrl = show.generateParentalGuideUrl()
        )
      }
    }

    val remoteRatings = remoteSource.omdb
      .fetchOmdbData(show.ids.imdb.id)
      .let { mappers.ratings.fromNetwork(it) }
      .copy(
        trakt = Ratings.Value(String.format(Locale.ENGLISH, "%.1f", show.rating), false),
        moctaleUrl = show.generateMoctaleId(),
        parentalGuideUrl = show.generateParentalGuideUrl()
      )

    val dbRatings = mappers.ratings.toShowDatabase(show.ids.trakt, remoteRatings)
    localSource.showRatings.upsert(dbRatings)

    return remoteRatings
  }

  private fun Show.generateMoctaleId(): String {
    val slug = title.lowercase()
      .replace("'", "") // Remove apostrophes (e.g. Widow's -> widows)
      .replace(Regex("[^a-z0-9]"), "-")
      .replace(Regex("-+"), "-")
      .trim('-')
    return "https://www.moctale.in/content/$slug-$year"
  }

  private fun Show.generateParentalGuideUrl(): String? {
    val imdbId = ids.imdb.id
    return if (imdbId.isNotBlank()) "https://www.imdb.com/title/$imdbId/parentalguide/" else null
  }
}
