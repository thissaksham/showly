package com.michaldrabik.ui_my_shows.common.recycler

import android.view.ViewGroup
import androidx.recyclerview.widget.AsyncListDiffer
import androidx.recyclerview.widget.RecyclerView
import com.michaldrabik.ui_base.BaseAdapter
import com.michaldrabik.ui_base.BaseMovieAdapter
import com.michaldrabik.ui_model.SortOrder
import com.michaldrabik.ui_model.SortType
import com.michaldrabik.ui_my_shows.common.recycler.CollectionListItem.FiltersItem
import com.michaldrabik.ui_my_shows.common.recycler.CollectionListItem.HeaderItem
import com.michaldrabik.ui_my_shows.common.recycler.CollectionListItem.ShowItem
import com.michaldrabik.ui_my_shows.common.views.CollectionHeaderView
import com.michaldrabik.ui_my_shows.common.views.CollectionShowFiltersView
import com.michaldrabik.ui_my_shows.common.views.CollectionShowView

class CollectionAdapter(
  listChangeListener: () -> Unit,
  private val itemClickListener: (CollectionListItem) -> Unit,
  private val itemLongClickListener: (CollectionListItem) -> Unit,
  private val sortChipClickListener: (SortOrder, SortType) -> Unit,
  private val upcomingChipClickListener: () -> Unit,
  private val networksChipClickListener: () -> Unit,
  private val genresChipClickListener: () -> Unit,
  private val missingImageListener: (CollectionListItem, Boolean) -> Unit,
  private val missingTranslationListener: (CollectionListItem) -> Unit,
  private val upcomingChipVisible: Boolean = true,
) : BaseAdapter<CollectionListItem>(
    listChangeListener = listChangeListener,
  ) {

  companion object {
    private const val VIEW_TYPE_SHOW = 1
    private const val VIEW_TYPE_FILTERS = 2
    private const val VIEW_TYPE_HEADER = 3
  }

  override val asyncDiffer = AsyncListDiffer(this, CollectionItemDiffCallback())

  override fun onCreateViewHolder(
    parent: ViewGroup,
    viewType: Int,
  ) = when (viewType) {
    VIEW_TYPE_SHOW -> BaseMovieAdapter.BaseViewHolder(
      CollectionShowView(parent.context).apply {
        itemClickListener = this@CollectionAdapter.itemClickListener
        itemLongClickListener = this@CollectionAdapter.itemLongClickListener
        missingImageListener = this@CollectionAdapter.missingImageListener
        missingTranslationListener = this@CollectionAdapter.missingTranslationListener
      },
    )
    VIEW_TYPE_FILTERS -> BaseMovieAdapter.BaseViewHolder(
      CollectionShowFiltersView(parent.context).apply {
        onSortChipClicked = this@CollectionAdapter.sortChipClickListener
        onFilterUpcomingClicked = this@CollectionAdapter.upcomingChipClickListener
        onNetworksChipClick = this@CollectionAdapter.networksChipClickListener
        onGenresChipClick = this@CollectionAdapter.genresChipClickListener
        isUpcomingChipVisible = upcomingChipVisible
      },
    )
    VIEW_TYPE_HEADER -> BaseMovieAdapter.BaseViewHolder(CollectionHeaderView(parent.context))
    else -> throw IllegalStateException()
  }

  override fun onBindViewHolder(
    holder: RecyclerView.ViewHolder,
    position: Int,
  ) {
    when (val item = asyncDiffer.currentList[position]) {
      is FiltersItem -> {
        (holder.itemView as CollectionShowFiltersView).bind(item)
      }
      is ShowItem -> {
        (holder.itemView as CollectionShowView).bind(item)
      }
      is HeaderItem -> {
        (holder.itemView as CollectionHeaderView).bind(item)
      }
    }
  }

  override fun getItemViewType(position: Int) =
    when (asyncDiffer.currentList[position]) {
      is ShowItem -> VIEW_TYPE_SHOW
      is FiltersItem -> VIEW_TYPE_FILTERS
      is HeaderItem -> VIEW_TYPE_HEADER
      else -> throw IllegalStateException()
    }
}
