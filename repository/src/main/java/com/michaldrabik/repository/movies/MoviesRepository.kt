package com.michaldrabik.repository.movies

import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class MoviesRepository @Inject constructor(
  val discoverMovies: DiscoverMoviesRepository,
  val relatedMovies: RelatedMoviesRepository,
  val movieDetails: MovieDetailsRepository,
  val myMovies: MyMoviesRepository,
  val watchlistMovies: WatchlistMoviesRepository,
  val droppedMovies: HiddenMoviesRepository,
) {

  suspend fun loadCollection(skipDropped: Boolean = false) =
    coroutineScope {
      val async1 = async { myMovies.loadAll() }
      val async2 = async { watchlistMovies.loadAll() }
      val async3 = async { if (skipDropped) emptyList() else droppedMovies.loadAll() }
      val (my, watchlist, hidden) = awaitAll(async1, async2, async3)
      (my + watchlist + hidden).distinctBy { it.traktId }
    }
}
