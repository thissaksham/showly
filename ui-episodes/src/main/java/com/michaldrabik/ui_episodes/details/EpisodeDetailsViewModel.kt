package com.michaldrabik.ui_episodes.details

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.michaldrabik.repository.CommentsRepository
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
import com.michaldrabik.ui_model.Comment
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
  private val commentsRepository: CommentsRepository,
  private val settingsRepository: SettingsRepository,
) : ViewModel(),
  ChannelsDelegate by DefaultChannelsDelegate() {

  private val imageState = MutableStateFlow<Image?>(null)
  private val imageLoadingState = MutableStateFlow(false)
  private val episodesState = MutableStateFlow<List<Episode>?>(null)
  private val commentsState = MutableStateFlow<List<Comment>?>(null)
  private val commentsLoadingState = MutableStateFlow(false)
  private val signedInState = MutableStateFlow(false)
  private val ratingState = MutableStateFlow<RatingState?>(null)
  private val translationState = MutableStateFlow<Translation?>(null)
  private val lastWatchedAtState = MutableStateFlow<ZonedDateTime?>(null)
  private val dateFormatState = MutableStateFlow<DateTimeFormatter?>(null)
  private val commentsDateFormatState = MutableStateFlow<DateTimeFormatter?>(null)
  private val spoilersState = MutableStateFlow<SpoilersSettings?>(null)

  init {
    viewModelScope.launch {
      dateFormatState.value = dateFormatProvider.loadFullDayFormat()
      commentsDateFormatState.value = dateFormatProvider.loadFullHourFormat()
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

  fun loadComments(showId: IdTrakt, seasonNumber: Int, episodeNumber: Int) {
    viewModelScope.launch {
      commentsLoadingState.value = true
      try {
        commentsState.value = commentsRepository.loadEpisodeComments(showId, seasonNumber, episodeNumber)
      } catch (e: Throwable) {
        rethrowCancellation(e)
      } finally {
        commentsLoadingState.value = false
      }
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

  fun loadCommentReplies(comment: Comment) {
    if (comment.replies <= 0) return
    viewModelScope.launch {
      try {
        val replies = commentsRepository.loadReplies(comment.id)
        val currentComments = commentsState.value?.toMutableList() ?: mutableListOf()
        val index = currentComments.indexOfFirst { it.id == comment.id }
        if (index != -1) {
          currentComments.addAll(index + 1, replies)
          currentComments[index] = currentComments[index].copy(replies = 0)
          commentsState.value = currentComments
        }
      } catch (e: Throwable) {
        rethrowCancellation(e)
      }
    }
  }

  fun addNewComment(comment: Comment) {
    // Removed.
  }

  fun deleteComment(comment: Comment) {
    // Removed.
  }

  val uiState = combine(
    imageState,
    imageLoadingState,
    episodesState,
    commentsState,
    commentsLoadingState,
    signedInState,
    ratingState,
    translationState,
    lastWatchedAtState,
    dateFormatState,
    commentsDateFormatState,
    spoilersState,
  ) { image, isImageLoading, episodes, comments, isCommentsLoading, isSignedIn, rating, translation, lastWatchedAt, dateFormat, commentsDateFormat, spoilers ->
    EpisodeDetailsUiState(
      image = image,
      isImageLoading = isImageLoading,
      episodes = episodes,
      comments = comments,
      isCommentsLoading = isCommentsLoading,
      isSignedIn = isSignedIn,
      rating = rating,
      translation = translation,
      lastWatchedAt = lastWatchedAt,
      dateFormat = dateFormat,
      commentsDateFormat = commentsDateFormat,
      spoilers = spoilers,
    )
  }.stateIn(
    scope = viewModelScope,
    started = SharingStarted.WhileSubscribed(SUBSCRIBE_STOP_TIMEOUT),
    initialValue = EpisodeDetailsUiState(),
  )
}
