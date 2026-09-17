package com.example.secureauth2fa.data.repository

import com.example.secureauth2fa.data.auth.FirebaseAuthDataSource
import com.example.secureauth2fa.data.local.EncryptedPrefsManager
import com.example.secureauth2fa.data.model.User
import com.example.secureauth2fa.domain.repository.AuthRepository
import com.example.secureauth2fa.utils.Constants
import com.example.secureauth2fa.utils.Resource
import java.util.UUID

class AuthRepositoryImpl(
    private val firebaseAuthDataSource: FirebaseAuthDataSource,
    private val prefsManager: EncryptedPrefsManager
) : AuthRepository {

    override suspend fun login(email: String, password: String): Resource<User> {
        val result = firebaseAuthDataSource.login(email, password)
        if (result is Resource.Success) {
            prefsManager.saveLastEmail(email)
        }
        return result
    }

    override suspend fun register(email: String, password: String, displayName: String): Resource<User> {
        val result = firebaseAuthDataSource.register(email, password, displayName)
        if (result is Resource.Success) {
            prefsManager.saveLastEmail(email)
        }
        return result
    }

    override suspend fun loginWithGoogle(idToken: String?, email: String, displayName: String): Resource<User> {
        val result = firebaseAuthDataSource.signInWithGoogle(idToken, email, displayName)
        if (result is Resource.Success) {
            prefsManager.saveLastEmail(email)
        }
        return result
    }

    override suspend fun sendPasswordReset(email: String): Resource<Unit> {
        return firebaseAuthDataSource.sendPasswordReset(email)
    }

    override suspend fun sendEmailVerification(): Resource<Unit> {
        return firebaseAuthDataSource.sendEmailVerification()
    }

    override fun logout() {
        firebaseAuthDataSource.logout()
    }

    override fun getCurrentUser(): User? {
        return firebaseAuthDataSource.getCurrentUser()
    }

    override fun getSavedTotpSecret(userId: String): String? {
        return prefsManager.getTotpSecret(userId)
    }

    override fun saveTotpSecret(userId: String, secret: String) {
        prefsManager.saveTotpSecret(userId, secret)
    }

    override fun clearTotpSecret(userId: String) {
        prefsManager.clearTotpSecret(userId)
    }

    override fun isDeviceRemembered(userId: String): Boolean {
        return prefsManager.isDeviceRemembered(userId)
    }

    override suspend fun rememberDevice(userId: String, days: Int): Resource<Unit> {
        val expiry = System.currentTimeMillis() + (days * 24L * 60L * 60L * 1000L)
        val token = UUID.randomUUID().toString()
        prefsManager.saveRememberedDeviceToken(userId, token, expiry)
        return firebaseAuthDataSource.saveRememberedDeviceToken(userId, token, expiry)
    }
}
