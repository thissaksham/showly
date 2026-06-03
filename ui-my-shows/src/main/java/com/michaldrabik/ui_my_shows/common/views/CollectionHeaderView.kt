package com.michaldrabik.ui_my_shows.common.views

import android.content.Context
import android.util.AttributeSet
import android.view.LayoutInflater
import android.widget.FrameLayout
import com.michaldrabik.ui_my_shows.common.recycler.CollectionListItem
import com.michaldrabik.ui_my_shows.databinding.ViewCollectionHeaderBinding

class CollectionHeaderView @JvmOverloads constructor(
  context: Context,
  attrs: AttributeSet? = null,
  defStyleAttr: Int = 0,
) : FrameLayout(context, attrs, defStyleAttr) {

  private val binding = ViewCollectionHeaderBinding.inflate(LayoutInflater.from(context), this, true)

  fun bind(item: CollectionListItem.HeaderItem) {
    binding.collectionHeaderTitle.text = context.getString(item.titleResId)
  }
}
