package com.michaldrabik.data_remote.tmdb.model

data class TmdbProductionCompany(
  val id: Int,
  val name: String,
  val logo_path: String?,
  val origin_country: String?,
  val description: String?,
  val headquarters: String?,
  val homepage: String?,
)

data class TmdbGenre(
  val id: Long?,
  val name: String?,
)

data class TmdbNetwork(
  val id: Long?,
  val name: String?,
)

/**
 * Returned when `append_to_response=external_ids` is requested. Carries the ids
 * the app used to receive from Trakt, so rows keep their cross-references.
 */
data class TmdbExternalIds(
  val imdb_id: String?,
  val tvdb_id: Long?,
)

/** Returned when `append_to_response=videos` is requested. */
data class TmdbVideos(
  val results: List<TmdbVideo>?,
) {
  companion object {
    private const val SITE_YOUTUBE = "YouTube"
    private const val TYPE_TRAILER = "Trailer"
    private const val TYPE_TEASER = "Teaser"
    const val YOUTUBE_WATCH_URL = "https://www.youtube.com/watch?v="
  }

  /**
   * The best trailer URL, or null. Only YouTube is usable - TMDB also lists Vimeo
   * and others the app cannot open. An official trailer wins; a teaser is the
   * fallback, since many upcoming titles have nothing else.
   */
  val trailerUrl: String?
    get() {
      val youtube = results.orEmpty().filter { it.site == SITE_YOUTUBE && !it.key.isNullOrBlank() }
      val best = youtube.firstOrNull { it.type == TYPE_TRAILER && it.official == true }
        ?: youtube.firstOrNull { it.type == TYPE_TRAILER }
        ?: youtube.firstOrNull { it.type == TYPE_TEASER }
      return best?.key?.let { "$YOUTUBE_WATCH_URL$it" }
    }
}

data class TmdbVideo(
  val key: String?,
  val site: String?,
  val type: String?,
  val official: Boolean?,
)

data class TmdbMovieDetails(
  val production_companies: List<TmdbProductionCompany>?,
  val id: Long? = null,
  val title: String? = null,
  val overview: String? = null,
  val release_date: String? = null,
  val runtime: Int? = null,
  val homepage: String? = null,
  val status: String? = null,
  val vote_average: Float? = null,
  val vote_count: Long? = null,
  val genres: List<TmdbGenre>? = null,
  val origin_country: List<String>? = null,
  val original_language: String? = null,
  val imdb_id: String? = null,
  val external_ids: TmdbExternalIds? = null,
  val videos: TmdbVideos? = null,
  // A movie belongs to at most one TMDB collection, and only a stub of it is
  // returned here - `parts` needs the collection endpoint.
  val belongs_to_collection: TmdbCollection? = null,
)

/** `find/{id}` - looks a title up by an id from another site, e.g. IMDB. */
data class TmdbFindResults(
  val movie_results: List<TmdbDiscoveryItem>?,
  val tv_results: List<TmdbDiscoveryItem>?,
)

data class TmdbCollection(
  val id: Long?,
  val name: String?,
  val overview: String?,
  val parts: List<TmdbDiscoveryItem>? = null,
)

data class TmdbShowDetails(
  val production_companies: List<TmdbProductionCompany>?,
  val id: Long? = null,
  val name: String? = null,
  val overview: String? = null,
  val first_air_date: String? = null,
  val episode_run_time: List<Int>? = null,
  val homepage: String? = null,
  val status: String? = null,
  val vote_average: Float? = null,
  val vote_count: Long? = null,
  val genres: List<TmdbGenre>? = null,
  val networks: List<TmdbNetwork>? = null,
  val origin_country: List<String>? = null,
  val number_of_episodes: Int? = null,
  val external_ids: TmdbExternalIds? = null,
  val videos: TmdbVideos? = null,
  val seasons: List<TmdbSeasonSummary>? = null,
  // TMDB leaves episode_run_time empty for most modern shows; a single episode is
  // the only runtime the API reliably reports.
  val last_episode_to_air: TmdbEpisode? = null,
  val next_episode_to_air: TmdbEpisode? = null,
) {
  val runtimeMinutes: Int
    get() = episode_run_time?.firstOrNull { it > 0 }
      ?: last_episode_to_air?.runtime?.takeIf { it > 0 }
      ?: next_episode_to_air?.runtime?.takeIf { it > 0 }
      ?: -1
}

/** The stub TMDB returns inside `tv/{id}`; episodes need a per-season request. */
data class TmdbSeasonSummary(
  val id: Long?,
  val season_number: Int?,
  val episode_count: Int?,
)

data class TmdbSeasonDetails(
  val id: Long?,
  val name: String?,
  val overview: String?,
  val air_date: String?,
  val season_number: Int?,
  val vote_average: Float?,
  val episodes: List<TmdbEpisode>?,
)

data class TmdbEpisode(
  val id: Long?,
  val name: String?,
  val overview: String?,
  val air_date: String?,
  val episode_number: Int?,
  val season_number: Int?,
  val runtime: Int?,
  val vote_average: Float?,
  // Int, not Long: Episode.votes is an Int and TMDB counts never approach the limit.
  val vote_count: Int?,
)

data class TmdbDiscovery(
  val results: List<TmdbDiscoveryItem>?,
)

data class TmdbDiscoveryItem(
  val id: Long,
  val title: String?,
  val name: String?,
  val overview: String?,
  val poster_path: String?,
  val release_date: String?,
  val first_air_date: String?,
  val vote_average: Float?,
  val vote_count: Long?,
  // Trending has no genre filter of its own, so it is applied to these client-side.
  val genre_ids: List<Long>? = null,
)

/**
 * `search/multi` mixes shows, movies and people in one list, distinguished by
 * [media_type]. People are filtered out before the results reach the app.
 */
data class TmdbSearchResponse(
  val results: List<TmdbSearchItem>?,
)

/**
 * `person/{id}/combined_credits` - everything a person worked on, shows and movies
 * together, split into the roles they acted in and the ones they crewed on. Items
 * carry the same shape as search results.
 */
data class TmdbPersonCredits(
  val cast: List<TmdbSearchItem>?,
  val crew: List<TmdbSearchItem>?,
)

data class TmdbSearchItem(
  val id: Long?,
  val media_type: String?,
  val title: String?,
  val name: String?,
  val overview: String?,
  val release_date: String?,
  val first_air_date: String?,
  val vote_average: Float?,
  val vote_count: Long?,
  val genre_ids: List<Long>?,
  val origin_country: List<String>?,
) {
  companion object {
    const val TYPE_SHOW = "tv"
    const val TYPE_MOVIE = "movie"
  }

  val isShow get() = media_type == TYPE_SHOW
  val isMovie get() = media_type == TYPE_MOVIE
}
