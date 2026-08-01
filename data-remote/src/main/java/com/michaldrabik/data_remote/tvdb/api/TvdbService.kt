package com.michaldrabik.data_remote.tvdb.api

import com.michaldrabik.data_remote.tvdb.model.TvdbLoginRequest
import com.michaldrabik.data_remote.tvdb.model.TvdbLoginResponse
import com.michaldrabik.data_remote.tvdb.model.TvdbSeriesResponse
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.Header
import retrofit2.http.POST
import retrofit2.http.Path
import retrofit2.http.Query

interface TvdbService {

  @POST("login")
  suspend fun login(
    @Body request: TvdbLoginRequest,
  ): TvdbLoginResponse

  /**
   * `airsTime` lives on the extended endpoint only - the plain `series/{id}` one
   * returns null for it. `short=true` drops the episode/artwork/cast payload,
   * which is everything we do not need.
   */
  @GET("series/{id}/extended")
  suspend fun fetchSeries(
    @Header("Authorization") auth: String,
    @Path("id") tvdbId: Long,
    @Query("short") short: Boolean,
  ): TvdbSeriesResponse
}
