package com.michaldrabik.data_remote.tvdb.api

import com.michaldrabik.data_remote.Config
import com.michaldrabik.data_remote.tvdb.TvdbRemoteDataSource
import com.michaldrabik.data_remote.tvdb.model.TvdbLoginRequest
import com.michaldrabik.data_remote.tvdb.model.TvdbSeries
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import retrofit2.HttpException

/**
 * TVDB hands out a bearer token in exchange for the API key. The token is good for
 * about a month, so it is kept in memory and only re-fetched when the API rejects
 * it - one login per app start rather than one per request.
 *
 * ponytail: in-memory, not persisted. Persisting saves one request per launch.
 */
internal class TvdbApi(
  private val service: TvdbService,
) : TvdbRemoteDataSource {

  private val loginLock = Mutex()

  @Volatile
  private var cachedToken: String? = null

  override suspend fun fetchSeries(tvdbId: Long): TvdbSeries? {
    if (tvdbId <= 0) return null
    val token = login()
    return try {
      service.fetchSeries(bearer(token), tvdbId, short = true).data
    } catch (error: HttpException) {
      if (error.code() != HTTP_UNAUTHORIZED) throw error
      // Expired or revoked. Drop it and try once more with a fresh one. A second
      // rejection is a real failure and is left to the caller.
      invalidate(token)
      service.fetchSeries(bearer(login()), tvdbId, short = true).data
    }
  }

  private suspend fun login(): String {
    cachedToken?.let { return it }
    return loginLock.withLock {
      // Another caller may have logged in while this one waited for the lock.
      cachedToken ?: run {
        val fresh = service
          .login(TvdbLoginRequest(Config.TVDB_API_KEY))
          .data
          ?.token
          ?.takeIf { it.isNotBlank() }
          ?: error("TVDB login returned no token.")
        cachedToken = fresh
        fresh
      }
    }
  }

  /** Only clears the token that actually failed, so a concurrent refresh survives. */
  private suspend fun invalidate(stale: String) {
    loginLock.withLock {
      if (cachedToken == stale) cachedToken = null
    }
  }

  private fun bearer(token: String) = "Bearer $token"

  private companion object {
    const val HTTP_UNAUTHORIZED = 401
  }
}
