package com.michaldrabik.ui_comments.fragment

import android.os.Bundle
import android.view.View
import androidx.core.os.bundleOf
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.updatePadding
import androidx.fragment.app.viewModels
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.michaldrabik.common.Mode
import com.michaldrabik.ui_base.BaseFragment
import com.michaldrabik.ui_base.utilities.extensions.doOnApplyWindowInsets
import com.michaldrabik.ui_base.utilities.extensions.launchAndRepeatStarted
import com.michaldrabik.ui_base.utilities.extensions.onClick
import com.michaldrabik.ui_base.utilities.extensions.visibleIf
import com.michaldrabik.ui_base.utilities.viewBinding
import com.michaldrabik.ui_comments.R
import com.michaldrabik.ui_comments.databinding.FragmentCommentsBinding
import com.michaldrabik.ui_comments.CommentsAdapter
import com.michaldrabik.ui_model.IdTrakt
import com.michaldrabik.ui_model.Movie
import com.michaldrabik.ui_model.Show
import com.michaldrabik.ui_navigation.java.NavigationArgs.ARG_ID
import com.michaldrabik.ui_navigation.java.NavigationArgs.ARG_TYPE
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class CommentsFragment : BaseFragment<CommentsViewModel>(R.layout.fragment_comments) {

  companion object {
    private const val BACK_UP_BUTTON_THRESHOLD = 5

    fun createBundle(movie: Movie) =
      bundleOf(ARG_ID to movie.ids.trakt, ARG_TYPE to Mode.MOVIES.name)

    fun createBundle(show: Show) =
      bundleOf(ARG_ID to show.ids.trakt, ARG_TYPE to Mode.SHOWS.name)
  }

  override val viewModel by viewModels<CommentsViewModel>()
  private val binding by viewBinding(FragmentCommentsBinding::bind)

  private var commentsAdapter: CommentsAdapter? = null

  override fun onViewCreated(
    view: View,
    savedInstanceState: Bundle?,
  ) {
    super.onViewCreated(view, savedInstanceState)
    setupView()
    setupInsets()
    setupRecycler()

    launchAndRepeatStarted(
      { viewModel.uiState.collect { render(it) } },
      doAfterLaunch = { viewModel.loadInitialState() },
    )
  }

  private fun setupView() {
    with(binding) {
      commentsBackArrow.onClick { findNavControl()?.popBackStack() }
      commentsUpButton.onClick { resetScroll() }
    }
  }

  private fun setupInsets() {
    binding.commentsRoot.doOnApplyWindowInsets { _, insets, padding, _ ->
      val inset = insets.getInsets(WindowInsetsCompat.Type.systemBars())
      binding.commentsRecycler.updatePadding(
        bottom = padding.bottom + inset.bottom,
      )
    }
  }

  private fun setupRecycler() {
    commentsAdapter = CommentsAdapter(
      onRepliesClickListener = { viewModel.loadCommentReplies(it) }
    )
    with(binding.commentsRecycler) {
      layoutManager = LinearLayoutManager(requireContext())
      adapter = commentsAdapter
      addOnScrollListener(recyclerScrollListener)
    }
  }

  private fun resetScroll() {
    binding.commentsRecycler.smoothScrollToPosition(0)
  }

  private fun render(uiState: CommentsUiState) {
    with(binding) {
      commentsProgress.visibleIf(uiState.isLoading)
      commentsEmpty.visibleIf(!uiState.isLoading && uiState.comments.isNullOrEmpty())
      commentsAdapter?.setItems(uiState.comments ?: emptyList(), uiState.dateFormat)
    }
  }

  override fun onDestroyView() {
    commentsAdapter = null
    super.onDestroyView()
  }

  private val recyclerScrollListener = object : RecyclerView.OnScrollListener() {
    override fun onScrollStateChanged(
      recyclerView: RecyclerView,
      newState: Int,
    ) {
      super.onScrollStateChanged(recyclerView, newState)
      val layoutManager = recyclerView.layoutManager as LinearLayoutManager
      val firstVisibleItemPosition = layoutManager.findFirstVisibleItemPosition()
      binding.commentsUpButton.visibleIf(firstVisibleItemPosition > BACK_UP_BUTTON_THRESHOLD)
    }
  }

  data class Options(
    val id: IdTrakt,
    val mode: Mode,
  )
}
