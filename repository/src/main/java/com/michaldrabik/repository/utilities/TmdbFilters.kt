package com.michaldrabik.repository.utilities

import com.michaldrabik.ui_model.Genre
import com.michaldrabik.ui_model.Network

/**
 * Translates the app's filter options into the TMDB ids the discover endpoints expect.
 *
 * The app's genres are Trakt slugs. TMDB keeps two separate genre lists and its TV one
 * is coarser: it merges Action with Adventure and Sci-Fi with Fantasy, and has no
 * History, Horror, Romance or Thriller at all. A genre with no TV equivalent is left
 * out of the query rather than mapped to something approximate, so the filter simply
 * does not narrow instead of returning the wrong shows.
 */
object TmdbFilters {

  private val SHOW_GENRES = mapOf(
    Genre.ACTION to 10759L, // Action & Adventure
    Genre.ADVENTURE to 10759L,
    Genre.ANIMATION to 16L,
    Genre.ANIME to 16L,
    Genre.COMEDY to 35L,
    Genre.CRIME to 80L,
    Genre.DOCUMENTARY to 99L,
    Genre.DRAMA to 18L,
    Genre.FANTASY to 10765L, // Sci-Fi & Fantasy
    Genre.SF to 10765L,
    Genre.WAR to 10768L, // War & Politics
    Genre.WESTERN to 37L,
  )

  private val MOVIE_GENRES = mapOf(
    Genre.ACTION to 28L,
    Genre.ADVENTURE to 12L,
    Genre.ANIMATION to 16L,
    Genre.ANIME to 16L,
    Genre.COMEDY to 35L,
    Genre.CRIME to 80L,
    Genre.DOCUMENTARY to 99L,
    Genre.DRAMA to 18L,
    Genre.FANTASY to 14L,
    Genre.HISTORY to 36L,
    Genre.HORROR to 27L,
    Genre.SF to 878L,
    Genre.ROMANCE to 10749L,
    Genre.THRILLER to 53L,
    Genre.WAR to 10752L,
    Genre.WESTERN to 37L,
  )

  // TMDB network ids. The app groups a broadcaster's channels under one option, so
  // where that matters both ids are listed and matched as OR.
  private val NETWORKS = mapOf(
    Network.ABC to listOf(2L),
    Network.AMC to listOf(174L),
    Network.APPLE to listOf(2552L),
    Network.AMAZON to listOf(1024L),
    Network.BBC to listOf(4L, 332L),
    Network.CBS to listOf(16L),
    Network.CRUNCHYROLL to listOf(1112L),
    Network.CW to listOf(71L),
    Network.DISCOVERY to listOf(64L),
    Network.DISNEY to listOf(2739L),
    Network.HBO to listOf(49L, 3186L),
    Network.FOX to listOf(19L),
    Network.HULU to listOf(453L),
    Network.ITV to listOf(9L),
    Network.NBC to listOf(6L),
    Network.NETFLIX to listOf(213L),
    Network.PARAMOUNT to listOf(4330L),
    Network.PEACOCK to listOf(3353L),
    Network.RAKUTEN to listOf(3185L),
    Network.SHOWTIME to listOf(67L),
  )

  fun showGenreIds(genres: List<Genre>): List<Long> = genres.mapNotNull { SHOW_GENRES[it] }.distinct()

  fun movieGenreIds(genres: List<Genre>): List<Long> = genres.mapNotNull { MOVIE_GENRES[it] }.distinct()

  fun networkIds(networks: List<Network>): List<Long> = networks.flatMap { NETWORKS[it].orEmpty() }.distinct()

  /** "|" is TMDB's OR; an empty filter must be null so the query drops it entirely. */
  fun query(ids: List<Long>): String? = ids.takeIf { it.isNotEmpty() }?.joinToString("|")
}
