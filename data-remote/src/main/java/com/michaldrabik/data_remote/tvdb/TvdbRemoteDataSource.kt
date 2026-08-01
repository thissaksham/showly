package com.michaldrabik.data_remote.tvdb

import com.michaldrabik.data_remote.tvdb.model.TvdbSeries

/**
 * Fetch remote resources via TVDB API
 */
interface TvdbRemoteDataSource {
  suspend fun fetchSeries(tvdbId: Long): TvdbSeries?
}
