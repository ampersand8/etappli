package com.nuelto.etappli.data

import android.content.Context
import androidx.credentials.CredentialManager
import androidx.credentials.CustomCredential
import androidx.credentials.GetCredentialRequest
import androidx.credentials.exceptions.GetCredentialCancellationException
import androidx.credentials.exceptions.NoCredentialException
import com.google.android.libraries.identity.googleid.GetGoogleIdOption
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.GoogleAuthProvider
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.tasks.await

data class AuthUser(val uid: String)

interface AuthRepository {
    val authState: Flow<AuthUser?>
    val currentUser: AuthUser?

    /** Returns null on success, or a user-displayable error message. */
    suspend fun signInWithGoogle(activityContext: Context): String?
    fun signOut()
}

/** Google Sign-In via Credential Manager, backed by Firebase Auth. */
class FirebaseAuthRepository(
    private val auth: FirebaseAuth,
    private val webClientId: String,
) : AuthRepository {

    override val authState: Flow<AuthUser?> = callbackFlow {
        val listener = FirebaseAuth.AuthStateListener { trySend(it.currentUser?.let { u -> AuthUser(u.uid) }) }
        auth.addAuthStateListener(listener)
        awaitClose { auth.removeAuthStateListener(listener) }
    }

    override val currentUser: AuthUser? get() = auth.currentUser?.let { AuthUser(it.uid) }

    override suspend fun signInWithGoogle(activityContext: Context): String? {
        if (webClientId.isBlank()) {
            return "Missing webClientId in local.properties — see FIREBASE_SETUP.md"
        }
        return try {
            val option = GetGoogleIdOption.Builder()
                .setFilterByAuthorizedAccounts(false)
                .setServerClientId(webClientId)
                .build()
            val request = GetCredentialRequest.Builder()
                .addCredentialOption(option)
                .build()
            val credential = CredentialManager.create(activityContext)
                .getCredential(activityContext, request)
                .credential
            if (credential is CustomCredential &&
                credential.type == GoogleIdTokenCredential.TYPE_GOOGLE_ID_TOKEN_CREDENTIAL
            ) {
                val idToken = GoogleIdTokenCredential.createFrom(credential.data).idToken
                auth.signInWithCredential(GoogleAuthProvider.getCredential(idToken, null)).await()
                null
            } else {
                "Unexpected credential type"
            }
        } catch (e: GetCredentialCancellationException) {
            null // user dismissed the picker; not an error worth showing
        } catch (e: NoCredentialException) {
            "No Google account on this device — add one in system settings, then try again."
        } catch (e: Exception) {
            e.message ?: "Sign-in failed"
        }
    }

    override fun signOut() {
        auth.signOut()
    }
}
