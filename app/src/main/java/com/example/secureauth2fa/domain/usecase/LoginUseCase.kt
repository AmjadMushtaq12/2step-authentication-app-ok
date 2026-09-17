package com.example.secureauth2fa.domain.usecase

import com.example.secureauth2fa.data.model.User
import com.example.secureauth2fa.domain.repository.AuthRepository
import com.example.secureauth2fa.domain.repository.UserRepository
import com.example.secureauth2fa.utils.Resource
import com.example.secureauth2fa.utils.Validators

class LoginUseCase(
    private val authRepository: AuthRepository,
    private val userRepository: UserRepository
) {
    suspend operator fun invoke(email: String, password: String): Resource<User> {
        if (!Validators.isValidEmail(email)) {
            return Resource.Error("Please enter a valid email address.")
        }
        if (password.isBlank()) {
            return Resource.Error("Please enter your password.")
        }

        val result = authRepository.login(email, password)
        if (result is Resource.Success) {
            val user = result.data
            // Fetch updated profile to verify 2FA status
            val profileRes = userRepository.getUserProfile(user.uid)
            val updatedUser = if (profileRes is Resource.Success) profileRes.data else user
            return Resource.Success(updatedUser)
        }
        return result
    }
}
