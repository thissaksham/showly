package com.michaldrabik.ui_show

import com.michaldrabik.ui_model.Image
import com.michaldrabik.ui_model.RatingState
import com.michaldrabik.ui_model.Show
import com.michaldrabik.ui_model.SpoilersSettings
import com.michaldrabik.ui_model.Translation
import com.michaldrabik.ui_show.helpers.ShowDetailsMeta

data class ShowDetailsUiState(
  val show: Show? = null,
  val showLoading: Boolean? = null,
  val image: Image? = null,
  val listsCount: Int? = null,
  val followedState: FollowedState? = null,
  val ratingState: RatingState? = null,
  val translation: Translation? = null,
  val meta: ShowDetailsMeta? = null,
  val spoilers: SpoilersSettings? = null,
) {

  data class FollowedState(
    val isMyShows: Boolean,
    val isWatchlist: Boolean,
    val isDropped: Boolean,
    val withAnimation: Boolean,
  ) {

    fun isInCollection() = isMyShows || isWatchlist || isDropped

    companion object {
      fun idle() = FollowedState(isMyShows = false, isWatchlist = false, isDropped = false, withAnimation = true)

      fun inMyShows() = FollowedState(isMyShows = true, isWatchlist = false, isDropped = false, withAnimation = true)

      fun inWatchlist() = FollowedState(isMyShows = false, isWatchlist = true, isDropped = false, withAnimation = true)

      fun inDropped() = FollowedState(isMyShows = false, isWatchlist = false, isDropped = true, withAnimation = true)
    }
  }
}
