package com.michaldrabik.ui_episodes.details

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.michaldrabik.repository.RatingsRepository
import com.michaldrabik.repository.TranslationsRepository
import com.michaldrabik.repository.images.EpisodeImagesProvider
import com.michaldrabik.repository.settings.SettingsRepository
import com.michaldrabik.repository.settings.SettingsSpoilersRepository
import com.michaldrabik.ui_base.dates.DateFormatProvider
import com.michaldrabik.ui_base.utilities.extensions.SUBSCRIBE_STOP_TIMEOUT
import com.michaldrabik.ui_base.utilities.extensions.combine
import com.michaldrabik.ui_base.utilities.extensions.rethrowCancellation
import com.michaldrabik.ui_episodes.details.cases.EpisodeDetailsSeasonCase
import com.michaldrabik.ui_episodes.details.cases.EpisodeDetailsWatchedCase
import com.michaldrabik.ui_base.viewmodel.ChannelsDelegate
import com.michaldrabik.ui_base.viewmodel.DefaultChannelsDelegate
import com.michaldrabik.ui_model.Episode
import com.michaldrabik.ui_model.IdTmdb
import com.michaldrabik.ui_model.IdTrakt
import com.michaldrabik.ui_model.Image
import com.michaldrabik.ui_model.RatingState
import com.michaldrabik.ui_model.SpoilersSettings
import com.michaldrabik.ui_model.Translation
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import javax.inject.Inject

@HiltViewModel
class EpisodeDetailsViewModel @Inject constructor(
  private val spoilersSettings: SettingsSpoilersRepository,
  private val seasonsCase: EpisodeDetailsSeasonCase,
  private val watchedCase: EpisodeDetailsWatchedCase,
  private val imagesProvider: EpisodeImagesProvider,
  private val dateFormatProvider: DateFormatProvider,
  private val ratingsRepository: RatingsRepository,
  private val translationsRepository: TranslationsRepository,
  private val settingsRepository: SettingsRepository,
) : ViewModel(),
  ChannelsDelegate by DefaultChannelsDelegate() {

  private val imageState = MutableStateFlow<Image?>(null)
  private val imageLoadingState = MutableStateFlow(false)
  private val episodesState = MutableStateFlow<List<Episode>?>(null)
  private val signedInState = MutableStateFlow(false)
  private val ratingState = MutableStateFlow<RatingState?>(null)
  private val translationState = MutableStateFlow<Translation?>(null)
  private val lastWatchedAtState = MutableStateFlow<ZonedDateTime?>(null)
  private val dateFormatState = MutableStateFlow<DateTimeFormatter?>(null)
  private val spoilersState = MutableStateFlow<SpoilersSettings?>(null)

  init {
    viewModelScope.launch {
      dateFormatState.value = dateFormatProvider.loadFullDayFormat()
      spoilersState.value = spoilersSettings.getAll()
    }
  }

  fun loadLastWatchedAt(showId: IdTrakt, episode: Episode) {
    viewModelScope.launch {
      lastWatchedAtState.value = watchedCase.getLastWatchedAt(showId, episode)
    }
  }

  fun loadImage(showId: IdTmdb, episode: Episode) {
    viewModelScope.launch {
      imageLoadingState.value = true
      imageState.value = imagesProvider.loadRemoteImage(showId, episode)
      imageLoadingState.value = false
    }
  }

  fun loadSeason(showId: IdTrakt, episode: Episode, seasonEpisodesIds: IntArray?) {
    viewModelScope.launch {
      episodesState.value = seasonsCase.loadSeason(showId, episode, seasonEpisodesIds)
    }
  }

  fun loadTranslation(showId: IdTrakt, episode: Episode) {
    viewModelScope.launch {
      translationState.value = translationsRepository.loadTranslation(
        episode,
        showId,
        settingsRepository.language,
        false,
      )
    }
  }

  fun loadRatings(episode: Episode) {
    viewModelScope.launch {
      val rating = ratingsRepository.shows.loadRating(episode)
      ratingState.value = RatingState(
        userRating = rating,
        rateLoading = false,
      )
    }
  }

  val uiState = combine(
    imageState,
    imageLoadingState,
    episodesState,
    signedInState,
    ratingState,
    translationState,
    lastWatchedAtState,
    dateFormatState,
    spoilersState,
  ) { image, isImageLoading, episodes, isSignedIn, rating, translation, lastWatchedAt, dateFormat, spoilers ->
    EpisodeDetailsUiState(
      image = image,
      isImageLoading = isImageLoading,
      episodes = episodes,
      isSignedIn = isSignedIn,
      rating = rating,
      translation = translation,
      lastWatchedAt = lastWatchedAt,
      dateFormat = dateFormat,
      spoilers = spoilers,
    )
  }.stateIn(
    scope = viewModelScope,
    started = SharingStarted.WhileSubscribed(SUBSCRIBE_STOP_TIMEOUT),
    initialValue = EpisodeDetailsUiState(),
  )
}
