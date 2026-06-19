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

data class TmdbMovieDetails(
  val production_companies: List<TmdbProductionCompany>?,
)

data class TmdbShowDetails(
  val production_companies: List<TmdbProductionCompany>?,
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
)
