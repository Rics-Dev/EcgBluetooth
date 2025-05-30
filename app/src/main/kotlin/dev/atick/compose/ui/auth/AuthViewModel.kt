package dev.atick.compose.ui.auth

import androidx.compose.runtime.mutableStateOf
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dev.atick.compose.auth.AppwriteAuthService
import dev.atick.compose.ui.utils.Property
import dev.atick.core.ui.BaseViewModel
import dev.atick.core.utils.Event
import dev.atick.storage.preferences.UserPreferences
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class AuthViewModel @Inject constructor(
    private val appwriteAuthService: AppwriteAuthService,
    private val userPreferences: UserPreferences
) : BaseViewModel() {

    val email = Property(mutableStateOf(""))
    val password = Property(mutableStateOf(""))
    val confirmPassword = Property(mutableStateOf(""))
    val name = Property(mutableStateOf(""))

    val isLoginMode = mutableStateOf(true)
    val isAuthenticating = mutableStateOf(false)

    private val _authSuccess = MutableLiveData<Event<Boolean>>()
    val authSuccess: LiveData<Event<Boolean>> get() = _authSuccess

    private val _authError = MutableLiveData<Event<String>>()
    val authError: LiveData<Event<String>> get() = _authError

    init {
        // Check for existing authentication session
        viewModelScope.launch {
            userPreferences.getUserId().collect { userId ->
                if (userId.isNotEmpty()) {
                    try {
                        val user = appwriteAuthService.getCurrentUser()
                        if (user != null) {
                            _authSuccess.postValue(Event(true))
                        } else {
                            // Clear invalid user ID if no valid session exists
                            userPreferences.saveUserId("")
                        }
                    } catch (e: Exception) {
                        _authError.postValue(Event("Session expired: ${e.message}"))
                        userPreferences.saveUserId("")
                    }
                }
            }
        }

        // Listen for authentication errors
        viewModelScope.launch {
            appwriteAuthService.authError.collect { error ->
                if (error != null) {
                    _authError.postValue(Event(error.message ?: "Authentication failed"))
                    appwriteAuthService.clearError()
                    isAuthenticating.value = false
                }
            }
        }
    }

    fun toggleAuthMode() {
        isLoginMode.value = !isLoginMode.value
        // Reset fields when switching modes
        if (!isLoginMode.value) {
            confirmPassword.state.value = ""
            name.state.value = ""
        } else {
            confirmPassword.state.value = ""
            name.state.value = ""
        }
    }

    fun authenticate() {
        val emailValue = email.state.value.trim()
        val passwordValue = password.state.value

        if (emailValue.isEmpty() || passwordValue.isEmpty()) {
            _authError.postValue(Event("Email and password cannot be empty"))
            return
        }

        if (!isLoginMode.value) {
            val nameValue = name.state.value.trim()
            val confirmPasswordValue = confirmPassword.state.value

            if (nameValue.isEmpty()) {
                _authError.postValue(Event("Name cannot be empty"))
                return
            }

            if (passwordValue != confirmPasswordValue) {
                _authError.postValue(Event("Passwords do not match"))
                return
            }

            signUp(emailValue, passwordValue, nameValue)
        } else {
            login(emailValue, passwordValue)
        }
    }

    private fun login(email: String, password: String) {
        isAuthenticating.value = true
        viewModelScope.launch {
            val success = appwriteAuthService.login(email, password)
            if (success) {
                val user = appwriteAuthService.getCurrentUser()
                user?.let {
                    userPreferences.saveUserId(it.id)
                }
                _authSuccess.postValue(Event(true))
            }
            isAuthenticating.value = false
        }
    }

    private fun signUp(email: String, password: String, name: String) {
        isAuthenticating.value = true
        viewModelScope.launch {
            val success = appwriteAuthService.createAccount(email, password, name)
            if (success) {
                val user = appwriteAuthService.getCurrentUser()
                user?.let {
                    userPreferences.saveUserId(it.id)
                }
                _authSuccess.postValue(Event(true))
            }
            isAuthenticating.value = false
        }
    }

    fun logout() {
        viewModelScope.launch {
            val success = appwriteAuthService.logout()
            if (success) {
                userPreferences.saveUserId("")
            }
        }
    }
}
