package com.michaldrabik.ui_model

enum class UpcomingFilter {
  OFF,
  UPCOMING,
  FINISHED,
  ONGOING,
  ;

  fun isActive() = this != OFF
}
