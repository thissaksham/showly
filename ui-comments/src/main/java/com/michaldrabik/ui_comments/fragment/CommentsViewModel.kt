package com.michaldrabik.ui_comments.fragment

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.michaldrabik.common.Mode
import com.michaldrabik.ui_base.dates.DateFormatProvider
import com.michaldrabik.ui_base.utilities.extensions.SUBSCRIBE_STOP_TIMEOUT
import com.michaldrabik.ui_base.utilities.extensions.rethrowCancellation
import com.michaldrabik.ui_comments.fragment.cases.LoadCommentsCase
import com.michaldrabik.ui_comments.fragment.cases.LoadRepliesCase
import com.michaldrabik.ui_model.Comment
import com.michaldrabik.ui_model.IdTrakt
import com.michaldrabik.ui_navigation.java.NavigationArgs.ARG_ID
import com.michaldrabik.ui_navigation.java.NavigationArgs.ARG_TYPE
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.format.DateTimeFormatter
import javax.inject.Inject

@HiltViewModel
class CommentsViewModel @Inject constructor(
  private val savedStateHandle: SavedStateHandle,
  private val commentsCase: LoadCommentsCase,
  private val repliesCase: LoadRepliesCase,
  private val dateFormatProvider: DateFormatProvider,
) : ViewModel() {

  private val commentsState = MutableStateFlow<List<Comment>?>(null)
  private val loadingState = MutableStateFlow(false)
  private val dateFormatState = MutableStateFlow<DateTimeFormatter?>(null)

  init {
    viewModelScope.launch {
      dateFormatState.value = dateFormatProvider.loadFullHourFormat()
    }
  }

  fun loadInitialState() {
    val id = savedStateHandle.get<IdTrakt>(ARG_ID)!!
    val mode = Mode.valueOf(savedStateHandle.get<String>(ARG_TYPE)!!)
    loadComments(id, mode)
  }

  private fun loadComments(
    id: IdTrakt,
    mode: Mode,
  ) {
    viewModelScope.launch {
      loadingState.value = true
      try {
        val comments = commentsCase.loadComments(id, mode)
        commentsState.value = comments
      } catch (e: Throwable) {
        rethrowCancellation(e)
      } finally {
        loadingState.value = false
      }
    }
  }

  fun loadCommentReplies(comment: Comment) {
    if (comment.replies <= 0) return
    viewModelScope.launch {
      try {
        val replies = repliesCase.loadReplies(comment)
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

  val uiState = combine(
    commentsState,
    loadingState,
    dateFormatState,
  ) { comments, isLoading, dateFormat ->
    CommentsUiState(
      comments = comments,
      isLoading = isLoading,
      dateFormat = dateFormat,
    )
  }.stateIn(
    scope = viewModelScope,
    started = SharingStarted.WhileSubscribed(SUBSCRIBE_STOP_TIMEOUT),
    initialValue = CommentsUiState(),
  )
}
