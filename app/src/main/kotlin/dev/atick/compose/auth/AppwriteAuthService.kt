package dev.atick.compose.auth

import android.content.Context
import com.orhanobut.logger.Logger
import dagger.hilt.android.qualifiers.ApplicationContext
import dev.atick.compose.constants.AppwriteConfig
import dev.atick.compose.services.AccountService
import io.appwrite.Client
import io.appwrite.models.User
import io.appwrite.services.Account
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AppwriteAuthService @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private val client = Client(context)
        .setEndpoint(AppwriteConfig.APPWRITE_PUBLIC_ENDPOINT)
        .setProject(AppwriteConfig.APPWRITE_PROJECT_ID)
        .setSelfSigned(true) // For development only, remove in production

    // Create a single Account instance for direct operations
    private val account = Account(client)

    // AccountService for higher-level operations
    private val accountService = AccountService(client)

    val currentUser: StateFlow<User<Map<String, Any>>?> = accountService.currentUser
    val authError: StateFlow<Exception?> = accountService.authError

    suspend fun createAccount(email: String, password: String, name: String): Boolean {
        return try {
            withContext(Dispatchers.IO) {
                val user = accountService.register(email, password, name)
                user != null
            }
        } catch (e: Exception) {
            Logger.e("ACCOUNT CREATION FAILED! ${e.message}")
            false
        }
    }

    suspend fun login(email: String, password: String): Boolean {
        return try {
            withContext(Dispatchers.IO) {
                val user = accountService.login(email, password)
                user != null
            }
        } catch (e: Exception) {
            Logger.e("LOGIN ATTEMPT FAILED! ${e.message}")
            false
        }
    }

    suspend fun logout(): Boolean {
        return try {
            withContext(Dispatchers.IO) {
                accountService.logout()
            }
        } catch (e: Exception) {
            Logger.e("LOGOUT FAILED! ${e.message}")
            false
        }
    }

    suspend fun getCurrentUser(): User<Map<String, Any>>? {
        return try {
            withContext(Dispatchers.IO) {
                account.get()
            }
        } catch (e: Exception) {
            Logger.e("FAILED TO GET CURRENT USER! ${e.message}")
            null
        }
    }

    fun clearError() {
        accountService.clearError()
    }
}
