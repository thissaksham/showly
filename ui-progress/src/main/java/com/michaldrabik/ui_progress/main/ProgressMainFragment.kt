package com.michaldrabik.ui_progress.main

import android.os.Bundle
import android.view.View
import android.view.ViewGroup
import androidx.activity.addCallback
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.updateMargins
import androidx.core.widget.doAfterTextChanged
import androidx.fragment.app.setFragmentResultListener
import androidx.fragment.app.viewModels
import androidx.viewpager.widget.ViewPager
import com.michaldrabik.common.extensions.UNKNOWN_DATE
import com.michaldrabik.common.extensions.nowUtc
import com.michaldrabik.ui_base.BaseFragment
import com.michaldrabik.ui_base.common.OnScrollResetListener
import com.michaldrabik.ui_base.common.OnSearchClickListener
import com.michaldrabik.ui_base.common.OnShowsMoviesSyncedListener
import com.michaldrabik.ui_base.common.OnTabReselectedListener
import com.michaldrabik.ui_base.common.sheets.context_menu.ContextMenuBottomSheet
import com.michaldrabik.ui_base.common.sheets.date_selection.DateSelectionBottomSheet
import com.michaldrabik.ui_base.common.sheets.date_selection.DateSelectionBottomSheet.Companion.REQUEST_DATE_SELECTION
import com.michaldrabik.ui_base.common.sheets.date_selection.DateSelectionBottomSheet.Result
import com.michaldrabik.ui_base.utilities.events.Event
import com.michaldrabik.ui_base.utilities.extensions.add
import com.michaldrabik.ui_base.utilities.extensions.dimenToPx
import com.michaldrabik.ui_base.utilities.extensions.disableUi
import com.michaldrabik.ui_base.utilities.extensions.doOnApplyWindowInsets
import com.michaldrabik.ui_base.utilities.extensions.enableUi
import com.michaldrabik.ui_base.utilities.extensions.fadeIn
import com.michaldrabik.ui_base.utilities.extensions.fadeOut
import com.michaldrabik.ui_base.utilities.extensions.gone
import com.michaldrabik.ui_base.utilities.extensions.hideKeyboard
import com.michaldrabik.ui_base.utilities.extensions.launchAndRepeatStarted
import com.michaldrabik.ui_base.utilities.extensions.navigateToSafe
import com.michaldrabik.ui_base.utilities.extensions.nextPage
import com.michaldrabik.ui_base.utilities.extensions.onClick
import com.michaldrabik.ui_base.utilities.extensions.requireParcelable
import com.michaldrabik.ui_base.utilities.extensions.showKeyboard
import com.michaldrabik.ui_base.utilities.extensions.visible
import com.michaldrabik.ui_base.utilities.extensions.visibleIf
import com.michaldrabik.ui_base.utilities.viewBinding
import com.michaldrabik.ui_episodes.details.EpisodeDetailsBottomSheet
import com.michaldrabik.ui_model.Episode
import com.michaldrabik.ui_model.EpisodeBundle
import com.michaldrabik.ui_model.Show
import com.michaldrabik.ui_progress.main.adapters.ProgressMainAdapter
import androidx.fragment.app.clearFragmentResultListener
import com.michaldrabik.ui_navigation.java.NavigationArgs.ARG_SHOW_ID
import com.michaldrabik.ui_navigation.java.NavigationArgs.REQUEST_ITEM_MENU
import com.michaldrabik.ui_progress.R
import com.michaldrabik.ui_progress.databinding.FragmentProgressMainBinding
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class ProgressMainFragment :
  BaseFragment<ProgressMainViewModel>(R.layout.fragment_progress_main),
  OnShowsMoviesSyncedListener,
  OnTabReselectedListener {

  companion object {
    private const val TRANSLATION_DURATION = 225L
  }

  override val navigationId = R.id.progressMainFragment

  override val viewModel by viewModels<ProgressMainViewModel>()
  private val binding by viewBinding(FragmentProgressMainBinding::bind)

  private var adapter: ProgressMainAdapter? = null

  private var searchViewTranslation = 0F
  private var tabsTranslation = 0F
  private var sideIconTranslation = 0F
  private var currentPage = 0
  private var isSearching = false

  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    savedInstanceState?.let {
      searchViewTranslation = it.getFloat("ARG_SEARCH_POSITION")
      tabsTranslation = it.getFloat("ARG_TABS_POSITION")
      sideIconTranslation = it.getFloat("ARG_SIDE_ICON_POSITION")
      currentPage = it.getInt("ARG_PAGE")
    }
  }

  override fun onViewCreated(
    view: View,
    savedInstanceState: Bundle?,
  ) {
    super.onViewCreated(view, savedInstanceState)
    setupView()
    setupPager()
    setupInsets()

    launchAndRepeatStarted(
      { viewModel.uiState.collect { render(it) } },
      { viewModel.eventFlow.collect { handleEvent(it) } },
      doAfterLaunch = { viewModel.loadProgress() },
    )
  }

  override fun onSaveInstanceState(outState: Bundle) {
    super.onSaveInstanceState(outState)
    outState.putFloat("ARG_SEARCH_POSITION", searchViewTranslation)
    outState.putFloat("ARG_TABS_POSITION", tabsTranslation)
    outState.putFloat("ARG_SIDE_ICON_POSITION", sideIconTranslation)
    outState.putInt("ARG_PAGE", currentPage)
  }

  override fun onResume() {
    super.onResume()
    showNavigation()
  }

  override fun onPause() {
    enableUi()
    with(binding) {
      searchViewTranslation = progressMainSearchView.translationY
      tabsTranslation = progressMainTabs.translationY
      sideIconTranslation = progressMainSideIcons.translationY
    }
    super.onPause()
  }

  override fun onDestroyView() {
    adapter = null
    super.onDestroyView()
  }

  private fun setupView() {
    with(binding) {
      with(progressMainSearchIcon) {
        onClick { if (!isSearching) enterSearch() else exitSearch() }
      }

      with(progressMainSearchView) {
        hint = getString(R.string.textSearchFor)
        settingsIconVisible = true
        statsIconVisible = true
        isClickable = false
        onClick { openMainSearch() }
        onSettingsClickListener = { openSettings() }
        onStatsClickListener = { openStatistics() }
      }

      with(progressMainPagerModeTabs) {
        visibleIf(moviesEnabled)
        onModeSelected = { (requireActivity() as? com.michaldrabik.ui_base.utilities.ModeHost)?.setMode(it, force = true) }
        selectShows()
      }

      with(progressMainSearchLocalView) {
        onCloseClickListener = { exitSearch() }
      }

      progressMainTabs.translationY = tabsTranslation
      progressMainPagerModeTabs.translationY = tabsTranslation
      progressMainSearchView.translationY = searchViewTranslation
      progressMainSideIcons.translationY = sideIconTranslation
    }
  }

  private fun setupPager() {
    with(binding) {
      progressMainPager.run {
        offscreenPageLimit = ProgressMainAdapter.PAGES_COUNT
        adapter = ProgressMainAdapter(childFragmentManager, requireContext()).also { this@ProgressMainFragment.adapter = it }
        addOnPageChangeListener(pageChangeListener)
      }
      progressMainTabs.setupWithViewPager(progressMainPager)
    }
  }

  private fun setupInsets() {
    with(binding) {
      progressMainRoot.doOnApplyWindowInsets { _, insets, _, _ ->
        val tabletOffset = if (isTablet) dimenToPx(R.dimen.spaceMedium) else 0
        val statusBarSize = insets.getInsets(WindowInsetsCompat.Type.systemBars()).top + tabletOffset
        (progressMainSearchView.layoutParams as ViewGroup.MarginLayoutParams)
          .updateMargins(top = statusBarSize + dimenToPx(R.dimen.spaceMedium))
        (progressMainPagerModeTabs.layoutParams as ViewGroup.MarginLayoutParams)
          .updateMargins(top = statusBarSize + dimenToPx(R.dimen.collectionTabsMargin))
        (progressMainSearchLocalView.layoutParams as ViewGroup.MarginLayoutParams)
          .updateMargins(top = statusBarSize + dimenToPx(R.dimen.progressSearchLocalViewPadding))
        arrayOf(progressMainSideIcons, progressMainTabs).forEach {
          val margin = statusBarSize + dimenToPx(R.dimen.progressSearchViewPadding)
          (it.layoutParams as ViewGroup.MarginLayoutParams).updateMargins(top = margin)
        }
      }
    }
  }

  override fun setupBackPressed() {
    val dispatcher = requireActivity().onBackPressedDispatcher
    dispatcher.addCallback(viewLifecycleOwner) {
      if (isSearching) {
        exitSearch()
      } else {
        isEnabled = false
        activity?.onBackPressed()
      }
    }
  }

  private fun openMainSearch() {
    disableUi()
    hideNavigation()
    with(binding) {
      progressMainPagerModeTabs.fadeOut(duration = 200).add(animations)
      progressMainTabs.fadeOut(duration = 200).add(animations)
      progressMainSideIcons.fadeOut(duration = 200).add(animations)
      progressMainPager
        .fadeOut(duration = 200) {
          navigateToSafe(R.id.actionProgressFragmentToSearch)
        }.add(animations)
    }
  }

  fun openShowDetails(show: Show) {
    hideNavigation()
    binding.progressMainRoot
      .fadeOut(150) {
        val bundle = Bundle().apply { putLong(ARG_SHOW_ID, show.traktId) }
        navigateToSafe(R.id.actionProgressFragmentToShowDetailsFragment, bundle)
        exitSearch()
      }.add(animations)
  }

  fun openShowMenu(show: Show) {
    setFragmentResultListener(REQUEST_ITEM_MENU) { requestKey, _ ->
      if (requestKey == REQUEST_ITEM_MENU) {
        viewModel.loadProgress()
      }
      clearFragmentResultListener(REQUEST_ITEM_MENU)
    }
    val bundle = ContextMenuBottomSheet.createBundle(show.ids.trakt, showPinButtons = true)
    navigateToSafe(R.id.actionProgressFragmentToItemMenu, bundle)
  }

  fun openEpisodeDetails(
    show: Show,
    episode: Episode,
    season: com.michaldrabik.ui_model.Season,
  ) {
    val bundle = EpisodeDetailsBottomSheet.createBundle(
      showIds = show.ids,
      episode = episode,
      seasonEpisodesIds = season.episodes.map { it.number },
      isWatched = true,
      showTabs = false,
    )
    navigateToSafe(R.id.actionProgressFragmentToEpisodeDetails, bundle)
  }

  fun openDateSelectionDialog(bundle: EpisodeBundle) {
    setFragmentResultListener(REQUEST_DATE_SELECTION) { _, res ->
      when (val result = res.requireParcelable<Result>(DateSelectionBottomSheet.RESULT_DATE_SELECTION)) {
        is Result.Now -> viewModel.setWatchedEpisode(bundle, nowUtc(), true)
        is Result.Unknown -> viewModel.setWatchedEpisode(bundle, UNKNOWN_DATE, true)
        is Result.CustomDate -> viewModel.setWatchedEpisode(bundle, result.date, true)
        is Result.ReleaseDate -> viewModel.setWatchedEpisode(bundle, result.date, true)
      }
    }
    val options = DateSelectionBottomSheet.createBundle(bundle.episode.firstAired)
    navigateToSafe(R.id.actionProgressFragmentToDateSelection, options)
  }

  private fun openSettings() {
    hideNavigation()
    exitSearch()
    navigateToSafe(R.id.actionProgressFragmentToSettingsFragment)
  }

  private fun openStatistics() {
    hideNavigation()
    exitSearch()
    navigateToSafe(R.id.actionProgressFragmentToStatistics)
  }

  private fun enterSearch() {
    binding.progressMainSearchLocalView.fadeIn(150)
    resetTranslations()
    with(binding.progressMainSearchLocalView.binding.searchViewLocalInput) {
      setText("")
      doAfterTextChanged { viewModel.onSearchQuery(it?.toString() ?: "") }
      visible()
      showKeyboard()
      requestFocus()
    }
    isSearching = true
    childFragmentManager.fragments.forEach { (it as? OnSearchClickListener)?.onEnterSearch() }
  }

  private fun exitSearch() {
    isSearching = false
    childFragmentManager.fragments.forEach { (it as? OnSearchClickListener)?.onExitSearch() }
    binding.progressMainSearchLocalView.gone()
    resetTranslations()
    with(binding.progressMainSearchLocalView.binding.searchViewLocalInput) {
      setText("")
      gone()
      hideKeyboard()
      clearFocus()
    }
  }

  fun toggleCalendarMode() {
    exitSearch()
    onScrollReset()
    resetTranslations()
    viewModel.toggleCalendarMode()
  }

  override fun onShowsMoviesSyncFinished() = viewModel.loadProgress()

  override fun onTabReselected() {
    if (view == null) return
    resetTranslations(duration = 0)
    binding.progressMainPager.nextPage()
    onScrollReset()
  }

  fun resetTranslations(duration: Long = TRANSLATION_DURATION) {
    if (view == null) return
    with(binding) {
      arrayOf(
        progressMainSearchView,
        progressMainTabs,
        progressMainPagerModeTabs,
        progressMainSideIcons,
        progressMainSearchLocalView,
      ).forEach {
        it
          .animate()
          .translationY(0F)
          .setDuration(duration)
          .add(animations)
          ?.start()
      }
    }
  }

  private fun onScrollReset() =
    childFragmentManager.fragments.forEach { (it as? OnScrollResetListener)?.onScrollReset() }

  private fun render(uiState: ProgressMainUiState) {
    if (uiState.resetScroll?.consume() == true) {
      onScrollReset()
    }
  }

  private fun handleEvent(event: Event<*>) {
    when (val result = event.peek()) {
      is OpenEpisodeDetails -> openEpisodeDetails(result.show, result.episode, com.michaldrabik.ui_model.Season.EMPTY) // simplified
    }
  }

  private val pageChangeListener = object : ViewPager.OnPageChangeListener {
    override fun onPageSelected(position: Int) {
      if (currentPage == position) return

      if (binding.progressMainTabs.translationY != 0F) {
        resetTranslations()
        requireView().postDelayed({ onScrollReset() }, TRANSLATION_DURATION)
      }

      currentPage = position
    }

    override fun onPageScrolled(
      position: Int,
      positionOffset: Float,
      positionOffsetPixels: Int,
    ) = Unit

    override fun onPageScrollStateChanged(state: Int) = Unit
  }
}
