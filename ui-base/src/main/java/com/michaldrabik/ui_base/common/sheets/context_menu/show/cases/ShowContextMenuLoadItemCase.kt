package com.michaldrabik.ui_base.common.sheets.context_menu.show.cases

import com.michaldrabik.common.Config
import com.michaldrabik.common.dispatchers.CoroutineDispatchers
import com.michaldrabik.repository.PinnedItemsRepository
import com.michaldrabik.repository.RatingsRepository
import com.michaldrabik.repository.TranslationsRepository
import com.michaldrabik.repository.images.ShowImagesProvider
import com.michaldrabik.repository.settings.SettingsRepository
import com.michaldrabik.repository.shows.ShowsRepository
import com.michaldrabik.repository.OnHoldItemsRepository
import com.michaldrabik.ui_base.common.sheets.context_menu.show.helpers.ShowContextItem
import com.michaldrabik.ui_model.IdTrakt
import com.michaldrabik.ui_model.ImageType
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ShowContextMenuLoadItemCase @Inject constructor(
  private val dispatchers: CoroutineDispatchers,
  private val showsRepository: ShowsRepository,
  private val pinnedItemsRepository: PinnedItemsRepository,
  private val onHoldItemsRepository: OnHoldItemsRepository,
  private val imagesProvider: ShowImagesProvider,
  private val translationsRepository: TranslationsRepository,
  private val ratingsRepository: RatingsRepository,
  private val settingsRepository: SettingsRepository,
) {

  suspend fun loadItem(idTrakt: IdTrakt): ShowContextItem =
    withContext(dispatchers.IO) {
      val show = showsRepository.detailsShow.load(idTrakt)
      val image = imagesProvider.findCachedImage(show, ImageType.POSTER)
      val language = translationsRepository.getLanguage()
      val translation = if (language != Config.DEFAULT_LANGUAGE) {
        translationsRepository.loadTranslation(show, language, onlyLocal = true)
      } else {
        null
      }
      val rating = ratingsRepository.shows.loadRatings(listOf(show)).firstOrNull()

      ShowContextItem(
        show = show,
        image = image,
        translation = translation,
        userRating = rating?.rating,
        isMyShow = showsRepository.myShows.exists(idTrakt),
        isWatchlist = showsRepository.watchlistShows.exists(idTrakt),
        isDropped = showsRepository.droppedShows.exists(idTrakt),
        isPinnedTop = pinnedItemsRepository.isItemPinned(show),
        isOnHold = onHoldItemsRepository.isOnHold(show),
        spoilers = settingsRepository.spoilers.getAll(),
      )
    }
}
