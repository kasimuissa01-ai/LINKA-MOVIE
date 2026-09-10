package com.example.presentation.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.repository.AuthRepository
import com.example.data.repository.MovieRepository
import com.example.domain.model.UserRole
import com.example.domain.model.UserSession
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class AuthViewModel(
    private val repository: MovieRepository,
    private val authRepository: AuthRepository = repository.getAuthRepository()
) : ViewModel() {

    val userSession: StateFlow<UserSession> = repository.userSession

    private val _authStatusMessage = MutableStateFlow<String?>(null)
    val authStatusMessage: StateFlow<String?> = _authStatusMessage.asStateFlow()

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    fun toggleRole() {
        val currentRole = userSession.value.role
        val nextRole = if (currentRole == UserRole.ADMIN) UserRole.USER else UserRole.ADMIN
        repository.switchRole(nextRole)
    }

    fun setRole(role: UserRole) {
        repository.switchRole(role)
    }

    fun signInWithEmail(email: String, pass: String) {
        if (email.isBlank() || pass.isBlank()) {
            _authStatusMessage.value = "Please provide email and password"
            return
        }
        viewModelScope.launch {
            _isLoading.value = true
            val result = repository.signInWithEmail(email, pass)
            result.fold(
                onSuccess = { msg ->
                    _authStatusMessage.value = msg
                },
                onFailure = { ex ->
                    _authStatusMessage.value = ex.message ?: "Authentication failed"
                }
            )
            _isLoading.value = false
        }
    }

    fun signUpWithEmail(email: String, pass: String, role: UserRole) {
        if (email.isBlank() || pass.isBlank()) {
            _authStatusMessage.value = "Please provide email and password"
            return
        }
        viewModelScope.launch {
            _isLoading.value = true
            val result = repository.signUpWithEmail(email, pass, role)
            result.fold(
                onSuccess = { msg ->
                    _authStatusMessage.value = msg
                },
                onFailure = { ex ->
                    _authStatusMessage.value = ex.message ?: "Registration failed"
                }
            )
            _isLoading.value = false
        }
    }

    fun getAuthService() = repository.getAuthService()

    fun signInAnonymouslyWithProfile(
        userName: String,
        phoneNumber: String,
        onSuccess: () -> Unit,
        onError: (String) -> Unit
    ) {
        viewModelScope.launch {
            _isLoading.value = true
            val res = repository.signInAnonymouslyWithProfile(userName, phoneNumber)
            _isLoading.value = false
            res.fold(
                onSuccess = {
                    _authStatusMessage.value = "Welcome, $userName"
                    onSuccess()
                },
                onFailure = { ex ->
                    _authStatusMessage.value = ex.message ?: "Sign-in failed"
                    onError(ex.message ?: "Sign-in failed")
                }
            )
        }
    }

    fun verifyAndSignInPhone(
        userName: String,
        phoneNumber: String,
        verificationId: String,
        otpCode: String,
        onSuccess: () -> Unit,
        onError: (String) -> Unit
    ) {
        viewModelScope.launch {
            _isLoading.value = true
            val res = repository.verifyAndSignInPhone(userName, phoneNumber, verificationId, otpCode)
            _isLoading.value = false
            res.fold(
                onSuccess = {
                    _authStatusMessage.value = "Welcome, $userName"
                    onSuccess()
                },
                onFailure = { ex ->
                    _authStatusMessage.value = ex.message ?: "Authentication failed"
                    onError(ex.message ?: "Authentication failed")
                }
            )
        }
    }

    fun signOut() {
        repository.signOut()
        _authStatusMessage.value = "Signed out successfully"
    }

    fun clearStatusMessage() {
        _authStatusMessage.value = null
    }
}
