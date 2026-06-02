package com.michaldrabik.ui_progress.progress.cases

import com.michaldrabik.repository.settings.SettingsRepository
import com.michaldrabik.ui_model.SortOrder
import com.michaldrabik.ui_model.SortType
import javax.inject.Inject

class ProgressSortOrderCase @Inject constructor(
  private val settingsRepository: SettingsRepository,
) {

  fun setSortOrder(sortOrder: SortOrder, sortType: SortType, newAtTop: Boolean) {
    settingsRepository.sorting.progressShowsSortOrder = sortOrder
    settingsRepository.sorting.progressShowsSortType = sortType
    settingsRepository.sorting.progressShowsNewAtTop = newAtTop
  }

  fun loadSortOrder(): Triple<SortOrder, SortType, Boolean> {
    val sort = settingsRepository.sorting.progressShowsSortOrder
    val type = settingsRepository.sorting.progressShowsSortType
    val newAtTop = settingsRepository.sorting.progressShowsNewAtTop
    return Triple(sort, type, newAtTop)
  }
}
