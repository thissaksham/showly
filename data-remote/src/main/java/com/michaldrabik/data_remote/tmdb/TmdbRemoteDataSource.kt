package com.michaldrabik.data_remote.tmdb

import com.michaldrabik.data_remote.tmdb.model.TmdbImage
import com.michaldrabik.data_remote.tmdb.model.TmdbCollection
import com.michaldrabik.data_remote.tmdb.model.TmdbDiscovery
import com.michaldrabik.data_remote.tmdb.model.TmdbDiscoveryItem
import com.michaldrabik.data_remote.tmdb.model.TmdbFindResults
import com.michaldrabik.data_remote.tmdb.model.TmdbImages
import com.michaldrabik.data_remote.tmdb.model.TmdbMovieDetails
import com.michaldrabik.data_remote.tmdb.model.TmdbPerson
import com.michaldrabik.data_remote.tmdb.model.TmdbProductionCompany
import com.michaldrabik.data_remote.tmdb.model.TmdbSearchItem
import com.michaldrabik.data_remote.tmdb.model.TmdbSeasonDetails
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

  /**
   * Seasons with their episodes. TMDB only lists season numbers on the show
   * endpoint, so each season is a separate request; they run in parallel.
   */
  suspend fun fetchSeasons(tmdbId: Long): List<TmdbSeasonDetails>

  /**
   * Everything a person worked on. [type] picks acting roles or crew credits;
   * items without an id are dropped - they cannot be resolved to a local row.
   */
  suspend fun fetchPersonCredits(
    tmdbId: Long,
    type: TmdbPerson.Type,
  ): List<TmdbSearchItem>

  /**
   * Searches shows and movies. People are filtered out, and items without an id
   * are dropped - they cannot be resolved to a local row.
   */
  suspend fun search(query: String): List<TmdbSearchItem>

  /**
   * Discover feeds. [genres] and [networks] are TMDB ids joined with "|" (OR), or
   * null for no filter. Trending has no filters of its own - callers narrow it.
   */
  suspend fun discoverShows(
    sortBy: String,
    genres: String?,
    networks: String?,
    airedAfter: String?,
    pages: Int,
  ): List<TmdbDiscoveryItem>

  suspend fun discoverMovies(
    sortBy: String,
    genres: String?,
    releasedAfter: String?,
    pages: Int,
  ): List<TmdbDiscoveryItem>

  suspend fun trendingShows(pages: Int): List<TmdbDiscoveryItem>

  suspend fun trendingMovies(pages: Int): List<TmdbDiscoveryItem>

  /**
   * Localised title and overview. TMDB falls back to English when a title has no
   * translation in the requested language, so a result is not proof of one.
   */
  suspend fun fetchShowTranslation(
    tmdbId: Long,
    language: String,
  ): TmdbShowDetails

  suspend fun fetchMovieTranslation(
    tmdbId: Long,
    language: String,
  ): TmdbMovieDetails

  suspend fun fetchSeasonTranslation(
    tmdbId: Long,
    seasonNumber: Int,
    language: String,
  ): TmdbSeasonDetails

  /** Looks a title up by its IMDB id. Either list may be empty. */
  suspend fun findByImdbId(imdbId: String): TmdbFindResults

  /** "You may also like" - TMDB's recommendations for a title. */
  suspend fun relatedShows(tmdbId: Long): List<TmdbDiscoveryItem>

  suspend fun relatedMovies(tmdbId: Long): List<TmdbDiscoveryItem>

  /** The collection a movie belongs to, or null. Carries no parts - see [fetchCollection]. */
  suspend fun fetchMovieCollection(tmdbMovieId: Long): TmdbCollection?

  /** A collection with its member movies. */
  suspend fun fetchCollection(collectionId: Long): TmdbCollection

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
