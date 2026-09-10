package com.example.data.local

import android.content.Context
import android.content.SharedPreferences
import com.example.domain.model.UserRole
import com.example.domain.model.UserSession

class SessionManager(context: Context) {

    private val prefs: SharedPreferences = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)

    companion object {
        private const val PREF_NAME = "movieroom_user_session"
        private const val KEY_IS_LOGGED_IN = "key_is_logged_in"
        private const val KEY_UID = "key_uid"
        private const val KEY_EMAIL = "key_email"
        private const val KEY_DISPLAY_NAME = "key_display_name"
        private const val KEY_PHONE_NUMBER = "key_phone_number"
        private const val KEY_ROLE = "key_role"
        private const val KEY_TOKEN = "key_token"
        private const val KEY_WATCHED_COUNT = "key_watched_count"
        private const val KEY_FAVORITE_COUNT = "key_favorite_count"
    }

    val isLoggedIn: Boolean
        get() = prefs.getBoolean(KEY_IS_LOGGED_IN, false)

    fun saveSession(session: UserSession) {
        prefs.edit().apply {
            putBoolean(KEY_IS_LOGGED_IN, true)
            putString(KEY_UID, session.uid)
            putString(KEY_EMAIL, session.email)
            putString(KEY_DISPLAY_NAME, session.displayName)
            putString(KEY_PHONE_NUMBER, session.phoneNumber)
            putString(KEY_ROLE, session.role.name)
            putString(KEY_TOKEN, session.token)
            putInt(KEY_WATCHED_COUNT, session.watchedCount)
            putInt(KEY_FAVORITE_COUNT, session.favoriteCount)
            apply()
        }
    }

    fun getSession(): UserSession? {
        if (!isLoggedIn) return null
        val uid = prefs.getString(KEY_UID, null) ?: return null
        val email = prefs.getString(KEY_EMAIL, "$uid@movieroom.stream") ?: "$uid@movieroom.stream"
        val displayName = prefs.getString(KEY_DISPLAY_NAME, "MovieRoom User") ?: "MovieRoom User"
        val phoneNumber = prefs.getString(KEY_PHONE_NUMBER, "+255 696 102 700") ?: "+255 696 102 700"
        val roleStr = prefs.getString(KEY_ROLE, UserRole.USER.name) ?: UserRole.USER.name
        val role = try { UserRole.valueOf(roleStr) } catch (e: Exception) { UserRole.USER }
        val token = prefs.getString(KEY_TOKEN, "") ?: ""
        val watchedCount = prefs.getInt(KEY_WATCHED_COUNT, 14)
        val favoriteCount = prefs.getInt(KEY_FAVORITE_COUNT, 8)

        return UserSession(
            uid = uid,
            email = email,
            displayName = displayName,
            phoneNumber = phoneNumber,
            role = role,
            token = token,
            watchedCount = watchedCount,
            favoriteCount = favoriteCount
        )
    }

    fun clearSession() {
        prefs.edit().apply {
            clear()
            apply()
        }
    }
}
