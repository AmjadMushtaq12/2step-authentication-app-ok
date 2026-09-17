package com.example.secureauth2fa.utils

import android.util.Patterns

data class PasswordValidationResult(
    val hasMinLength: Boolean = false,
    val hasUpperCase: Boolean = false,
    val hasNumber: Boolean = false,
    val hasSpecialChar: Boolean = false
) {
    val isValid: Boolean
        get() = hasMinLength && hasUpperCase && hasNumber && hasSpecialChar

    val strengthScore: Float
        get() {
            var score = 0
            if (hasMinLength) score++
            if (hasUpperCase) score++
            if (hasNumber) score++
            if (hasSpecialChar) score++
            return score / 4f
        }
}

object Validators {
    fun isValidEmail(email: String): Boolean {
        return email.isNotBlank() && Patterns.EMAIL_ADDRESS.matcher(email.trim()).matches()
    }

    fun validatePassword(password: String): PasswordValidationResult {
        return PasswordValidationResult(
            hasMinLength = password.length >= 8,
            hasUpperCase = password.any { it.isUpperCase() },
            hasNumber = password.any { it.isDigit() },
            hasSpecialChar = password.any { !it.isLetterOrDigit() && !it.isWhitespace() }
        )
    }

    fun isValidOtpCode(otp: String): Boolean {
        return otp.length == 6 && otp.all { it.isDigit() }
    }

    fun isValidBackupCode(code: String): Boolean {
        val sanitized = code.replace("-", "").trim()
        return sanitized.length == 8 && sanitized.all { it.isLetterOrDigit() }
    }
}
