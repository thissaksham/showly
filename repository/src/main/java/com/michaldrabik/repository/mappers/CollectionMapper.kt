package com.michaldrabik.repository.mappers

import com.michaldrabik.common.extensions.nowUtc
import com.michaldrabik.data_remote.tmdb.model.TmdbCollection
import com.michaldrabik.ui_model.IdTrakt
import com.michaldrabik.ui_model.MovieCollection
import java.time.ZonedDateTime
import javax.inject.Inject
import com.michaldrabik.data_local.database.model.MovieCollection as MovieCollectionEntity

class CollectionMapper @Inject constructor() {

  /**
   * [localId] is minted from the TMDB collection id, so it cannot collide with the
   * Trakt collection ids already stored. [itemCount] must be a real count: -1 is the
   * app's "this movie has no collection" marker and gets filtered out downstream.
   */
  fun fromTmdb(
    input: TmdbCollection,
    localId: Long,
    itemCount: Int,
  ): MovieCollection =
    MovieCollection(
      id = IdTrakt(localId),
      name = input.name ?: "",
      description = input.overview ?: "",
      itemCount = itemCount,
    )

  fun fromEntity(input: MovieCollectionEntity): MovieCollection =
    MovieCollection(
      id = IdTrakt(input.idTrakt),
      name = input.name,
      description = input.description,
      itemCount = input.itemCount,
    )

  fun toEntity(
    movieId: Long,
    input: MovieCollection,
    updatedAt: ZonedDateTime = nowUtc(),
    createdAt: ZonedDateTime = nowUtc(),
  ): MovieCollectionEntity =
    MovieCollectionEntity(
      idTrakt = input.id.id,
      idTraktMovie = movieId,
      name = input.name,
      description = input.description,
      itemCount = input.itemCount,
      updatedAt = updatedAt,
      createdAt = createdAt,
    )
}
