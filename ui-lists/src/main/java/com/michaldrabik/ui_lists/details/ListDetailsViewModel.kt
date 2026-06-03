package com.michaldrabik.ui_lists.details

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
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
import com.michaldrabik.ui_model.CustomList
import com.michaldrabik.ui_model.SortOrder
import com.michaldrabik.ui_model.SortType
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
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

  fun loadDetails(listId: Long) {
    viewModelScope.launch {
      loadingState.value = true
      try {
        val listDetails = mainCase.loadDetails(listId)
        listDetailsState.value = listDetails

        val (items, _) = itemsCase.loadItems(listDetails)
        listItemsState.value = items
      } catch (e: Throwable) {
        rethrowCancellation(e)
      } finally {
        loadingState.value = false
      }
    }
  }

  fun loadMissingImage(
    item: ListDetailsItem,
    force: Boolean,
  ) {
    viewModelScope.launch {
      updateItem(item.copy(isLoading = true))
      try {
        val image = if (item.isShow()) {
          showImagesProvider.loadRemoteImage(item.requireShow(), item.image.type, force)
        } else {
          movieImagesProvider.loadRemoteImage(item.requireMovie(), item.image.type, force)
        }
        updateItem(item.copy(isLoading = false, image = image))
      } catch (t: Throwable) {
        updateItem(item.copy(isLoading = false))
        rethrowCancellation(t)
      }
    }
  }

  fun loadMissingTranslation(item: ListDetailsItem) {
    if (item.translation != null) return
    viewModelScope.launch {
      try {
        val translation = translationsCase.loadTranslation(item, false)
        updateItem(item.copy(translation = translation))
      } catch (e: Throwable) {
        rethrowCancellation(e)
      }
    }
  }

  fun setReorderMode(
    listId: Long,
    reorderMode: Boolean,
  ) {
    manageModeState.value = reorderMode
  }

  fun updateRanks(
    listId: Long,
    items: List<ListDetailsItem>,
  ) {
    viewModelScope.launch {
      try {
        mainCase.updateRanks(listId, items)
      } catch (e: Throwable) {
        rethrowCancellation(e)
      }
    }
  }

  fun setSortOrder(
    listId: Long,
    sortOrder: SortOrder,
    sortType: SortType,
  ) {
    viewModelScope.launch {
      try {
        sortCase.setSortOrder(listId, sortOrder, sortType)
        loadDetails(listId)
      } catch (e: Throwable) {
        rethrowCancellation(e)
      }
    }
  }

  fun setFilterTypes(
    listId: Long,
    filterTypes: List<com.michaldrabik.common.Mode>,
  ) {
    viewModelScope.launch {
      try {
        sortCase.setFilterTypes(listId, filterTypes)
        loadDetails(listId)
      } catch (e: Throwable) {
        rethrowCancellation(e)
      }
    }
  }

  fun deleteList(
    listId: Long,
    deleteOnTrakt: Boolean,
  ) {
    viewModelScope.launch {
      try {
        mainCase.deleteList(listId)
        listDeleteState.value = Event(true)
      } catch (e: Throwable) {
        rethrowCancellation(e)
      }
    }
  }

  fun deleteListItem(
    listId: Long,
    item: ListDetailsItem,
  ) {
    viewModelScope.launch {
      try {
        itemsCase.deleteListItem(listId, item.getTraktId(), if (item.isShow()) com.michaldrabik.common.Mode.SHOWS else com.michaldrabik.common.Mode.MOVIES)
        loadDetails(listId)
      } catch (e: Throwable) {
        rethrowCancellation(e)
      }
    }
  }

  private fun updateItem(new: ListDetailsItem) {
    val items = listItemsState.value?.toMutableList() ?: return
    val index = items.indexOfFirst { it.id == new.id }
    if (index != -1) {
      items[index] = new
      listItemsState.value = items
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
  ) { listDetails, listItems, listDelete, manageMode, quickRemove, scroll, loading, filtersVisible ->
    ListDetailsUiState(
      listDetails = listDetails,
      listItems = listItems,
      deleteEvent = listDelete,
      isManageMode = manageMode,
      isQuickRemoveEnabled = quickRemove,
      resetScroll = scroll,
      isLoading = loading,
      isFiltersVisible = filtersVisible,
    )
  }.stateIn(
    scope = viewModelScope,
    started = SharingStarted.WhileSubscribed(SUBSCRIBE_STOP_TIMEOUT),
    initialValue = ListDetailsUiState(),
  )
}
