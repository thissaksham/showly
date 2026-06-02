package com.michaldrabik.ui_base.common.sheets.context_menu.show

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.michaldrabik.repository.images.ShowImagesProvider
import com.michaldrabik.repository.OnHoldItemsRepository
import com.michaldrabik.ui_base.common.sheets.context_menu.show.cases.ShowContextMenuHiddenCase
import com.michaldrabik.ui_base.common.sheets.context_menu.show.cases.ShowContextMenuLoadItemCase
import com.michaldrabik.ui_base.common.sheets.context_menu.show.cases.ShowContextMenuMyShowsCase
import com.michaldrabik.ui_base.common.sheets.context_menu.show.cases.ShowContextMenuPinnedCase
import com.michaldrabik.ui_base.common.sheets.context_menu.show.cases.ShowContextMenuWatchlistCase
import com.michaldrabik.ui_base.common.sheets.context_menu.show.helpers.ShowContextItem
import com.michaldrabik.ui_base.utilities.events.Event
import com.michaldrabik.ui_base.utilities.events.FinishUiEvent
import com.michaldrabik.ui_base.utilities.events.MessageEvent
import com.michaldrabik.ui_base.utilities.extensions.SUBSCRIBE_STOP_TIMEOUT
import com.michaldrabik.ui_base.utilities.extensions.rethrowCancellation
import com.michaldrabik.ui_model.IdTrakt
import com.michaldrabik.ui_model.ImageType
import com.michaldrabik.ui_model.Show
import com.michaldrabik.ui_model.Ids
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class ShowContextMenuViewModel @Inject constructor(
  private val loadItemCase: ShowContextMenuLoadItemCase,
  private val myShowsCase: ShowContextMenuMyShowsCase,
  private val watchlistCase: ShowContextMenuWatchlistCase,
  private val hiddenCase: ShowContextMenuHiddenCase,
  private val pinnedCase: ShowContextMenuPinnedCase,
  private val onHoldCase: OnHoldItemsRepository,
  private val imagesProvider: ShowImagesProvider,
) : ViewModel() {

  var showIdValue: Long = -1L

  private val loadingState = MutableStateFlow(false)
  private val loadingSecondaryState = MutableStateFlow(false)
  private val itemState = MutableStateFlow<ShowContextItem?>(null)

  private val eventChannel = Channel<Event<*>>(Channel.BUFFERED)
  val eventFlow = eventChannel.receiveAsFlow()

  private val messageChannel = Channel<MessageEvent>(Channel.BUFFERED)
  val messageFlow = messageChannel.receiveAsFlow()

  fun loadShow(idTrakt: IdTrakt) {
    showIdValue = idTrakt.id
    viewModelScope.launch {
      loadingState.value = true
      try {
        val item = loadItemCase.loadItem(idTrakt)
        itemState.value = item
        preloadImage()
      } catch (e: Throwable) {
        onError(e)
      } finally {
        loadingState.value = false
      }
    }
  }

  fun moveToMyShows() {
    viewModelScope.launch {
      loadingSecondaryState.value = true
      try {
        myShowsCase.moveToMyShows(IdTrakt(showIdValue))
        loadShow(IdTrakt(showIdValue))
      } catch (e: Throwable) {
        onError(e)
      } finally {
        loadingSecondaryState.value = false
      }
    }
  }

  fun removeFromMyShows() {
    viewModelScope.launch {
      loadingSecondaryState.value = true
      try {
        myShowsCase.removeFromMyShows(IdTrakt(showIdValue), false)
        loadShow(IdTrakt(showIdValue))
      } catch (e: Throwable) {
        onError(e)
      } finally {
        loadingSecondaryState.value = false
      }
    }
  }

  fun moveToWatchlist() {
    viewModelScope.launch {
      loadingSecondaryState.value = true
      try {
        watchlistCase.moveToWatchlist(IdTrakt(showIdValue), false)
        loadShow(IdTrakt(showIdValue))
      } catch (e: Throwable) {
        onError(e)
      } finally {
        loadingSecondaryState.value = false
      }
    }
  }

  fun removeFromWatchlist() {
    viewModelScope.launch {
      loadingSecondaryState.value = true
      try {
        watchlistCase.removeFromWatchlist(IdTrakt(showIdValue))
        loadShow(IdTrakt(showIdValue))
      } catch (e: Throwable) {
        onError(e)
      } finally {
        loadingSecondaryState.value = false
      }
    }
  }

  fun moveToHidden() {
    viewModelScope.launch {
      loadingSecondaryState.value = true
      try {
        hiddenCase.moveToHidden(IdTrakt(showIdValue), false)
        loadShow(IdTrakt(showIdValue))
      } catch (e: Throwable) {
        onError(e)
      } finally {
        loadingSecondaryState.value = false
      }
    }
  }

  fun removeFromHidden() {
    viewModelScope.launch {
      loadingSecondaryState.value = true
      try {
        hiddenCase.removeFromHidden(IdTrakt(showIdValue))
        loadShow(IdTrakt(showIdValue))
      } catch (e: Throwable) {
        onError(e)
      } finally {
        loadingSecondaryState.value = false
      }
    }
  }

  fun addToTopPinned() {
    viewModelScope.launch {
      pinnedCase.addToTopPinned(IdTrakt(showIdValue))
      loadShow(IdTrakt(showIdValue))
    }
  }

  fun removeFromTopPinned() {
    viewModelScope.launch {
      pinnedCase.removeFromTopPinned(IdTrakt(showIdValue))
      loadShow(IdTrakt(showIdValue))
    }
  }

  fun addToOnHoldPinned() {
    viewModelScope.launch {
      onHoldCase.addItem(IdTrakt(showIdValue))
      loadShow(IdTrakt(showIdValue))
    }
  }

  fun removeFromOnHoldPinned() {
    viewModelScope.launch {
      onHoldCase.removeItem(Show.EMPTY.copy(ids = Ids.EMPTY.copy(trakt = IdTrakt(showIdValue))))
      loadShow(IdTrakt(showIdValue))
    }
  }

  private fun preloadImage() {
    val item = itemState.value ?: return
    viewModelScope.launch {
      imagesProvider.findCachedImage(item.show, ImageType.POSTER)
    }
  }

  private suspend fun onError(error: Throwable) {
    loadingState.value = false
    loadingSecondaryState.value = false
    messageChannel.send(MessageEvent.Error(com.michaldrabik.ui_base.R.string.errorGeneral))
    rethrowCancellation(error)
  }

  val uiState = combine(
    loadingState,
    loadingSecondaryState,
    itemState,
  ) { loading, loadingSecondary, item ->
    ShowContextMenuUiState(
      isLoading = loading,
      isLoadingSecondary = loadingSecondary,
      item = item,
    )
  }.stateIn(
    scope = viewModelScope,
    started = SharingStarted.WhileSubscribed(SUBSCRIBE_STOP_TIMEOUT),
    initialValue = ShowContextMenuUiState(),
  )
}
