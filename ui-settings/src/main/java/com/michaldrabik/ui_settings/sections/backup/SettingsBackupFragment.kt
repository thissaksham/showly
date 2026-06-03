package com.michaldrabik.ui_settings.sections.backup

import android.app.backup.BackupManager
import android.content.ComponentName
import android.content.Intent
import android.os.Bundle
import android.provider.Settings
import android.text.format.DateUtils
import android.view.View
import androidx.fragment.app.viewModels
import com.michaldrabik.ui_base.BaseFragment
import com.michaldrabik.ui_base.utilities.extensions.launchAndRepeatStarted
import com.michaldrabik.ui_base.utilities.extensions.onClick
import com.michaldrabik.ui_base.utilities.extensions.visibleIf
import com.michaldrabik.ui_base.utilities.viewBinding
import com.michaldrabik.ui_settings.R
import com.michaldrabik.ui_settings.databinding.FragmentSettingsBackupBinding
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class SettingsBackupFragment : BaseFragment<SettingsBackupViewModel>(R.layout.fragment_settings_backup) {

  override val viewModel by viewModels<SettingsBackupViewModel>()
  private val binding by viewBinding(FragmentSettingsBackupBinding::bind)

  override fun onViewCreated(
    view: View,
    savedInstanceState: Bundle?,
  ) {
    super.onViewCreated(view, savedInstanceState)
    setupView()

    launchAndRepeatStarted(
      { viewModel.cloudBackupTimestamp.collect { renderTimestamp(it) } },
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
      settingsBackupCloud.onClick {
        val intents = listOf(
          // 1. Direct Google Backup Activity (Play Services)
          Intent().apply {
            component = ComponentName(
              "com.google.android.gms",
              "com.google.android.gms.backup.component.BackupSettingsActivity",
            )
          },
          // 2. Standard Backup and Reset Settings
          Intent("android.settings.BACKUP_AND_RESET_SETTINGS"),
          // 3. Fallback to general settings
          Intent(Settings.ACTION_SETTINGS),
        )

        for (intent in intents) {
          try {
            startActivity(intent)
            viewModel.updateCloudBackupTimestamp()
            BackupManager(requireContext()).dataChanged()
            return@onClick
          } catch (_: Exception) {
            // Try next intent
          }
        }
      }
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
}
