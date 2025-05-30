package dev.atick.compose.services

import io.appwrite.Client
import io.appwrite.ID
import io.appwrite.exceptions.AppwriteException
import io.appwrite.models.User
import io.appwrite.services.Account
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class AccountService(private val client: Client) {
    private val account = Account(client)

    private val _currentUser = MutableStateFlow<User<Map<String, Any>>?>(null)
    val currentUser: StateFlow<User<Map<String, Any>>?> = _currentUser.asStateFlow()

    private val _authError = MutableStateFlow<Exception?>(null)
    val authError: StateFlow<Exception?> = _authError.asStateFlow()

    suspend fun getLoggedIn(): User<Map<String, Any>>? {
        return try {
            val user = account.get()
            _currentUser.value = user
            user
        } catch (e: AppwriteException) {
            _authError.value = e
            null
        }
    }

    suspend fun login(email: String, password: String): User<Map<String, Any>>? {
        return try {
            account.createEmailPasswordSession(email, password)
            getLoggedIn()
        } catch (e: AppwriteException) {
            _authError.value = e
            null
        }
    }

    suspend fun register(email: String, password: String, name: String = ""): User<Map<String, Any>>? {
        return try {
            account.create(ID.unique(), email, password, name)
            login(email, password)
        } catch (e: AppwriteException) {
            _authError.value = e
            null
        }
    }

    suspend fun logout(): Boolean {
        return try {
            account.deleteSession("current")
            _currentUser.value = null
            true
        } catch (e: AppwriteException) {
            _authError.value = e
            false
        }
    }

    fun clearError() {
        _authError.value = null
    }
}
