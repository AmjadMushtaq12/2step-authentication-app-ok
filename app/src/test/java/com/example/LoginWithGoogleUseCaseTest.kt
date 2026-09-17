package com.example

import com.example.secureauth2fa.data.model.BackupCode
import com.example.secureauth2fa.data.model.Session
import com.example.secureauth2fa.data.model.User
import com.example.secureauth2fa.domain.repository.AuthRepository
import com.example.secureauth2fa.domain.repository.UserRepository
import com.example.secureauth2fa.domain.usecase.LoginWithGoogleUseCase
import com.example.secureauth2fa.utils.Resource
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class LoginWithGoogleUseCaseTest {

    private class FakeAuthRepository(
        private val shouldSucceed: Boolean = true,
        private val isTwoFactorEnabled: Boolean = false
    ) : AuthRepository {
        override suspend fun login(email: String, password: String): Resource<User> =
            Resource.Success(User(uid = "uid1", email = email, displayName = "User"))

        override suspend fun register(email: String, password: String, displayName: String): Resource<User> =
            Resource.Success(User(uid = "uid1", email = email, displayName = displayName))

        override suspend fun loginWithGoogle(idToken: String?, email: String, displayName: String): Resource<User> {
            return if (shouldSucceed) {
                Resource.Success(
                    User(
                        uid = "google_test_uid",
                        email = email,
                        displayName = displayName,
                        isEmailVerified = true,
                        isTwoFactorEnabled = isTwoFactorEnabled
                    )
                )
            } else {
                Resource.Error("Google authentication failed. Please try again.")
            }
        }

        override suspend fun sendPasswordReset(email: String): Resource<Unit> = Resource.Success(Unit)
        override suspend fun sendEmailVerification(): Resource<Unit> = Resource.Success(Unit)
        override fun logout() {}
        override fun getCurrentUser(): User? = null
        override fun getSavedTotpSecret(userId: String): String? = null
        override fun saveTotpSecret(userId: String, secret: String) {}
        override fun clearTotpSecret(userId: String) {}
        override fun isDeviceRemembered(userId: String): Boolean = false
        override suspend fun rememberDevice(userId: String, days: Int): Resource<Unit> = Resource.Success(Unit)
    }

    private class FakeUserRepository(
        private val profileToReturn: User? = null
    ) : UserRepository {
        override suspend fun getUserProfile(userId: String): Resource<User> =
            profileToReturn?.let { Resource.Success(it) } ?: Resource.Error("Not found")
        override suspend fun updateTwoFactorStatus(userId: String, isEnabled: Boolean): Resource<Unit> = Resource.Success(Unit)
        override suspend fun saveBackupCodes(userId: String, codes: List<BackupCode>): Resource<Unit> = Resource.Success(Unit)
        override suspend fun verifyAndConsumeBackupCode(userId: String, code: String): Resource<Boolean> = Resource.Success(true)
        override suspend fun getSessions(userId: String): Resource<List<Session>> = Resource.Success(emptyList())
        override suspend fun registerCurrentSession(userId: String, isRemembered: Boolean): Resource<Unit> = Resource.Success(Unit)
    }

    @Test
    fun loginWithGoogle_success_returnsUser() = runTest {
        val authRepo = FakeAuthRepository(shouldSucceed = true, isTwoFactorEnabled = false)
        val userRepo = FakeUserRepository()
        val useCase = LoginWithGoogleUseCase(authRepo, userRepo)

        val result = useCase(idToken = "dummy_token", email = "test@gmail.com", displayName = "Google User")
        assertTrue(result is Resource.Success)
        val user = (result as Resource.Success).data
        assertEquals("google_test_uid", user.uid)
        assertEquals("test@gmail.com", user.email)
        assertEquals("Google User", user.displayName)
    }

    @Test
    fun loginWithGoogle_withTwoFactorEnabled_propagatesTwoFactorFlag() = runTest {
        val authRepo = FakeAuthRepository(shouldSucceed = true, isTwoFactorEnabled = true)
        val userRepo = FakeUserRepository()
        val useCase = LoginWithGoogleUseCase(authRepo, userRepo)

        val result = useCase(idToken = null, email = "secure@gmail.com", displayName = "Secure User")
        assertTrue(result is Resource.Success)
        val user = (result as Resource.Success).data
        assertTrue(user.isTwoFactorEnabled)
    }

    @Test
    fun loginWithGoogle_failure_returnsError() = runTest {
        val authRepo = FakeAuthRepository(shouldSucceed = false)
        val userRepo = FakeUserRepository()
        val useCase = LoginWithGoogleUseCase(authRepo, userRepo)

        val result = useCase(idToken = null, email = "fail@gmail.com", displayName = "Fail User")
        assertTrue(result is Resource.Error)
        assertEquals("Google authentication failed. Please try again.", (result as Resource.Error).message)
    }
}
