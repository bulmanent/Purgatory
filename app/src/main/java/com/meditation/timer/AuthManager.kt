package com.meditation.timer

import android.content.Context
import com.google.android.gms.auth.GoogleAuthUtil
import com.google.android.gms.auth.UserRecoverableAuthException
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.gms.auth.api.signin.GoogleSignInAccount
import com.google.android.gms.auth.api.signin.GoogleSignInOptions
import com.google.android.gms.common.api.Scope

object AuthManager {
    private const val SHEETS_SCOPE = "https://www.googleapis.com/auth/spreadsheets"

    fun getSignInClient(context: Context) =
        GoogleSignIn.getClient(
            context,
            GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
                .requestEmail()
                .requestScopes(Scope(SHEETS_SCOPE))
                .build()
        )

    fun getSignedInAccount(context: Context): GoogleSignInAccount? =
        GoogleSignIn.getLastSignedInAccount(context)

    @Throws(UserRecoverableAuthException::class)
    fun getAccessToken(context: Context, account: GoogleSignInAccount): String {
        val scope = "oauth2:$SHEETS_SCOPE"
        return GoogleAuthUtil.getToken(context, account.account, scope)
    }
}
