package com.michaldrabik.repository

import com.google.common.truth.Truth.assertThat
import com.michaldrabik.data_remote.tmdb.model.TmdbEpisode
import com.michaldrabik.data_remote.tvdb.model.TvdbSeries
import com.michaldrabik.repository.mappers.EpisodeMapper
import com.michaldrabik.repository.mappers.IdsMapper
import com.michaldrabik.repository.mappers.ShowMapper
import com.michaldrabik.ui_model.AirTime
import org.junit.Test
import java.time.Instant
import java.time.ZonedDateTime

/**
 * TMDB gives a date and no time, so every episode used to land at 00:00 UTC.
 * These pin down what the TVDB air time does with that date.
 *
 * Instants are compared rather than ZonedDateTimes: the two carry different zones
 * by design and only the moment matters.
 */
class EpisodeAirTimeTest {

  private val episodeMapper = EpisodeMapper()
  private val showMapper = ShowMapper(IdsMapper())

  @Test
  fun `Should place a broadcast episode at its network slot`() {
    // 9-1-1 on FOX: TVDB says 20:00, usa. 8pm Eastern is midnight UTC.
    val aired = airedAt("2026-05-07", airTime(time = "20:00", country = "usa"))

    assertThat(aired).isEqualTo(instant("2026-05-08T00:00Z"))
  }

  @Test
  fun `Should read one global release the same way from every country`() {
    // Netflix drops a title worldwide at midnight Pacific. TVDB records that one
    // moment differently per country, and all of them must come back to it. These
    // five times are taken verbatim from TVDB records of Netflix originals.
    val sameDrop = listOf(
      airTime(time = "03:00", country = "usa"),
      airTime(time = "12:30", country = "ind"),
      airTime(time = "08:00", country = "gbr"),
      airTime(time = "16:00", country = "kor"),
      airTime(time = "09:00", country = "swe"),
    ).map { airedAt("2026-08-07", it) }

    assertThat(sameDrop.distinct()).hasSize(1)
    assertThat(sameDrop.first()).isEqualTo(instant("2026-08-07T07:00Z"))
  }

  @Test
  fun `Should not pretend a stored time tracks daylight saving`() {
    // TVDB stores one fixed local time, recorded whenever the show premiered. The
    // same Netflix drop reads 16:00 in Seoul over a Pacific summer and 17:00 over a
    // Pacific winter, so records made either side of a clock change sit an hour
    // apart. Nothing in the data says which, and this asserts we do not paper over it.
    val summerRecord = airedAt("2026-08-07", airTime(time = "16:00", country = "kor"))
    val winterRecord = airedAt("2026-08-07", airTime(time = "17:00", country = "kor"))

    assertThat(winterRecord).isEqualTo(summerRecord!!.plusSeconds(3600))
  }

  @Test
  fun `Should keep midnight UTC when the country has no known timezone`() {
    // Unchanged behaviour beats a guessed one: an unmapped country must not shift.
    val aired = airedAt("2026-08-07", airTime(time = "21:00", country = "zzz"))

    assertThat(aired).isEqualTo(instant("2026-08-07T00:00Z"))
  }

  @Test
  fun `Should keep midnight UTC when TVDB has no air time`() {
    val aired = airedAt("2026-08-07", showMapper.airTimeFromTvdb(TvdbSeries(null, "usa", null)))

    assertThat(aired).isEqualTo(instant("2026-08-07T00:00Z"))
  }

  @Test
  fun `Should shift an early streaming date forward to the official air day`() {
    // Lucky: TMDB says Tuesday (Aug 4), but TVDB says Wednesday (Aug 5) at 00:00.
    val aired = airedAt(
      airDate = "2026-08-04",
      airTime = AirTime(day = "wednesday", time = "00:00", timezone = "America/New_York")
    )

    // Should land on Wednesday 5th, 09:30 AM IST (04:00 UTC)
    assertThat(aired).isEqualTo(instant("2026-08-05T04:00Z"))
  }

  @Test
  fun `Should leave an unaired episode without a date`() {
    assertThat(airedAt(null, airTime(time = "20:00", country = "usa"))).isNull()
    assertThat(airedAt("", airTime(time = "20:00", country = "usa"))).isNull()
  }

  @Test
  fun `Should survive an air time it cannot parse`() {
    // Falls back rather than throwing halfway through a season sync.
    val aired = airedAt("2026-08-07", AirTime(day = "", time = "8 PM", timezone = "America/New_York"))

    assertThat(aired).isEqualTo(instant("2026-08-07T00:00Z"))
  }

  private fun instant(text: String): Instant = ZonedDateTime.parse(text).toInstant()

  private fun airTime(
    time: String,
    country: String,
  ) = showMapper.airTimeFromTvdb(TvdbSeries(airsTime = time, originalCountry = country, airsDays = null))

  private fun airedAt(
    airDate: String?,
    airTime: AirTime,
  ): Instant? =
    episodeMapper
      .fromTmdb(
        episode = TmdbEpisode(
          id = 1,
          name = "",
          overview = "",
          air_date = airDate,
          episode_number = 1,
          season_number = 1,
          runtime = 42,
          vote_average = 0f,
          vote_count = 0,
        ),
        localId = -2,
        airTime = airTime,
      ).firstAired
      ?.toInstant()
}
