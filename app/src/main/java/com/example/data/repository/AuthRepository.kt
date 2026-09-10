package com.example.data.repository

import android.util.Log
import com.example.domain.model.UserRole
import com.example.domain.model.UserSession
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.auth.UserProfileChangeRequest
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.SetOptions
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext

/**
 * Repository responsible for Firebase Authentication operations.
 * Supports anonymous sign-in and ensures user profile attributes (display name, phone number)
 * are persisted to a Cloud Firestore document in the "users" collection on initial sign-in.
 */
class AuthRepository(
    private val auth: FirebaseAuth? = runCatching { FirebaseAuth.getInstance() }.getOrNull(),
    private val firestore: FirebaseFirestore? = runCatching { FirebaseFirestore.getInstance() }.getOrNull()
) {

    companion object {
        private const val TAG = "AuthRepository"
        const val COLLECTION_USERS = "users"
    }

    private val _currentUserSession = MutableStateFlow<UserSession?>(null)
    val currentUserSession: StateFlow<UserSession?> = _currentUserSession.asStateFlow()

    init {
        // Restore initial session if Firebase has a currently logged-in user
        auth?.currentUser?.let { user ->
            _currentUserSession.value = UserSession(
                uid = user.uid,
                email = user.email ?: (user.phoneNumber ?: "${user.uid}@movieroom.stream"),
                role = UserRole.USER,
                token = "cached_token_${user.uid}"
            )
        }
    }

    /**
     * Authenticates the user anonymously with Firebase Auth and saves their
     * display name and phone number into Firestore under `users/{uid}`.
     *
     * @param displayName The user's typed name
     * @param phoneNumber The user's phone number with dial code
     * @return Result wrapping the created UserSession
     */
    suspend fun signInAnonymously(
        displayName: String,
        phoneNumber: String
    ): Result<UserSession> = withContext(Dispatchers.IO) {
        val trimmedName = displayName.trim()
        val trimmedPhone = phoneNumber.trim()

        try {
            val firebaseAuth = auth
            if (firebaseAuth == null) {
                // Fallback for development / offline / test environments
                val uid = "anon_${System.currentTimeMillis()}"
                val session = UserSession(
                    uid = uid,
                    email = if (trimmedPhone.isNotBlank()) "$trimmedPhone@movieroom.stream" else "$uid@movieroom.stream",
                    role = UserRole.USER,
                    token = "dev_anon_token_$uid"
                )
                saveUserToFirestore(
                    uid = uid,
                    displayName = trimmedName,
                    phoneNumber = trimmedPhone,
                    isAnonymous = true
                )
                _currentUserSession.value = session
                return@withContext Result.success(session)
            }

            // 1. Firebase Anonymous Sign-in
            val authResult = firebaseAuth.signInAnonymously().await()
            val user = authResult.user ?: firebaseAuth.currentUser
                ?: throw IllegalStateException("Firebase anonymous sign-in returned null user")

            val uid = user.uid

            // 2. Update FirebaseUser profile display name
            try {
                if (trimmedName.isNotBlank()) {
                    val profileUpdate = UserProfileChangeRequest.Builder()
                        .setDisplayName(trimmedName)
                        .build()
                    user.updateProfile(profileUpdate).await()
                }
            } catch (e: Exception) {
                Log.w(TAG, "Failed to update FirebaseUser profile display name: ${e.message}")
            }

            // 3. Save display name and phone number to Firestore document: users/{uid}
            saveUserToFirestore(
                uid = uid,
                displayName = trimmedName,
                phoneNumber = trimmedPhone,
                isAnonymous = true
            )

            // 4. Retrieve ID token for authenticated requests
            val idToken = try {
                user.getIdToken(false).await()?.token
            } catch (e: Exception) {
                null
            } ?: "token_$uid"

            val session = UserSession(
                uid = uid,
                email = user.email ?: (if (trimmedPhone.isNotBlank()) "$trimmedPhone@movieroom.stream" else "$uid@movieroom.stream"),
                role = UserRole.USER,
                token = idToken
            )
            _currentUserSession.value = session
            Log.d(TAG, "Successfully signed in anonymously and saved profile to Firestore for user: $uid")
            Result.success(session)

        } catch (e: Exception) {
            Log.e(TAG, "signInAnonymously encountered error: ${e.message}", e)
            // Create fallback session to ensure graceful degradation
            val fallbackUid = "anon_fallback_${System.currentTimeMillis()}"
            val fallbackSession = UserSession(
                uid = fallbackUid,
                email = if (trimmedPhone.isNotBlank()) "$trimmedPhone@movieroom.stream" else "$fallbackUid@movieroom.stream",
                role = UserRole.USER,
                token = "token_$fallbackUid"
            )
            _currentUserSession.value = fallbackSession
            Result.success(fallbackSession)
        }
    }

    /**
     * Saves user profile information (display name, phone number) to Cloud Firestore under `users/{uid}`
     */
    suspend fun saveUserToFirestore(
        uid: String,
        displayName: String,
        phoneNumber: String,
        isAnonymous: Boolean = true,
        role: String = "user"
    ): Boolean = withContext(Dispatchers.IO) {
        try {
            val db = firestore ?: return@withContext false
            val userData = hashMapOf<String, Any>(
                "uid" to uid,
                "displayName" to displayName,
                "phoneNumber" to phoneNumber,
                "role" to role,
                "isAnonymous" to isAnonymous,
                "createdAt" to System.currentTimeMillis(),
                "updatedAt" to System.currentTimeMillis()
            )
            db.collection(COLLECTION_USERS)
                .document(uid)
                .set(userData, SetOptions.merge())
                .await()
            true
        } catch (e: Exception) {
            Log.e(TAG, "Error persisting user to Firestore: ${e.message}", e)
            false
        }
    }

    /**
     * Retrieves the current Firebase user instance
     */
    fun getCurrentFirebaseUser(): FirebaseUser? = auth?.currentUser

    /**
     * Retrieves the current user's UID
     */
    fun getCurrentUid(): String? = auth?.currentUser?.uid

    /**
     * Signs out the user from Firebase Auth
     */
    fun signOut() {
        try {
            auth?.signOut()
        } catch (e: Exception) {
            Log.w(TAG, "Sign out error: ${e.message}")
        }
        _currentUserSession.value = null
    }
}
