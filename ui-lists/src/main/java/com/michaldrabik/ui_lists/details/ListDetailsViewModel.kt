package com.michaldrabik.ui_lists.details

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.michaldrabik.common.Mode
import com.michaldrabik.repository.images.MovieImagesProvider
import com.michaldrabik.repository.images.ShowImagesProvider
import com.michaldrabik.ui_base.utilities.events.Event
import com.michaldrabik.ui_base.utilities.extensions.SUBSCRIBE_STOP_TIMEOUT
import com.michaldrabik.ui_base.utilities.extensions.combine
import com.michaldrabik.ui_base.utilities.extensions.rethrowCancellation
import com.michaldrabik.ui_lists.details.cases.ListDetailsItemsCase
import com.michaldrabik.ui_lists.details.cases.ListDetailsMainCase
import com.michaldrabik.ui_lists.details.cases.ListDetailsSortCase
import com.michaldrabik.ui_lists.details.cases.ListDetailsTipsCase
import com.michaldrabik.ui_lists.details.cases.ListDetailsTranslationsCase
import com.michaldrabik.ui_lists.details.recycler.ListDetailsItem
import com.michaldrabik.ui_base.common.ListViewMode
import com.michaldrabik.ui_model.CustomList
import com.michaldrabik.ui_model.ImageType
import com.michaldrabik.ui_model.SortOrder
import com.michaldrabik.ui_model.SortType
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class ListDetailsViewModel @Inject constructor(
  private val mainCase: ListDetailsMainCase,
  private val itemsCase: ListDetailsItemsCase,
  private val translationsCase: ListDetailsTranslationsCase,
  private val sortCase: ListDetailsSortCase,
  private val tipsCase: ListDetailsTipsCase,
  private val showImagesProvider: ShowImagesProvider,
  private val movieImagesProvider: MovieImagesProvider,
) : ViewModel() {

  private val listDetailsState = MutableStateFlow<CustomList?>(null)
  private val listItemsState = MutableStateFlow<List<ListDetailsItem>?>(null)
  private val listDeleteState = MutableStateFlow<Event<Boolean>?>(null)
  private val manageModeState = MutableStateFlow(false)
  private val quickRemoveState = MutableStateFlow(false)
  private val scrollState = MutableStateFlow<Event<Boolean>?>(null)
  private val loadingState = MutableStateFlow(false)
  private val filtersVisibleState = MutableStateFlow(false)
  private val viewModeState = MutableStateFlow(ListViewMode.LIST_NORMAL)

  fun loadDetails(listId: Long) {
    viewModelScope.launch {
      loadingState.value = true
      try {
        val details = mainCase.loadDetails(listId)
        listDetailsState.value = details
        val items = itemsCase.loadItems(details)
        listItemsState.value = items.first
        quickRemoveState.value = mainCase.isQuickRemoveEnabled(details)
      } catch (e: Throwable) {
        rethrowCancellation(e)
      } finally {
        loadingState.value = false
      }
    }
  }

  fun loadMissingImage(item: ListDetailsItem, force: Boolean = false) {
    viewModelScope.launch {
      val image = if (item.isShow()) {
        showImagesProvider.findCachedImage(item.show!!, ImageType.POSTER)
      } else {
        movieImagesProvider.findCachedImage(item.movie!!, ImageType.POSTER)
      }
      if (image != null) {
        updateItem(item.copy(image = image))
      }
    }
  }

  fun loadMissingTranslation(item: ListDetailsItem) {
    viewModelScope.launch {
      val translation = translationsCase.loadTranslation(item, false)
      if (translation != null) {
        updateItem(item.copy(translation = translation))
      }
    }
  }

  fun setReorderMode(listId: Long, enabled: Boolean) {
    manageModeState.value = enabled
    if (!enabled) {
      updateRanks(listId, listItemsState.value ?: emptyList())
    }
  }

  fun updateRanks(listId: Long, items: List<ListDetailsItem>) {
    viewModelScope.launch {
      val updatedItems = mainCase.updateRanks(listId, items)
      listItemsState.value = updatedItems
    }
  }

  fun setSortOrder(listId: Long, sort: SortOrder, type: SortType) {
    viewModelScope.launch {
      sortCase.setSortOrder(listId, sort, type)
      loadDetails(listId)
    }
  }

  fun setFilterTypes(listId: Long, types: List<Mode>) {
    viewModelScope.launch {
      sortCase.setFilterTypes(listId, types)
      loadDetails(listId)
    }
  }

  fun deleteList(listId: Long, removeFromTrakt: Boolean) {
    viewModelScope.launch {
      mainCase.deleteList(listId)
      listDeleteState.value = Event(true)
    }
  }

  fun deleteListItem(listId: Long, item: ListDetailsItem) {
    viewModelScope.launch {
      val type = if (item.isShow()) Mode.SHOWS else Mode.MOVIES
      itemsCase.deleteListItem(listId, item.getTraktId(), type)
      val currentItems = listItemsState.value?.toMutableList() ?: return@launch
      currentItems.remove(item)
      listItemsState.value = currentItems
    }
  }

  private fun updateItem(item: ListDetailsItem) {
    val currentItems = listItemsState.value?.toMutableList() ?: return
    val index = currentItems.indexOfFirst { it.id == item.id }
    if (index != -1) {
      currentItems[index] = item
      listItemsState.value = currentItems
    }
  }

  val uiState = combine(
    listDetailsState,
    listItemsState,
    listDeleteState,
    manageModeState,
    quickRemoveState,
    scrollState,
    loadingState,
    filtersVisibleState,
    viewModeState,
  ) { listDetails, listItems, listDelete, manageMode, quickRemove, scroll, loading, filtersVisible, viewMode ->
    ListDetailsUiState(
      listDetails = listDetails,
      listItems = listItems,
      deleteEvent = listDelete,
      isManageMode = manageMode,
      isQuickRemoveEnabled = quickRemove,
      resetScroll = scroll,
      isLoading = loading,
      isFiltersVisible = filtersVisible,
      viewMode = viewMode,
    )
  }.stateIn(
    scope = viewModelScope,
    started = SharingStarted.WhileSubscribed(SUBSCRIBE_STOP_TIMEOUT),
    initialValue = ListDetailsUiState(),
  )
}
