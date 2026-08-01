package com.michaldrabik.data_remote

object Config {
  const val TMDB_BASE_URL = "https://api.themoviedb.org/3/"
  const val TMDB_API_KEY = BuildConfig.TMDB_API_KEY

  // TMDB returns a fixed 20 items per page, so a feed's size is set by how many pages
  // it asks for. Pages are fetched in parallel.
  const val TMDB_PAGE_SIZE = 20
  const val TMDB_DISCOVER_PAGES = 12
  const val TMDB_ANTICIPATED_PAGES = 2

  const val OMDB_BASE_URL = "https://omdbapi.com/"
  const val OMDB_API_KEY = BuildConfig.OMDB_API_KEY

  // TVDB is the only source that knows what time of day an episode airs. TMDB gives
  // a date and nothing else, which is why everything used to land at midnight UTC.
  const val TVDB_BASE_URL = "https://api4.thetvdb.com/v4/"
  const val TVDB_API_KEY = BuildConfig.TVDB_API_KEY

  const val AWS_BASE_URL = "https://showly2.s3.eu-west-2.amazonaws.com/"
}
