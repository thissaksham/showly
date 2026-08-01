package com.michaldrabik.data_remote.di.module

import com.michaldrabik.data_remote.tvdb.TvdbRemoteDataSource
import com.michaldrabik.data_remote.tvdb.api.TvdbApi
import com.michaldrabik.data_remote.tvdb.api.TvdbService
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import retrofit2.Retrofit
import javax.inject.Named
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object TvdbModule {

  @Provides
  @Singleton
  fun providesTvdbApi(
    @Named("retrofitTvdb") retrofit: Retrofit,
  ): TvdbRemoteDataSource = TvdbApi(retrofit.create(TvdbService::class.java))
}
