package com.michaldrabik.repository.utilities

/**
 * TVDB reports an air time but no timezone - the time is local to the show's
 * country of origin. This turns that country into a zone.
 *
 * The mapping is verified rather than guessed: Netflix drops a title worldwide at
 * midnight Pacific, and TVDB records that same moment as 03:00 for `usa`, 12:30 for
 * `ind`, 08:00 for `gbr`, 17:00 for `kor` and 09:00 for `swe`/`esp`/`pol`. Every one
 * of those lands back on 00:00 Pacific through the zones below.
 *
 * Countries that span several zones use the one their television actually schedules
 * against - Eastern for the US, which is what a "9pm" slot means there.
 *
 * ponytail: covers the countries that appear in a real library. An unlisted country
 * returns null and the episode keeps its date-only behaviour rather than guessing.
 */
object AirTimeZones {

  private val ZONES = mapOf(
    "usa" to "America/New_York",
    "can" to "America/Toronto",
    "mex" to "America/Mexico_City",
    "bra" to "America/Sao_Paulo",
    "arg" to "America/Argentina/Buenos_Aires",
    "col" to "America/Bogota",
    "chl" to "America/Santiago",
    "gbr" to "Europe/London",
    "irl" to "Europe/Dublin",
    "fra" to "Europe/Paris",
    "deu" to "Europe/Berlin",
    "aut" to "Europe/Vienna",
    "che" to "Europe/Zurich",
    "esp" to "Europe/Madrid",
    "prt" to "Europe/Lisbon",
    "ita" to "Europe/Rome",
    "nld" to "Europe/Amsterdam",
    "bel" to "Europe/Brussels",
    "dnk" to "Europe/Copenhagen",
    "swe" to "Europe/Stockholm",
    "nor" to "Europe/Oslo",
    "fin" to "Europe/Helsinki",
    "isl" to "Atlantic/Reykjavik",
    "pol" to "Europe/Warsaw",
    "cze" to "Europe/Prague",
    "hun" to "Europe/Budapest",
    "rou" to "Europe/Bucharest",
    "grc" to "Europe/Athens",
    "ukr" to "Europe/Kyiv",
    "rus" to "Europe/Moscow",
    "tur" to "Europe/Istanbul",
    "isr" to "Asia/Jerusalem",
    "are" to "Asia/Dubai",
    "sau" to "Asia/Riyadh",
    "zaf" to "Africa/Johannesburg",
    "nga" to "Africa/Lagos",
    "egy" to "Africa/Cairo",
    "ind" to "Asia/Kolkata",
    "pak" to "Asia/Karachi",
    "chn" to "Asia/Shanghai",
    "hkg" to "Asia/Hong_Kong",
    "twn" to "Asia/Taipei",
    "jpn" to "Asia/Tokyo",
    "kor" to "Asia/Seoul",
    "tha" to "Asia/Bangkok",
    "vnm" to "Asia/Ho_Chi_Minh",
    "phl" to "Asia/Manila",
    "idn" to "Asia/Jakarta",
    "mys" to "Asia/Kuala_Lumpur",
    "sgp" to "Asia/Singapore",
    "aus" to "Australia/Sydney",
    "nzl" to "Pacific/Auckland",
  )

  fun zoneIdOf(country: String?): String? = country?.lowercase()?.let { ZONES[it] }
}
