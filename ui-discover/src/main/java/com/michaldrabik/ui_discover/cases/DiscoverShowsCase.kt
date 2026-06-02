package com.michaldrabik.ui_discover.cases

import com.michaldrabik.common.Config.DEFAULT_LANGUAGE
import com.michaldrabik.common.dispatchers.CoroutineDispatchers
import com.michaldrabik.repository.TranslationsRepository
import com.michaldrabik.repository.images.ShowImagesProvider
import com.michaldrabik.repository.shows.ShowsRepository
import com.michaldrabik.ui_discover.helpers.itemtype.ImageTypeProvider
import com.michaldrabik.ui_discover.recycler.DiscoverListItem
import com.michaldrabik.ui_model.DiscoverFeed
import com.michaldrabik.ui_model.DiscoverFilters
import com.michaldrabik.ui_model.ImageType
import com.michaldrabik.ui_model.Show
import com.michaldrabik.ui_model.Translation
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
internal class DiscoverShowsCase @Inject constructor(
  private val dispatchers: CoroutineDispatchers,
  private val showsRepository: ShowsRepository,
  private val imageTypeProvider: ImageTypeProvider,
  private val imagesProvider: ShowImagesProvider,
  private val translationsRepository: TranslationsRepository,
) {

  suspend fun isCacheValid() = showsRepository.discoverShows.isCacheValid()

  suspend fun loadCachedShows(filters: DiscoverFilters): List<DiscoverListItem> =
    withContext(dispatchers.IO) {
      val shows = showsRepository.discoverShows.loadAllCached()
      val myShowsIds = showsRepository.myShows.loadAllIds()
      val watchlistShowsIds = showsRepository.watchlistShows.loadAllIds()

      prepareItems(
        shows = shows,
        myShowsIds = myShowsIds,
        watchlistShowsIds = watchlistShowsIds,
        filters = filters,
      )
    }

  suspend fun loadRemoteShows(filters: DiscoverFilters): List<DiscoverListItem> =
    withContext(dispatchers.IO) {
      val shows = showsRepository.discoverShows.loadAllRemote(
        order = filters.feedOrder,
        showCollection = false,
        collectionSize = 50,
        genres = filters.genres,
        networks = filters.networks
      )

      val myShowsIds = showsRepository.myShows.loadAllIds()
      val watchlistShowsIds = showsRepository.watchlistShows.loadAllIds()

      prepareItems(
        shows = shows,
        myShowsIds = myShowsIds,
        watchlistShowsIds = watchlistShowsIds,
        filters = filters,
      )
    }

  private suspend fun prepareItems(
    shows: List<Show>,
    myShowsIds: List<Long>,
    watchlistShowsIds: List<Long>,
    filters: DiscoverFilters,
  ): List<DiscoverListItem> =
    withContext(dispatchers.IO) {
      val language = translationsRepository.getLanguage()

      shows
        .filter { if (filters.hideCollection) it.traktId !in myShowsIds else true }
        .mapIndexed { index, show ->
          async {
            val itemType = imageTypeProvider.getImageType(index)
            val image = imagesProvider.findCachedImage(show, itemType)
            val translation = loadTranslation(language, show)

            DiscoverListItem(
              show = show,
              image = image,
              isLoading = false,
              isFollowed = show.traktId in myShowsIds,
              isWatchlist = show.traktId in watchlistShowsIds,
              translation = translation,
            )
          }
        }.awaitAll()
    }

  private suspend fun loadTranslation(
    language: String,
    show: Show,
  ): Translation? {
    if (language == DEFAULT_LANGUAGE) return null
    return try {
      translationsRepository.loadTranslation(show, language, onlyLocal = true)
    } catch (t: Throwable) {
      null
    }
  }
}
