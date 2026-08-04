package com.michaldrabik.data_remote.tvdb.model

data class TvdbLoginRequest(
  val apikey: String,
)

data class TvdbLoginResponse(
  val data: TvdbToken?,
)

data class TvdbToken(
  val token: String?,
)

data class TvdbSeriesResponse(
  val data: TvdbSeries?,
)

/**
 * Only the scheduling fields. Everything else about a show still comes from TMDB.
 *
 * [airsTime] is local to [originalCountry] - TVDB stores no timezone of its own.
 * A Netflix show reads 03:00 for `usa`, 12:30 for `ind` and 08:00 for `gbr`, which
 * is one global midnight-Pacific drop expressed three ways.
 */
data class TvdbSeries(
  val airsTime: String?,
  val originalCountry: String?,
  val airsDays: TvdbAirsDays?,
)

data class TvdbAirsDays(
  val monday: Boolean = false,
  val tuesday: Boolean = false,
  val wednesday: Boolean = false,
  val thursday: Boolean = false,
  val friday: Boolean = false,
  val saturday: Boolean = false,
  val sunday: Boolean = false,
) {
  fun asCommaSeparatedString(): String =
    listOfNotNull(
      "monday".takeIf { monday },
      "tuesday".takeIf { tuesday },
      "wednesday".takeIf { wednesday },
      "thursday".takeIf { thursday },
      "friday".takeIf { friday },
      "saturday".takeIf { saturday },
      "sunday".takeIf { sunday },
    ).joinToString(",")
}
