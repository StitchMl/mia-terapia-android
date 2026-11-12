package it.miaterapia.family.sync

import android.app.Activity
import android.app.PendingIntent
import android.content.Intent
import com.google.android.gms.auth.api.identity.AuthorizationRequest
import com.google.android.gms.auth.api.identity.Identity
import com.google.android.gms.common.api.Scope

object DriveAuthorization {
    const val DRIVE_FILE_SCOPE = "https://www.googleapis.com/auth/drive.file"

    fun authorize(activity: Activity, resolution: (PendingIntent) -> Unit, token: (String) -> Unit, error: (String) -> Unit) {
        val request = AuthorizationRequest.builder().setRequestedScopes(listOf(Scope(DRIVE_FILE_SCOPE))).build()
        Identity.getAuthorizationClient(activity).authorize(request)
            .addOnSuccessListener { result ->
                when {
                    result.hasResolution() -> result.pendingIntent?.let(resolution) ?: error("Consenso Google non disponibile")
                    result.accessToken != null -> token(result.accessToken!!)
                    else -> error("Google non ha restituito un token")
                }
            }
            .addOnFailureListener { error(it.localizedMessage ?: "Autorizzazione Google non riuscita") }
    }

    fun tokenFromResult(activity: Activity, data: Intent?): String =
        Identity.getAuthorizationClient(activity).getAuthorizationResultFromIntent(data ?: Intent()).accessToken
            ?: error("Google non ha restituito un token")
}
