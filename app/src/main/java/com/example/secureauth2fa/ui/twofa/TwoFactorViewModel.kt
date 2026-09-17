package com.example.secureauth2fa.ui.twofa

import android.app.Activity
import android.graphics.Bitmap
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.secureauth2fa.ads.AdManager
import com.example.secureauth2fa.data.auth.TotpSetupResult
import com.example.secureauth2fa.data.model.BackupCode
import com.example.secureauth2fa.domain.usecase.SessionUseCase
import com.example.secureauth2fa.domain.usecase.Setup2FAUseCase
import com.example.secureauth2fa.domain.usecase.VerifyTotpUseCase
import com.example.secureauth2fa.utils.Constants
import com.example.secureauth2fa.utils.Resource
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

data class TwoFactorUiState(
    // Setup state
    val secretKey: String = "",
    val qrCodeBitmap: Bitmap? = null,
    val otpAuthUri: String = "",
    val backupCodes: List<BackupCode> = emptyList(),
    val setupVerificationCode: String = "",

    // Verification state
    val enteredCode: String = "",
    val recoveryCodeInput: String = "",
    val isUsingRecoveryCode: Boolean = false,
    val rememberDevice: Boolean = true,
    val timerRemainingSeconds: Int = 30,
    val timerProgress: Float = 1.0f,

    // Status
    val isLoading: Boolean = false,
    val errorMessage: String? = null,
    val successMessage: String? = null,
    val isSetupComplete: Boolean = false,
    val isVerificationSuccess: Boolean = false
)

class TwoFactorViewModel(
    private val setup2FAUseCase: Setup2FAUseCase,
    private val verifyTotpUseCase: VerifyTotpUseCase,
    private val sessionUseCase: SessionUseCase
) : ViewModel() {

    private val _uiState = MutableStateFlow(TwoFactorUiState())
    val uiState: StateFlow<TwoFactorUiState> = _uiState.asStateFlow()

    private var cachedSetupResult: TotpSetupResult? = null

    init {
        startTotpCountdownTimer()
    }

    private fun startTotpCountdownTimer() {
        viewModelScope.launch {
            while (isActive) {
                val currentSecond = (System.currentTimeMillis() / 1000L) % Constants.TOTP_PERIOD_SECONDS
                val remaining = (Constants.TOTP_PERIOD_SECONDS - currentSecond).toInt()
                val progress = remaining.toFloat() / Constants.TOTP_PERIOD_SECONDS.toFloat()

                _uiState.value = _uiState.value.copy(
                    timerRemainingSeconds = remaining,
                    timerProgress = progress
                )
                delay(1000L)
            }
        }
    }

    fun initializeSetup(userEmail: String) {
        val email = if (userEmail.isNotBlank()) userEmail else "user@secureauth.com"
        val result = setup2FAUseCase.generateSetupData(email)
        cachedSetupResult = result
        _uiState.value = _uiState.value.copy(
            secretKey = result.secretKey,
            qrCodeBitmap = result.qrCodeBitmap,
            otpAuthUri = result.otpAuthUri,
            backupCodes = result.backupCodes,
            errorMessage = null
        )
    }

    fun onSetupCodeChanged(code: String) {
        _uiState.value = _uiState.value.copy(setupVerificationCode = code, errorMessage = null)
    }

    fun onVerifyCodeChanged(code: String) {
        _uiState.value = _uiState.value.copy(enteredCode = code, errorMessage = null)
    }

    fun onRecoveryCodeChanged(code: String) {
        _uiState.value = _uiState.value.copy(recoveryCodeInput = code, errorMessage = null)
    }

    fun toggleUseRecoveryCode(useRecovery: Boolean) {
        _uiState.value = _uiState.value.copy(
            isUsingRecoveryCode = useRecovery,
            errorMessage = null
        )
    }

    fun onRememberDeviceToggled(remember: Boolean) {
        _uiState.value = _uiState.value.copy(rememberDevice = remember)
    }

    fun autoFillSetupCode() {
        val secret = _uiState.value.secretKey
        if (secret.isNotBlank()) {
            val code = setup2FAUseCase.getCurrentTotpCode(secret)
            _uiState.value = _uiState.value.copy(setupVerificationCode = code, errorMessage = null)
        }
    }

    fun autoFillVerifyCode(userId: String) {
        val code = verifyTotpUseCase.getCurrentTotpCode(userId)
        if (code != null) {
            _uiState.value = _uiState.value.copy(enteredCode = code, errorMessage = null)
        }
    }

    fun completeSetup(userId: String, onComplete: () -> Unit) {
        val code = _uiState.value.setupVerificationCode.trim()
        val setupResult = cachedSetupResult ?: return

        if (code.length != Constants.TOTP_DIGITS) {
            _uiState.value = _uiState.value.copy(errorMessage = "Please enter the 6-digit code from your authenticator app.")
            return
        }

        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true, errorMessage = null)
            val result = setup2FAUseCase.completeSetup(userId, setupResult.secretKey, code, setupResult)
            when (result) {
                is Resource.Success -> {
                    _uiState.value = _uiState.value.copy(
                        isLoading = false,
                        isSetupComplete = true,
                        successMessage = "Two-Factor Authentication successfully enabled!"
                    )
                    onComplete()
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

    fun verifyCode(userId: String, activity: Activity?, onSuccess: () -> Unit) {
        val remember = _uiState.value.rememberDevice

        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true, errorMessage = null)

            val result = if (_uiState.value.isUsingRecoveryCode) {
                val recoveryCode = _uiState.value.recoveryCodeInput.trim()
                verifyTotpUseCase.verifyWithBackupCode(userId, recoveryCode, remember)
            } else {
                val otp = _uiState.value.enteredCode.trim()
                verifyTotpUseCase.verifyOtp(userId, otp, remember)
            }

            when (result) {
                is Resource.Success -> {
                    sessionUseCase.startSession(userId)
                    _uiState.value = _uiState.value.copy(
                        isLoading = false,
                        isVerificationSuccess = true
                    )
                    // Show Interstitial ad after successful 2FA verification (max once every 3 minutes)
                    if (activity != null) {
                        AdManager.getInstance().showInterstitialAfter2FA(activity) {
                            onSuccess()
                        }
                    } else {
                        onSuccess()
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
}
