package cz.hodiny.google

import android.content.Context
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.gms.auth.api.signin.GoogleSignInClient
import com.google.android.gms.auth.api.signin.GoogleSignInOptions
import com.google.android.gms.common.api.Scope
import com.google.api.client.googleapis.extensions.android.gms.auth.GoogleAccountCredential
import com.google.api.services.drive.DriveScopes
import com.google.api.services.sheets.v4.SheetsScopes
import kotlinx.coroutines.tasks.await

object GoogleManager {

    private val SCOPES = listOf(SheetsScopes.SPREADSHEETS, DriveScopes.DRIVE_FILE)

    fun getSignInClient(context: Context): GoogleSignInClient {
        val options = GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
            .requestEmail()
            .requestScopes(Scope(SheetsScopes.SPREADSHEETS), Scope(DriveScopes.DRIVE_FILE))
            .build()
        return GoogleSignIn.getClient(context, options)
    }

    fun isSignedIn(context: Context): Boolean =
        GoogleSignIn.getLastSignedInAccount(context) != null

    fun getEmail(context: Context): String? =
        GoogleSignIn.getLastSignedInAccount(context)?.email

    fun getCredential(context: Context): GoogleAccountCredential? {
        val account = GoogleSignIn.getLastSignedInAccount(context) ?: return null
        return GoogleAccountCredential.usingOAuth2(context, SCOPES).apply {
            selectedAccount = account.account
        }
    }

    suspend fun signOut(context: Context) {
        getSignInClient(context).signOut().await()
    }
}
