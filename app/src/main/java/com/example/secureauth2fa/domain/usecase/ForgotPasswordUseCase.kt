package com.example.secureauth2fa.domain.usecase

import com.example.secureauth2fa.domain.repository.AuthRepository
import com.example.secureauth2fa.utils.Resource
import com.example.secureauth2fa.utils.Validators

class ForgotPasswordUseCase(
    private val authRepository: AuthRepository
) {
    suspend operator fun invoke(email: String): Resource<Unit> {
        if (!Validators.isValidEmail(email)) {
            return Resource.Error("Please enter a valid email address.")
        }
        return authRepository.sendPasswordReset(email)
    }
}
