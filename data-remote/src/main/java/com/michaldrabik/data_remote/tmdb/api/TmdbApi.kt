package com.michaldrabik.data_remote.tmdb.api

import com.michaldrabik.data_remote.tmdb.TmdbRemoteDataSource
import com.michaldrabik.data_remote.tmdb.model.TmdbDiscovery
import com.michaldrabik.data_remote.tmdb.model.TmdbDiscoveryItem
import com.michaldrabik.data_remote.tmdb.model.TmdbImages
import com.michaldrabik.data_remote.tmdb.model.TmdbMovieDetails
import com.michaldrabik.data_remote.tmdb.model.TmdbPerson
import com.michaldrabik.data_remote.tmdb.model.TmdbProductionCompany
import com.michaldrabik.data_remote.tmdb.model.TmdbSearchItem
import com.michaldrabik.data_remote.tmdb.model.TmdbSeasonDetails
import com.michaldrabik.data_remote.tmdb.model.TmdbShowDetails
import com.michaldrabik.data_remote.tmdb.model.TmdbStreamingCountry
import com.michaldrabik.data_remote.tmdb.model.TmdbTranslation
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope

internal class TmdbApi(
  private val service: TmdbService,
) : TmdbRemoteDataSource {

  private companion object {
    // Pulls imdb/tvdb ids and the trailer into the details response, so rows keep
    // the cross-references and the trailer link they used to get from Trakt.
    const val DETAILS_APPEND = "external_ids,videos"
    const val IMDB_SOURCE = "imdb_id"
  }

  override suspend fun fetchShowImages(tmdbId: Long) =
    try {
      if (tmdbId <= 0) TmdbImages.EMPTY
      service.fetchShowImages(tmdbId)
    } catch (error: Throwable) {
      TmdbImages.EMPTY
    }

  override suspend fun fetchEpisodeImage(
    showTmdbId: Long?,
    season: Int?,
    episode: Int?,
  ) = try {
    if (showTmdbId == null || showTmdbId <= 0) TmdbImages.EMPTY
    if (season == null || season <= 0) TmdbImages.EMPTY
    if (episode == null || episode <= 0) TmdbImages.EMPTY
    val images = service.fetchEpisodeImages(showTmdbId, season, episode)
    images.stills?.firstOrNull()
  } catch (error: Throwable) {
    null
  }

  override suspend fun fetchMovieImages(tmdbId: Long) =
    try {
      if (tmdbId <= 0) TmdbImages.EMPTY
      service.fetchMovieImages(tmdbId)
    } catch (error: Throwable) {
      TmdbImages.EMPTY
    }

  override suspend fun fetchMoviePeople(tmdbId: Long): Map<TmdbPerson.Type, List<TmdbPerson>> {
    val result = service.fetchMoviePeople(tmdbId)
    val cast = result.cast?.toList() ?: emptyList()
    val crew = result.crew?.toList() ?: emptyList()
    return mapOf(
      TmdbPerson.Type.CAST to cast,
      TmdbPerson.Type.CREW to crew,
    )
  }

  override suspend fun fetchShowPeople(tmdbId: Long): Map<TmdbPerson.Type, List<TmdbPerson>> {
    val result = service.fetchShowPeople(tmdbId)
    val cast = result.cast?.toList() ?: emptyList()
    val crew = result.crew?.toList() ?: emptyList()
    return mapOf(
      TmdbPerson.Type.CAST to cast,
      TmdbPerson.Type.CREW to crew,
    )
  }

  override suspend fun fetchShowWatchProviders(
    tmdbId: Long,
    countryCode: String,
  ): TmdbStreamingCountry? {
    val result = service.fetchShowWatchProviders(tmdbId)
    val code = when (countryCode.uppercase()) {
      "UK" -> "GB"
      else -> countryCode.uppercase()
    }
    return result.results[code]
  }

  override suspend fun fetchMovieWatchProviders(
    tmdbId: Long,
    countryCode: String,
  ): TmdbStreamingCountry? {
    val result = service.fetchMovieWatchProviders(tmdbId)
    val code = when (countryCode.uppercase()) {
      "UK" -> "GB"
      else -> countryCode.uppercase()
    }
    return result.results[code]
  }

  override suspend fun fetchMovieDetails(tmdbId: Long) =
    service.fetchMovieDetails(tmdbId, append = DETAILS_APPEND)

  override suspend fun fetchShowDetails(tmdbId: Long) =
    service.fetchShowDetails(tmdbId, append = DETAILS_APPEND)

  override suspend fun fetchSeasons(tmdbId: Long): List<TmdbSeasonDetails> =
    coroutineScope {
      service
        .fetchShowDetails(tmdbId, append = DETAILS_APPEND)
        .seasons
        .orEmpty()
        .mapNotNull { it.season_number }
        .map { number -> async { runCatching { service.fetchSeason(tmdbId, number) }.getOrNull() } }
        .awaitAll()
        .filterNotNull()
        .sortedByDescending { it.season_number ?: -1 }
    }

  override suspend fun search(query: String): List<TmdbSearchItem> =
    service
      .search(query, includeAdult = false)
      .results
      .orEmpty()
      // People come back in the same list; an item without an id cannot be
      // resolved to a local row, so it is dropped rather than persisted.
      .filter { (it.isShow || it.isMovie) && (it.id ?: 0) > 0 }

  override suspend fun fetchPersonCredits(
    tmdbId: Long,
    type: TmdbPerson.Type,
  ): List<TmdbSearchItem> {
    val credits = service.fetchPersonCredits(tmdbId)
    val items = when (type) {
      TmdbPerson.Type.CAST -> credits.cast
      else -> credits.crew
    }
    return items
      .orEmpty()
      .filter { (it.isShow || it.isMovie) && (it.id ?: 0) > 0 }
  }

  /**
   * TMDB caps a response at 20 items, so a feed is assembled from several pages
   * fetched in parallel. A page that fails is skipped rather than failing the feed.
   */
  private suspend fun paged(
    pages: Int,
    fetch: suspend (Int) -> TmdbDiscovery,
  ): List<TmdbDiscoveryItem> =
    coroutineScope {
      (1..pages)
        .map { page -> async { runCatching { fetch(page).results.orEmpty() }.getOrDefault(emptyList()) } }
        .awaitAll()
        .flatten()
        .distinctBy { it.id }
    }

  override suspend fun discoverShows(
    sortBy: String,
    genres: String?,
    networks: String?,
    airedAfter: String?,
    pages: Int,
  ) = paged(pages) { page -> service.discoverShows(sortBy, genres, networks, airedAfter, page) }

  override suspend fun discoverMovies(
    sortBy: String,
    genres: String?,
    releasedAfter: String?,
    pages: Int,
  ) = paged(pages) { page -> service.discoverMovies(sortBy, genres, releasedAfter, page) }

  override suspend fun trendingShows(pages: Int) = paged(pages) { page -> service.trendingShows(page) }

  override suspend fun trendingMovies(pages: Int) = paged(pages) { page -> service.trendingMovies(page) }

  override suspend fun fetchShowTranslation(
    tmdbId: Long,
    language: String,
  ) = service.fetchShowTranslation(tmdbId, language)

  override suspend fun fetchMovieTranslation(
    tmdbId: Long,
    language: String,
  ) = service.fetchMovieTranslation(tmdbId, language)

  override suspend fun fetchSeasonTranslation(
    tmdbId: Long,
    seasonNumber: Int,
    language: String,
  ) = service.fetchSeasonTranslation(tmdbId, seasonNumber, language)

  override suspend fun findByImdbId(imdbId: String) = service.findByExternalId(imdbId, source = IMDB_SOURCE)

  override suspend fun relatedShows(tmdbId: Long) = service.relatedShows(tmdbId).results.orEmpty()

  override suspend fun relatedMovies(tmdbId: Long) = service.relatedMovies(tmdbId).results.orEmpty()

  override suspend fun fetchMovieCollection(tmdbMovieId: Long) =
    service.fetchMovieDetails(tmdbMovieId, append = DETAILS_APPEND).belongs_to_collection

  override suspend fun fetchCollection(collectionId: Long) = service.fetchCollection(collectionId)

  override suspend fun fetchCompanyDetails(companyId: Long) = service.fetchCompanyDetails(companyId)

  override suspend fun discoverMoviesByCompany(
    companyId: Long,
    sortBy: String,
    releasedAfter: String?,
  ) = service.discoverMoviesByCompany(companyId, sortBy, releasedAfter)

  override suspend fun discoverShowsByCompany(
    companyId: Long,
    sortBy: String,
    airedAfter: String?,
  ) = service.discoverShowsByCompany(companyId, sortBy, airedAfter)

  override suspend fun fetchPersonDetails(id: Long): TmdbPerson = service.fetchPersonDetails(id)

  override suspend fun fetchPersonTranslations(id: Long): Map<String, TmdbTranslation.Data> {
    val result = service.fetchPersonTranslation(id).translations ?: emptyList()
    return result
      .filter {
        if (it.iso_639_1.lowercase() != "zh") true else it.iso_3166_1.lowercase() == "cn"
      } // Chinese Simplified filter
      .associateBy(
        keySelector = { it.iso_639_1.lowercase() },
        valueTransform = { it.data ?: TmdbTranslation.Data(null) },
      )
  }

  override suspend fun fetchPersonImages(tmdbId: Long) =
    try {
      if (tmdbId <= 0) TmdbImages.EMPTY
      service.fetchPersonImages(tmdbId)
    } catch (error: Throwable) {
      TmdbImages.EMPTY
    }
}
