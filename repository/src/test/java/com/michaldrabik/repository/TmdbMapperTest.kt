package com.michaldrabik.repository

import com.google.common.truth.Truth.assertThat
import com.michaldrabik.data_remote.tmdb.model.TmdbExternalIds
import com.michaldrabik.data_remote.tmdb.model.TmdbGenre
import com.michaldrabik.data_remote.tmdb.model.TmdbMovieDetails
import com.michaldrabik.data_remote.tmdb.model.TmdbNetwork
import com.michaldrabik.data_remote.tmdb.model.TmdbSearchItem
import com.michaldrabik.data_remote.tmdb.model.TmdbShowDetails
import com.michaldrabik.repository.mappers.IdsMapper
import com.michaldrabik.repository.mappers.MovieMapper
import com.michaldrabik.repository.mappers.ShowMapper
import com.michaldrabik.ui_model.MovieStatus
import com.michaldrabik.ui_model.ShowStatus
import org.junit.Test
import java.time.LocalDate

class TmdbMapperTest {

  private val showMapper = ShowMapper(IdsMapper())
  private val movieMapper = MovieMapper(IdsMapper())

  @Test
  fun `Should map show details and keep the resolved local id`() {
    val details = showDetails()

    val result = showMapper.fromTmdb(details, localId = 106499)

    // The local id must win - this is what keeps watch history attached.
    assertThat(result.ids.trakt.id).isEqualTo(106499)
    assertThat(result.ids.tmdb.id).isEqualTo(66008)
    assertThat(result.ids.imdb.id).isEqualTo("tt4635276")
    assertThat(result.ids.tvdb.id).isEqualTo(305574)
    assertThat(result.title).isEqualTo("You Me Her")
    assertThat(result.year).isEqualTo(2016)
    assertThat(result.runtime).isEqualTo(30)
    assertThat(result.network).isEqualTo("AT&T Audience Network")
    assertThat(result.airedEpisodes).isEqualTo(53)
    assertThat(result.genres).containsExactly("Comedy", "Drama")
  }

  @Test
  fun `Should translate TMDB title-case status onto the app's lowercase keys`() {
    val ended = showMapper.fromTmdb(showDetails(status = "Ended"), localId = 1)
    val returning = showMapper.fromTmdb(showDetails(status = "Returning Series"), localId = 1)

    assertThat(ended.status).isEqualTo(ShowStatus.ENDED)
    assertThat(returning.status).isEqualTo(ShowStatus.RETURNING)
  }

  @Test
  fun `Should expand a TMDB date into the instant format the app stores`() {
    val result = showMapper.fromTmdb(showDetails(), localId = 1)

    assertThat(result.firstAired).isEqualTo("2016-03-22T00:00:00Z")
  }

  @Test
  fun `Should leave first aired blank when TMDB has no date`() {
    val result = showMapper.fromTmdb(showDetails(firstAirDate = null), localId = 1)

    assertThat(result.firstAired).isEmpty()
  }

  @Test
  fun `Should map a show search hit`() {
    val item = TmdbSearchItem(
      id = 66008,
      media_type = "tv",
      title = null,
      name = "You Me Her",
      overview = "A suburban couple.",
      release_date = null,
      first_air_date = "2016-03-22",
      vote_average = 7.1f,
      vote_count = 120,
      genre_ids = null,
      origin_country = listOf("US"),
    )

    val result = showMapper.fromTmdbSearch(item, localId = 106499)

    assertThat(result.ids.trakt.id).isEqualTo(106499)
    assertThat(result.ids.tmdb.id).isEqualTo(66008)
    assertThat(result.title).isEqualTo("You Me Her")
    assertThat(result.country).isEqualTo("us")
  }

  @Test
  fun `Should map movie details and keep the resolved local id`() {
    val details = TmdbMovieDetails(
      production_companies = null,
      id = 82825,
      title = "Kahaani",
      overview = "A pregnant woman searches for her husband.",
      release_date = "2012-03-09",
      runtime = 122,
      homepage = "",
      status = "Released",
      vote_average = 7.6f,
      vote_count = 300,
      genres = listOf(TmdbGenre(80, "Crime")),
      origin_country = listOf("IN"),
      original_language = "hi",
      imdb_id = "tt1821480",
      external_ids = null,
    )

    val result = movieMapper.fromTmdb(details, localId = 61981)

    assertThat(result.ids.trakt.id).isEqualTo(61981)
    assertThat(result.ids.tmdb.id).isEqualTo(82825)
    assertThat(result.ids.imdb.id).isEqualTo("tt1821480")
    assertThat(result.title).isEqualTo("Kahaani")
    assertThat(result.released).isEqualTo(LocalDate.of(2012, 3, 9))
    assertThat(result.status).isEqualTo(MovieStatus.RELEASED)
    assertThat(result.language).isEqualTo("hi")
  }

  @Test
  fun `Should not throw when TMDB sends an unusable release date`() {
    listOf(null, "", "unknown").forEach { bad ->
      val details = TmdbMovieDetails(production_companies = null, id = 1, release_date = bad)

      val result = movieMapper.fromTmdb(details, localId = 1)

      assertThat(result.released).isNull()
    }
  }

  private fun showDetails(
    status: String? = "Ended",
    firstAirDate: String? = "2016-03-22",
  ) = TmdbShowDetails(
    production_companies = null,
    id = 66008,
    name = "You Me Her",
    overview = "A suburban couple.",
    first_air_date = firstAirDate,
    episode_run_time = listOf(30),
    homepage = "https://example.com",
    status = status,
    vote_average = 7.1f,
    vote_count = 120,
    genres = listOf(TmdbGenre(35, "Comedy"), TmdbGenre(18, "Drama")),
    networks = listOf(TmdbNetwork(1, "AT&T Audience Network")),
    origin_country = listOf("US"),
    number_of_episodes = 53,
    external_ids = TmdbExternalIds(imdb_id = "tt4635276", tvdb_id = 305574),
  )
}
