package com.michaldrabik.ui_show

import android.annotation.SuppressLint
import android.content.pm.ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
import android.content.res.Configuration.ORIENTATION_PORTRAIT
import android.os.Bundle
import android.view.View
import android.view.ViewGroup.MarginLayoutParams
import android.view.animation.DecelerateInterpolator
import androidx.activity.addCallback
import androidx.constraintlayout.widget.ConstraintSet
import androidx.core.os.bundleOf
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.updateMargins
import androidx.core.view.updatePadding
import androidx.fragment.app.setFragmentResultListener
import androidx.fragment.app.viewModels
import androidx.transition.AutoTransition
import androidx.transition.TransitionManager
import com.bumptech.glide.Glide
import com.bumptech.glide.load.resource.bitmap.CenterCrop
import com.bumptech.glide.load.resource.drawable.DrawableTransitionOptions.withCrossFade
import com.google.android.material.snackbar.Snackbar
import com.michaldrabik.common.Config.IMAGE_FADE_DURATION_MS
import com.michaldrabik.common.Config.SPOILERS_HIDE_SYMBOL
import com.michaldrabik.common.Config.SPOILERS_REGEX
import com.michaldrabik.common.Mode
import com.michaldrabik.ui_base.BaseFragment
import com.michaldrabik.ui_base.utilities.SnackbarHost
import com.michaldrabik.ui_base.utilities.events.Event
import com.michaldrabik.ui_base.utilities.events.MessageEvent
import com.michaldrabik.ui_base.utilities.extensions.copyToClipboard
import com.michaldrabik.ui_base.utilities.extensions.dimenToPx
import com.michaldrabik.ui_base.utilities.extensions.doOnApplyWindowInsets
import com.michaldrabik.ui_base.utilities.extensions.fadeIf
import com.michaldrabik.ui_base.utilities.extensions.gone
import com.michaldrabik.ui_base.utilities.extensions.launchAndRepeatStarted
import com.michaldrabik.ui_base.utilities.extensions.navigateToSafe
import com.michaldrabik.ui_base.utilities.extensions.onClick
import com.michaldrabik.ui_base.utilities.extensions.onLongClick
import com.michaldrabik.ui_base.utilities.extensions.openWebUrl
import com.michaldrabik.ui_base.utilities.extensions.requireLong
import com.michaldrabik.ui_base.utilities.extensions.screenHeight
import com.michaldrabik.ui_base.utilities.extensions.screenWidth
import com.michaldrabik.ui_base.utilities.extensions.showInfoSnackbar
import com.michaldrabik.ui_base.utilities.extensions.visible
import com.michaldrabik.ui_base.utilities.extensions.visibleIf
import com.michaldrabik.ui_base.utilities.extensions.withFailListener
import com.michaldrabik.ui_base.utilities.extensions.withSuccessListener
import com.michaldrabik.ui_base.utilities.viewBinding
import com.michaldrabik.ui_model.Genre
import com.michaldrabik.ui_model.IdTrakt
import com.michaldrabik.ui_model.Image
import com.michaldrabik.ui_model.ImageFamily.SHOW
import com.michaldrabik.ui_model.ImageStatus.UNAVAILABLE
import com.michaldrabik.ui_model.ImageType.FANART
import com.michaldrabik.ui_model.Show
import com.michaldrabik.ui_model.SpoilersSettings
import com.michaldrabik.ui_model.Tip.SHOW_DETAILS_GALLERY
import com.michaldrabik.ui_model.Translation
import com.michaldrabik.ui_navigation.java.NavigationArgs.ARG_FAMILY
import com.michaldrabik.ui_navigation.java.NavigationArgs.ARG_ID
import com.michaldrabik.ui_navigation.java.NavigationArgs.ARG_SHOW_ID
import com.michaldrabik.ui_navigation.java.NavigationArgs.ARG_TYPE
import com.michaldrabik.ui_navigation.java.NavigationArgs.REQUEST_MANAGE_LISTS
import com.michaldrabik.ui_show.ShowDetailsEvent.Finish
import com.michaldrabik.ui_show.databinding.FragmentShowDetailsBinding
import com.michaldrabik.ui_show.views.AddToShowsButton
import dagger.hilt.android.AndroidEntryPoint
import java.util.Locale.ENGLISH

@SuppressLint("SetTextI18n", "DefaultLocale", "SourceLockedOrientationActivity")
@AndroidEntryPoint
class ShowDetailsFragment : BaseFragment<ShowDetailsViewModel>(R.layout.fragment_show_details) {

  override val navigationId = R.id.showDetailsFragment
  val binding by viewBinding(FragmentShowDetailsBinding::bind)

  override val viewModel by viewModels<ShowDetailsViewModel>()

  private val showId by lazy { IdTrakt(requireLong(ARG_SHOW_ID)) }

  private val imageHeight by lazy {
    if (resources.configuration.orientation == ORIENTATION_PORTRAIT) {
      screenHeight()
    } else {
      screenWidth()
    }
  }
  private val imageRatio by lazy { resources.getString(R.string.detailsImageRatio).toFloat() }
  private val imagePadded by lazy { resources.getBoolean(R.bool.detailsImagePadded) }

  override fun onViewCreated(
    view: View,
    savedInstanceState: Bundle?,
  ) {
    super.onViewCreated(view, savedInstanceState)
    requireActivity().requestedOrientation = SCREEN_ORIENTATION_PORTRAIT
    setupView()
    setupInsets()

    launchAndRepeatStarted(
      { viewModel.uiState.collect { render(it) } },
      { viewModel.eventFlow.collect { handleEvent(it) } },
      { viewModel.messageFlow.collect { renderSnack(it) } },
      doAfterLaunch = {
        if (!isInitialized) {
          viewModel.loadDetails(showId)
          isInitialized = true
        }
      },
    )
  }

  private fun setupView() {
    with(binding) {
      hideNavigation()
      showDetailsImageGuideline.setGuidelineBegin((imageHeight * imageRatio).toInt())
      showDetailsBackArrow.onClick { requireActivity().onBackPressed() }
      showDetailsImage.onClick {
        val bundle = bundleOf(
          ARG_SHOW_ID to showId.id,
          ARG_FAMILY to SHOW,
          ARG_TYPE to FANART,
        )
        navigateToSafe(R.id.actionShowDetailsFragmentToArtGallery, bundle)
      }
      showDetailsTipGallery.onClick {
        it.gone()
        showTip(SHOW_DETAILS_GALLERY)
      }
      showDetailsAddButton.run {
        isEnabled = false
        onAddMyShowsClickListener = { viewModel.addFollowedShow() }
        onAddWatchlistClickListener = { viewModel.addWatchlistShow() }
        onRemoveClickListener = { viewModel.removeFromFollowed() }
      }
      showDetailsManageListsLabel.onClick { openListsDialog() }
      showDetailsHideLabel.onClick { viewModel.addHiddenShow() }
      showDetailsTitle.onClick {
        requireContext().copyToClipboard(showDetailsTitle.text.toString())
        showSnack(MessageEvent.Info(R.string.textCopiedToClipboard))
      }
      showDetailsDescription.onLongClick {
        val text = showDetailsDescription.text.toString()
        if (text.count { it.toString() == SPOILERS_HIDE_SYMBOL } == 0) {
          requireContext().copyToClipboard(text)
          showSnack(MessageEvent.Info(R.string.textCopiedToClipboard))
        }
      }
      showDetailsTrailerButton.onClick {
        viewModel.uiState.value.show?.let { show ->
          openWebUrl(show.trailer) ?: showSnack(MessageEvent.Info(R.string.errorCouldNotFindApp))
        }
      }
    }
  }

  private fun setupInsets() {
    with(binding) {
      root.doOnApplyWindowInsets { _, insets, _, _ ->
        val inset = insets.getInsets(WindowInsetsCompat.Type.systemBars())
        if (imagePadded) {
          showDetailsMainLayout
            .updatePadding(top = inset.top)
        }
        showDetailsMainContent.updatePadding(bottom = inset.bottom + dimenToPx(R.dimen.spaceNormal))
        (showDetailsBackArrow.layoutParams as MarginLayoutParams).updateMargins(top = inset.top)
      }
    }
  }

  private fun handleEvent(event: Event<*>) {
    when (event) {
      is Finish -> requireActivity().onBackPressed()
    }
  }

  private fun render(uiState: ShowDetailsUiState) {
    uiState.run {
      with(binding) {
        show?.let { show ->
          showDetailsStatus.text = getString(show.status.displayName)
          renderTitleDescription(show, translation, followedState, spoilers)
          renderExtraInfo(show)
          showDetailsTrailerButton.visibleIf(show.trailer.isNotBlank())
          showDetailsAddButton.isEnabled = true
        }
        showLoading?.let {
          showDetailsMainLayout.fadeIf(!it, hardware = true)
          showDetailsMainProgress.visibleIf(it)
        }
        followedState?.let {
          when {
            it.isMyShows -> showDetailsAddButton.setState(AddToShowsButton.State.IN_MY_SHOWS, it.withAnimation)
            it.isWatchlist -> showDetailsAddButton.setState(AddToShowsButton.State.IN_WATCHLIST, it.withAnimation)
            it.isHidden -> showDetailsAddButton.setState(AddToShowsButton.State.IN_HIDDEN, it.withAnimation)
            else -> showDetailsAddButton.setState(AddToShowsButton.State.ADD, it.withAnimation)
          }
          showDetailsHideLabel.visibleIf(!it.isHidden)
        }
        listsCount?.let {
          val text =
            if (it > 0) {
              getString(R.string.textShowManageListsCount, it)
            } else {
              getString(R.string.textShowManageLists)
            }
          showDetailsManageListsLabel.text = text
        }
        image?.let { renderImage(it) }
        renderRating()
      }
    }
  }

  private fun renderTitleDescription(
    show: Show,
    translation: Translation?,
    followedState: ShowDetailsUiState.FollowedState?,
    spoilersSettings: SpoilersSettings?,
  ) {
    with(binding) {
      var title = show.title
      var description = show.overview

      if (translation?.title?.isNotBlank() == true) {
        title = translation.title
      }
      if (translation?.overview?.isNotBlank() == true) {
        description = translation.overview
      }

      if (followedState == null || spoilersSettings == null) {
        showDetailsTitle.text = title
        showDetailsDescription.text = description
        return
      }

      val isMyShowHidden = spoilersSettings.isMyShowsHidden && followedState.isMyShows
      val isWatchlistHidden = spoilersSettings.isWatchlistShowsHidden && followedState.isWatchlist
      val isHiddenShowHidden = spoilersSettings.isHiddenShowsHidden && followedState.isHidden
      val isNotCollectedHidden = spoilersSettings.isNotCollectedShowsHidden && (!followedState.isInCollection())

      if (isMyShowHidden || isWatchlistHidden || isHiddenShowHidden || isNotCollectedHidden) {
        showDetailsDescription.tag = description
        description = SPOILERS_REGEX.replace(description, SPOILERS_HIDE_SYMBOL)

        if (spoilersSettings.isTapToReveal) {
          with(showDetailsDescription) {
            onClick {
              tag?.let { text = it.toString() }
              enableFoldOnClick()
            }
          }
        }
      }

      showDetailsTitle.text = title
      showDetailsDescription.text = description
    }
  }

  private fun renderExtraInfo(show: Show) {
    val year = if (show.year > 0) String.format(ENGLISH, "%d", show.year) else ""
    val country = if (show.country.isNotBlank()) String.format(ENGLISH, "(%s)", show.country) else ""
    val genres = show.genres
      .take(5)
      .mapNotNull { Genre.fromSlug(it) }
      .joinToString(", ") { getString(it.displayName) }

    var extraInfoText = getString(
      R.string.textShowExtraInfo,
      show.network,
      year,
      country.uppercase(),
      "⏲ ${show.runtime}",
      getString(R.string.textMinutesShort),
      genres,
    )

    if (genres.isEmpty()) {
      extraInfoText = extraInfoText.trim().removeSuffix("|")
    }

    binding.showDetailsExtraInfo.text = extraInfoText
  }

  private fun renderRating() {
    // Section removed
  }

  private fun renderImage(image: Image) {
    with(binding) {
      if (image.status == UNAVAILABLE) {
        showDetailsImageProgress.gone()
        showDetailsPlaceholder.visible()
        showDetailsImage.isClickable = false
        showDetailsImage.isEnabled = false
        return
      }
      Glide
        .with(this@ShowDetailsFragment)
        .load(image.fullFileUrl)
        .transform(CenterCrop())
        .transition(withCrossFade(IMAGE_FADE_DURATION_MS))
        .withFailListener {
          showDetailsImageProgress.gone()
          showDetailsPlaceholder.visible()
          showDetailsImage.isClickable = true
          showDetailsImage.isEnabled = true
        }.withSuccessListener {
          showDetailsImageProgress.gone()
          showDetailsPlaceholder.gone()
          showDetailsTipGallery.fadeIf(!isTipShown(SHOW_DETAILS_GALLERY))
        }.into(showDetailsImage)
    }
  }

  private fun renderSnack(event: MessageEvent) {
    if (event.textResId == R.string.errorMalformedShow) {
      val host = (requireActivity() as SnackbarHost).provideSnackbarLayout()
      val snack = host.showInfoSnackbar(getString(event.textResId), length = Snackbar.LENGTH_INDEFINITE) {
        viewModel.removeMalformedShow(showId)
      }
      snackbars.add(snack)
      return
    }
    showSnack(event)
  }

  private fun openListsDialog() {
    if (findNavControl()?.currentDestination?.id != R.id.showDetailsFragment) {
      return
    }
    setFragmentResultListener(REQUEST_MANAGE_LISTS) { _, _ -> viewModel.loadListsCount() }
    val bundle = bundleOf(
      ARG_ID to showId.id,
      ARG_TYPE to Mode.SHOWS.type,
    )
    navigateToSafe(R.id.actionShowDetailsFragmentToManageLists, bundle)
  }

  fun showStreamingsView(animate: Boolean) {
    with(binding) {
      if (!animate) {
        showDetailsStreamingsFragment.visible()
        return
      }
      val animation = ConstraintSet().apply {
        clone(showDetailsMainContent)
        setVisibility(showDetailsStreamingsFragment.id, View.VISIBLE)
      }
      val transition = AutoTransition().apply {
        interpolator = DecelerateInterpolator(1.5F)
        duration = 200
      }
      TransitionManager.beginDelayedTransition(showDetailsMainContent, transition)
      animation.applyTo(showDetailsMainContent)
    }
  }

  override fun setupBackPressed() {
    val dispatcher = requireActivity().onBackPressedDispatcher
    dispatcher.addCallback(viewLifecycleOwner) {
      isEnabled = false
      findNavControl()?.popBackStack()
    }
  }
}
