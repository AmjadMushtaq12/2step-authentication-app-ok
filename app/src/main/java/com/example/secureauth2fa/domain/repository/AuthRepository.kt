package com.example.secureauth2fa.domain.repository

import com.example.secureauth2fa.data.model.User
import com.example.secureauth2fa.utils.Resource

interface AuthRepository {
    suspend fun login(email: String, password: String): Resource<User>
    suspend fun register(email: String, password: String, displayName: String): Resource<User>
    suspend fun loginWithGoogle(idToken: String?, email: String, displayName: String): Resource<User>
    suspend fun sendPasswordReset(email: String): Resource<Unit>
    suspend fun sendEmailVerification(): Resource<Unit>
    fun logout()
    fun getCurrentUser(): User?
    fun getSavedTotpSecret(userId: String): String?
    fun saveTotpSecret(userId: String, secret: String)
    fun clearTotpSecret(userId: String)
    fun isDeviceRemembered(userId: String): Boolean
    suspend fun rememberDevice(userId: String, days: Int = 30): Resource<Unit>
}
