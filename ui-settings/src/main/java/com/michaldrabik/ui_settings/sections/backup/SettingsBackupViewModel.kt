package com.michaldrabik.ui_settings.sections.backup

import androidx.lifecycle.ViewModel
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject

/**
 * Nothing left to hold: this section only navigates now. Kept because BaseFragment
 * requires a view model.
 */
@HiltViewModel
class SettingsBackupViewModel @Inject constructor() : ViewModel()
