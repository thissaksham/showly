package com.michaldrabik.data_local.database.dao

import androidx.room.Dao
import androidx.room.MapColumn
import androidx.room.Query
import androidx.room.Transaction
import com.michaldrabik.data_local.database.model.Show
import com.michaldrabik.data_local.database.model.ShowSearch
import com.michaldrabik.data_local.sources.ShowsLocalDataSource

@Dao
interface ShowsDao :
  BaseDao<Show>,
  ShowsLocalDataSource {

  @Query("SELECT * FROM shows")
  override suspend fun getAll(): List<Show>

  @Query("SELECT * FROM shows WHERE id_trakt IN (:ids)")
  override suspend fun getAll(ids: List<Long>): List<Show>

  @Query("SELECT id_trakt, id_tmdb FROM shows WHERE id_trakt IN (:traktIds)")
  override suspend fun getAllTmdbIds(
    traktIds: List<Long>,
  ): Map<@MapColumn(columnName = "id_trakt") Long, @MapColumn(columnName = "id_tmdb") Long>

  @Query("SELECT shows.id_trakt, shows.title FROM shows")
  override suspend fun getAllForSearch(): List<ShowSearch>

  @Transaction
  override suspend fun getAllChunked(ids: List<Long>): List<Show> =
    ids
      .chunked(500)
      .fold(mutableListOf()) { acc, chunk ->
        acc += getAll(chunk)
        acc
      }

  @Query("SELECT * FROM shows WHERE id_trakt == :traktId")
  override suspend fun getById(traktId: Long): Show?

  @Query("SELECT * FROM shows WHERE id_tmdb == :tmdbId")
  override suspend fun getByTmdbId(tmdbId: Long): Show?

  @Query("SELECT * FROM shows WHERE id_tmdb IN (:tmdbIds)")
  override suspend fun getByTmdbIds(tmdbIds: List<Long>): List<Show>

  @Query("SELECT * FROM shows WHERE id_slug == :slug")
  override suspend fun getBySlug(slug: String): Show?

  @Query("SELECT * FROM shows WHERE id_imdb == :imdbId")
  override suspend fun getById(imdbId: String): Show?

  @Query("DELETE FROM shows where id_trakt == :traktId")
  override suspend fun deleteById(traktId: Long)

  @Query("UPDATE seasons SET id_show_trakt = :mainShowId WHERE id_show_trakt = :duplicateShowId")
  override suspend fun moveSeasons(duplicateShowId: Long, mainShowId: Long)

  @Query("UPDATE episodes SET id_show_trakt = :mainShowId WHERE id_show_trakt = :duplicateShowId")
  override suspend fun moveEpisodes(duplicateShowId: Long, mainShowId: Long)

  @Query("UPDATE OR IGNORE shows_my_shows SET id_trakt = :mainShowId WHERE id_trakt = :duplicateShowId")
  override suspend fun moveMyShow(duplicateShowId: Long, mainShowId: Long)

  @Query("UPDATE OR IGNORE shows_see_later SET id_trakt = :mainShowId WHERE id_trakt = :duplicateShowId")
  override suspend fun moveWatchlistShow(duplicateShowId: Long, mainShowId: Long)

  @Query("UPDATE OR IGNORE shows_archive SET id_trakt = :mainShowId WHERE id_trakt = :duplicateShowId")
  override suspend fun moveArchiveShow(duplicateShowId: Long, mainShowId: Long)

  @Transaction
  override suspend fun upsert(shows: List<Show>) {
    val result = insert(shows)

    val updateList = mutableListOf<Show>()
    result.forEachIndexed { index, id ->
      if (id == -1L) updateList.add(shows[index])
    }

    if (updateList.isNotEmpty()) update(updateList)
  }

  @Query("SELECT id_tmdb FROM shows WHERE id_tmdb > 0 GROUP BY id_tmdb HAVING COUNT(id_tmdb) > 1")
  override suspend fun getDuplicatesByTmdbId(): List<Long>
}
