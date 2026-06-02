package com.michaldrabik.ui_comments.fragment.cases

import com.michaldrabik.common.Mode
import com.michaldrabik.common.dispatchers.CoroutineDispatchers
import com.michaldrabik.repository.CommentsRepository
import com.michaldrabik.ui_model.Comment
import com.michaldrabik.ui_model.IdTrakt
import kotlinx.coroutines.withContext
import javax.inject.Inject

class LoadCommentsCase @Inject constructor(
  private val dispatchers: CoroutineDispatchers,
  private val commentsRepository: CommentsRepository,
) {

  suspend fun loadComments(
    id: IdTrakt,
    mode: Mode,
  ): List<Comment> =
    withContext(dispatchers.IO) {
      val comments = commentsRepository.loadComments(id, mode)
      comments.map {
        it.copy(
          isMe = false,
          isSignedIn = false,
        )
      }
    }
}
