package com.prtracker.data

import android.content.Context
import android.content.Intent
import com.google.android.gms.auth.GoogleAuthUtil
import com.google.android.gms.auth.UserRecoverableAuthException
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.gms.auth.api.signin.GoogleSignInAccount
import com.google.android.gms.auth.api.signin.GoogleSignInOptions
import com.google.android.gms.common.api.ApiException
import com.google.android.gms.common.api.CommonStatusCodes
import com.google.android.gms.common.api.Scope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class DriveAuthManager(private val context: Context) {
    private val options = GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
        .requestEmail()
        .requestScopes(Scope(DRIVE_APPDATA_SCOPE))
        .build()
    private val client = GoogleSignIn.getClient(context, options)

    fun signInIntent(): Intent = client.signInIntent

    fun handleSignInResult(data: Intent?): SignInResult {
        return try {
            val account = GoogleSignIn.getSignedInAccountFromIntent(data)
                .getResult(ApiException::class.java)
            SignInResult.Success(account.email ?: "Google account")
        } catch (error: ApiException) {
            val statusName = CommonStatusCodes.getStatusCodeString(error.statusCode)
            val setupHint = if (error.statusCode == CommonStatusCodes.DEVELOPER_ERROR) {
                " Check the Google Cloud Android OAuth client package name and SHA-1."
            } else {
                ""
            }
            SignInResult.Failure("Google sign-in failed: $statusName (${error.statusCode}).$setupHint")
        } catch (error: Exception) {
            SignInResult.Failure(error.message ?: "Google sign-in failed.")
        }
    }

    fun accountEmail(): String? = GoogleSignIn.getLastSignedInAccount(context)?.email
    fun hasDrivePermission(): Boolean = currentAccount() != null

    suspend fun signOut() {
        withContext(Dispatchers.IO) {
            client.signOut()
        }
    }

    suspend fun accessToken(): AuthTokenResult = withContext(Dispatchers.IO) {
        val account = currentAccount() ?: return@withContext AuthTokenResult.NotSignedIn
        val androidAccount = account.account ?: return@withContext AuthTokenResult.NotSignedIn
        try {
            AuthTokenResult.Token(
                GoogleAuthUtil.getToken(context, androidAccount, "oauth2:$DRIVE_APPDATA_SCOPE"),
            )
        } catch (recoverable: UserRecoverableAuthException) {
            recoverable.intent
                ?.let { AuthTokenResult.NeedsResolution(it) }
                ?: AuthTokenResult.Failure("Google Drive authorization could not be opened.")
        } catch (error: Exception) {
            AuthTokenResult.Failure(error.message ?: "Unable to authorize Google Drive.")
        }
    }

    private fun currentAccount(): GoogleSignInAccount? {
        val account = GoogleSignIn.getLastSignedInAccount(context) ?: return null
        return if (GoogleSignIn.hasPermissions(account, Scope(DRIVE_APPDATA_SCOPE))) account else null
    }

    companion object {
        const val DRIVE_APPDATA_SCOPE = "https://www.googleapis.com/auth/drive.appdata"
    }
}

sealed interface AuthTokenResult {
    data class Token(val value: String) : AuthTokenResult
    data class NeedsResolution(val intent: Intent) : AuthTokenResult
    data class Failure(val message: String) : AuthTokenResult
    data object NotSignedIn : AuthTokenResult
}

sealed interface SignInResult {
    data class Success(val accountEmail: String) : SignInResult
    data class Failure(val message: String) : SignInResult
}
