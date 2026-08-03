package com.michaldrabik.ui_settings.sections.backup

import android.os.Bundle
import android.view.View
import androidx.fragment.app.viewModels
import com.michaldrabik.ui_base.BaseFragment
import com.michaldrabik.ui_base.utilities.extensions.onClick
import com.michaldrabik.ui_base.utilities.viewBinding
import com.michaldrabik.ui_settings.R
import com.michaldrabik.ui_settings.databinding.FragmentSettingsBackupBinding
import dagger.hilt.android.AndroidEntryPoint

/**
 * Three rows, three screens. Cloud backup used to be rendered inline here with its
 * own buttons, sign-in flow and progress; it has its own screen now, so this section
 * only has to navigate.
 */
@AndroidEntryPoint
class SettingsBackupFragment : BaseFragment<SettingsBackupViewModel>(R.layout.fragment_settings_backup) {

  override val viewModel by viewModels<SettingsBackupViewModel>()
  private val binding by viewBinding(FragmentSettingsBackupBinding::bind)

  override fun onViewCreated(
    view: View,
    savedInstanceState: Bundle?,
  ) {
    super.onViewCreated(view, savedInstanceState)
    with(binding) {
      settingsBackupExport.onClick { navigateTo(R.id.actionSettingsFragmentToBackupExport) }
      settingsBackupImport.onClick { navigateTo(R.id.actionSettingsFragmentToBackupImport) }
      settingsBackupCloud.onClick { navigateTo(R.id.actionSettingsFragmentToBackupCloud) }
    }
  }
}
