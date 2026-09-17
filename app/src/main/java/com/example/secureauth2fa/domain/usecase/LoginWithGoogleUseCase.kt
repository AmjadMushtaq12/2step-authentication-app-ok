package com.example.secureauth2fa.domain.usecase

import com.example.secureauth2fa.data.model.User
import com.example.secureauth2fa.domain.repository.AuthRepository
import com.example.secureauth2fa.domain.repository.UserRepository
import com.example.secureauth2fa.utils.Resource

class LoginWithGoogleUseCase(
    private val authRepository: AuthRepository,
    private val userRepository: UserRepository
) {
    suspend operator fun invoke(
        idToken: String?,
        email: String,
        displayName: String
    ): Resource<User> {
        val result = authRepository.loginWithGoogle(idToken, email, displayName)
        if (result is Resource.Success) {
            val user = result.data
            val profileRes = userRepository.getUserProfile(user.uid)
            val updatedUser = if (profileRes is Resource.Success) {
                user.copy(
                    displayName = profileRes.data.displayName.ifBlank { user.displayName },
                    isTwoFactorEnabled = profileRes.data.isTwoFactorEnabled || user.isTwoFactorEnabled
                )
            } else {
                user
            }
            return Resource.Success(updatedUser)
        }
        return result
    }
}
