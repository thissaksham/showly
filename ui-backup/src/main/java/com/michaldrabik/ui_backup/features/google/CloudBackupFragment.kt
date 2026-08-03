package com.michaldrabik.ui_backup.features.google

import android.os.Bundle
import android.text.format.DateUtils
import android.view.View
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.updatePadding
import androidx.fragment.app.viewModels
import androidx.work.WorkInfo
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.jakewharton.processphoenix.ProcessPhoenix
import com.michaldrabik.ui_backup.R
import com.michaldrabik.ui_backup.databinding.FragmentBackupCloudBinding
import com.michaldrabik.ui_base.BaseFragment
import com.michaldrabik.ui_base.utilities.SnackbarHost
import com.michaldrabik.ui_base.utilities.extensions.doOnApplyWindowInsets
import com.michaldrabik.ui_base.utilities.extensions.launchAndRepeatStarted
import com.michaldrabik.ui_base.utilities.extensions.onClick
import com.michaldrabik.ui_base.utilities.extensions.showErrorSnackbar
import com.michaldrabik.ui_base.utilities.extensions.showInfoSnackbar
import com.michaldrabik.ui_base.utilities.extensions.visibleIf
import com.michaldrabik.ui_base.utilities.viewBinding
import dagger.hilt.android.AndroidEntryPoint
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import javax.inject.Inject

@AndroidEntryPoint
class CloudBackupFragment : BaseFragment<CloudBackupViewModel>(R.layout.fragment_backup_cloud) {

  @Inject
  lateinit var googleAuthManager: GoogleAuthManager

  override val viewModel by viewModels<CloudBackupViewModel>()
  private val binding by viewBinding(FragmentBackupCloudBinding::bind)

  /** Set once a restore has been seen running, so its result is acted on only once. */
  private var isRestoreRunning = false

  /**
   * The warning survives across emissions until the dialog is dismissed, and finishing
   * the backup emits again straight after raising it - without this the dialog opens twice.
   */
  private var isShrinkDialogShown = false

  private val googleSignInLauncher = registerForActivityResult(
    ActivityResultContracts.StartActivityForResult(),
  ) { result ->
    val task = GoogleSignIn.getSignedInAccountFromIntent(result.data)
    if (task.isSuccessful) {
      viewModel.onConnected(requireContext().applicationContext)
    } else {
      snack(getString(R.string.errorCloudBackupSignIn), isError = true)
    }
  }

  override fun onViewCreated(
    view: View,
    savedInstanceState: Bundle?,
  ) {
    super.onViewCreated(view, savedInstanceState)
    setupView()
    setupInsets()
    observeRestoreWork()

    launchAndRepeatStarted(
      { viewModel.uiState.collect { render(it) } },
    )

    viewModel.refresh(requireContext().applicationContext)
  }

  private fun setupView() {
    with(binding) {
      toolbar.onClick { activity?.onBackPressed() }
      toolbar.inflateMenu(R.menu.menu_cloud_backup)
      toolbar.setOnMenuItemClickListener { item ->
        if (item.itemId == R.id.menuCloudDisconnect) {
          showDisconnectDialog()
          true
        } else {
          false
        }
      }
      connectButton.onClick { googleSignInLauncher.launch(googleAuthManager.getSignInIntent()) }
      backupButton.onClick { viewModel.runBackup() }
      restoreButton.onClick { showRestorePicker() }
    }
  }

  private fun setupInsets() {
    with(binding) {
      root.doOnApplyWindowInsets { view, insets, padding, _ ->
        val inset = insets.getInsets(WindowInsetsCompat.Type.systemBars())
        view.updatePadding(
          top = padding.top + inset.top,
          bottom = padding.bottom + inset.bottom,
        )
      }
    }
  }

  private fun render(uiState: CloudBackupUiState) {
    with(binding) {
      connectButton.visibleIf(!uiState.isConnected)
      backupButton.visibleIf(uiState.isConnected)
      restoreButton.visibleIf(uiState.isConnected && uiState.backups.isNotEmpty())
      toolbar.menu.findItem(R.id.menuCloudDisconnect)?.isVisible = uiState.isConnected

      progressBar.visibleIf(uiState.isBusy)
      backupButton.isEnabled = !uiState.isBusy
      restoreButton.isEnabled = !uiState.isBusy

      timestampText.text = timestampLabel(uiState.lastBackupTimestamp)
      statusText.visibleIf(uiState.isBusy)
      statusText.text = when {
        uiState.isRestoring -> getString(R.string.textCloudRestoreInProgress)
        uiState.isBackingUp -> getString(R.string.textCloudBackupInProgress)
        else -> ""
      }
    }

    uiState.shrinkWarning?.let {
      if (!isShrinkDialogShown) showShrinkDialog(it)
    }
    uiState.successMessage?.let {
      snack(getString(it), isError = false)
      viewModel.clearEvents()
    }
    uiState.restoreSkippedCount?.let { skipped ->
      if (skipped > 0) {
        snack(getString(R.string.textCloudRestoreSkipped, skipped), isError = true)
        viewModel.clearEvents()
      } else {
        viewModel.clearEvents()
        snack(getString(R.string.textCloudRestoreSuccess), isError = false)
        ProcessPhoenix.triggerRebirth(requireContext().applicationContext)
      }
    }
    uiState.error?.let {
      snack(it.message?.takeIf { m -> m.isNotBlank() } ?: getString(R.string.errorCloudBackupGeneral), isError = true)
      viewModel.clearEvents()
    }
  }

  /**
   * Restore state comes from WorkManager, not the view model: it keeps running after
   * this screen is gone and may already be part way through when it is reopened.
   */
  private fun observeRestoreWork() {
    CloudRestoreWorker.workInfo(requireContext().applicationContext).observe(viewLifecycleOwner) { infos ->
      val info = infos?.firstOrNull() ?: return@observe
      when (info.state) {
        WorkInfo.State.ENQUEUED, WorkInfo.State.RUNNING -> {
          isRestoreRunning = true
          viewModel.onRestoreStateChanged(true)
        }
        WorkInfo.State.SUCCEEDED -> {
          val wasRunning = isRestoreRunning
          isRestoreRunning = false
          viewModel.onRestoreStateChanged(false)
          if (wasRunning) {
            viewModel.onRestoreFinished(info.outputData.getInt(CloudRestoreWorker.KEY_SKIPPED_COUNT, 0))
          }
        }
        WorkInfo.State.FAILED, WorkInfo.State.CANCELLED -> {
          val wasRunning = isRestoreRunning
          isRestoreRunning = false
          viewModel.onRestoreStateChanged(false)
          if (wasRunning) {
            val message = info.outputData.getString(CloudRestoreWorker.KEY_ERROR)
            viewModel.onRestoreFailed(Exception(message?.takeIf { it.isNotBlank() } ?: getString(R.string.errorCloudBackupGeneral)))
          }
        }
        WorkInfo.State.BLOCKED -> Unit
      }
    }
  }

  /**
   * A picker rather than a second button: which copy is being restored, and how much
   * is in it, is exactly what the user needs to know before a 15 minute job starts.
   */
  private fun showRestorePicker() {
    val backups = viewModel.uiState.value.backups
    if (backups.isEmpty()) return

    val labels = backups.map { backupLabel(it) }.toTypedArray()
    MaterialAlertDialogBuilder(requireContext(), R.style.AlertDialog)
      .setBackground(ContextCompat.getDrawable(requireContext(), R.drawable.bg_dialog))
      .setTitle(R.string.textCloudRestorePickerTitle)
      .setItems(labels) { dialog, index ->
        viewModel.runRestore(requireContext().applicationContext, backups[index].fileName)
        snack(getString(R.string.textCloudRestoreStarted), isError = false)
        dialog.dismiss()
      }.show()
  }

  private fun backupLabel(file: CloudBackupFile): String {
    val name = getString(
      if (file.isLatest) R.string.textCloudRestoreLatest else R.string.textCloudRestorePrevious,
    )
    val date = if (file.modifiedAt > 0) {
      DATE_FORMAT.format(Instant.ofEpochMilli(file.modifiedAt).atZone(ZoneId.systemDefault()))
    } else {
      getString(R.string.textCloudRestoreUnknownDate)
    }
    val counts = file.counts?.let { getString(R.string.textCloudRestoreItems, it.total) }.orEmpty()
    return listOf(name, date, counts).filter { it.isNotBlank() }.joinToString("  ·  ")
  }

  private fun showDisconnectDialog() {
    MaterialAlertDialogBuilder(requireContext(), R.style.AlertDialog)
      .setBackground(ContextCompat.getDrawable(requireContext(), R.drawable.bg_dialog))
      .setTitle(R.string.textCloudBackupDisconnect)
      .setMessage(R.string.textCloudBackupDisconnectConfirm)
      .setPositiveButton(R.string.textCloudBackupDisconnect) { dialog, _ ->
        viewModel.disconnect(requireContext().applicationContext)
        dialog.dismiss()
      }.setNegativeButton(android.R.string.cancel) { dialog, _ -> dialog.dismiss() }
      .show()
  }

  /** The guard refused to replace a much larger backup with a much smaller one. */
  private fun showShrinkDialog(warning: BackupShrinkException) {
    isShrinkDialogShown = true
    MaterialAlertDialogBuilder(requireContext(), R.style.AlertDialog)
      .setBackground(ContextCompat.getDrawable(requireContext(), R.drawable.bg_dialog))
      .setTitle(R.string.textCloudBackupShrinkTitle)
      .setMessage(getString(R.string.textCloudBackupShrinkMessage, warning.stored.total, warning.incoming.total))
      .setPositiveButton(R.string.textCloudBackupOverwrite) { dialog, _ ->
        viewModel.runBackup(force = true)
        dialog.dismiss()
      }.setNegativeButton(android.R.string.cancel) { dialog, _ -> dialog.dismiss() }
      .setOnDismissListener {
        isShrinkDialogShown = false
        viewModel.clearEvents()
      }.show()
  }

  private fun timestampLabel(timestamp: Long): String =
    if (timestamp > 0) {
      val relative = DateUtils.getRelativeTimeSpanString(
        timestamp,
        System.currentTimeMillis(),
        DateUtils.MINUTE_IN_MILLIS,
      )
      getString(R.string.textCloudBackupLastTimestamp, relative)
    } else {
      getString(R.string.textCloudBackupLastTimestampNever)
    }

  private fun snack(
    message: String,
    isError: Boolean,
  ) {
    val host = (requireActivity() as SnackbarHost).provideSnackbarLayout()
    if (isError) host.showErrorSnackbar(message) else host.showInfoSnackbar(message)
  }

  private companion object {
    val DATE_FORMAT: DateTimeFormatter = DateTimeFormatter.ofPattern("d MMM, HH:mm")
  }
}
