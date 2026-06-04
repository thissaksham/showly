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
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class GoogleDriveManager @Inject constructor(
  @ApplicationContext private val context: Context,
) {

  private val driveService: Drive?
    get() {
      val account = GoogleSignIn.getLastSignedInAccount(context) ?: return null
      val credential = GoogleAccountCredential.usingOAuth2(context, listOf(DriveScopes.DRIVE_APPDATA))
      credential.selectedAccount = account.account

      return Drive.Builder(
        AndroidHttp.newCompatibleTransport(),
        GsonFactory.getDefaultInstance(),
        credential
      ).setApplicationName("Showly+").build()
    }

  suspend fun uploadBackup(jsonContent: String, fileName: String): Result<Unit> = withContext(Dispatchers.IO) {
    try {
      val service = driveService ?: return@withContext Result.failure(Exception("Google Account not connected"))

      // Find existing file to update or create new
      val existingFileId = findFileId(service, fileName)

      val metadata = File().apply {
        name = fileName
        parents = listOf("appDataFolder")
      }

      val content = ByteArrayContent.fromString("application/json", jsonContent)

      if (existingFileId != null) {
        service.files().update(existingFileId, null, content).execute()
      } else {
        service.files().create(metadata, content).execute()
      }

      Result.success(Unit)
    } catch (e: Exception) {
      Result.failure(e)
    }
  }

  suspend fun downloadBackup(fileName: String): Result<String> = withContext(Dispatchers.IO) {
    try {
      val service = driveService ?: return@withContext Result.failure(Exception("Google Account not connected"))

      val fileId = findFileId(service, fileName) ?: return@withContext Result.failure(Exception("Backup not found on Google Drive"))

      val outputStream = java.io.ByteArrayOutputStream()
      service.files().get(fileId).executeMediaAndDownloadTo(outputStream)

      Result.success(outputStream.toString())
    } catch (e: Exception) {
      Result.failure(e)
    }
  }

  private fun findFileId(service: Drive, fileName: String): String? {
    val result = service.files().list()
      .setSpaces("appDataFolder")
      .setQ("name = '$fileName'")
      .setFields("files(id, name)")
      .execute()

    return result.files.firstOrNull()?.id
  }
}
