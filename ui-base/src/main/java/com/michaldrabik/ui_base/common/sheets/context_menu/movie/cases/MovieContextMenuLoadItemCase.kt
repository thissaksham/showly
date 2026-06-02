package com.michaldrabik.ui_base.common.sheets.context_menu.movie.cases

import com.michaldrabik.common.Config
import com.michaldrabik.common.dispatchers.CoroutineDispatchers
import com.michaldrabik.repository.PinnedItemsRepository
import com.michaldrabik.repository.RatingsRepository
import com.michaldrabik.repository.TranslationsRepository
import com.michaldrabik.repository.images.MovieImagesProvider
import com.michaldrabik.repository.movies.MoviesRepository
import com.michaldrabik.repository.settings.SettingsSpoilersRepository
import com.michaldrabik.ui_base.common.sheets.context_menu.movie.helpers.MovieContextItem
import com.michaldrabik.ui_base.dates.DateFormatProvider
import com.michaldrabik.ui_model.IdTrakt
import com.michaldrabik.ui_model.ImageType
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class MovieContextMenuLoadItemCase @Inject constructor(
  private val dispatchers: CoroutineDispatchers,
  private val moviesRepository: MoviesRepository,
  private val pinnedItemsRepository: PinnedItemsRepository,
  private val imagesProvider: MovieImagesProvider,
  private val translationsRepository: TranslationsRepository,
  private val ratingsRepository: RatingsRepository,
  private val settingsSpoilersRepository: SettingsSpoilersRepository,
  private val dateFormatProvider: DateFormatProvider,
) {

  suspend fun loadItem(idTrakt: IdTrakt): MovieContextItem =
    withContext(dispatchers.IO) {
      val movie = moviesRepository.movieDetails.load(idTrakt)
      val image = imagesProvider.findCachedImage(movie, ImageType.POSTER)
      val language = translationsRepository.getLanguage()
      val translation = if (language != Config.DEFAULT_LANGUAGE) {
        translationsRepository.loadTranslation(movie, language, onlyLocal = true)
      } else {
        null
      }
      val rating = ratingsRepository.movies.loadRatings(listOf(movie)).firstOrNull()

      MovieContextItem(
        movie = movie,
        image = image,
        translation = translation,
        dateFormat = dateFormatProvider.loadFullDayFormat(),
        userRating = rating?.rating,
        isMyMovie = moviesRepository.myMovies.exists(idTrakt),
        isWatchlist = moviesRepository.watchlistMovies.exists(idTrakt),
        isDropped = moviesRepository.droppedMovies.exists(idTrakt),
        isPinnedTop = pinnedItemsRepository.isItemPinned(movie),
        spoilers = settingsSpoilersRepository.getAll(),
      )
    }
}
