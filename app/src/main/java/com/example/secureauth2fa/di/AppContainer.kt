package com.example.secureauth2fa.di

import android.content.Context
import com.example.secureauth2fa.data.auth.FirebaseAuthDataSource
import com.example.secureauth2fa.data.auth.GoogleAuthManager
import com.example.secureauth2fa.data.auth.TotpDataSource
import com.example.secureauth2fa.data.local.EncryptedPrefsManager
import com.example.secureauth2fa.data.local.SessionManager
import com.example.secureauth2fa.data.repository.AuthRepositoryImpl
import com.example.secureauth2fa.data.repository.UserRepositoryImpl
import com.example.secureauth2fa.domain.repository.AuthRepository
import com.example.secureauth2fa.domain.repository.UserRepository
import com.example.secureauth2fa.domain.usecase.ForgotPasswordUseCase
import com.example.secureauth2fa.domain.usecase.LoginUseCase
import com.example.secureauth2fa.domain.usecase.LoginWithGoogleUseCase
import com.example.secureauth2fa.domain.usecase.RegisterUseCase
import com.example.secureauth2fa.domain.usecase.SessionUseCase
import com.example.secureauth2fa.domain.usecase.Setup2FAUseCase
import com.example.secureauth2fa.domain.usecase.VerifyTotpUseCase

class AppContainer(private val context: Context) {

    val prefsManager: EncryptedPrefsManager by lazy {
        EncryptedPrefsManager(context)
    }

    val sessionManager: SessionManager by lazy {
        SessionManager(prefsManager)
    }

    val totpDataSource: TotpDataSource by lazy {
        TotpDataSource()
    }

    val firebaseAuthDataSource: FirebaseAuthDataSource by lazy {
        FirebaseAuthDataSource(context)
    }

    val googleAuthManager: GoogleAuthManager by lazy {
        GoogleAuthManager(context)
    }

    val authRepository: AuthRepository by lazy {
        AuthRepositoryImpl(firebaseAuthDataSource, prefsManager)
    }

    val userRepository: UserRepository by lazy {
        UserRepositoryImpl(firebaseAuthDataSource, prefsManager)
    }

    val loginUseCase: LoginUseCase by lazy {
        LoginUseCase(authRepository, userRepository)
    }

    val loginWithGoogleUseCase: LoginWithGoogleUseCase by lazy {
        LoginWithGoogleUseCase(authRepository, userRepository)
    }

    val registerUseCase: RegisterUseCase by lazy {
        RegisterUseCase(authRepository)
    }

    val forgotPasswordUseCase: ForgotPasswordUseCase by lazy {
        ForgotPasswordUseCase(authRepository)
    }

    val setup2FAUseCase: Setup2FAUseCase by lazy {
        Setup2FAUseCase(totpDataSource, authRepository, userRepository)
    }

    val verifyTotpUseCase: VerifyTotpUseCase by lazy {
        VerifyTotpUseCase(totpDataSource, authRepository, userRepository)
    }

    val sessionUseCase: SessionUseCase by lazy {
        SessionUseCase(authRepository, userRepository, sessionManager, prefsManager, totpDataSource)
    }
}
