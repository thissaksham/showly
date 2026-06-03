package com.michaldrabik.ui_my_movies.mymovies

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.michaldrabik.common.Config
import com.michaldrabik.repository.images.ShowImagesProvider
import com.michaldrabik.repository.settings.SettingsRepository
import com.michaldrabik.ui_base.events.EventsManager
import com.michaldrabik.ui_base.events.ReloadData
import com.michaldrabik.ui_base.utilities.events.Event
import com.michaldrabik.ui_base.utilities.extensions.SUBSCRIBE_STOP_TIMEOUT
import com.michaldrabik.ui_base.utilities.extensions.findReplace
import com.michaldrabik.ui_base.viewmodel.ChannelsDelegate
import com.michaldrabik.ui_base.viewmodel.DefaultChannelsDelegate
import com.michaldrabik.ui_model.Image
import com.michaldrabik.ui_model.ImageType
import com.michaldrabik.ui_model.ImageType.POSTER
import com.michaldrabik.ui_model.Movie
import com.michaldrabik.ui_model.MyMoviesSection.ALL
import com.michaldrabik.ui_model.MyMoviesSection.RECENTS
import com.michaldrabik.ui_model.SortOrder
import com.michaldrabik.ui_model.SortType
import com.michaldrabik.ui_model.SpoilersSettings
import com.michaldrabik.ui_model.TraktRating
import com.michaldrabik.ui_my_movies.main.FollowedMoviesUiState
import com.michaldrabik.ui_my_movies.mymovies.cases.MyMoviesLoadCase
import com.michaldrabik.ui_my_movies.mymovies.cases.MyMoviesRatingsCase
import com.michaldrabik.ui_my_movies.mymovies.cases.MyMoviesSortingCase
import com.michaldrabik.ui_my_movies.mymovies.recycler.MyMoviesItem
import com.michaldrabik.ui_my_movies.mymovies.recycler.MyMoviesItem.Type
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import timber.log.Timber
import java.time.format.DateTimeFormatter
import javax.inject.Inject
import com.michaldrabik.ui_base.events.Event as EventSync

@HiltViewModel
class MyMoviesViewModel @Inject constructor(
  private val loadMoviesCase: MyMoviesLoadCase,
  private val ratingsCase: MyMoviesRatingsCase,
  private val sortingCase: MyMoviesSortingCase,
  private val settingsRepository: SettingsRepository,
  private val eventsManager: EventsManager,
) : ViewModel(),
  ChannelsDelegate by DefaultChannelsDelegate() {

  private var loadItemsJob: Job? = null

  private val itemsState = MutableStateFlow<List<MyMoviesItem>?>(null)
  private val itemsUpdateState = MutableStateFlow<Event<Boolean>?>(null)
  private val showEmptyViewState = MutableStateFlow(false)

  private var searchQuery: String? = null

  init {
    viewModelScope.launch { eventsManager.events.collect { onEvent(it) } }
  }

  fun onParentState(state: FollowedMoviesUiState) {
    when {
      this.searchQuery != state.searchQuery -> {
        this.searchQuery = state.searchQuery
        loadMovies(resetScroll = state.searchQuery.isNullOrBlank())
      }
    }
  }

  fun loadMovies(resetScroll: Boolean = false) {
    loadItemsJob?.cancel()
    loadItemsJob = viewModelScope.launch {
      val settings = loadMoviesCase.loadSettings()
      val ratings = ratingsCase.loadRatings()
      val sortOrder = settingsRepository.sorting.myMoviesAllSortOrder
      val genres = settingsRepository.filters.myMoviesGenres
      val spoilers = settingsRepository.spoilers.getAll()
      val dateFormat = loadMoviesCase.loadDateFormat()
      val fullDateFormat = loadMoviesCase.loadShortDateFormat()

      val movies = loadMoviesCase
        .loadAll()
        .map {
          toListItemAsync(
            itemType = Type.ALL_MOVIES_ITEM,
            movie = it,
            dateFormat = dateFormat,
            fullDateFormat = fullDateFormat,
            type = POSTER,
            userRating = ratings[it.ids.trakt],
            sortOrder = sortOrder,
            spoilers = spoilers,
          )
        }.awaitAll()

      val allMovies = loadMoviesCase.filterSectionMovies(
        allMovies = movies,
        sortOrder = sortingCase.loadSortOrder(),
        genres = genres.map { it.slug },
        searchQuery = searchQuery,
      )

      val recentMovies = if (settings.myRecentsAmount > 0) {
        loadMoviesCase
          .loadRecentMovies()
          .map {
            toListItemAsync(
              Type.RECENT_MOVIES,
              it,
              dateFormat,
              fullDateFormat,
              ImageType.FANART,
              ratings[it.ids.trakt],
              null,
              spoilers,
            )
          }.awaitAll()
      } else {
        emptyList()
      }

      val isNotSearching = searchQuery.isNullOrBlank()
      val listItems = mutableListOf<MyMoviesItem>()
      listItems.run {
        if (isNotSearching && recentMovies.isNotEmpty()) {
          add(MyMoviesItem.createHeader(RECENTS, recentMovies.count(), null, null))
          add(MyMoviesItem.createRecentsSection(recentMovies))
        }
        if (movies.isNotEmpty()) {
          add(
            MyMoviesItem.createHeader(
              section = ALL,
              itemCount = allMovies.count(),
              sortOrder = sortingCase.loadSortOrder(),
              genres = settingsRepository.filters.myMoviesGenres,
            ),
          )
          addAll(allMovies)
        }
      }

      itemsState.value = listItems
      itemsUpdateState.value = Event(resetScroll)
      showEmptyViewState.value = movies.isEmpty()
    }
  }

  fun setSortOrder(
    sortOrder: SortOrder,
    sortType: SortType,
  ) {
    viewModelScope.launch {
      sortingCase.setSortOrder(sortOrder, sortType)
      loadMovies()
    }
  }

  fun loadMissingImage(
    item: MyMoviesItem,
    force: Boolean,
  ) {
    viewModelScope.launch {
      updateItem(item.copy(isLoading = true))
      try {
        val image = loadMoviesCase.loadMissingImage(item.movie, item.image.type, force)
        updateItem(item.copy(isLoading = false, image = image))
      } catch (t: Throwable) {
        updateItem(item.copy(isLoading = false, image = Image.createUnavailable(item.image.type)))
      }
    }
  }

  fun loadMissingTranslation(item: MyMoviesItem) {
    if (item.translation != null) return
    viewModelScope.launch {
      try {
        val translation = loadMoviesCase.loadTranslation(item.movie, false)
        updateItem(item.copy(translation = translation))
      } catch (error: Throwable) {
        Timber.e(error)
      }
    }
  }

  private fun updateItem(new: MyMoviesItem) {
    val items = uiState.value.items?.toMutableList()
    items?.findReplace(new) { it.isSameAs(new) }
    itemsState.value = items
  }

  private fun CoroutineScope.toListItemAsync(
    itemType: Type,
    movie: Movie,
    dateFormat: DateTimeFormatter,
    fullDateFormat: DateTimeFormatter,
    type: ImageType = POSTER,
    userRating: TraktRating?,
    sortOrder: SortOrder?,
    spoilers: SpoilersSettings,
  ) = async {
    val image = loadMoviesCase.findCachedImage(movie, type)
    val translation = loadMoviesCase.loadTranslation(movie, true)
    MyMoviesItem(
      type = itemType,
      header = null,
      recentsSection = null,
      movie = movie,
      image = image,
      isLoading = false,
      translation = translation,
      userRating = userRating?.rating,
      dateFormat = dateFormat,
      shortDateFormat = fullDateFormat,
      sortOrder = sortOrder,
      spoilers = MyMoviesItem.Spoilers(
        isSpoilerHidden = spoilers.isMyMoviesHidden,
        isSpoilerRatingsHidden = spoilers.isMyMoviesRatingsHidden,
        isSpoilerTapToReveal = spoilers.isTapToReveal,
      ),
    )
  }

  private fun onEvent(event: EventSync) =
    when (event) {
      is ReloadData -> loadMovies()
      else -> Unit
    }

  val uiState = combine(
    itemsState,
    itemsUpdateState,
    showEmptyViewState,
  ) { s1, s2, s3 ->
    MyMoviesUiState(
      items = s1,
      resetScroll = s2,
      showEmptyView = s3,
    )
  }.stateIn(
    scope = viewModelScope,
    started = SharingStarted.WhileSubscribed(SUBSCRIBE_STOP_TIMEOUT),
    initialValue = MyMoviesUiState(),
  )
}
