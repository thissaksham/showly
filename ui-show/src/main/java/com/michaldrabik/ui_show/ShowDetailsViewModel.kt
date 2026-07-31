package com.michaldrabik.ui_show

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.michaldrabik.repository.images.ShowImagesProvider
import com.michaldrabik.repository.settings.SettingsRepository
import com.michaldrabik.repository.utilities.LocalIdResolver
import com.michaldrabik.ui_base.events.EventsManager
import com.michaldrabik.ui_base.events.ReloadData
import com.michaldrabik.ui_base.utilities.events.Event
import com.michaldrabik.ui_base.utilities.events.MessageEvent
import com.michaldrabik.ui_base.utilities.extensions.SUBSCRIBE_STOP_TIMEOUT
import com.michaldrabik.ui_base.utilities.extensions.combine
import com.michaldrabik.ui_base.utilities.extensions.rethrowCancellation
import com.michaldrabik.ui_model.IdTmdb
import com.michaldrabik.ui_model.IdTrakt
import com.michaldrabik.ui_model.Image
import com.michaldrabik.ui_model.ImageType
import com.michaldrabik.ui_model.RatingState
import com.michaldrabik.ui_model.Show
import com.michaldrabik.ui_model.SpoilersSettings
import com.michaldrabik.ui_model.Translation
import com.michaldrabik.ui_show.cases.ShowDetailsDroppedCase
import com.michaldrabik.ui_show.cases.ShowDetailsListsCase
import com.michaldrabik.ui_show.cases.ShowDetailsMainCase
import com.michaldrabik.ui_show.cases.ShowDetailsMyShowsCase
import com.michaldrabik.ui_show.cases.ShowDetailsTranslationCase
import com.michaldrabik.ui_show.cases.ShowDetailsWatchlistCase
import com.michaldrabik.ui_show.helpers.ShowDetailsMeta
import com.michaldrabik.ui_show.sections.ratings.cases.ShowDetailsRatingCase
import com.michaldrabik.ui_show.sections.seasons.helpers.SeasonsCache
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class ShowDetailsViewModel @Inject constructor(
  private val mainCase: ShowDetailsMainCase,
  private val translationCase: ShowDetailsTranslationCase,
  private val ratingsCase: ShowDetailsRatingCase,
  private val watchlistCase: ShowDetailsWatchlistCase,
  private val droppedCase: ShowDetailsDroppedCase,
  private val myShowsCase: ShowDetailsMyShowsCase,
  private val listsCase: ShowDetailsListsCase,
  private val settingsRepository: SettingsRepository,
  private val seasonsCache: SeasonsCache,
  private val imagesProvider: ShowImagesProvider,
  private val eventsManager: EventsManager,
) : ViewModel() {

  lateinit var show: Show

  private val _parentEvents = MutableSharedFlow<ShowDetailsEvent<*>>()
  val parentEvents: SharedFlow<ShowDetailsEvent<*>> = _parentEvents

  private val showState = MutableStateFlow<Show?>(null)
  val parentShowState: SharedFlow<Show?> = showState

  private val showLoadingState = MutableStateFlow<Boolean?>(null)
  private val imageState = MutableStateFlow<Image?>(null)
  private val followedState = MutableStateFlow<ShowDetailsUiState.FollowedState?>(null)
  val parentFollowedState: SharedFlow<ShowDetailsUiState.FollowedState?> = followedState
  private val ratingState = MutableStateFlow<RatingState?>(null)
  private val translationState = MutableStateFlow<Translation?>(null)
  private val listsCountState = MutableStateFlow(0)
  private val spoilersState = MutableStateFlow<SpoilersSettings?>(null)
  private val metaState = MutableStateFlow<ShowDetailsMeta?>(null)

  private val messageChannel = MutableSharedFlow<MessageEvent>()
  val messageFlow: SharedFlow<MessageEvent> = messageChannel

  private val eventChannel = MutableSharedFlow<Event<*>>()
  val eventFlow: SharedFlow<Event<*>> = eventChannel

  fun loadDetails(showId: IdTrakt, force: Boolean = false) {
    viewModelScope.launch {
      showLoadingState.value = true
      try {
        // A negative id carries a TMDB id. Decode it the same way it was minted -
        // plain negation is off by one and lands on a different show.
        val targetId = LocalIdResolver
          .tmdbIdOf(showId.id)
          ?.let { mainCase.resolveTraktId(it) }
          ?: showId
        val result = mainCase.loadDetails(targetId, force)
        show = result
        showState.value = result
        loadBackgroundImage(result)
        loadTranslation(result)
        loadListsCount(result)
        loadUserRating()

        val isFollowed = myShowsCase.isMyShows(result)
        val isWatchlist = watchlistCase.isWatchlist(result)
        val isDropped = droppedCase.isDropped(result)

        followedState.value = ShowDetailsUiState.FollowedState(
          isMyShows = isFollowed,
          isWatchlist = isWatchlist,
          isDropped = isDropped,
          withAnimation = false,
        )
        metaState.value = ShowDetailsMeta(isSignedIn = false)
        spoilersState.value = settingsRepository.spoilers.getAll()
      } catch (e: Throwable) {
        rethrowCancellation(e)
      } finally {
        showLoadingState.value = false
      }
    }
  }

  private fun loadBackgroundImage(show: Show?) {
    if (show == null) return
    viewModelScope.launch {
      imageState.value = imagesProvider.findCachedImage(show, ImageType.FANART)
    }
  }

  private fun loadTranslation(show: Show) {
    viewModelScope.launch {
      translationState.value = translationCase.loadTranslation(show)
    }
  }

  fun loadListsCount(show: Show? = null) {
    val targetShow = show ?: this.show
    viewModelScope.launch {
      listsCountState.value = listsCase.getListsCount(targetShow)
    }
  }

  fun loadUserRating() {
    viewModelScope.launch {
      val rating = ratingsCase.loadRating(show)
      ratingState.value = RatingState(userRating = rating, rateLoading = false)
    }
  }

  fun addFollowedShow() {
    viewModelScope.launch {
      myShowsCase.addToMyShows(show, emptyList(), emptyList())
      followedState.value = followedState.value?.copy(isMyShows = true, isWatchlist = false, isDropped = false)
      messageChannel.emit(MessageEvent.Info(com.michaldrabik.ui_base.R.string.textAddedToMyShows))
      eventsManager.sendEvent(ReloadData)
    }
  }

  fun addWatchlistShow() {
    viewModelScope.launch {
      watchlistCase.addToWatchlist(show)
      followedState.value = followedState.value?.copy(isWatchlist = true, isMyShows = false, isDropped = false)
      messageChannel.emit(MessageEvent.Info(com.michaldrabik.ui_base.R.string.textAddedToWatchlist))
      eventsManager.sendEvent(ReloadData)
    }
  }

  fun addDroppedShow() {
    viewModelScope.launch {
      droppedCase.addToDropped(show, false)
      followedState.value = followedState.value?.copy(isDropped = true, isMyShows = false, isWatchlist = false)
      messageChannel.emit(MessageEvent.Info(com.michaldrabik.ui_base.R.string.textAddedToDropped))
      eventsManager.sendEvent(ReloadData)
    }
  }

  fun removeFromFollowed() {
    viewModelScope.launch {
      val state = followedState.value ?: return@launch
      when {
        state.isMyShows -> {
          myShowsCase.removeFromMyShows(show, false)
          followedState.value = state.copy(isMyShows = false, withAnimation = true)
          messageChannel.emit(MessageEvent.Info(com.michaldrabik.ui_base.R.string.textRemovedFromMyShows))
        }
        state.isWatchlist -> {
          watchlistCase.removeFromWatchlist(show)
          followedState.value = state.copy(isWatchlist = false, withAnimation = true)
          messageChannel.emit(MessageEvent.Info(com.michaldrabik.ui_base.R.string.textRemovedFromWatchlist))
        }
        state.isDropped -> {
          droppedCase.removeFromDropped(show)
          followedState.value = state.copy(isDropped = false, withAnimation = true)
          messageChannel.emit(MessageEvent.Info(com.michaldrabik.ui_base.R.string.textRemovedFromDropped))
        }
      }
      eventsManager.sendEvent(ReloadData)
    }
  }

  fun removeMalformedShow(showId: IdTrakt) {
    // Removed.
  }

  fun refreshSeasons() {
    viewModelScope.launch {
      _parentEvents.emit(ShowDetailsEvent.RefreshSeasons)
    }
  }

  fun checkSeasonsLoaded(): Boolean = seasonsCache.hasSeasons(show.ids.trakt)

  fun refresh() {
    loadDetails(show.ids.trakt, force = true)
  }

  val uiState = combine(
    showState,
    showLoadingState,
    imageState,
    followedState,
    ratingState,
    translationState,
    metaState,
    spoilersState,
    listsCountState,
  ) { show, showLoading, image, followedState, ratingState, translation, meta, spoilers, listsCount ->
    ShowDetailsUiState(
      show = show,
      showLoading = showLoading ?: false,
      image = image,
      followedState = followedState,
      ratingState = ratingState,
      translation = translation,
      meta = meta,
      spoilers = spoilers,
      listsCount = listsCount,
    )
  }.stateIn(
    scope = viewModelScope,
    started = SharingStarted.WhileSubscribed(SUBSCRIBE_STOP_TIMEOUT),
    initialValue = ShowDetailsUiState(),
  )
}
