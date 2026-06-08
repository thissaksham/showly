package com.michaldrabik.ui_progress_movies.progress

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.michaldrabik.repository.TranslationsRepository
import com.michaldrabik.repository.images.MovieImagesProvider
import com.michaldrabik.repository.settings.SettingsRepository
import com.michaldrabik.ui_base.utilities.events.Event
import com.michaldrabik.ui_base.utilities.extensions.SUBSCRIBE_STOP_TIMEOUT
import com.michaldrabik.ui_base.utilities.extensions.combine
import com.michaldrabik.ui_base.utilities.extensions.rethrowCancellation
import com.michaldrabik.ui_base.utilities.events.MessageEvent
import com.michaldrabik.ui_base.viewmodel.ChannelsDelegate
import com.michaldrabik.ui_base.viewmodel.DefaultChannelsDelegate
import com.michaldrabik.ui_model.ImageType
import com.michaldrabik.ui_model.Movie
import com.michaldrabik.ui_model.SortOrder
import com.michaldrabik.ui_model.SortType
import com.michaldrabik.ui_progress_movies.main.MovieCheckActionUiEvent
import com.michaldrabik.ui_progress_movies.main.ProgressMoviesMainUiState
import com.michaldrabik.ui_progress_movies.progress.cases.ProgressMoviesItemsCase
import com.michaldrabik.ui_progress_movies.progress.cases.ProgressMoviesPinnedCase
import com.michaldrabik.ui_progress_movies.progress.cases.ProgressMoviesSortCase
import com.michaldrabik.ui_progress_movies.progress.recycler.ProgressMovieListItem
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class ProgressMoviesViewModel @Inject constructor(
  private val itemsCase: ProgressMoviesItemsCase,
  private val sortCase: ProgressMoviesSortCase,
  private val pinnedCase: ProgressMoviesPinnedCase,
  private val imagesProvider: MovieImagesProvider,
  private val settingsRepository: SettingsRepository,
  private val translationsRepository: TranslationsRepository,
) : ViewModel(),
  ChannelsDelegate by DefaultChannelsDelegate() {

  private var loadItemsJob: Job? = null

  private val itemsState = MutableStateFlow<List<ProgressMovieListItem>?>(null)
  private val scrollState = MutableStateFlow(Event(false))
  private val sortOrderState = MutableStateFlow<Event<Pair<SortOrder, SortType>>?>(null)
  private val overscrollState = MutableStateFlow(false)

  private var searchQuery: String? = null
  private var timestamp = 0L

  fun onParentState(state: ProgressMoviesMainUiState) {
    if (state.timestamp != timestamp) {
      timestamp = state.timestamp ?: 0L
      loadItems(timestamp == 0L)
    }
    if (state.searchQuery != searchQuery) {
      searchQuery = state.searchQuery
      loadItems()
    }
  }

  fun onMovieChecked(movie: Movie) {
    viewModelScope.launch {
      eventChannel.send(MovieCheckActionUiEvent(movie, settingsRepository.progressDateSelectionType))
    }
  }

  fun loadItems(force: Boolean = false) {
    loadItemsJob?.cancel()
    loadItemsJob = viewModelScope.launch {
      try {
        val items = itemsCase.loadItems(searchQuery ?: "")
        itemsState.value = items
        overscrollState.value = false
      } catch (e: Throwable) {
        rethrowCancellation(e)
      }
    }
  }

  fun findMissingImage(
    item: ProgressMovieListItem.MovieItem,
    force: Boolean = false,
  ) {
    viewModelScope.launch {
      val image = imagesProvider.findCachedImage(item.movie, ImageType.POSTER)
      if (image != null) {
        updateItem(item.copy(image = image))
      }
    }
  }

  fun findMissingTranslation(item: ProgressMovieListItem.MovieItem) {
    viewModelScope.launch {
      val translation = translationsRepository.loadTranslation(
        item.movie,
        settingsRepository.language,
        false,
      )
      if (translation != null) {
        updateItem(item.copy(translation = translation))
      }
    }
  }

  fun setSortOrder(
    sort: SortOrder,
    type: SortType,
  ) {
    sortCase.setSortOrder(sort, type)
    loadItems()
  }

  fun togglePinItem(item: ProgressMovieListItem.MovieItem) {
    viewModelScope.launch {
      pinnedCase.togglePinned(item.movie)
      loadItems()
    }
  }

  private fun updateItem(item: ProgressMovieListItem.MovieItem) {
    val currentItems = itemsState.value?.toMutableList() ?: return
    val index = currentItems.indexOfFirst { (it as? ProgressMovieListItem.MovieItem)?.movie?.ids?.trakt == item.movie.ids.trakt }
    if (index != -1) {
      currentItems[index] = item
      itemsState.value = currentItems
    }
  }

  val uiState = kotlinx.coroutines.flow.combine(
    itemsState,
    scrollState,
    sortOrderState,
    overscrollState,
  ) { items, scroll, sortOrder, isOverscroll ->
    ProgressMoviesUiState(
      items = items,
      scrollReset = scroll,
      sortOrder = sortOrder,
      isOverScrollEnabled = isOverscroll,
    )
  }.stateIn(
    scope = viewModelScope,
    started = SharingStarted.WhileSubscribed(SUBSCRIBE_STOP_TIMEOUT),
    initialValue = ProgressMoviesUiState(),
  )
}
