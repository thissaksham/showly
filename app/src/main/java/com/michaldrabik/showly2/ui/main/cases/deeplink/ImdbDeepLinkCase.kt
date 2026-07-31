package com.michaldrabik.showly2.ui.main.cases.deeplink

import com.michaldrabik.data_remote.RemoteDataSource
import com.michaldrabik.repository.movies.MovieDetailsRepository
import com.michaldrabik.repository.shows.ShowDetailsRepository
import com.michaldrabik.showly2.utilities.deeplink.DeepLinkBundle
import com.michaldrabik.ui_model.IdImdb
import javax.inject.Inject

class ImdbDeepLinkCase @Inject constructor(
  private val remoteSource: RemoteDataSource,
  private val showDetailsRepository: ShowDetailsRepository,
  private val movieDetailsRepository: MovieDetailsRepository,
) {

  suspend fun findById(imdbId: IdImdb): DeepLinkBundle {
    val show = showDetailsRepository.find(imdbId)
    if (show != null) {
      return DeepLinkBundle(show = show)
    }

    val movie = movieDetailsRepository.find(imdbId)
    if (movie != null) {
      return DeepLinkBundle(movie = movie)
    }

    // This used to ask Trakt to translate the IMDB id. TMDB's find endpoint does the
    // same job; load() then fetches full details and persists the row.
    return runCatching {
      val results = remoteSource.tmdb.findByImdbId(imdbId.id)

      results.tv_results?.firstOrNull()?.let { item ->
        val localId = showDetailsRepository.resolveTraktId(item.id) ?: return DeepLinkBundle.EMPTY
        return DeepLinkBundle(show = showDetailsRepository.load(localId))
      }

      results.movie_results?.firstOrNull()?.let { item ->
        val localId = movieDetailsRepository.resolveTraktId(item.id) ?: return DeepLinkBundle.EMPTY
        return DeepLinkBundle(movie = movieDetailsRepository.load(localId))
      }

      DeepLinkBundle.EMPTY
    }.getOrDefault(DeepLinkBundle.EMPTY)
  }
}
