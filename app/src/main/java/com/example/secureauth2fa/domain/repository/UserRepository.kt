package com.example.secureauth2fa.domain.repository

import com.example.secureauth2fa.data.model.BackupCode
import com.example.secureauth2fa.data.model.Session
import com.example.secureauth2fa.data.model.User
import com.example.secureauth2fa.utils.Resource

interface UserRepository {
    suspend fun getUserProfile(userId: String): Resource<User>
    suspend fun updateTwoFactorStatus(userId: String, isEnabled: Boolean): Resource<Unit>
    suspend fun saveBackupCodes(userId: String, codes: List<BackupCode>): Resource<Unit>
    suspend fun verifyAndConsumeBackupCode(userId: String, code: String): Resource<Boolean>
    suspend fun getSessions(userId: String): Resource<List<Session>>
    suspend fun registerCurrentSession(userId: String, isRemembered: Boolean): Resource<Unit>
}
