package com.michaldrabik.ui_lists.details

import android.os.Bundle
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.activity.addCallback
import androidx.appcompat.widget.PopupMenu
import androidx.core.content.ContextCompat
import androidx.core.os.bundleOf
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.updatePadding
import androidx.fragment.app.setFragmentResultListener
import androidx.fragment.app.viewModels
import androidx.recyclerview.widget.ItemTouchHelper
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.michaldrabik.common.Mode
import com.michaldrabik.repository.settings.SettingsRepository
import com.michaldrabik.ui_base.BaseFragment
import com.michaldrabik.ui_base.common.sheets.context_menu.ContextMenuBottomSheet
import com.michaldrabik.ui_base.utilities.extensions.disableUi
import com.michaldrabik.ui_base.utilities.extensions.doOnApplyWindowInsets
import com.michaldrabik.ui_base.utilities.extensions.fadeOut
import com.michaldrabik.ui_base.utilities.extensions.launchAndRepeatStarted
import com.michaldrabik.ui_base.utilities.extensions.navigateToSafe
import com.michaldrabik.ui_base.utilities.extensions.onClick
import com.michaldrabik.ui_base.utilities.extensions.requireParcelable
import com.michaldrabik.ui_base.utilities.extensions.visibleIf
import com.michaldrabik.ui_base.utilities.viewBinding
import com.michaldrabik.ui_lists.R
import com.michaldrabik.ui_lists.databinding.FragmentListDetailsBinding
import com.michaldrabik.ui_lists.details.helpers.ListItemDragListener
import com.michaldrabik.ui_lists.details.helpers.ListItemSwipeListener
import com.michaldrabik.ui_lists.details.recycler.ListDetailsAdapter
import com.michaldrabik.ui_lists.details.recycler.ListDetailsItem
import com.michaldrabik.ui_model.CustomList
import com.michaldrabik.ui_model.SortOrder
import com.michaldrabik.ui_model.SortType
import com.michaldrabik.ui_navigation.java.NavigationArgs
import com.michaldrabik.ui_navigation.java.NavigationArgs.ARG_LIST
import com.michaldrabik.ui_navigation.java.NavigationArgs.ARG_MOVIE_ID
import com.michaldrabik.ui_navigation.java.NavigationArgs.ARG_SHOW_ID
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

@AndroidEntryPoint
class ListDetailsFragment :
  BaseFragment<ListDetailsViewModel>(R.layout.fragment_list_details) {

  companion object {
    private const val ARG_HEADER_TRANSLATION = "ARG_HEADER_TRANSLATION"
  }

  @Inject lateinit var settingsRepository: SettingsRepository

  override val viewModel by viewModels<ListDetailsViewModel>()
  private val binding by viewBinding(FragmentListDetailsBinding::bind)

  private val list by lazy { requireParcelable<CustomList>(ARG_LIST) }

  private var adapter: ListDetailsAdapter? = null
  private var touchHelper: ItemTouchHelper? = null
  private var layoutManager: RecyclerView.LayoutManager? = null

  private var headerTranslation = 0F
  private var isReorderMode = false

  override fun onCreateView(
    inflater: LayoutInflater,
    container: ViewGroup?,
    savedInstanceState: Bundle?,
  ): View? {
    headerTranslation = savedInstanceState?.getFloat(ARG_HEADER_TRANSLATION) ?: 0F
    return super.onCreateView(inflater, container, savedInstanceState)
  }

  override fun onViewCreated(
    view: View,
    savedInstanceState: Bundle?,
  ) {
    super.onViewCreated(view, savedInstanceState)
    setupView()
    setupInsets()
    setupRecycler()
    setupBackPressed()

    launchAndRepeatStarted(
      { viewModel.uiState.collect { render(it) } },
      doAfterLaunch = { viewModel.loadDetails(list.id) },
    )
  }

  override fun onResume() {
    super.onResume()
    viewModel.loadDetails(list.id)
  }

  private fun setupView() {
    with(binding) {
      fragmentListDetailsToolbar.setNavigationOnClickListener { findNavControl()?.popBackStack() }
      fragmentListDetailsManageButton.onClick { toggleReorderMode() }
      fragmentListDetailsFiltersView.onSortClickListener = { order, type -> openSortOrderDialog(order, type) }
      fragmentListDetailsFiltersView.onTypesChangeListener = { viewModel.setFilterTypes(list.id, it) }
    }
  }

  private fun setupInsets() {
    binding.fragmentListDetailsRoot.doOnApplyWindowInsets { _, insets, _, _ ->
      val inset = insets.getInsets(WindowInsetsCompat.Type.systemBars())
      binding.fragmentListDetailsRoot.updatePadding(top = inset.top)
    }
  }

  private fun setupRecycler() {
    adapter = ListDetailsAdapter(
      itemClickListener = { onItemClick(it) },
      missingImageListener = { item, force -> viewModel.loadMissingImage(item, force) },
      missingTranslationListener = { viewModel.loadMissingTranslation(it) },
      itemsChangedListener = { /* No-op */ },
      itemsClearedListener = { /* No-op */ },
      itemsSwipedListener = { onItemDeleteClick(it) },
      itemDragStartListener = object : ListItemDragListener {
        override fun onListItemDragStarted(viewHolder: RecyclerView.ViewHolder) {
          touchHelper?.startDrag(viewHolder)
        }
      },
      itemSwipeStartListener = object : ListItemSwipeListener {
        override fun onListItemSwipeStarted(viewHolder: RecyclerView.ViewHolder) {
          // No-op
        }
      }
    )
    binding.fragmentListDetailsRecycler.adapter = adapter
  }

  override fun setupBackPressed() {
    val dispatcher = requireActivity().onBackPressedDispatcher
    dispatcher.addCallback(viewLifecycleOwner) {
      if (isReorderMode) {
        toggleReorderMode()
      } else {
        isEnabled = false
        findNavControl()?.popBackStack()
      }
    }
  }

  private fun openSortOrderDialog(
    currentSortOrder: SortOrder,
    currentSortType: SortType,
  ) {
    // Navigate to sort order dialog...
  }

  private fun openDeleteDialog() {
    MaterialAlertDialogBuilder(requireContext(), R.style.AlertDialog)
      .setBackground(ContextCompat.getDrawable(requireContext(), R.drawable.bg_dialog))
      .setTitle(R.string.textConfirmDeleteListTitle)
      .setMessage(R.string.textConfirmDeleteListSubtitle)
      .setPositiveButton(R.string.textYes) { _, _ ->
        viewModel.deleteList(list.id, false)
      }.setNegativeButton(R.string.textNo) { _, _ -> }
      .show()
  }

  private fun openEditDialog() {
    setFragmentResultListener(NavigationArgs.REQUEST_CREATE_LIST) { _, _ ->
      viewModel.loadDetails(list.id)
    }
    val bundle = bundleOf(ARG_LIST to list)
    navigateToSafe(R.id.actionListDetailsFragmentToEditListDialog, bundle)
  }

  private fun onItemClick(item: ListDetailsItem) {
    if (isReorderMode) return
    openItemDetails(item)
  }

  private fun onItemDeleteClick(item: ListDetailsItem) {
    viewModel.deleteListItem(list.id, item)
  }

  private fun openItemDetails(listItem: ListDetailsItem) {
    disableUi()
    binding.fragmentListDetailsRoot
      .fadeOut(150) {
        val bundle = bundleOf(
          ARG_SHOW_ID to listItem.show?.traktId,
          ARG_MOVIE_ID to listItem.movie?.traktId,
        )
        val destination =
          when {
            listItem.isShow() -> R.id.actionListDetailsFragmentToShowDetailsFragment
            listItem.isMovie() -> R.id.actionListDetailsFragmentToMovieDetailsFragment
            else -> throw IllegalStateException()
          }
        navigateToSafe(destination, bundle)
      }
  }

  private fun openPopupMenu() {
    PopupMenu(requireContext(), binding.fragmentListDetailsMoreButton, Gravity.CENTER).apply {
      inflate(R.menu.menu_list_details)
      setOnMenuItemClickListener { menuItem ->
        when (menuItem.itemId) {
          R.id.menuListDetailsEdit -> openEditDialog()
          R.id.menuListDetailsDelete -> openDeleteDialog()
        }
        true
      }
      show()
    }
  }

  private fun toggleReorderMode() {
    isReorderMode = !isReorderMode
    viewModel.setReorderMode(list.id, isReorderMode)
  }

  private fun render(uiState: ListDetailsUiState) {
    fun renderTitle(
      name: String?,
      itemsCount: Int? = null,
    ) {
      if (name.isNullOrBlank()) return
      binding.fragmentListDetailsToolbar.title = when {
        itemsCount != null && itemsCount > 0 -> "$name ($itemsCount)"
        else -> name
      }
    }

    uiState.run {
      renderTitle(listDetails?.name, listItems?.size)
      with(binding) {
        listDetails?.let { details ->
          fragmentListDetailsToolbar.subtitle = details.description
          fragmentListDetailsMoreButton.onClick { openPopupMenu() }
        }
        listItems?.let {
          adapter?.setItems(it, false)
        }
        isLoading.let {
          fragmentListDetailsLoadingView.visibleIf(it)
        }
        deleteEvent?.let { event ->
          event.consume()?.let { findNavControl()?.popBackStack() }
        }
      }
    }
  }

  override fun onDestroyView() {
    adapter = null
    touchHelper = null
    layoutManager = null
    super.onDestroyView()
  }
}
