package com.example.secureauth2fa.data.repository

import android.os.Build
import com.example.secureauth2fa.data.auth.FirebaseAuthDataSource
import com.example.secureauth2fa.data.local.EncryptedPrefsManager
import com.example.secureauth2fa.data.model.BackupCode
import com.example.secureauth2fa.data.model.Session
import com.example.secureauth2fa.data.model.User
import com.example.secureauth2fa.domain.repository.UserRepository
import com.example.secureauth2fa.utils.Resource

class UserRepositoryImpl(
    private val firebaseAuthDataSource: FirebaseAuthDataSource,
    private val prefsManager: EncryptedPrefsManager
) : UserRepository {

    override suspend fun getUserProfile(userId: String): Resource<User> {
        return firebaseAuthDataSource.getUserProfile(userId)
    }

    override suspend fun updateTwoFactorStatus(userId: String, isEnabled: Boolean): Resource<Unit> {
        return firebaseAuthDataSource.updateTwoFactorStatus(userId, isEnabled)
    }

    override suspend fun saveBackupCodes(userId: String, codes: List<BackupCode>): Resource<Unit> {
        return firebaseAuthDataSource.saveBackupCodes(userId, codes)
    }

    override suspend fun verifyAndConsumeBackupCode(userId: String, code: String): Resource<Boolean> {
        return firebaseAuthDataSource.verifyAndConsumeBackupCode(userId, code)
    }

    override suspend fun getSessions(userId: String): Resource<List<Session>> {
        return firebaseAuthDataSource.getSessions(userId)
    }

    override suspend fun registerCurrentSession(userId: String, isRemembered: Boolean): Resource<Unit> {
        val deviceId = prefsManager.getOrCreateDeviceId()
        val session = Session(
            sessionId = deviceId,
            deviceName = "${Build.MANUFACTURER.replaceFirstChar { it.uppercase() }} ${Build.MODEL}",
            osVersion = "Android ${Build.VERSION.RELEASE}",
            lastActiveTime = System.currentTimeMillis(),
            isCurrentDevice = true,
            isRemembered = isRemembered
        )
        return firebaseAuthDataSource.saveDeviceSession(userId, session)
    }
}
