package com.michaldrabik.showly2.ui.main.cases.deeplink

import com.michaldrabik.repository.movies.MovieDetailsRepository
import com.michaldrabik.repository.shows.ShowDetailsRepository
import com.michaldrabik.showly2.utilities.deeplink.DeepLinkBundle
import com.michaldrabik.showly2.utilities.deeplink.DeepLinkResolver.Companion.TMDB_TYPE_MOVIE
import com.michaldrabik.showly2.utilities.deeplink.DeepLinkResolver.Companion.TMDB_TYPE_TV
import com.michaldrabik.ui_model.IdTmdb
import javax.inject.Inject

class TmdbDeepLinkCase @Inject constructor(
  private val showDetailsRepository: ShowDetailsRepository,
  private val movieDetailsRepository: MovieDetailsRepository,
) {

  suspend fun findById(
    tmdbId: IdTmdb,
    type: String,
  ): DeepLinkBundle {
    val localShow = showDetailsRepository.find(tmdbId)
    if (localShow != null && type == TMDB_TYPE_TV) {
      return DeepLinkBundle(show = localShow)
    }

    val localMovie = movieDetailsRepository.find(tmdbId)
    if (localMovie != null && type == TMDB_TYPE_MOVIE) {
      return DeepLinkBundle(movie = localMovie)
    }

    // The link already carries the TMDB id and the type, so this used to ask Trakt to
    // translate an id the app can now use directly. load() persists the row itself.
    return runCatching {
      when (type) {
        TMDB_TYPE_TV -> {
          val localId = showDetailsRepository.resolveTraktId(tmdbId.id) ?: return DeepLinkBundle.EMPTY
          DeepLinkBundle(show = showDetailsRepository.load(localId))
        }
        TMDB_TYPE_MOVIE -> {
          val localId = movieDetailsRepository.resolveTraktId(tmdbId.id) ?: return DeepLinkBundle.EMPTY
          DeepLinkBundle(movie = movieDetailsRepository.load(localId))
        }
        else -> DeepLinkBundle.EMPTY
      }
    }.getOrDefault(DeepLinkBundle.EMPTY)
  }
}
