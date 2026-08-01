package com.michaldrabik.ui_settings.sections.backup

import android.os.Bundle
import android.text.format.DateUtils
import android.view.View
import androidx.activity.result.contract.ActivityResultContracts
import androidx.fragment.app.viewModels
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.jakewharton.processphoenix.ProcessPhoenix
import com.michaldrabik.ui_backup.features.google.GoogleAuthManager
import com.michaldrabik.ui_base.BaseFragment
import com.michaldrabik.ui_base.utilities.events.MessageEvent
import com.michaldrabik.ui_base.utilities.extensions.launchAndRepeatStarted
import com.michaldrabik.ui_base.utilities.extensions.onClick
import com.michaldrabik.ui_base.utilities.extensions.showErrorSnackbar
import com.michaldrabik.ui_base.utilities.extensions.visibleIf
import com.michaldrabik.ui_base.utilities.viewBinding
import com.michaldrabik.ui_settings.R
import com.michaldrabik.ui_settings.databinding.FragmentSettingsBackupBinding
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

@AndroidEntryPoint
class SettingsBackupFragment : BaseFragment<SettingsBackupViewModel>(R.layout.fragment_settings_backup) {

  @Inject
  lateinit var googleAuthManager: GoogleAuthManager

  override val viewModel by viewModels<SettingsBackupViewModel>()
  private val binding by viewBinding(FragmentSettingsBackupBinding::bind)

  private val googleSignInLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
    val task = GoogleSignIn.getSignedInAccountFromIntent(result.data)
    if (task.isSuccessful) {
      viewModel.onGoogleAccountConnected(requireContext().applicationContext)
    } else {
      showSnack(MessageEvent.Error(R.string.errorGeneral))
    }
  }

  override fun onViewCreated(
    view: View,
    savedInstanceState: Bundle?,
  ) {
    super.onViewCreated(view, savedInstanceState)
    setupView()
    viewModel.checkAndScheduleWorker(requireContext().applicationContext)

    launchAndRepeatStarted(
      { viewModel.cloudBackupTimestamp.collect { renderTimestamp(it) } },
      { viewModel.isGoogleConnected.collect { renderGoogleState(it) } },
      { viewModel.isLoading.collect { renderLoading(it) } },
      { viewModel.error.collect { handleError(it) } },
      { viewModel.successMessage.collect { handleSuccess(it) } },
      { viewModel.shouldRestart.collect { handleRestart(it) } }
    )
  }

  private fun setupView() {
    with(binding) {
      settingsBackupExport.onClick {
        navigateTo(R.id.actionSettingsFragmentToBackupExport)
      }
      settingsBackupImport.onClick {
        navigateTo(R.id.actionSettingsFragmentToBackupImport)
      }
      settingsBackupCloudConnect.onClick {
        googleSignInLauncher.launch(googleAuthManager.getSignInIntent())
      }
      settingsBackupCloudDisconnect.onClick {
        viewModel.disconnectGoogleAccount(requireContext().applicationContext)
      }
      settingsBackupCloudBackupNow.onClick {
        viewModel.runCloudBackup()
      }
      settingsBackupCloudRestore.onClick {
        viewModel.runCloudRestore()
      }
    }
  }

  private fun renderGoogleState(isConnected: Boolean) {
    with(binding) {
      settingsBackupCloudConnect.visibleIf(!isConnected)
      settingsBackupCloudActions.visibleIf(isConnected)
    }
  }

  private fun renderLoading(isLoading: Boolean) {
    with(binding) {
      settingsBackupCloudProgress.visibleIf(isLoading)
      settingsBackupCloudBackupNow.isEnabled = !isLoading
      settingsBackupCloudRestore.isEnabled = !isLoading
    }
  }

  private fun renderTimestamp(timestamp: Long) {
    with(binding) {
      if (timestamp > 0) {
        val relativeTime = DateUtils.getRelativeTimeSpanString(
          timestamp,
          System.currentTimeMillis(),
          DateUtils.MINUTE_IN_MILLIS,
        )
        settingsBackupCloudTimestamp.text = getString(R.string.textSettingsCloudBackupLastTimestamp, relativeTime)
      } else {
        settingsBackupCloudTimestamp.text = getString(R.string.textSettingsCloudBackupLastTimestampNever)
      }
    }
  }

  private fun handleSuccess(messageRes: Int?) {
    messageRes?.let {
      showSnack(MessageEvent.Info(it))
      viewModel.clearEvents()
    }
  }

  private fun handleError(error: Throwable?) {
    error?.let {
      // Google says exactly what is wrong - permission revoked, sign-in needed,
      // no network. Showing "something went wrong" instead makes cloud backup
      // impossible to diagnose from the device.
      val message = it.message?.takeIf { msg -> msg.isNotBlank() }
      if (message != null) {
        binding.root.showErrorSnackbar(message)
      } else {
        showSnack(MessageEvent.Error(R.string.errorGeneral))
      }
      viewModel.clearEvents()
    }
  }

  private fun handleRestart(shouldRestart: Boolean) {
    if (shouldRestart) {
      ProcessPhoenix.triggerRebirth(requireContext().applicationContext)
    }
  }
}
