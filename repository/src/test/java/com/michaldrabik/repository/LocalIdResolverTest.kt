package com.michaldrabik.repository

import com.google.common.truth.Truth.assertThat
import com.michaldrabik.data_local.database.dao.MoviesDao
import com.michaldrabik.data_local.database.dao.ShowsDao
import com.michaldrabik.data_local.database.model.Movie
import com.michaldrabik.data_local.database.model.Show
import com.michaldrabik.repository.common.BaseMockTest
import com.michaldrabik.repository.utilities.LocalIdResolver
import io.mockk.coEvery
import io.mockk.impl.annotations.MockK
import kotlinx.coroutines.runBlocking
import org.junit.Before
import org.junit.Test

class LocalIdResolverTest : BaseMockTest() {

  @MockK
  lateinit var showsDao: ShowsDao

  @MockK
  lateinit var moviesDao: MoviesDao

  private lateinit var SUT: LocalIdResolver

  @Before
  override fun setUp() {
    super.setUp()
    coEvery { database.shows } returns showsDao
    coEvery { database.movies } returns moviesDao
    SUT = LocalIdResolver(database)
  }

  @Test
  fun `Should keep the existing local id for a show already in the database`() {
    runBlocking {
      // "You Me Her" from a real backup export: trakt 106499, tmdb 66008.
      coEvery { showsDao.getByTmdbId(66008) } returns show(idTrakt = 106499, idTmdb = 66008)

      assertThat(SUT.showId(66008)).isEqualTo(106499)
    }
  }

  @Test
  fun `Should mint a negative local id for a show not in the database`() {
    runBlocking {
      coEvery { showsDao.getByTmdbId(66008) } returns null

      assertThat(SUT.showId(66008)).isEqualTo(-66009)
    }
  }

  @Test
  fun `Should keep the existing local id for a movie already in the database`() {
    runBlocking {
      // "Kahaani" from a real backup export: trakt 61981, tmdb 82825.
      coEvery { moviesDao.getByTmdbId(82825) } returns movie(idTrakt = 61981, idTmdb = 82825)

      assertThat(SUT.movieId(82825)).isEqualTo(61981)
    }
  }

  @Test
  fun `Should mint a negative local id for a movie not in the database`() {
    runBlocking {
      coEvery { moviesDao.getByTmdbId(82825) } returns null

      assertThat(SUT.movieId(82825)).isEqualTo(-82826)
    }
  }

  @Test
  fun `Minted ids should never collide with stored Trakt ids`() {
    // Trakt only ever issued positive ids, so a minted id must be negative.
    listOf(1L, 66008L, Long.MAX_VALUE).forEach { tmdbId ->
      assertThat(LocalIdResolver.newId(tmdbId)).isLessThan(0L)
    }
  }

  @Test
  fun `Should reject ids that would negate into a valid Trakt id`() {
    // id_tmdb defaults to -1 in the schema. Negating that yields 1 - a real Trakt
    // id - which would silently attach data to someone else's show.
    listOf(-1L, 0L).forEach { invalid ->
      val error = runCatching { LocalIdResolver.newId(invalid) }.exceptionOrNull()
      assertThat(error).isInstanceOf(IllegalArgumentException::class.java)
    }
  }

  @Test
  fun `Should recover the TMDB id only from a minted id`() {
    assertThat(LocalIdResolver.tmdbIdOf(-66009)).isEqualTo(66008)
    // Pre-migration rows carry their TMDB id in the id_tmdb column, not in the key.
    assertThat(LocalIdResolver.tmdbIdOf(106499)).isNull()
  }

  @Test
  fun `Should not mistake the unknown-id sentinel for a minted id`() {
    // IdTrakt() defaults to -1. Reading that as a TMDB id fetched tv/1 - a random
    // unrelated show - and displayed it in place of the one the user tapped.
    assertThat(LocalIdResolver.tmdbIdOf(-1)).isNull()
  }

  @Test
  fun `Should never mint the unknown-id sentinel`() {
    // TMDB id 1 is a real show. Negating it plainly would produce -1.
    assertThat(LocalIdResolver.newId(1)).isNotEqualTo(-1L)
    assertThat(LocalIdResolver.tmdbIdOf(LocalIdResolver.newId(1))).isEqualTo(1L)
  }

  private fun show(
    idTrakt: Long,
    idTmdb: Long,
  ) = Show(
    idTrakt = idTrakt,
    idTvdb = -1,
    idTmdb = idTmdb,
    idImdb = "",
    idSlug = "",
    idTvrage = -1,
    title = "",
    year = -1,
    overview = "",
    firstAired = "",
    runtime = -1,
    airtimeDay = "",
    airtimeTime = "",
    airtimeTimezone = "",
    certification = "",
    network = "",
    country = "",
    trailer = "",
    homepage = "",
    status = "",
    rating = -1f,
    votes = -1,
    commentCount = -1,
    genres = "",
    airedEpisodes = -1,
    createdAt = -1,
    updatedAt = -1,
  )

  private fun movie(
    idTrakt: Long,
    idTmdb: Long,
  ) = Movie(
    idTrakt = idTrakt,
    idTmdb = idTmdb,
    idImdb = "",
    idSlug = "",
    title = "",
    year = -1,
    overview = "",
    released = "",
    runtime = -1,
    country = "",
    trailer = "",
    language = "",
    homepage = "",
    status = "",
    rating = -1f,
    votes = -1,
    commentCount = -1,
    genres = "",
    updatedAt = -1,
    createdAt = -1,
  )
}
