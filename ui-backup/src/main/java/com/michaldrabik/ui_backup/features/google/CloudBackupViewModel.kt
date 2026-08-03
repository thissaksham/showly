package com.michaldrabik.ui_backup.features.google

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.michaldrabik.repository.settings.SettingsRepository
import com.michaldrabik.ui_backup.R
import com.michaldrabik.ui_backup.features.google.cases.CloudBackupUseCase
import com.michaldrabik.ui_base.Logger
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class CloudBackupViewModel @Inject constructor(
  private val settingsRepository: SettingsRepository,
  private val googleAuthManager: GoogleAuthManager,
  private val googleDriveManager: GoogleDriveManager,
  private val cloudBackupUseCase: CloudBackupUseCase,
) : ViewModel() {

  // ponytail: one state object rather than nine flows through combine, which only
  // has typed overloads up to five anyway.
  private val state = MutableStateFlow(
    CloudBackupUiState(
      isConnected = googleAuthManager.getSignedInAccount() != null,
      lastBackupTimestamp = settingsRepository.cloudBackupTimestamp,
    ),
  )
  val uiState = state.asStateFlow()

  fun onConnected(context: Context) {
    state.update { it.copy(isConnected = true) }
    CloudBackupWorker.schedule(context)
    loadBackups()
  }

  fun refresh(context: Context) {
    val isConnected = googleAuthManager.getSignedInAccount() != null
    state.update {
      it.copy(
        isConnected = isConnected,
        lastBackupTimestamp = settingsRepository.cloudBackupTimestamp,
      )
    }
    if (isConnected) {
      CloudBackupWorker.schedule(context)
      loadBackups()
    }
  }

  /**
   * Drops the Google session and the Drive grant, so reconnecting shows the consent
   * screen again. Signing out alone leaves the cached account signing in silently
   * once Drive access has been revoked outside the app, and every call then fails.
   */
  fun disconnect(context: Context) {
    CloudBackupWorker.cancel(context)
    googleAuthManager.disconnect {
      state.update { it.copy(isConnected = false, backups = emptyList()) }
    }
  }

  private fun loadBackups() {
    viewModelScope.launch {
      googleDriveManager
        .listBackups()
        .onSuccess { files -> state.update { it.copy(backups = files) } }
        .onFailure { Logger.record(it, "CloudBackupViewModel::loadBackups") }
    }
  }

  fun runBackup(force: Boolean = false) {
    if (state.value.isBusy) return
    viewModelScope.launch {
      try {
        state.update { it.copy(isBackingUp = true, error = null) }
        cloudBackupUseCase(force).fold(
          onSuccess = {
            val now = System.currentTimeMillis()
            settingsRepository.cloudBackupTimestamp = now
            state.update {
              it.copy(lastBackupTimestamp = now, successMessage = R.string.textCloudBackupSuccess)
            }
            loadBackups()
          },
          onFailure = { throw it },
        )
      } catch (error: BackupShrinkException) {
        // Not a failure: the user is asked whether replacing a large backup with a
        // much smaller one is really what they meant.
        state.update { it.copy(shrinkWarning = error) }
      } catch (error: Exception) {
        state.update { it.copy(error = error) }
        Logger.record(error, "CloudBackupViewModel::runBackup")
      } finally {
        state.update { it.copy(isBackingUp = false) }
      }
    }
  }

  /**
   * Handed to a worker rather than run here. A restore takes many minutes, and a
   * coroutine owned by this screen dies the moment the user leaves it - which used to
   * leave a half imported library behind with nothing said about it.
   */
  fun runRestore(
    context: Context,
    fileName: String,
  ) {
    CloudRestoreWorker.start(context, fileName)
  }

  fun onRestoreStateChanged(isRunning: Boolean) = state.update { it.copy(isRestoring = isRunning) }

  fun onRestoreFinished(skippedCount: Int) {
    state.update { it.copy(restoreSkippedCount = skippedCount) }
    loadBackups()
  }

  fun onRestoreFailed(error: Throwable) = state.update { it.copy(error = error) }

  fun clearEvents() {
    state.update {
      it.copy(error = null, successMessage = null, shrinkWarning = null, restoreSkippedCount = null)
    }
  }
}
