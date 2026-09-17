package com.example.secureauth2fa.domain.usecase

import com.example.secureauth2fa.data.model.User
import com.example.secureauth2fa.domain.repository.AuthRepository
import com.example.secureauth2fa.utils.Resource
import com.example.secureauth2fa.utils.Validators

class RegisterUseCase(
    private val authRepository: AuthRepository
) {
    suspend operator fun invoke(email: String, password: String, displayName: String): Resource<User> {
        if (!Validators.isValidEmail(email)) {
            return Resource.Error("Please enter a valid email address.")
        }
        val passValidation = Validators.validatePassword(password)
        if (!passValidation.isValid) {
            return Resource.Error("Password must be at least 8 characters, include an uppercase letter, a number, and a special character.")
        }

        return authRepository.register(email, password, displayName)
    }
}
