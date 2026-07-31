package com.michaldrabik.data_remote.tmdb.api

import com.michaldrabik.data_remote.tmdb.model.TmdbCollection
import com.michaldrabik.data_remote.tmdb.model.TmdbDiscovery
import com.michaldrabik.data_remote.tmdb.model.TmdbFindResults
import com.michaldrabik.data_remote.tmdb.model.TmdbImages
import com.michaldrabik.data_remote.tmdb.model.TmdbMovieDetails
import com.michaldrabik.data_remote.tmdb.model.TmdbPeople
import com.michaldrabik.data_remote.tmdb.model.TmdbPersonCredits
import com.michaldrabik.data_remote.tmdb.model.TmdbPerson
import com.michaldrabik.data_remote.tmdb.model.TmdbProductionCompany
import com.michaldrabik.data_remote.tmdb.model.TmdbSearchResponse
import com.michaldrabik.data_remote.tmdb.model.TmdbSeasonDetails
import com.michaldrabik.data_remote.tmdb.model.TmdbShowDetails
import com.michaldrabik.data_remote.tmdb.model.TmdbStreamings
import com.michaldrabik.data_remote.tmdb.model.TmdbTranslationResponse
import retrofit2.http.GET
import retrofit2.http.Path
import retrofit2.http.Query

interface TmdbService {

  @GET("tv/{tmdbId}/images")
  suspend fun fetchShowImages(
    @Path("tmdbId") tmdbId: Long,
  ): TmdbImages

  @GET("tv/{tmdbId}/season/{season}/episode/{episode}/images")
  suspend fun fetchEpisodeImages(
    @Path("tmdbId") tmdbId: Long?,
    @Path("season") seasonNumber: Int?,
    @Path("episode") episodeNumber: Int?,
  ): TmdbImages

  @GET("movie/{tmdbId}/images")
  suspend fun fetchMovieImages(
    @Path("tmdbId") tmdbId: Long,
  ): TmdbImages

  @GET("person/{tmdbId}/images")
  suspend fun fetchPersonImages(
    @Path("tmdbId") tmdbId: Long,
  ): TmdbImages

  @GET("person/{tmdbId}")
  suspend fun fetchPersonDetails(
    @Path("tmdbId") tmdbId: Long,
  ): TmdbPerson

  @GET("person/{tmdbId}/translations")
  suspend fun fetchPersonTranslation(
    @Path("tmdbId") tmdbId: Long,
  ): TmdbTranslationResponse

  @GET("movie/{tmdbId}/credits")
  suspend fun fetchMoviePeople(
    @Path("tmdbId") tmdbId: Long,
  ): TmdbPeople

  @GET("tv/{tmdbId}/aggregate_credits")
  suspend fun fetchShowPeople(
    @Path("tmdbId") tmdbId: Long,
  ): TmdbPeople

  @GET("movie/{tmdbId}/watch/providers")
  suspend fun fetchMovieWatchProviders(
    @Path("tmdbId") tmdbId: Long,
  ): TmdbStreamings

  @GET("tv/{tmdbId}/watch/providers")
  suspend fun fetchShowWatchProviders(
    @Path("tmdbId") tmdbId: Long,
  ): TmdbStreamings

  // No default argument values in this interface: Kotlin compiles those into
  // synthetic methods that Retrofit's dynamic proxy does not implement.
  @GET("movie/{tmdbId}")
  suspend fun fetchMovieDetails(
    @Path("tmdbId") tmdbId: Long,
    @Query("append_to_response") append: String,
  ): TmdbMovieDetails

  @GET("tv/{tmdbId}")
  suspend fun fetchShowDetails(
    @Path("tmdbId") tmdbId: Long,
    @Query("append_to_response") append: String,
  ): TmdbShowDetails

  @GET("person/{personId}/combined_credits")
  suspend fun fetchPersonCredits(
    @Path("personId") personId: Long,
  ): TmdbPersonCredits

  @GET("tv/{tmdbId}/season/{seasonNumber}")
  suspend fun fetchSeason(
    @Path("tmdbId") tmdbId: Long,
    @Path("seasonNumber") seasonNumber: Int,
  ): TmdbSeasonDetails

  @GET("search/multi")
  suspend fun search(
    @Query("query") query: String,
    @Query("include_adult") includeAdult: Boolean,
  ): TmdbSearchResponse

  // Translations come from the same endpoints with a language parameter, which is
  // simpler than the /translations endpoints and returns the same fields the app shows.
  @GET("tv/{tmdbId}")
  suspend fun fetchShowTranslation(
    @Path("tmdbId") tmdbId: Long,
    @Query("language") language: String,
  ): TmdbShowDetails

  @GET("movie/{tmdbId}")
  suspend fun fetchMovieTranslation(
    @Path("tmdbId") tmdbId: Long,
    @Query("language") language: String,
  ): TmdbMovieDetails

  @GET("tv/{tmdbId}/season/{seasonNumber}")
  suspend fun fetchSeasonTranslation(
    @Path("tmdbId") tmdbId: Long,
    @Path("seasonNumber") seasonNumber: Int,
    @Query("language") language: String,
  ): TmdbSeasonDetails

  @GET("find/{externalId}")
  suspend fun findByExternalId(
    @Path("externalId") externalId: String,
    @Query("external_source") source: String,
  ): TmdbFindResults

  @GET("tv/{tmdbId}/recommendations")
  suspend fun relatedShows(
    @Path("tmdbId") tmdbId: Long,
  ): TmdbDiscovery

  @GET("movie/{tmdbId}/recommendations")
  suspend fun relatedMovies(
    @Path("tmdbId") tmdbId: Long,
  ): TmdbDiscovery

  @GET("collection/{collectionId}")
  suspend fun fetchCollection(
    @Path("collectionId") collectionId: Long,
  ): TmdbCollection

  @GET("company/{companyId}")
  suspend fun fetchCompanyDetails(
    @Path("companyId") companyId: Long,
  ): TmdbProductionCompany

  // Nulls are dropped from the query string by Retrofit, so an unset filter is simply absent.
  @GET("discover/tv")
  suspend fun discoverShows(
    @Query("sort_by") sortBy: String,
    @Query("with_genres") genres: String?,
    @Query("with_networks") networks: String?,
    @Query("first_air_date.gte") airedAfter: String?,
    @Query("page") page: Int,
  ): TmdbDiscovery

  @GET("discover/movie")
  suspend fun discoverMovies(
    @Query("sort_by") sortBy: String,
    @Query("with_genres") genres: String?,
    @Query("primary_release_date.gte") releasedAfter: String?,
    @Query("page") page: Int,
  ): TmdbDiscovery

  @GET("trending/tv/week")
  suspend fun trendingShows(
    @Query("page") page: Int,
  ): TmdbDiscovery

  @GET("trending/movie/week")
  suspend fun trendingMovies(
    @Query("page") page: Int,
  ): TmdbDiscovery

  @GET("discover/movie")
  suspend fun discoverMoviesByCompany(
    @Query("with_companies") companyId: Long,
    @Query("sort_by") sortBy: String,
    @Query("primary_release_date.gte") releasedAfter: String?,
  ): TmdbDiscovery

  @GET("discover/tv")
  suspend fun discoverShowsByCompany(
    @Query("with_companies") companyId: Long,
    @Query("sort_by") sortBy: String,
    @Query("first_air_date.gte") airedAfter: String?,
  ): TmdbDiscovery
}
