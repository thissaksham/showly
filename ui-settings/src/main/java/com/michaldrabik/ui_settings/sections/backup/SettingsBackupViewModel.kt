package com.michaldrabik.ui_settings.sections.backup

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.michaldrabik.repository.settings.SettingsRepository
import com.michaldrabik.ui_backup.features.google.CloudBackupWorker
import com.michaldrabik.ui_backup.features.google.GoogleAuthManager
import com.michaldrabik.ui_backup.features.google.cases.CloudBackupUseCase
import com.michaldrabik.ui_backup.features.google.cases.CloudRestoreUseCase
import com.michaldrabik.ui_base.Logger
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class SettingsBackupViewModel @Inject constructor(
  private val settingsRepository: SettingsRepository,
  private val googleAuthManager: GoogleAuthManager,
  private val cloudBackupUseCase: CloudBackupUseCase,
  private val cloudRestoreUseCase: CloudRestoreUseCase,
) : ViewModel() {

  private val _cloudBackupTimestamp = MutableStateFlow(settingsRepository.cloudBackupTimestamp)
  val cloudBackupTimestamp = _cloudBackupTimestamp.asStateFlow()

  private val _isGoogleConnected = MutableStateFlow(googleAuthManager.getSignedInAccount() != null)
  val isGoogleConnected = _isGoogleConnected.asStateFlow()

  private val _isLoading = MutableStateFlow(false)
  val isLoading = _isLoading.asStateFlow()

  private val _error = MutableStateFlow<Throwable?>(null)
  val error = _error.asStateFlow()

  private val _successMessage = MutableStateFlow<Int?>(null)
  val successMessage = _successMessage.asStateFlow()

  private val _shouldRestart = MutableStateFlow(false)
  val shouldRestart = _shouldRestart.asStateFlow()

  fun onGoogleAccountConnected(context: Context) {
    _isGoogleConnected.value = true
    CloudBackupWorker.schedule(context)
    runCloudRestore()
  }

  fun checkAndScheduleWorker(context: Context) {
    if (_isGoogleConnected.value) {
      CloudBackupWorker.schedule(context)
      _cloudBackupTimestamp.value = settingsRepository.cloudBackupTimestamp
    }
  }

  /**
   * Drops the Google session and the Drive grant, so reconnecting shows the consent
   * screen again. Needed when Drive access is revoked from outside the app: the
   * cached account still signs in silently and every Drive call fails.
   */
  fun disconnectGoogleAccount(context: Context) {
    CloudBackupWorker.cancel(context)
    googleAuthManager.disconnect {
      _isGoogleConnected.value = false
    }
  }

  fun runCloudBackup() {
    if (_isLoading.value) return
    viewModelScope.launch {
      try {
        _isLoading.value = true
        _error.value = null
        cloudBackupUseCase().fold(
          onSuccess = {
            updateCloudBackupTimestamp()
            _successMessage.value = com.michaldrabik.ui_settings.R.string.textSettingsCloudBackupSuccess
          },
          onFailure = { throw it }
        )
      } catch (e: Exception) {
        _error.value = e
        Logger.record(e, "SettingsBackupViewModel::runCloudBackup")
      } finally {
        _isLoading.value = false
      }
    }
  }

  fun runCloudRestore() {
    if (_isLoading.value) return
    viewModelScope.launch {
      try {
        _isLoading.value = true
        _error.value = null
        cloudRestoreUseCase().fold(
          onSuccess = {
            _successMessage.value = com.michaldrabik.ui_settings.R.string.textSettingsCloudRestoreSuccess
            _shouldRestart.value = true
          },
          onFailure = { throw it }
        )
      } catch (e: Exception) {
        _error.value = e
        Logger.record(e, "SettingsBackupViewModel::runCloudRestore")
      } finally {
        _isLoading.value = false
      }
    }
  }

  fun updateCloudBackupTimestamp() {
    val now = System.currentTimeMillis()
    settingsRepository.cloudBackupTimestamp = now
    _cloudBackupTimestamp.value = now
  }

  fun clearEvents() {
    _error.value = null
    _successMessage.value = null
  }
}
