package com.michaldrabik.data_remote.tmdb

import com.michaldrabik.data_remote.tmdb.model.TmdbImage
import com.michaldrabik.data_remote.tmdb.model.TmdbDiscovery
import com.michaldrabik.data_remote.tmdb.model.TmdbImages
import com.michaldrabik.data_remote.tmdb.model.TmdbMovieDetails
import com.michaldrabik.data_remote.tmdb.model.TmdbPerson
import com.michaldrabik.data_remote.tmdb.model.TmdbProductionCompany
import com.michaldrabik.data_remote.tmdb.model.TmdbShowDetails
import com.michaldrabik.data_remote.tmdb.model.TmdbStreamingCountry
import com.michaldrabik.data_remote.tmdb.model.TmdbTranslation

/**
 * Fetch/post remote resources via TMDB API
 */
interface TmdbRemoteDataSource {

  suspend fun fetchShowImages(tmdbId: Long): TmdbImages

  suspend fun fetchEpisodeImage(
    showTmdbId: Long?,
    season: Int?,
    episode: Int?,
  ): TmdbImage?

  suspend fun fetchMovieImages(tmdbId: Long): TmdbImages

  suspend fun fetchMoviePeople(tmdbId: Long): Map<TmdbPerson.Type, List<TmdbPerson>>

  suspend fun fetchShowPeople(tmdbId: Long): Map<TmdbPerson.Type, List<TmdbPerson>>

  suspend fun fetchShowWatchProviders(
    tmdbId: Long,
    countryCode: String,
  ): TmdbStreamingCountry?

  suspend fun fetchMovieWatchProviders(
    tmdbId: Long,
    countryCode: String,
  ): TmdbStreamingCountry?

  suspend fun fetchMovieDetails(tmdbId: Long): TmdbMovieDetails

  suspend fun fetchShowDetails(tmdbId: Long): TmdbShowDetails

  suspend fun fetchCompanyDetails(companyId: Long): TmdbProductionCompany

  suspend fun discoverMoviesByCompany(
    companyId: Long,
    sortBy: String = "popularity.desc",
    releasedAfter: String? = null,
  ): TmdbDiscovery

  suspend fun discoverShowsByCompany(
    companyId: Long,
    sortBy: String = "popularity.desc",
    airedAfter: String? = null,
  ): TmdbDiscovery

  suspend fun fetchPersonDetails(id: Long): TmdbPerson

  suspend fun fetchPersonTranslations(id: Long): Map<String, TmdbTranslation.Data>

  suspend fun fetchPersonImages(tmdbId: Long): TmdbImages
}
