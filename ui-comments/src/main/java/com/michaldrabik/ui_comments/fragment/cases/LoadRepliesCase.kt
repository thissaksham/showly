package com.michaldrabik.ui_comments.fragment.cases

import com.michaldrabik.common.dispatchers.CoroutineDispatchers
import com.michaldrabik.repository.CommentsRepository
import com.michaldrabik.ui_model.Comment
import kotlinx.coroutines.withContext
import javax.inject.Inject

class LoadRepliesCase @Inject constructor(
  private val dispatchers: CoroutineDispatchers,
  private val commentsRepository: CommentsRepository,
) {

  suspend fun loadReplies(comment: Comment): List<Comment> =
    withContext(dispatchers.IO) {
      val replies = commentsRepository.loadReplies(comment.id)
      replies.map {
        it.copy(
          isMe = false,
          isSignedIn = false,
        )
      }
    }
}
