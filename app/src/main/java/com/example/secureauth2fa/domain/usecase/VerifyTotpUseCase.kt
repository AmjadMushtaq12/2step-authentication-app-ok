package com.example.secureauth2fa.domain.usecase

import com.example.secureauth2fa.data.auth.TotpDataSource
import com.example.secureauth2fa.domain.repository.AuthRepository
import com.example.secureauth2fa.domain.repository.UserRepository
import com.example.secureauth2fa.utils.Resource
import com.example.secureauth2fa.utils.Validators

class VerifyTotpUseCase(
    private val totpDataSource: TotpDataSource,
    private val authRepository: AuthRepository,
    private val userRepository: UserRepository
) {
    fun getCurrentTotpCode(userId: String): String? {
        val secretKey = authRepository.getSavedTotpSecret(userId) ?: return null
        return totpDataSource.getCurrentTotpCode(secretKey)
    }

    suspend fun verifyOtp(
        userId: String,
        otpCode: String,
        rememberDevice: Boolean
    ): Resource<Boolean> {
        if (!Validators.isValidOtpCode(otpCode)) {
            return Resource.Error("Please enter a valid 6-digit verification code.")
        }

        val secretKey = authRepository.getSavedTotpSecret(userId)
            ?: return Resource.Error("No 2FA secret found for this account. Please re-setup 2FA.")

        val isValid = totpDataSource.verifyTotpCode(secretKey, otpCode)
        if (!isValid) {
            return Resource.Error("Incorrect or expired 6-digit code. Authenticator codes refresh every 30 seconds.")
        }

        if (rememberDevice) {
            authRepository.rememberDevice(userId, days = 30)
        }

        // Register session in Firestore
        userRepository.registerCurrentSession(userId, isRemembered = rememberDevice)

        return Resource.Success(true)
    }

    suspend fun verifyWithBackupCode(
        userId: String,
        backupCode: String,
        rememberDevice: Boolean
    ): Resource<Boolean> {
        if (backupCode.isBlank()) {
            return Resource.Error("Please enter a recovery code.")
        }

        val res = userRepository.verifyAndConsumeBackupCode(userId, backupCode)
        if (res is Resource.Success && res.data) {
            if (rememberDevice) {
                authRepository.rememberDevice(userId, days = 30)
            }
            userRepository.registerCurrentSession(userId, isRemembered = rememberDevice)
            return Resource.Success(true)
        }
        return res
    }

    suspend fun disable2FA(userId: String, confirmationOtp: String): Resource<Unit> {
        val secretKey = authRepository.getSavedTotpSecret(userId)
            ?: return Resource.Error("No 2FA secret found.")

        val isValid = totpDataSource.verifyTotpCode(secretKey, confirmationOtp)
        if (!isValid) {
            return Resource.Error("Invalid 6-digit confirmation code.")
        }

        authRepository.clearTotpSecret(userId)
        return userRepository.updateTwoFactorStatus(userId, false)
    }
}
