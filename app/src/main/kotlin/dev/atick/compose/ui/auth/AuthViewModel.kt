package dev.atick.compose.ui.auth

import androidx.compose.runtime.mutableStateOf
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.viewModelScope
import com.orhanobut.logger.Logger
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
        viewModelScope.launch {
            userPreferences.getUserId().collect { userId ->
                if (userId.isNotEmpty() && userId != "-1") {
                    try {
                        // Verify the session is still valid
                        val user = appwriteAuthService.getCurrentUser()
                        if (user != null && user.id == userId) {
                            Logger.d("Valid session found for user: ${user.id}")
                            _authSuccess.postValue(Event(true))
                        } else {
                            Logger.w("Invalid session, clearing stored user ID")
                            userPreferences.saveUserId("")
                        }
                    } catch (e: Exception) {
                        Logger.e("Session validation failed: ${e.message}")
                        userPreferences.saveUserId("")
                        _authError.postValue(Event("Session expired, please login again"))
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
//    init {
//        // Check for existing authentication session
//        viewModelScope.launch {
//            userPreferences.getUserId().collect { userId ->
//                if (userId.isNotEmpty()) {
//                    try {
//                        val user = appwriteAuthService.getCurrentUser()
//                        if (user != null) {
//                            _authSuccess.postValue(Event(true))
//                        } else {
//                            // Clear invalid user ID if no valid session exists
//                            userPreferences.saveUserId("")
//                        }
//                    } catch (e: Exception) {
//                        _authError.postValue(Event("Session expired: ${e.message}"))
//                        userPreferences.saveUserId("")
//                    }
//                }
//            }
//        }
//
//        // Listen for authentication errors
//        viewModelScope.launch {
//            appwriteAuthService.authError.collect { error ->
//                if (error != null) {
//                    _authError.postValue(Event(error.message ?: "Authentication failed"))
//                    appwriteAuthService.clearError()
//                    isAuthenticating.value = false
//                }
//            }
//        }
//    }

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
            try {
                val success = appwriteAuthService.login(email, password)
                if (success) {
                    val user = appwriteAuthService.getCurrentUser()
                    if (user != null) {
                        // Save the actual user ID from Appwrite
                        userPreferences.saveUserId(user.id)
                        Logger.d("User logged in successfully with ID: ${user.id}")
                        _authSuccess.postValue(Event(true))
                    } else {
                        Logger.e("Login successful but failed to get user details")
                        _authError.postValue(Event("Failed to get user information"))
                    }
                } else {
                    Logger.e("Login failed")
                }
            } catch (e: Exception) {
                Logger.e("Login error: ${e.message}")
                _authError.postValue(Event("Login failed: ${e.message}"))
            } finally {
                isAuthenticating.value = false
            }
        }
    }

//    private fun login(email: String, password: String) {
//        isAuthenticating.value = true
//        viewModelScope.launch {
//            val success = appwriteAuthService.login(email, password)
//            if (success) {
//                val user = appwriteAuthService.getCurrentUser()
//                user?.let {
//                    userPreferences.saveUserId(it.id)
//                }
//                _authSuccess.postValue(Event(true))
//            }
//            isAuthenticating.value = false
//        }
//    }

    private fun signUp(email: String, password: String, name: String) {
        isAuthenticating.value = true
        viewModelScope.launch {
            try {
                val success = appwriteAuthService.createAccount(email, password, name)
                if (success) {
                    val user = appwriteAuthService.getCurrentUser()
                    if (user != null) {
                        // Save the actual user ID from Appwrite
                        userPreferences.saveUserId(user.id)
                        Logger.d("User registered successfully with ID: ${user.id}")
                        _authSuccess.postValue(Event(true))
                    } else {
                        Logger.e("Registration successful but failed to get user details")
                        _authError.postValue(Event("Failed to get user information"))
                    }
                } else {
                    Logger.e("Registration failed")
                }
            } catch (e: Exception) {
                Logger.e("Registration error: ${e.message}")
                _authError.postValue(Event("Registration failed: ${e.message}"))
            } finally {
                isAuthenticating.value = false
            }
        }
    }

//    private fun signUp(email: String, password: String, name: String) {
//        isAuthenticating.value = true
//        viewModelScope.launch {
//            val success = appwriteAuthService.createAccount(email, password, name)
//            if (success) {
//                val user = appwriteAuthService.getCurrentUser()
//                user?.let {
//                    userPreferences.saveUserId(it.id)
//                }
//                _authSuccess.postValue(Event(true))
//            }
//            isAuthenticating.value = false
//        }
//    }

    fun logout() {
        viewModelScope.launch {
            val success = appwriteAuthService.logout()
            if (success) {
                userPreferences.saveUserId("")
            }
        }
    }
}
