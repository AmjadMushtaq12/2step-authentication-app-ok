package com.example.secureauth2fa.domain.usecase

import com.example.secureauth2fa.data.auth.TotpDataSource
import com.example.secureauth2fa.data.auth.TotpSetupResult
import com.example.secureauth2fa.domain.repository.AuthRepository
import com.example.secureauth2fa.domain.repository.UserRepository
import com.example.secureauth2fa.utils.Resource

class Setup2FAUseCase(
    private val totpDataSource: TotpDataSource,
    private val authRepository: AuthRepository,
    private val userRepository: UserRepository
) {
    fun generateSetupData(email: String): TotpSetupResult {
        return totpDataSource.prepareSetup(email)
    }

    fun getCurrentTotpCode(secretKey: String): String {
        return totpDataSource.getCurrentTotpCode(secretKey)
    }

    suspend fun completeSetup(
        userId: String,
        secretKey: String,
        verificationCode: String,
        setupResult: TotpSetupResult
    ): Resource<Unit> {
        val isValid = totpDataSource.verifyTotpCode(secretKey, verificationCode)
        if (!isValid) {
            return Resource.Error("The 6-digit verification code is invalid or expired. Please check your authenticator app.")
        }

        // 1. Store TOTP secret encrypted in local EncryptedSharedPreferences
        authRepository.saveTotpSecret(userId, secretKey)

        // 2. Save 10 hashed backup recovery codes in Firestore
        val backupSaveRes = userRepository.saveBackupCodes(userId, setupResult.backupCodes)
        if (backupSaveRes is Resource.Error) {
            return backupSaveRes
        }

        // 3. Update user profile in Firestore
        val statusRes = userRepository.updateTwoFactorStatus(userId, true)
        return statusRes
    }
}
