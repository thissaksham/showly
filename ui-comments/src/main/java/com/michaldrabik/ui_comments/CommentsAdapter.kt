package com.michaldrabik.ui_comments

import android.view.ViewGroup
import androidx.recyclerview.widget.AsyncListDiffer
import androidx.recyclerview.widget.RecyclerView
import com.michaldrabik.ui_model.Comment
import java.time.format.DateTimeFormatter

class CommentsAdapter(
  private val onRepliesClickListener: ((Comment) -> Unit)? = null,
) : RecyclerView.Adapter<RecyclerView.ViewHolder>() {

  private val asyncDiffer = AsyncListDiffer(this, CommentItemDiffCallback())
  private var dateFormat: DateTimeFormatter? = null

  fun setItems(
    items: List<Comment>,
    dateFormat: DateTimeFormatter? = null,
  ) {
    this.dateFormat = dateFormat
    asyncDiffer.submitList(items)
  }

  override fun onCreateViewHolder(
    parent: ViewGroup,
    viewType: Int,
  ): ViewHolderShow {
    val view = CommentView(parent.context).apply {
      onRepliesClickListener = this@CommentsAdapter.onRepliesClickListener
    }
    return ViewHolderShow(view)
  }

  override fun onBindViewHolder(
    holder: RecyclerView.ViewHolder,
    position: Int,
  ) {
    val item = asyncDiffer.currentList[position]
    item?.let { (holder.itemView as CommentView).bind(it, dateFormat) }
  }

  override fun getItemCount() = asyncDiffer.currentList.size

  inner class ViewHolderShow(view: CommentView) : RecyclerView.ViewHolder(view)
}
