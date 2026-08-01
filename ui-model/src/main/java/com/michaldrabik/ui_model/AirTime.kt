package com.michaldrabik.ui_model

/**
 * [time] is local to [timezone], which is a zone id like "Asia/Kolkata". Both are
 * blank when the air time is unknown, which is the case for anything that has not
 * been looked up on TVDB.
 */
data class AirTime(
  val day: String,
  val time: String,
  val timezone: String,
) {
  companion object {
    val EMPTY = AirTime(day = "", time = "", timezone = "")
  }
}
