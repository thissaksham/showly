package com.michaldrabik.ui_progress.progress

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.michaldrabik.repository.TranslationsRepository
import com.michaldrabik.repository.images.ShowImagesProvider
import com.michaldrabik.repository.settings.SettingsRepository
import com.michaldrabik.ui_base.dates.DateFormatProvider
import com.michaldrabik.ui_base.viewmodel.ChannelsDelegate
import com.michaldrabik.ui_base.viewmodel.DefaultChannelsDelegate
import com.michaldrabik.ui_base.utilities.events.Event
import com.michaldrabik.ui_base.utilities.extensions.SUBSCRIBE_STOP_TIMEOUT
import com.michaldrabik.ui_model.ImageType
import com.michaldrabik.ui_model.SortOrder
import com.michaldrabik.ui_model.SortType
import com.michaldrabik.ui_model.EpisodeBundle
import com.michaldrabik.ui_progress.main.EpisodeCheckActionUiEvent
import com.michaldrabik.ui_progress.main.ProgressMainUiState
import com.michaldrabik.ui_progress.progress.cases.ProgressFiltersCase
import com.michaldrabik.ui_progress.progress.cases.ProgressHeadersCase
import com.michaldrabik.ui_progress.progress.cases.ProgressItemsCase
import com.michaldrabik.ui_progress.progress.cases.ProgressSortOrderCase
import com.michaldrabik.ui_progress.progress.recycler.ProgressListItem
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.format.DateTimeFormatter
import javax.inject.Inject

@HiltViewModel
class ProgressViewModel @Inject constructor(
  private val itemsCase: ProgressItemsCase,
  private val headersCase: ProgressHeadersCase,
  private val sortOrderCase: ProgressSortOrderCase,
  private val filtersCase: ProgressFiltersCase,
  private val imagesProvider: ShowImagesProvider,
  private val dateFormatProvider: DateFormatProvider,
  private val translationsRepository: TranslationsRepository,
  private val settingsRepository: SettingsRepository,
) : ViewModel(),
  ChannelsDelegate by DefaultChannelsDelegate() {

  private var loadItemsJob: Job? = null

  private val itemsState = MutableStateFlow<List<ProgressListItem>?>(null)
  private val loadingState = MutableStateFlow(false)
  private val scrollState = MutableStateFlow(Event(false))
  private val sortOrderState = MutableStateFlow<Event<Triple<SortOrder, SortType, Boolean>>?>(null)
  private val dateFormatState = MutableStateFlow<DateTimeFormatter?>(null)

  private var searchQuery: String? = null
  private var timestamp: Long = 0L

  init {
    dateFormatState.value = dateFormatProvider.loadShortDayFormat()
  }

  fun onParentState(parentState: ProgressMainUiState) {
    if (this.timestamp != parentState.timestamp) {
      this.timestamp = parentState.timestamp ?: 0L
      loadItems(withLoading = true)
    }
    if (this.searchQuery != parentState.searchQuery) {
      this.searchQuery = parentState.searchQuery
      loadItems(withLoading = false)
    }
  }

  fun loadItems(withLoading: Boolean = false) {
    loadItemsJob?.cancel()
    loadItemsJob = viewModelScope.launch {
      if (withLoading) loadingState.value = true
      val items = itemsCase.loadItems(searchQuery ?: "", true)
      itemsState.value = items
      loadingState.value = false
    }
  }

  fun onSortOrderClicked() {
    viewModelScope.launch {
      val sortOrder = sortOrderCase.loadSortOrder()
      sortOrderState.value = Event(sortOrder)
    }
  }

  fun onEpisodeChecked(item: ProgressListItem.Episode) {
    viewModelScope.launch {
      val bundle = EpisodeBundle(item.requireEpisode(), item.requireSeason(), item.show)
      eventChannel.send(EpisodeCheckActionUiEvent(bundle, settingsRepository.progressDateSelectionType))
    }
  }

  fun findMissingImage(item: ProgressListItem, withLoading: Boolean) {
    if (item !is ProgressListItem.Episode) return
    viewModelScope.launch {
      val image = imagesProvider.findCachedImage(item.show, ImageType.POSTER)
      if (image.fileUrl.isNotBlank()) {
        updateItem(item.copy(image = image))
      }
    }
  }

  fun findMissingTranslation(item: ProgressListItem) {
    if (item !is ProgressListItem.Episode) return
    viewModelScope.launch {
      val language = translationsRepository.getLanguage()
      val translation = translationsRepository.loadTranslation(item.show, language)
      if (translation != null) {
        updateItem(item.copy(translations = item.translations?.copy(show = translation)))
      }
    }
  }

  fun setSortOrder(order: SortOrder, type: SortType, isNewAlwaysAtTop: Boolean) {
    viewModelScope.launch {
      sortOrderCase.setSortOrder(order, type, isNewAlwaysAtTop)
      loadItems(withLoading = true)
    }
  }

  fun setUpcomingFilter(enabled: Boolean) {
    filtersCase.setUpcomingFilter(enabled)
    loadItems(withLoading = true)
  }

  fun setOnHoldFilter(enabled: Boolean) {
    filtersCase.setOnHoldFilter(enabled)
    loadItems(withLoading = true)
  }

  fun toggleHeaderCollapsed(type: ProgressListItem.Header.Type) {
    headersCase.toggleHeaderCollapsed(type)
    loadItems()
  }

  private fun updateItem(newItem: ProgressListItem) {
    val currentItems = itemsState.value ?: return
    val newItems = currentItems.toMutableList()
    val index = newItems.indexOfFirst { it isSameAs newItem }
    if (index != -1) {
      newItems[index] = newItem
      itemsState.value = newItems
    }
  }

  val uiState: StateFlow<ProgressUiState> = combine(
    itemsState,
    loadingState,
    scrollState,
    sortOrderState,
  ) { items, loading, scroll, sortOrder ->
    ProgressUiState(
      items = items,
      isLoading = loading,
      scrollReset = scroll,
      sortOrder = sortOrder,
    )
  }.stateIn(
    scope = viewModelScope,
    started = SharingStarted.WhileSubscribed(SUBSCRIBE_STOP_TIMEOUT),
    initialValue = ProgressUiState(),
  )
}
