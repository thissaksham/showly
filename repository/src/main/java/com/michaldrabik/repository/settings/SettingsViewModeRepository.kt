package com.michaldrabik.repository.settings

import android.content.SharedPreferences
import com.michaldrabik.repository.utilities.IntPreference
import javax.inject.Inject
import javax.inject.Named
import javax.inject.Singleton

@Singleton
class SettingsViewModeRepository @Inject constructor(
  @Named("miscPreferences") private var preferences: SharedPreferences,
) {

  companion object Key {
    private const val TABLET_GRID_SPAN_SIZE = "TABLET_GRID_SPAN_SIZE"
  }

  var tabletGridSpanSize by IntPreference(preferences, TABLET_GRID_SPAN_SIZE, 3)
}
