package com.example.secureauth2fa.domain.usecase

import com.example.secureauth2fa.data.auth.TotpDataSource
import com.example.secureauth2fa.data.local.EncryptedPrefsManager
import com.example.secureauth2fa.data.local.InactivityAction
import com.example.secureauth2fa.data.local.SessionManager
import com.example.secureauth2fa.data.local.SessionState
import com.example.secureauth2fa.data.model.Session
import com.example.secureauth2fa.domain.repository.AuthRepository
import com.example.secureauth2fa.domain.repository.UserRepository
import com.example.secureauth2fa.utils.Resource
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow

class SessionUseCase(
    private val authRepository: AuthRepository,
    private val userRepository: UserRepository,
    private val sessionManager: SessionManager,
    private val prefsManager: EncryptedPrefsManager,
    private val totpDataSource: TotpDataSource? = null
) {
    val isSessionExpired: StateFlow<Boolean> = sessionManager.isSessionExpired
    val isReauthPromptRequired: StateFlow<Boolean> = sessionManager.isReauthPromptRequired
    val remainingTimeMs: StateFlow<Long> = sessionManager.remainingTimeMs
    val sessionState: StateFlow<SessionState> = sessionManager.sessionState
    val sessionEvent: SharedFlow<SessionManager.SessionEvent> = sessionManager.sessionEvent

    fun startSession(userId: String) {
        sessionManager.startSession(userId)
    }

    fun isSessionActive(): Boolean {
        return sessionState.value is SessionState.Active && !isSessionExpired.value
    }

    fun endSession(userId: String) {
        sessionManager.endSession()
        authRepository.logout()
    }

    fun onUserInteracted() {
        sessionManager.updateActivity()
    }

    fun checkInactivityExpired(): Boolean {
        return sessionManager.checkInactivity()
    }

    fun onReauthenticated() {
        sessionManager.onReauthenticated()
    }

    fun triggerReauthPrompt() {
        sessionManager.triggerReauthPrompt()
    }

    suspend fun reauthenticateWithPassword(email: String, password: String): Resource<Unit> {
        return when (val res = authRepository.login(email, password)) {
            is Resource.Success -> {
                sessionManager.onReauthenticated()
                Resource.Success(Unit)
            }
            is Resource.Error -> Resource.Error(res.message)
            Resource.Loading -> Resource.Loading
        }
    }

    suspend fun reauthenticateWithTotp(userId: String, code: String): Resource<Unit> {
        val secretKey = authRepository.getSavedTotpSecret(userId)
            ?: return Resource.Error("No 2FA secret found for this account.")
        val ds = totpDataSource ?: TotpDataSource()
        val isValid = ds.verifyTotpCode(secretKey, code.trim())
        return if (isValid) {
            sessionManager.onReauthenticated()
            Resource.Success(Unit)
        } else {
            Resource.Error("Incorrect 6-digit TOTP verification code.")
        }
    }

    fun logoutDueToInactivity(reason: String) {
        sessionManager.logoutDueToInactivity(reason)
        authRepository.logout()
    }

    suspend fun getActiveSessions(userId: String): Resource<List<Session>> {
        return userRepository.getSessions(userId)
    }

    fun isBiometricEnabled(): Boolean = prefsManager.isBiometricEnabled()

    fun setBiometricEnabled(enabled: Boolean) {
        prefsManager.setBiometricEnabled(enabled)
    }

    fun getInactivityTimeoutMinutes(): Int = sessionManager.getInactivityTimeoutMinutes()

    fun setInactivityTimeoutMinutes(minutes: Int) {
        sessionManager.setInactivityTimeoutMinutes(minutes)
    }

    fun getInactivityAction(): InactivityAction = sessionManager.getInactivityAction()

    fun setInactivityAction(action: InactivityAction) {
        sessionManager.setInactivityAction(action)
    }

    fun isDeviceRemembered(userId: String): Boolean {
        return authRepository.isDeviceRemembered(userId)
    }
}

