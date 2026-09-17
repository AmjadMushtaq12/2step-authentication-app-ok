package com.example.secureauth2fa.ui.settings

import android.app.Activity
import androidx.lifecycle.ViewModel
import com.example.secureauth2fa.ads.AdManager
import com.example.secureauth2fa.data.local.InactivityAction
import com.example.secureauth2fa.domain.usecase.SessionUseCase
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.concurrent.TimeUnit

data class SettingsUiState(
    val isBiometricEnabled: Boolean = false,
    val inactivityTimeoutMinutes: Int = 15,
    val inactivityAction: InactivityAction = InactivityAction.REAUTH_PROMPT,
    val isAdFree: Boolean = false,
    val adFreeRemainingText: String = "",
    val isLoadingAd: Boolean = false,
    val message: String? = null,
    val errorMessage: String? = null
)

class SettingsViewModel(
    private val sessionUseCase: SessionUseCase
) : ViewModel() {

    private val _uiState = MutableStateFlow(SettingsUiState())
    val uiState: StateFlow<SettingsUiState> = _uiState.asStateFlow()

    init {
        loadSettings()
    }

    fun loadSettings() {
        val biometric = sessionUseCase.isBiometricEnabled()
        val timeout = sessionUseCase.getInactivityTimeoutMinutes()
        val action = sessionUseCase.getInactivityAction()
        val isAdFree = AdManager.getInstance().isAdFree()
        val until = AdManager.getInstance().adFreeUntilTimestamp.value
        val remainingText = formatRemainingAdFreeTime(until)

        _uiState.value = _uiState.value.copy(
            isBiometricEnabled = biometric,
            inactivityTimeoutMinutes = timeout,
            inactivityAction = action,
            isAdFree = isAdFree,
            adFreeRemainingText = remainingText
        )
    }

    fun toggleBiometric(enabled: Boolean) {
        sessionUseCase.setBiometricEnabled(enabled)
        _uiState.value = _uiState.value.copy(isBiometricEnabled = enabled)
    }

    fun setInactivityTimeout(minutes: Int) {
        sessionUseCase.setInactivityTimeoutMinutes(minutes)
        _uiState.value = _uiState.value.copy(inactivityTimeoutMinutes = minutes)
    }

    fun setInactivityAction(action: InactivityAction) {
        sessionUseCase.setInactivityAction(action)
        _uiState.value = _uiState.value.copy(inactivityAction = action)
    }

    fun testInactivityReauthNow() {
        sessionUseCase.triggerReauthPrompt()
    }

    fun watchRewardedAd(activity: Activity) {
        _uiState.value = _uiState.value.copy(isLoadingAd = true, errorMessage = null)
        AdManager.getInstance().showRewardedAd(
            activity = activity,
            onUserRewarded = { newExpiryMs ->
                _uiState.value = _uiState.value.copy(
                    isLoadingAd = false,
                    isAdFree = true,
                    adFreeRemainingText = formatRemainingAdFreeTime(newExpiryMs),
                    message = "7 Days of Ad-Free Experience Unlocked!"
                )
            },
            onAdClosed = {
                _uiState.value = _uiState.value.copy(isLoadingAd = false)
            },
            onError = { error ->
                _uiState.value = _uiState.value.copy(
                    isLoadingAd = false,
                    errorMessage = error
                )
            }
        )
    }

    private fun formatRemainingAdFreeTime(timestampMs: Long): String {
        val diff = timestampMs - System.currentTimeMillis()
        if (diff <= 0) return ""
        val days = TimeUnit.MILLISECONDS.toDays(diff)
        val hours = TimeUnit.MILLISECONDS.toHours(diff) % 24
        return if (days > 0) "$days days, $hours hrs remaining" else "$hours hrs remaining"
    }

    fun clearMessages() {
        _uiState.value = _uiState.value.copy(message = null, errorMessage = null)
    }
}
