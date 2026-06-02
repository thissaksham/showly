package com.michaldrabik.ui_base.common.sheets.context_menu.movie

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.michaldrabik.ui_base.common.sheets.context_menu.movie.cases.MovieContextMenuHiddenCase
import com.michaldrabik.ui_base.common.sheets.context_menu.movie.cases.MovieContextMenuLoadItemCase
import com.michaldrabik.ui_base.common.sheets.context_menu.movie.cases.MovieContextMenuMyMoviesCase
import com.michaldrabik.ui_base.common.sheets.context_menu.movie.cases.MovieContextMenuPinnedCase
import com.michaldrabik.ui_base.common.sheets.context_menu.movie.cases.MovieContextMenuWatchlistCase
import com.michaldrabik.ui_base.common.sheets.context_menu.movie.helpers.MovieContextItem
import com.michaldrabik.ui_base.utilities.events.Event
import com.michaldrabik.ui_base.utilities.events.FinishUiEvent
import com.michaldrabik.ui_base.utilities.events.MessageEvent
import com.michaldrabik.ui_base.utilities.extensions.SUBSCRIBE_STOP_TIMEOUT
import com.michaldrabik.ui_base.utilities.extensions.rethrowCancellation
import com.michaldrabik.ui_model.IdTrakt
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.ZonedDateTime
import javax.inject.Inject

@HiltViewModel
class MovieContextMenuViewModel @Inject constructor(
  private val loadItemCase: MovieContextMenuLoadItemCase,
  private val myMoviesCase: MovieContextMenuMyMoviesCase,
  private val watchlistCase: MovieContextMenuWatchlistCase,
  private val hiddenCase: MovieContextMenuHiddenCase,
  private val pinnedCase: MovieContextMenuPinnedCase,
) : ViewModel() {

  var movieIdValue: Long = -1L

  private val loadingState = MutableStateFlow(false)
  private val itemState = MutableStateFlow<MovieContextItem?>(null)

  private val eventChannel = Channel<Event<*>>(Channel.BUFFERED)
  val eventFlow = eventChannel.receiveAsFlow()

  private val messageChannel = Channel<MessageEvent>(Channel.BUFFERED)
  val messageFlow = messageChannel.receiveAsFlow()

  fun loadMovie(idTrakt: IdTrakt) {
    movieIdValue = idTrakt.id
    viewModelScope.launch {
      loadingState.value = true
      try {
        val item = loadItemCase.loadItem(idTrakt)
        itemState.value = item
      } catch (e: Throwable) {
        onError(e)
      } finally {
        loadingState.value = false
      }
    }
  }

  fun moveToMyMovies(isCustomDateSelected: Boolean, customDate: ZonedDateTime?) {
    viewModelScope.launch {
      loadingState.value = true
      try {
        myMoviesCase.moveToMyMovies(IdTrakt(movieIdValue), customDate)
        loadMovie(IdTrakt(movieIdValue))
      } catch (e: Throwable) {
        onError(e)
      } finally {
        loadingState.value = false
      }
    }
  }

  fun removeFromMyMovies() {
    viewModelScope.launch {
      loadingState.value = true
      try {
        myMoviesCase.removeFromMyMovies(IdTrakt(movieIdValue))
        loadMovie(IdTrakt(movieIdValue))
      } catch (e: Throwable) {
        onError(e)
      } finally {
        loadingState.value = false
      }
    }
  }

  fun moveToWatchlist() {
    viewModelScope.launch {
      loadingState.value = true
      try {
        watchlistCase.moveToWatchlist(IdTrakt(movieIdValue))
        loadMovie(IdTrakt(movieIdValue))
      } catch (e: Throwable) {
        onError(e)
      } finally {
        loadingState.value = false
      }
    }
  }

  fun removeFromWatchlist() {
    viewModelScope.launch {
      loadingState.value = true
      try {
        watchlistCase.removeFromWatchlist(IdTrakt(movieIdValue))
        loadMovie(IdTrakt(movieIdValue))
      } catch (e: Throwable) {
        onError(e)
      } finally {
        loadingState.value = false
      }
    }
  }

  fun moveToHidden() {
    viewModelScope.launch {
      loadingState.value = true
      try {
        hiddenCase.moveToHidden(IdTrakt(movieIdValue))
        loadMovie(IdTrakt(movieIdValue))
      } catch (e: Throwable) {
        onError(e)
      } finally {
        loadingState.value = false
      }
    }
  }

  fun removeFromHidden() {
    viewModelScope.launch {
      loadingState.value = true
      try {
        hiddenCase.removeFromHidden(IdTrakt(movieIdValue))
        loadMovie(IdTrakt(movieIdValue))
      } catch (e: Throwable) {
        onError(e)
      } finally {
        loadingState.value = false
      }
    }
  }

  fun addToTopPinned() {
    viewModelScope.launch {
      pinnedCase.addToTopPinned(IdTrakt(movieIdValue))
      loadMovie(IdTrakt(movieIdValue))
    }
  }

  fun removeFromTopPinned() {
    viewModelScope.launch {
      pinnedCase.removeFromTopPinned(IdTrakt(movieIdValue))
      loadMovie(IdTrakt(movieIdValue))
    }
  }

  private suspend fun onError(error: Throwable) {
    loadingState.value = false
    messageChannel.send(MessageEvent.Error(com.michaldrabik.ui_base.R.string.errorGeneral))
    rethrowCancellation(error)
  }

  val uiState = combine(
    loadingState,
    itemState,
  ) { loading, item ->
    MovieContextMenuUiState(
      isLoading = loading,
      item = item,
    )
  }.stateIn(
    scope = viewModelScope,
    started = SharingStarted.WhileSubscribed(SUBSCRIBE_STOP_TIMEOUT),
    initialValue = MovieContextMenuUiState(),
  )
}
