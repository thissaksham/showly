package com.michaldrabik.ui_backup.features.google

import android.content.Context
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.api.client.extensions.android.http.AndroidHttp
import com.google.api.client.googleapis.extensions.android.gms.auth.GoogleAccountCredential
import com.google.api.client.http.ByteArrayContent
import com.google.api.client.json.gson.GsonFactory
import com.google.api.services.drive.Drive
import com.google.api.services.drive.DriveScopes
import com.google.api.services.drive.model.File
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import timber.log.Timber
import java.io.ByteArrayOutputStream
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class GoogleDriveManager @Inject constructor(
  @ApplicationContext private val context: Context,
) {

  companion object {
    const val BACKUP_FILE_NAME = "showly_plus_cloud_backup.json"

    /** The copy taken before each overwrite, so one bad upload stays recoverable. */
    const val PREVIOUS_BACKUP_FILE_NAME = "showly_plus_cloud_backup_previous.json"

    private const val APP_DATA_FOLDER = "appDataFolder"
  }

  private val driveService: Drive?
    get() {
      val account = GoogleSignIn.getLastSignedInAccount(context) ?: return null
      val credential = GoogleAccountCredential.usingOAuth2(context, listOf(DriveScopes.DRIVE_APPDATA))
      credential.selectedAccount = account.account

      return Drive
        .Builder(
          AndroidHttp.newCompatibleTransport(),
          GsonFactory.getDefaultInstance(),
          credential,
        ).setApplicationName("Showly+")
        .build()
    }

  /**
   * [force] skips the shrink guard. Only ever pass it from an explicit user
   * confirmation - the periodic worker must never set it.
   */
  suspend fun uploadBackup(
    jsonContent: String,
    fileName: String = BACKUP_FILE_NAME,
    force: Boolean = false,
  ): Result<Unit> = withContext(Dispatchers.IO) {
    try {
      val service = driveService ?: return@withContext Result.failure(Exception("Google Account not connected"))
      val incoming = BackupCounts.of(jsonContent)
      val existing = findFile(service, fileName)

      if (existing != null && incoming != null && !force) {
        val stored = storedCounts(service, existing)
        if (stored != null && incoming.wouldTruncate(stored)) {
          Timber.w("Blocked a backup of ${incoming.total} items over one of ${stored.total}.")
          return@withContext Result.failure(BackupShrinkException(stored, incoming))
        }
      }

      val content = ByteArrayContent.fromString("application/json", jsonContent)
      val properties = incoming?.toProperties()

      if (existing != null) {
        preservePrevious(service, existing)
        // No parents on an update - Drive rejects it. Only the properties change.
        service
          .files()
          .update(existing.id, File().apply { appProperties = properties }, content)
          .execute()
      } else {
        val metadata = File().apply {
          name = fileName
          parents = listOf(APP_DATA_FOLDER)
          appProperties = properties
        }
        service.files().create(metadata, content).execute()
      }

      Result.success(Unit)
    } catch (error: Exception) {
      Result.failure(error)
    }
  }

  suspend fun downloadBackup(fileName: String = BACKUP_FILE_NAME): Result<String> = withContext(Dispatchers.IO) {
    try {
      val service = driveService ?: return@withContext Result.failure(Exception("Google Account not connected"))
      val file = findFile(service, fileName)
        ?: return@withContext Result.failure(Exception("Backup not found on Google Drive"))
      Result.success(download(service, file.id))
    } catch (error: Exception) {
      Result.failure(error)
    }
  }

  /**
   * What is actually sitting in Drive, so a restore can be described before it runs
   * rather than after. Counts come from the file's own properties where possible; a
   * file written before those were recorded reports null rather than downloading
   * megabytes just to draw a list.
   */
  suspend fun listBackups(): Result<List<CloudBackupFile>> = withContext(Dispatchers.IO) {
    try {
      val service = driveService ?: return@withContext Result.failure(Exception("Google Account not connected"))
      val files = listOf(BACKUP_FILE_NAME, PREVIOUS_BACKUP_FILE_NAME).mapNotNull { name ->
        findFile(service, name)?.let {
          CloudBackupFile(
            fileName = name,
            isLatest = name == BACKUP_FILE_NAME,
            modifiedAt = it.modifiedTime?.value ?: 0L,
            counts = BackupCounts.fromProperties(it.appProperties),
          )
        }
      }
      Result.success(files)
    } catch (error: Exception) {
      Result.failure(error)
    }
  }

  /**
   * Counts recorded on the file when it was written. A file written before those were
   * recorded has to be downloaded once to be counted, which is exactly the upgrade
   * case - where the guard matters most.
   */
  private fun storedCounts(
    service: Drive,
    file: File,
  ): BackupCounts? =
    BackupCounts.fromProperties(file.appProperties)
      ?: runCatching { BackupCounts.of(download(service, file.id)) }
        .onFailure { Timber.w(it, "Could not read the stored backup to count it.") }
        .getOrNull()

  /**
   * Failing to keep the previous copy must not stop the backup itself, so this
   * swallows its errors - a missing safety net is better than no backup at all.
   */
  private fun preservePrevious(
    service: Drive,
    existing: File,
  ) {
    try {
      findFile(service, PREVIOUS_BACKUP_FILE_NAME)?.let { service.files().delete(it.id).execute() }
      val metadata = File().apply {
        name = PREVIOUS_BACKUP_FILE_NAME
        parents = listOf(APP_DATA_FOLDER)
      }
      service.files().copy(existing.id, metadata).execute()
    } catch (error: Exception) {
      Timber.w(error, "Could not keep a previous copy of the backup.")
    }
  }

  private fun download(
    service: Drive,
    fileId: String,
  ): String {
    val outputStream = ByteArrayOutputStream()
    service.files().get(fileId).executeMediaAndDownloadTo(outputStream)
    // Explicit: titles carry non-ASCII characters and the platform default encoding
    // is not guaranteed to be UTF-8.
    return outputStream.toString(Charsets.UTF_8.name())
  }

  private fun findFile(
    service: Drive,
    fileName: String,
  ): File? =
    service
      .files()
      .list()
      .setSpaces(APP_DATA_FOLDER)
      .setQ("name = '$fileName' and trashed = false")
      .setFields("files(id, name, appProperties, modifiedTime)")
      .execute()
      .files
      .firstOrNull()
}
