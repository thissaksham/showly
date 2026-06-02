package com.michaldrabik.ui_discover_movies.cases

import com.michaldrabik.common.Config.DEFAULT_LANGUAGE
import com.michaldrabik.common.dispatchers.CoroutineDispatchers
import com.michaldrabik.repository.TranslationsRepository
import com.michaldrabik.repository.images.MovieImagesProvider
import com.michaldrabik.repository.movies.MoviesRepository
import com.michaldrabik.ui_discover_movies.helpers.itemtype.ImageTypeProvider
import com.michaldrabik.ui_discover_movies.recycler.DiscoverMovieListItem
import com.michaldrabik.ui_model.DiscoverFilters
import com.michaldrabik.ui_model.Movie
import com.michaldrabik.ui_model.Translation
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
internal class DiscoverMoviesCase @Inject constructor(
  private val dispatchers: CoroutineDispatchers,
  private val moviesRepository: MoviesRepository,
  private val imagesProvider: MovieImagesProvider,
  private val imageTypeProvider: ImageTypeProvider,
  private val translationsRepository: TranslationsRepository,
) {

  suspend fun isCacheValid() = moviesRepository.discoverMovies.isCacheValid()

  suspend fun loadCachedMovies(filters: DiscoverFilters): List<DiscoverMovieListItem> =
    withContext(dispatchers.IO) {
      val movies = moviesRepository.discoverMovies.loadAllCached()
      val myMoviesIds = moviesRepository.myMovies.loadAllIds()
      val watchlistMoviesIds = moviesRepository.watchlistMovies.loadAllIds()
      val hiddenMoviesIds = moviesRepository.hiddenMovies.loadAllIds()

      prepareItems(
        movies = movies,
        myMoviesIds = myMoviesIds,
        watchlistMoviesIds = watchlistMoviesIds,
        hiddenMoviesIds = hiddenMoviesIds,
        filters = filters,
        language = translationsRepository.getLanguage(),
      )
    }

  suspend fun loadRemoteMovies(filters: DiscoverFilters): List<DiscoverMovieListItem> =
    withContext(dispatchers.IO) {
      val movies = moviesRepository.discoverMovies.loadAllRemote(
        order = filters.feedOrder,
        showCollection = false,
        collectionSize = 50,
        genres = filters.genres,
      )

      val myMoviesIds = moviesRepository.myMovies.loadAllIds()
      val watchlistMoviesIds = moviesRepository.watchlistMovies.loadAllIds()
      val hiddenMoviesIds = moviesRepository.hiddenMovies.loadAllIds()

      prepareItems(
        movies = movies,
        myMoviesIds = myMoviesIds,
        watchlistMoviesIds = watchlistMoviesIds,
        hiddenMoviesIds = hiddenMoviesIds,
        filters = filters,
        language = translationsRepository.getLanguage(),
      )
    }

  private suspend fun prepareItems(
    movies: List<Movie>,
    myMoviesIds: List<Long>,
    watchlistMoviesIds: List<Long>,
    hiddenMoviesIds: List<Long>,
    filters: DiscoverFilters,
    language: String,
  ) = coroutineScope {
    val collectionIds = myMoviesIds + watchlistMoviesIds
    movies
      .filter { !hiddenMoviesIds.contains(it.traktId) }
      .filter {
        if (!filters.hideCollection) {
          true
        } else {
          !collectionIds.contains(it.traktId)
        }
      }
      .mapIndexed { index, movie ->
        async {
          val itemType = imageTypeProvider.getImageType(index)
          val image = imagesProvider.findCachedImage(movie, itemType)
          val translation = loadTranslation(language, movie)
          DiscoverMovieListItem(
            movie,
            image,
            isCollected = movie.ids.trakt.id in myMoviesIds,
            isWatchlist = movie.ids.trakt.id in watchlistMoviesIds,
            translation = translation,
          )
        }
      }.awaitAll()
      .toList()
  }

  private suspend fun loadTranslation(
    language: String,
    movie: Movie,
  ): Translation? {
    if (language == DEFAULT_LANGUAGE) return null
    return try {
      translationsRepository.loadTranslation(movie, language, onlyLocal = true)
    } catch (t: Throwable) {
      null
    }
  }
}
