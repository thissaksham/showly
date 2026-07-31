package com.michaldrabik.showly2.ui.main.cases.deeplink

import com.michaldrabik.repository.movies.MovieDetailsRepository
import com.michaldrabik.repository.shows.ShowDetailsRepository
import com.michaldrabik.showly2.utilities.deeplink.DeepLinkBundle
import com.michaldrabik.showly2.utilities.deeplink.DeepLinkResolver.Companion.TRAKT_TYPE_MOVIE
import com.michaldrabik.showly2.utilities.deeplink.DeepLinkResolver.Companion.TRAKT_TYPE_TV
import com.michaldrabik.ui_model.IdSlug
import javax.inject.Inject

/**
 * trakt.tv links are resolved by their Trakt slug, which only Trakt could translate.
 * Nothing replaces it, so this now answers from the local database only and the
 * manifest no longer registers the app for trakt.tv links.
 *
 * A title already in the library still opens; anything else falls through to the
 * browser, which is the honest outcome.
 */
class TraktDeepLinkCase @Inject constructor(
  private val showDetailsRepository: ShowDetailsRepository,
  private val movieDetailsRepository: MovieDetailsRepository,
) {

  suspend fun findById(
    traktSlug: IdSlug,
    type: String,
  ) = when (type) {
    TRAKT_TYPE_TV -> showDetailsRepository
      .find(traktSlug)
      ?.let { DeepLinkBundle(show = it) }
      ?: DeepLinkBundle.EMPTY
    TRAKT_TYPE_MOVIE -> movieDetailsRepository
      .find(traktSlug)
      ?.let { DeepLinkBundle(movie = it) }
      ?: DeepLinkBundle.EMPTY
    else -> DeepLinkBundle.EMPTY
  }
}
