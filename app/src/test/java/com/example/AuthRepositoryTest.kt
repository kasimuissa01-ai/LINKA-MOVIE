package com.example

import com.example.data.repository.AuthRepository
import com.example.domain.model.UserRole
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class AuthRepositoryTest {

    @Test
    fun testSignInAnonymouslyFallbackCreatesValidSession() = runBlocking {
        // Passing null auth & firestore simulates offline / local testing fallback
        val repository = AuthRepository(auth = null, firestore = null)

        val result = repository.signInAnonymously(
            displayName = "Jordan MovieGoer",
            phoneNumber = "+15551234567"
        )

        assertTrue(result.isSuccess)
        val session = result.getOrNull()
        assertNotNull(session)
        assertEquals(UserRole.USER, session?.role)
        assertTrue(session?.email?.contains("5551234567") == true)
        assertNotNull(repository.currentUserSession.value)
    }

    @Test
    fun testSignOutClearsSession() = runBlocking {
        val repository = AuthRepository(auth = null, firestore = null)
        repository.signInAnonymously("Jordan", "+15551234567")
        assertNotNull(repository.currentUserSession.value)

        repository.signOut()
        assertEquals(null, repository.currentUserSession.value)
    }
}
