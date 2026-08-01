package com.michaldrabik.ui_backup.features.google

import android.content.Context
import android.content.Intent
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.gms.auth.api.signin.GoogleSignInAccount
import com.google.android.gms.auth.api.signin.GoogleSignInOptions
import com.google.android.gms.common.api.Scope
import com.google.api.services.drive.DriveScopes
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class GoogleAuthManager @Inject constructor(
  @ApplicationContext private val context: Context,
) {

  private val gso = GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
    .requestEmail()
    .requestScopes(Scope(DriveScopes.DRIVE_APPDATA))
    .build()

  private val googleSignInClient = GoogleSignIn.getClient(context, gso)

  fun getSignInIntent(): Intent = googleSignInClient.signInIntent

  fun getSignedInAccount(): GoogleSignInAccount? = GoogleSignIn.getLastSignedInAccount(context)

  fun hasDrivePermission(): Boolean {
    val account = getSignedInAccount() ?: return false
    return GoogleSignIn.hasPermissions(account, Scope(DriveScopes.DRIVE_APPDATA))
  }

  fun signOut(onComplete: () -> Unit) {
    googleSignInClient.signOut().addOnCompleteListener { onComplete() }
  }

  /**
   * Signing out only clears the local session, which is not enough once Drive access
   * has been revoked outside the app: the cached account is still returned, sign-in
   * succeeds without showing the consent screen again, and every Drive call then
   * fails. Revoking drops the grant as well, so the next sign-in asks properly.
   */
  fun disconnect(onComplete: () -> Unit) {
    googleSignInClient.revokeAccess().addOnCompleteListener {
      googleSignInClient.signOut().addOnCompleteListener { onComplete() }
    }
  }
}
