package com.example.secureauth2fa.ui.auth

import android.app.Activity
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.secureauth2fa.data.auth.GoogleAuthManager
import com.example.secureauth2fa.data.auth.GoogleSignInResult
import com.example.secureauth2fa.data.model.User
import com.example.secureauth2fa.domain.usecase.ForgotPasswordUseCase
import com.example.secureauth2fa.domain.usecase.LoginUseCase
import com.example.secureauth2fa.domain.usecase.LoginWithGoogleUseCase
import com.example.secureauth2fa.domain.usecase.RegisterUseCase
import com.example.secureauth2fa.domain.usecase.SessionUseCase
import com.example.secureauth2fa.utils.PasswordValidationResult
import com.example.secureauth2fa.utils.Resource
import com.example.secureauth2fa.utils.Validators
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class AuthUiState(
    val email: String = "",
    val password: String = "",
    val displayName: String = "",
    val rememberDevice: Boolean = true,
    val isLoading: Boolean = false,
    val errorMessage: String? = null,
    val successMessage: String? = null,
    val passwordCriteria: PasswordValidationResult = PasswordValidationResult(),
    val authenticatedUser: User? = null,
    val navigateTo2FA: Boolean = false,
    val navigateToSetup2FA: Boolean = false,
    val navigateToDashboard: Boolean = false,
    val isBiometricAvailable: Boolean = false,
    val showGoogleAccountChooser: Boolean = false
)

class AuthViewModel(
    private val loginUseCase: LoginUseCase,
    private val registerUseCase: RegisterUseCase,
    private val forgotPasswordUseCase: ForgotPasswordUseCase,
    private val sessionUseCase: SessionUseCase,
    private val loginWithGoogleUseCase: LoginWithGoogleUseCase,
    private val googleAuthManager: GoogleAuthManager
) : ViewModel() {

    private val _uiState = MutableStateFlow(AuthUiState())
    val uiState: StateFlow<AuthUiState> = _uiState.asStateFlow()

    fun onEmailChanged(newEmail: String) {
        _uiState.value = _uiState.value.copy(email = newEmail, errorMessage = null)
    }

    fun onPasswordChanged(newPassword: String) {
        val criteria = Validators.validatePassword(newPassword)
        _uiState.value = _uiState.value.copy(
            password = newPassword,
            passwordCriteria = criteria,
            errorMessage = null
        )
    }

    fun onDisplayNameChanged(newName: String) {
        _uiState.value = _uiState.value.copy(displayName = newName, errorMessage = null)
    }

    fun onRememberDeviceToggled(checked: Boolean) {
        _uiState.value = _uiState.value.copy(rememberDevice = checked)
    }

    fun clearMessages() {
        _uiState.value = _uiState.value.copy(errorMessage = null, successMessage = null)
    }

    fun login() {
        val email = _uiState.value.email.trim()
        val password = _uiState.value.password

        if (email.isBlank()) {
            _uiState.value = _uiState.value.copy(errorMessage = "Please enter your email.")
            return
        }
        if (password.isBlank()) {
            _uiState.value = _uiState.value.copy(errorMessage = "Please enter your password.")
            return
        }

        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true, errorMessage = null)
            val result = loginUseCase(email, password)
            when (result) {
                is Resource.Success -> {
                    val user = result.data
                    val isRemembered = sessionUseCase.isDeviceRemembered(user.uid)
                    
                    if (user.isTwoFactorEnabled && !isRemembered) {
                        // If user has explicitly enabled 2FA and device is not remembered, prompt for 2FA code
                        _uiState.value = _uiState.value.copy(
                            isLoading = false,
                            authenticatedUser = user,
                            navigateTo2FA = true
                        )
                    } else {
                        // Directly go to dashboard (home screen)
                        sessionUseCase.startSession(user.uid)
                        _uiState.value = _uiState.value.copy(
                            isLoading = false,
                            authenticatedUser = user,
                            navigateToDashboard = true
                        )
                    }
                }
                is Resource.Error -> {
                    _uiState.value = _uiState.value.copy(
                        isLoading = false,
                        errorMessage = result.message
                    )
                }
                Resource.Loading -> {}
            }
        }
    }

    fun register() {
        val email = _uiState.value.email.trim()
        val password = _uiState.value.password
        val displayName = _uiState.value.displayName.trim()

        if (!Validators.isValidEmail(email)) {
            _uiState.value = _uiState.value.copy(errorMessage = "Please enter a valid email.")
            return
        }
        if (!_uiState.value.passwordCriteria.isValid) {
            _uiState.value = _uiState.value.copy(errorMessage = "Password does not meet the security criteria.")
            return
        }

        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true, errorMessage = null)
            val result = registerUseCase(email, password, displayName)
            when (result) {
                is Resource.Success -> {
                    sessionUseCase.startSession(result.data.uid)
                    _uiState.value = _uiState.value.copy(
                        isLoading = false,
                        authenticatedUser = result.data,
                        successMessage = "Account registered successfully!",
                        navigateToDashboard = true
                    )
                }
                is Resource.Error -> {
                    _uiState.value = _uiState.value.copy(
                        isLoading = false,
                        errorMessage = result.message
                    )
                }
                Resource.Loading -> {}
            }
        }
    }

    fun loginWithGoogle(activity: Activity? = null) {
        if (activity == null) {
            _uiState.value = _uiState.value.copy(showGoogleAccountChooser = true)
            return
        }
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true, errorMessage = null)
            when (val result = googleAuthManager.requestGoogleSignIn(activity)) {
                is GoogleSignInResult.Success -> {
                    processGoogleSignIn(
                        idToken = result.idToken,
                        email = result.email,
                        displayName = result.displayName
                    )
                }
                is GoogleSignInResult.NeedAccountSelection -> {
                    _uiState.value = _uiState.value.copy(
                        isLoading = false,
                        showGoogleAccountChooser = true
                    )
                }
                is GoogleSignInResult.Cancelled -> {
                    _uiState.value = _uiState.value.copy(isLoading = false)
                }
                is GoogleSignInResult.Error -> {
                    _uiState.value = _uiState.value.copy(
                        isLoading = false,
                        errorMessage = result.message
                    )
                }
            }
        }
    }

    fun onGoogleAccountSelected(email: String, displayName: String) {
        _uiState.value = _uiState.value.copy(showGoogleAccountChooser = false)
        processGoogleSignIn(idToken = null, email = email, displayName = displayName)
    }

    fun dismissGoogleAccountChooser() {
        _uiState.value = _uiState.value.copy(showGoogleAccountChooser = false)
    }

    fun processGoogleSignIn(idToken: String?, email: String, displayName: String) {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true, errorMessage = null)
            val result = loginWithGoogleUseCase(idToken = idToken, email = email, displayName = displayName)
            when (result) {
                is Resource.Success -> {
                    val user = result.data
                    val isRemembered = sessionUseCase.isDeviceRemembered(user.uid)
                    if (user.isTwoFactorEnabled && !isRemembered) {
                        _uiState.value = _uiState.value.copy(
                            isLoading = false,
                            authenticatedUser = user,
                            navigateTo2FA = true
                        )
                    } else {
                        sessionUseCase.startSession(user.uid)
                        _uiState.value = _uiState.value.copy(
                            isLoading = false,
                            authenticatedUser = user,
                            navigateToDashboard = true
                        )
                    }
                }
                is Resource.Error -> {
                    _uiState.value = _uiState.value.copy(
                        isLoading = false,
                        errorMessage = result.message
                    )
                }
                Resource.Loading -> {}
            }
        }
    }

    fun skipLogin() {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true, errorMessage = null)
            val guestUser = User(
                uid = "guest_user_id",
                email = "guest@secureauth.local",
                displayName = "Guest User",
                isTwoFactorEnabled = false,
                isEmailVerified = true,
                createdAt = System.currentTimeMillis()
            )
            sessionUseCase.startSession(guestUser.uid)
            _uiState.value = _uiState.value.copy(
                isLoading = false,
                authenticatedUser = guestUser,
                navigateToDashboard = true
            )
        }
    }

    fun forgotPassword() {
        val email = _uiState.value.email.trim()
        if (!Validators.isValidEmail(email)) {
            _uiState.value = _uiState.value.copy(errorMessage = "Please enter a valid email address.")
            return
        }

        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true, errorMessage = null)
            val result = forgotPasswordUseCase(email)
            when (result) {
                is Resource.Success -> {
                    _uiState.value = _uiState.value.copy(
                        isLoading = false,
                        successMessage = "Password reset email sent. Please check your inbox."
                    )
                }
                is Resource.Error -> {
                    _uiState.value = _uiState.value.copy(
                        isLoading = false,
                        errorMessage = result.message
                    )
                }
                Resource.Loading -> {}
            }
        }
    }

    fun resetNavigation() {
        _uiState.value = _uiState.value.copy(
            navigateTo2FA = false,
            navigateToSetup2FA = false,
            navigateToDashboard = false
        )
    }
}
