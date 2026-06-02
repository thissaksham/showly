package com.michaldrabik.ui_base.common.sheets.context_menu.movie

import android.os.Bundle
import android.view.View
import androidx.core.os.bundleOf
import androidx.fragment.app.viewModels
import com.michaldrabik.common.Config.SPOILERS_HIDE_SYMBOL
import com.michaldrabik.common.Config.SPOILERS_RATINGS_HIDE_SYMBOL
import com.michaldrabik.common.Config.SPOILERS_REGEX
import com.michaldrabik.ui_base.R
import com.michaldrabik.ui_base.common.sheets.context_menu.ContextMenuBottomSheet
import com.michaldrabik.ui_base.common.sheets.context_menu.movie.helpers.MovieContextItem
import com.michaldrabik.ui_base.utilities.events.Event
import com.michaldrabik.ui_base.utilities.events.FinishUiEvent
import com.michaldrabik.ui_base.utilities.extensions.launchAndRepeatStarted
import com.michaldrabik.ui_base.utilities.extensions.onClick
import com.michaldrabik.ui_base.utilities.extensions.requireLong
import com.michaldrabik.ui_base.utilities.extensions.visibleIf
import com.michaldrabik.ui_model.IdTrakt
import com.michaldrabik.ui_model.Movie
import com.michaldrabik.ui_model.Translation
import com.michaldrabik.ui_navigation.java.NavigationArgs.ARG_MOVIE_ID
import com.michaldrabik.ui_navigation.java.NavigationArgs.ARG_ID
import dagger.hilt.android.AndroidEntryPoint
import java.util.Locale

@AndroidEntryPoint
class MovieContextMenuBottomSheet : ContextMenuBottomSheet() {

  private val viewModel by viewModels<MovieContextMenuViewModel>()

  private val movieId by lazy { IdTrakt(requireLong(ARG_ID)) }

  override fun onViewCreated(
    view: View,
    savedInstanceState: Bundle?,
  ) {
    super.onViewCreated(view, savedInstanceState)
    setupView()

    launchAndRepeatStarted(
      { viewModel.uiState.collect { render(it) } },
      { viewModel.messageFlow.collect { renderSnackbar(it) } },
      { viewModel.eventFlow.collect { handleEvent(it) } },
      doAfterLaunch = { viewModel.loadMovie(movieId) },
    )
  }

  override fun setupView() {
    super.setupView()
    with(binding) {
      contextMenuItemMoveToMyButton.text = getString(R.string.textMoveToMyMovies)
      contextMenuItemRemoveFromMyButton.text = getString(R.string.textRemoveFromMyMovies)

      contextMenuItemMoveToMyButton.onClick { viewModel.moveToMyMovies(false, null) }
      contextMenuItemRemoveFromMyButton.onClick { viewModel.removeFromMyMovies() }
      contextMenuItemMoveToWatchlistButton.onClick { viewModel.moveToWatchlist() }
      contextMenuItemRemoveFromWatchlistButton.onClick { viewModel.removeFromWatchlist() }
      contextMenuItemMoveToHiddenButton.onClick { viewModel.moveToHidden() }
      contextMenuItemRemoveFromHiddenButton.onClick { viewModel.removeFromHidden() }
      contextMenuItemPinButton.onClick { viewModel.addToTopPinned() }
      contextMenuItemUnpinButton.onClick { viewModel.removeFromTopPinned() }
    }
  }

  private fun render(uiState: MovieContextMenuUiState) {
    uiState.run {
      with(binding) {
        contextMenuItemProgress.visibleIf(isLoading == true)
        item?.let { item ->
          renderItemTitle(item.movie, item.translation)
          renderItemDescription(item)
          renderItemRating(item)
          renderItemInfo(item)

          val loading = isLoading == true

          contextMenuItemMoveToMyButton.visibleIf(!item.isMyMovie && !loading)
          contextMenuItemMoveToWatchlistButton.visibleIf(!item.isWatchlist && !loading)
          contextMenuItemMoveToHiddenButton.visibleIf(!item.isHidden && !loading)

          contextMenuItemRemoveFromMyButton.visibleIf(item.isMyMovie && !loading)
          contextMenuItemRemoveFromWatchlistButton.visibleIf(item.isWatchlist && !loading)
          contextMenuItemRemoveFromHiddenButton.visibleIf(item.isHidden && !loading)

          contextMenuItemPinButton.visibleIf(!item.isPinnedTop && !loading)
          contextMenuItemUnpinButton.visibleIf(item.isPinnedTop && !loading)
          
          renderImage(item.image)
        }
      }
    }
  }

  private fun renderItemTitle(
    movie: Movie,
    translation: Translation?,
  ) {
    var title = movie.title
    if (translation?.title?.isNotBlank() == true) {
      title = translation.title
    }
    binding.contextMenuItemTitle.text = title
  }

  private fun renderItemDescription(item: MovieContextItem) {
    with(binding) {
      var description = item.movie.overview
      if (item.translation?.overview?.isNotBlank() == true) {
        description = item.translation.overview
      }

      val isMyMovieHidden = item.spoilers.isMyMoviesHidden && item.isMyMovie
      val isWatchlistHidden = item.spoilers.isWatchlistMoviesHidden && item.isWatchlist
      val isHiddenMovieHidden = item.spoilers.isHiddenMoviesHidden && item.isHidden
      val isNotCollectedHidden = item.spoilers.isNotCollectedMoviesHidden && (!item.isInCollection())

      if (isMyMovieHidden || isWatchlistHidden || isHiddenMovieHidden || isNotCollectedHidden) {
        contextMenuItemDescription.tag = description
        description = SPOILERS_REGEX.replace(description, SPOILERS_HIDE_SYMBOL)

        if (item.spoilers.isTapToReveal) {
          with(contextMenuItemDescription) {
            onClick {
              tag?.let { text = it.toString() }
              isClickable = false
            }
          }
        }
      }

      contextMenuItemDescription.text = description.ifBlank { getString(R.string.textNoDescription) }
    }
  }

  private fun renderItemRating(item: MovieContextItem) {
    with(binding) {
      val rating = String.format(Locale.ENGLISH, "%.1f", item.movie.rating)

      val isMyMovieHidden = item.spoilers.isMyMoviesRatingsHidden && item.isMyMovie
      val isWatchlistHidden = item.spoilers.isWatchlistMoviesRatingsHidden && item.isWatchlist
      val isHiddenMovieHidden = item.spoilers.isHiddenMoviesRatingsHidden && item.isHidden
      val isNotCollectedHidden = item.spoilers.isNotCollectedMoviesRatingsHidden && (!item.isInCollection())

      if (isMyMovieHidden || isWatchlistHidden || isHiddenMovieHidden || isNotCollectedHidden) {
        contextMenuRating.tag = rating
        contextMenuRating.text = SPOILERS_RATINGS_HIDE_SYMBOL

        if (item.spoilers.isTapToReveal) {
          with(contextMenuRating) {
            onClick {
              tag?.let { text = it.toString() }
              isClickable = false
            }
          }
        }
      } else {
        contextMenuRating.text = rating
      }

      contextMenuRating.visibleIf(item.movie.rating > 0)
      contextMenuRatingStar.visibleIf(item.movie.rating > 0)

      val userRating = item.userRating
      contextMenuUserRating.visibleIf(userRating != null && userRating > 0)
      contextMenuUserRatingStar.visibleIf(userRating != null && userRating > 0)
      userRating?.let {
        contextMenuUserRating.text = userRating.toString()
      }
    }
  }

  private fun renderItemInfo(item: MovieContextItem) {
    with(binding) {
      val year = if (item.movie.year > 0) item.movie.year.toString() else ""
      val runtime = "${item.movie.runtime} ${getString(R.string.textMinutesShort)}"
      contextMenuItemNetwork.text = "$year | $runtime"
    }
  }

  private fun handleEvent(event: Event<*>) {
    when (event.peek()) {
      is FinishUiEvent -> close()
    }
  }

  override fun openDetails() {
    val bundle = bundleOf(ARG_MOVIE_ID to itemId.id)
    navigateTo(R.id.actionMovieItemContextDialogToMovieDetails, bundle)
  }
}
