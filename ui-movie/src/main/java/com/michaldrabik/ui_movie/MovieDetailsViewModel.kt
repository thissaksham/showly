package com.michaldrabik.ui_movie

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.michaldrabik.common.Mode
import com.michaldrabik.repository.TranslationsRepository
import com.michaldrabik.repository.images.MovieImagesProvider
import com.michaldrabik.repository.settings.SettingsRepository
import com.michaldrabik.ui_base.dates.DateFormatProvider
import com.michaldrabik.ui_base.notifications.AnnouncementManager
import com.michaldrabik.ui_base.utilities.events.Event
import com.michaldrabik.ui_base.utilities.events.MessageEvent
import com.michaldrabik.ui_base.utilities.extensions.SUBSCRIBE_STOP_TIMEOUT
import com.michaldrabik.ui_base.utilities.extensions.combine
import com.michaldrabik.ui_base.utilities.extensions.rethrowCancellation
import com.michaldrabik.ui_movie.cases.MovieDetailsHiddenCase
import com.michaldrabik.ui_movie.cases.MovieDetailsListsCase
import com.michaldrabik.ui_movie.cases.MovieDetailsMainCase
import com.michaldrabik.ui_movie.cases.MovieDetailsMyMoviesCase
import com.michaldrabik.ui_movie.sections.ratings.cases.MovieDetailsRatingCase
import com.michaldrabik.ui_movie.cases.MovieDetailsTranslationCase
import com.michaldrabik.ui_movie.cases.MovieDetailsWatchlistCase
import com.michaldrabik.ui_movie.helpers.MovieDetailsMeta
import com.michaldrabik.ui_model.IdTrakt
import com.michaldrabik.ui_model.Image
import com.michaldrabik.ui_model.ImageType
import com.michaldrabik.ui_model.Movie
import com.michaldrabik.ui_model.RatingState
import com.michaldrabik.ui_model.SpoilersSettings
import com.michaldrabik.ui_model.Translation
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.ZonedDateTime
import javax.inject.Inject

@HiltViewModel
class MovieDetailsViewModel @Inject constructor(
  private val mainCase: MovieDetailsMainCase,
  private val translationCase: MovieDetailsTranslationCase,
  private val myMoviesCase: MovieDetailsMyMoviesCase,
  private val ratingsCase: MovieDetailsRatingCase,
  private val watchlistCase: MovieDetailsWatchlistCase,
  private val hiddenCase: MovieDetailsHiddenCase,
  private val listsCase: MovieDetailsListsCase,
  private val settingsRepository: SettingsRepository,
  private val imagesProvider: MovieImagesProvider,
  private val dateFormatProvider: DateFormatProvider,
  private val announcementManager: AnnouncementManager,
) : ViewModel() {

  lateinit var movie: Movie

  private val movieState = MutableStateFlow<Movie?>(null)
  val parentMovieState: SharedFlow<Movie?> = movieState
  private val movieLoadingState = MutableStateFlow<Boolean?>(null)
  private val imageState = MutableStateFlow<Image?>(null)
  private val followedState = MutableStateFlow<MovieDetailsUiState.FollowedState?>(null)
  val parentFollowedState: SharedFlow<MovieDetailsUiState.FollowedState?> = followedState
  private val ratingState = MutableStateFlow<RatingState?>(null)
  private val translationState = MutableStateFlow<Translation?>(null)
  private val metaState = MutableStateFlow<MovieDetailsMeta?>(null)
  private val spoilersState = MutableStateFlow<SpoilersSettings?>(null)
  private val listsCountState = MutableStateFlow(0)

  private val messageChannel = MutableSharedFlow<MessageEvent>()
  val messageFlow: SharedFlow<MessageEvent> = messageChannel

  private val eventChannel = MutableSharedFlow<Event<*>>()
  val eventFlow: SharedFlow<Event<*>> = eventChannel

  fun loadDetails(movieId: IdTrakt) {
    viewModelScope.launch {
      movieLoadingState.value = true
      try {
        val result = mainCase.loadDetails(movieId)
        movie = result
        movieState.value = result
        loadBackgroundImage(result)
        loadTranslation()
        loadListsCount(result)
        loadUserRating()

        val isFollowed = myMoviesCase.getMyMovie(result) != null
        val isWatchlist = watchlistCase.isWatchlist(result)
        val isHidden = hiddenCase.isHidden(result)

        followedState.value = MovieDetailsUiState.FollowedState(
          isMyMovie = isFollowed,
          isWatchlist = isWatchlist,
          isHidden = isHidden,
          withAnimation = false,
          watchedAt = null,
        )
        metaState.value = MovieDetailsMeta(
          dateFormat = dateFormatProvider.loadShortDayFormat(),
          commentsDateFormat = dateFormatProvider.loadFullHourFormat(),
          watchedAtDateFormat = dateFormatProvider.loadFullDayFormat(),
          isSignedIn = false,
        )
        spoilersState.value = settingsRepository.spoilers.getAll()
      } catch (e: Throwable) {
        rethrowCancellation(e)
      } finally {
        movieLoadingState.value = false
      }
    }
  }

  private fun loadBackgroundImage(movie: Movie?) {
    if (movie == null) return
    viewModelScope.launch {
      imageState.value = imagesProvider.findCachedImage(movie, ImageType.FANART)
    }
  }

  private fun loadTranslation() {
    viewModelScope.launch {
      translationState.value = translationCase.loadTranslation(movie)
    }
  }

  fun loadListsCount(movie: Movie? = null) {
    val targetMovie = movie ?: this.movie
    viewModelScope.launch {
      listsCountState.value = listsCase.countLists(targetMovie)
    }
  }

  fun loadUserRating() {
    viewModelScope.launch {
      val rating = ratingsCase.loadRating(movie)
      ratingState.value = RatingState(userRating = rating, rateLoading = false)
    }
  }

  fun addToMyMovies(isCustomDateSelected: Boolean, customDate: ZonedDateTime?) {
    viewModelScope.launch {
      myMoviesCase.addToMyMovies(movie, customDate)
      followedState.value = followedState.value?.copy(isMyMovie = true, isWatchlist = false, isHidden = false)
      messageChannel.emit(MessageEvent.Info(com.michaldrabik.ui_base.R.string.textAddedToMyMovies))
    }
  }

  fun addToWatchlist() {
    viewModelScope.launch {
      watchlistCase.addToWatchlist(movie)
      followedState.value = followedState.value?.copy(isWatchlist = true, isMyMovie = false, isHidden = false)
      messageChannel.emit(MessageEvent.Info(com.michaldrabik.ui_base.R.string.textAddedToWatchlist))
    }
  }

  fun addToHidden() {
    viewModelScope.launch {
      hiddenCase.addToHidden(movie)
      followedState.value = followedState.value?.copy(isHidden = true, isMyMovie = false, isWatchlist = false)
      messageChannel.emit(MessageEvent.Info(com.michaldrabik.ui_base.R.string.textAddedToHidden))
    }
  }

  fun removeFromMyMovies() {
    viewModelScope.launch {
      myMoviesCase.removeFromMyMovies(movie)
      followedState.value = followedState.value?.copy(isMyMovie = false)
      messageChannel.emit(MessageEvent.Info(com.michaldrabik.ui_base.R.string.textRemovedFromMyMovies))
    }
  }

  fun removeMalformedMovie(movieId: IdTrakt) {
    // Removed.
  }

  val uiState = combine(
    movieState,
    movieLoadingState,
    imageState,
    followedState,
    ratingState,
    translationState,
    metaState,
    spoilersState,
    listsCountState,
  ) { movie, movieLoading, image, followedState, ratingState, translation, meta, spoilers, listsCount ->
    MovieDetailsUiState(
      movie = movie,
      movieLoading = movieLoading ?: false,
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
    initialValue = MovieDetailsUiState(),
  )
}
