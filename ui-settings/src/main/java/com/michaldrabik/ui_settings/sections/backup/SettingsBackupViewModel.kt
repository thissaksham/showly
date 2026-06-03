package com.michaldrabik.ui_settings.sections.backup

import androidx.lifecycle.ViewModel
import com.michaldrabik.repository.settings.SettingsRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject

@HiltViewModel
class SettingsBackupViewModel @Inject constructor(
  private val settingsRepository: SettingsRepository,
) : ViewModel() {

  private val _cloudBackupTimestamp = MutableStateFlow(settingsRepository.cloudBackupTimestamp)
  val cloudBackupTimestamp = _cloudBackupTimestamp.asStateFlow()

  fun updateCloudBackupTimestamp() {
    val now = System.currentTimeMillis()
    settingsRepository.cloudBackupTimestamp = now
    _cloudBackupTimestamp.value = now
  }
}
