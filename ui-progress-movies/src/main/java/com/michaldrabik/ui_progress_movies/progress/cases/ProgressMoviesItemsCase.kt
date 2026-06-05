package com.michaldrabik.ui_progress_movies.progress.cases

import com.michaldrabik.common.Config
import com.michaldrabik.common.dispatchers.CoroutineDispatchers
import com.michaldrabik.common.extensions.nowUtcDay
import com.michaldrabik.repository.PinnedItemsRepository
import com.michaldrabik.repository.RatingsRepository
import com.michaldrabik.repository.TranslationsRepository
import com.michaldrabik.repository.images.MovieImagesProvider
import com.michaldrabik.repository.movies.MoviesRepository
import com.michaldrabik.repository.settings.SettingsRepository
import com.michaldrabik.ui_base.dates.DateFormatProvider
import com.michaldrabik.ui_base.utilities.extensions.removeDiacritics
import com.michaldrabik.ui_model.ImageType
import com.michaldrabik.ui_model.Movie
import com.michaldrabik.ui_model.SortOrder
import com.michaldrabik.ui_model.SortType
import com.michaldrabik.ui_progress_movies.helpers.ProgressMoviesItemsSorter
import com.michaldrabik.ui_progress_movies.progress.recycler.ProgressMovieListItem
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ProgressMoviesItemsCase @Inject constructor(
  private val dispatchers: CoroutineDispatchers,
  private val moviesRepository: MoviesRepository,
  private val translationsRepository: TranslationsRepository,
  private val ratingsRepository: RatingsRepository,
  private val settingsRepository: SettingsRepository,
  private val pinnedItemsRepository: PinnedItemsRepository,
  private val imagesProvider: MovieImagesProvider,
  private val dateFormatProvider: DateFormatProvider,
  private val sorter: ProgressMoviesItemsSorter,
) {

  suspend fun loadItems(searchQuery: String = ""): List<ProgressMovieListItem> =
    withContext(dispatchers.IO) {
      val language = translationsRepository.getLanguage()
      val sortOrder = settingsRepository.sorting.progressMoviesSortOrder
      val sortType = settingsRepository.sorting.progressMoviesSortType
      val filtersItem = loadFiltersItem(sortOrder, sortType)
      val spoilers = settingsRepository.spoilers.getAll()

      val watchlistMovies = moviesRepository.watchlistMovies.loadAll()
      val items = watchlistMovies.map { movie ->
        async {
          val image = imagesProvider.findCachedImage(movie, ImageType.POSTER)
          val rating = ratingsRepository.movies.loadRatings(listOf(movie)).firstOrNull()
          val isPinned = pinnedItemsRepository.isItemPinned(movie)
          val translation = if (language != Config.DEFAULT_LANGUAGE) {
            translationsRepository.loadTranslation(movie, language, onlyLocal = true)
          } else {
            null
          }

          ProgressMovieListItem.MovieItem(
            movie = movie,
            image = image,
            translation = translation,
            userRating = rating?.rating,
            isPinned = isPinned,
            spoilers = spoilers,
          )
        }
      }.awaitAll()

      val filteredItems = filterItems(searchQuery, items)
      val validItems = filteredItems.filter { it.movie.hasAired() }
      val sortedItems = prepareItems(validItems, sortOrder, sortType)

      if (sortedItems.isNotEmpty()) {
        listOf(filtersItem) + sortedItems
      } else {
        emptyList()
      }
    }

  fun loadFiltersItem(sortOrder: SortOrder, sortType: SortType): ProgressMovieListItem.FiltersItem =
    ProgressMovieListItem.FiltersItem(
      sortOrder = sortOrder,
      sortType = sortType,
    )

  private fun filterItems(
    query: String,
    items: List<ProgressMovieListItem.MovieItem>,
  ): List<ProgressMovieListItem.MovieItem> {
    if (query.isBlank()) return items
    return items.filter {
      it.movie.title.removeDiacritics().contains(query, true) ||
        it.translation?.title?.removeDiacritics()?.contains(query, true) == true
    }
  }

  private fun prepareItems(
    items: List<ProgressMovieListItem.MovieItem>,
    sortOrder: SortOrder,
    sortType: SortType,
  ): List<ProgressMovieListItem.MovieItem> {
    return items.sortedWith(
      compareByDescending<ProgressMovieListItem.MovieItem> { it.isPinned }
        .then(sorter.sort(sortOrder, sortType)),
    )
  }
}
