package com.example.data.remote

import android.content.Context
import android.util.Log
import com.example.domain.model.UserRole
import com.example.domain.model.UserSession
import android.app.Activity
import com.google.firebase.FirebaseException
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.auth.GoogleAuthProvider
import com.google.firebase.auth.PhoneAuthCredential
import com.google.firebase.auth.PhoneAuthOptions
import com.google.firebase.auth.PhoneAuthProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import java.util.concurrent.TimeUnit

/**
 * Service handling Firebase Authentication (Email/Password + Google Sign-In).
 * Provides Firebase ID token used to authenticate calls to Supabase Edge Functions.
 */
class FirebaseAuthService(private val context: Context) {

    companion object {
        private const val TAG = "FirebaseAuthService"
        const val WEB_CLIENT_ID = "791839339295-hrvsao6av3ndccilkflg855gdn8fpkpp.apps.googleusercontent.com"
    }

    private val auth: FirebaseAuth? by lazy {
        try {
            FirebaseAuth.getInstance()
        } catch (e: Exception) {
            Log.w(TAG, "FirebaseAuth initialization fallback: ${e.message}")
            null
        }
    }

    /**
     * Obtains the current Firebase user ID token to send in `Authorization: Bearer <token>`
     */
    suspend fun getIdToken(forceRefresh: Boolean = false): String? = withContext(Dispatchers.IO) {
        try {
            val user = auth?.currentUser
            if (user != null) {
                val result = user.getIdToken(forceRefresh).await()
                return@withContext result.token
            }
        } catch (e: Exception) {
            Log.w(TAG, "getIdToken failed: ${e.message}")
        }
        return@withContext null
    }

    /**
     * Signs in with Email and Password
     */
    suspend fun signInWithEmail(email: String, password: String): Result<FirebaseUser?> =
        withContext(Dispatchers.IO) {
            try {
                val firebaseAuth = auth
                    ?: return@withContext Result.failure(IllegalStateException("Firebase Auth not initialized"))
                val authResult = firebaseAuth.signInWithEmailAndPassword(email, password).await()
                Result.success(authResult.user)
            } catch (e: Exception) {
                Log.e(TAG, "signInWithEmail failed: ${e.message}", e)
                Result.failure(e)
            }
        }

    /**
     * Registers a new user with Email and Password
     */
    suspend fun signUpWithEmail(email: String, password: String): Result<FirebaseUser?> =
        withContext(Dispatchers.IO) {
            try {
                val firebaseAuth = auth
                    ?: return@withContext Result.failure(IllegalStateException("Firebase Auth not initialized"))
                val authResult = firebaseAuth.createUserWithEmailAndPassword(email, password).await()
                Result.success(authResult.user)
            } catch (e: Exception) {
                Log.e(TAG, "signUpWithEmail failed: ${e.message}", e)
                Result.failure(e)
            }
        }

    /**
     * Signs in with Google credentials (ID token from Credential Manager)
     */
    suspend fun signInWithGoogle(idToken: String): Result<FirebaseUser?> =
        withContext(Dispatchers.IO) {
            try {
                val firebaseAuth = auth
                    ?: return@withContext Result.failure(IllegalStateException("Firebase Auth not initialized"))
                val credential = GoogleAuthProvider.getCredential(idToken, null)
                val authResult = firebaseAuth.signInWithCredential(credential).await()
                Result.success(authResult.user)
            } catch (e: Exception) {
                Log.e(TAG, "signInWithGoogle failed: ${e.message}", e)
                Result.failure(e)
            }
        }

    /**
     * Signs in anonymously for guest browsing
     */
    suspend fun signInAnonymously(): Result<FirebaseUser?> =
        withContext(Dispatchers.IO) {
            try {
                val firebaseAuth = auth
                    ?: return@withContext Result.failure(IllegalStateException("Firebase Auth not initialized"))
                val authResult = firebaseAuth.signInAnonymously().await()
                Result.success(authResult.user)
            } catch (e: Exception) {
                Log.e(TAG, "signInAnonymously failed: ${e.message}", e)
                Result.failure(e)
            }
        }

    /**
     * Signs out from Firebase
     */
    fun signOut() {
        try {
            auth?.signOut()
        } catch (e: Exception) {
            Log.w(TAG, "signOut failed: ${e.message}")
        }
    }

    /**
     * Starts Firebase Phone Auth verification
     */
    fun verifyPhoneNumber(
        activity: Activity,
        phoneNumber: String,
        callbacks: PhoneAuthProvider.OnVerificationStateChangedCallbacks
    ) {
        val firebaseAuth = auth
        if (firebaseAuth == null) {
            callbacks.onVerificationFailed(FirebaseException("FirebaseAuth not initialized"))
            return
        }
        val options = PhoneAuthOptions.newBuilder(firebaseAuth)
            .setPhoneNumber(phoneNumber)
            .setTimeout(60L, TimeUnit.SECONDS)
            .setActivity(activity)
            .setCallbacks(callbacks)
            .build()
        PhoneAuthProvider.verifyPhoneNumber(options)
    }

    /**
     * Signs in using the SMS code and verificationId
     */
    suspend fun signInWithPhoneCode(
        verificationId: String,
        smsCode: String
    ): Result<FirebaseUser?> = withContext(Dispatchers.IO) {
        try {
            val firebaseAuth = auth
                ?: return@withContext Result.failure(IllegalStateException("Firebase Auth not initialized"))
            val credential = PhoneAuthProvider.getCredential(verificationId, smsCode)
            val authResult = firebaseAuth.signInWithCredential(credential).await()
            Result.success(authResult.user)
        } catch (e: Exception) {
            Log.e(TAG, "signInWithPhoneCode failed: ${e.message}", e)
            Result.failure(e)
        }
    }

    /**
     * Gets current user from Firebase Auth
     */
    fun getCurrentUser(): FirebaseUser? {
        return auth?.currentUser
    }
}
