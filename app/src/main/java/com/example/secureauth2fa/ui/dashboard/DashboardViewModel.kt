package com.example.secureauth2fa.ui.dashboard

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.secureauth2fa.data.auth.TotpDataSource
import com.example.secureauth2fa.data.local.EncryptedPrefsManager
import com.example.secureauth2fa.data.local.InactivityAction
import com.example.secureauth2fa.data.model.AuthenticatorAccount
import com.example.secureauth2fa.data.model.SecurityHistoryItem
import com.example.secureauth2fa.data.model.Session
import com.example.secureauth2fa.data.model.User
import com.example.secureauth2fa.domain.repository.UserRepository
import com.example.secureauth2fa.domain.usecase.SessionUseCase
import com.example.secureauth2fa.domain.usecase.VerifyTotpUseCase
import com.example.secureauth2fa.utils.Resource
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.net.URLDecoder
import java.util.UUID

enum class DashboardTab {
    HOME,
    HISTORY,
    SETTINGS
}

data class DashboardUiState(
    val user: User? = null,
    val selectedTab: DashboardTab = DashboardTab.HOME,

    // Accounts & Live TOTP
    val accounts: List<AuthenticatorAccount> = emptyList(),
    val filteredAccounts: List<AuthenticatorAccount> = emptyList(),
    val searchQuery: String = "",
    val totpCodes: Map<String, String> = emptyMap(), // accountId -> 6-digit code
    val secondsRemaining: Int = 30,
    val progress: Float = 1.0f,

    // Add Account Form / Sheet
    val isAddAccountSheetVisible: Boolean = false,
    val formIssuer: String = "",
    val formAccountName: String = "",
    val formSecretKey: String = "",
    val formAlgorithm: String = "SHA1",
    val formDigits: Int = 6,
    val formPeriod: Int = 30,
    val formError: String? = null,
    val isSavingAccount: Boolean = false,

    // Account Detail Dialogs
    val accountToDelete: AuthenticatorAccount? = null,
    val accountToViewSecret: AuthenticatorAccount? = null,

    // History Tab
    val historyItems: List<SecurityHistoryItem> = emptyList(),
    val historyFilter: String = "ALL", // "ALL", "CODES", "ACCOUNTS", "SECURITY"
    val showClearHistoryDialog: Boolean = false,

    // Settings & Security state
    val isTwoFactorEnabled: Boolean = false,
    val sessions: List<Session> = emptyList(),
    val isDeviceRemembered: Boolean = false,
    val isBiometricEnabled: Boolean = false,
    val sessionTimeoutMinutes: Int = 15,
    val inactivityAction: String = "reauth_prompt",

    // UI Loading & Messages
    val isLoading: Boolean = false,
    val isSessionsLoading: Boolean = false,
    val errorMessage: String? = null,
    val successMessage: String? = null,
    val showDisable2FADialog: Boolean = false,
    val disable2FAConfirmCode: String = "",
    val isDisabling2FA: Boolean = false
)

class DashboardViewModel(
    private val userRepository: UserRepository,
    private val sessionUseCase: SessionUseCase,
    private val verifyTotpUseCase: VerifyTotpUseCase,
    private val prefsManager: EncryptedPrefsManager,
    private val totpDataSource: TotpDataSource
) : ViewModel() {

    private val _uiState = MutableStateFlow(DashboardUiState())
    val uiState: StateFlow<DashboardUiState> = _uiState.asStateFlow()

    private var timerJob: Job? = null
    private var currentUserId: String = ""

    fun loadDashboard(userId: String) {
        currentUserId = userId
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, errorMessage = null) }
            val profileRes = userRepository.getUserProfile(userId)
            val isRemembered = sessionUseCase.isDeviceRemembered(userId)
            val isBio = prefsManager.isBiometricEnabled()
            val timeout = prefsManager.getInactivityTimeoutMinutes()
            val action = prefsManager.getInactivityAction()

            // Load accounts from secure storage
            var loadedAccounts = prefsManager.getAccounts(userId)

            // If accounts are empty and the user has an active TOTP secret from profile, seed their primary account
            val savedSecret = prefsManager.getTotpSecret(userId)
            if (loadedAccounts.isEmpty() && !savedSecret.isNullOrBlank()) {
                val primaryAccount = AuthenticatorAccount(
                    issuer = "SecureAuth Account",
                    accountName = (profileRes as? Resource.Success)?.data?.email ?: "Primary Account",
                    secretKey = savedSecret
                )
                prefsManager.saveAccount(userId, primaryAccount)
                loadedAccounts = listOf(primaryAccount)
            }

            val history = prefsManager.getHistory(userId)

            _uiState.update { state ->
                state.copy(
                    user = if (profileRes is Resource.Success) profileRes.data else state.user,
                    isTwoFactorEnabled = (profileRes as? Resource.Success)?.data?.isTwoFactorEnabled ?: state.isTwoFactorEnabled,
                    isDeviceRemembered = isRemembered,
                    isBiometricEnabled = isBio,
                    sessionTimeoutMinutes = timeout,
                    inactivityAction = action,
                    accounts = loadedAccounts,
                    filteredAccounts = filterAccounts(loadedAccounts, state.searchQuery),
                    historyItems = history,
                    isLoading = false
                )
            }

            // Start ticking timer for live TOTP codes and countdown
            startTotpTimer()

            // Load sessions for settings/sessions view
            loadSessions(userId)
        }
    }

    private fun startTotpTimer() {
        timerJob?.cancel()
        timerJob = viewModelScope.launch {
            while (isActive) {
                val currentTimeSeconds = System.currentTimeMillis() / 1000L
                val period = 30L
                val elapsedInCycle = currentTimeSeconds % period
                val remaining = (period - elapsedInCycle).toInt()
                val progress = remaining / 30f

                val accounts = _uiState.value.accounts
                val codes = accounts.associate { account ->
                    account.id to generateCodeSafe(account.secretKey)
                }

                _uiState.update { state ->
                    state.copy(
                        secondsRemaining = remaining,
                        progress = progress,
                        totpCodes = codes
                    )
                }

                delay(1000L)
            }
        }
    }

    private fun generateCodeSafe(secretKey: String): String {
        return try {
            val cleanKey = secretKey.replace(" ", "").trim().uppercase()
            if (cleanKey.length >= 8) {
                totpDataSource.getCurrentTotpCode(cleanKey)
            } else {
                "------"
            }
        } catch (e: Exception) {
            "------"
        }
    }

    fun selectTab(tab: DashboardTab) {
        _uiState.update { it.copy(selectedTab = tab) }
    }

    // --- Search & Filtering ---
    fun onSearchQueryChanged(query: String) {
        _uiState.update { state ->
            state.copy(
                searchQuery = query,
                filteredAccounts = filterAccounts(state.accounts, query)
            )
        }
    }

    private fun filterAccounts(accounts: List<AuthenticatorAccount>, query: String): List<AuthenticatorAccount> {
        if (query.isBlank()) return accounts
        val lower = query.lowercase().trim()
        return accounts.filter {
            it.issuer.lowercase().contains(lower) || it.accountName.lowercase().contains(lower)
        }
    }

    // --- Add Account Sheet & Form ---
    fun openAddAccountSheet() {
        _uiState.update {
            it.copy(
                isAddAccountSheetVisible = true,
                formIssuer = "",
                formAccountName = "",
                formSecretKey = "",
                formAlgorithm = "SHA1",
                formDigits = 6,
                formPeriod = 30,
                formError = null
            )
        }
    }

    fun closeAddAccountSheet() {
        _uiState.update { it.copy(isAddAccountSheetVisible = false, formError = null) }
    }

    fun onFormIssuerChanged(value: String) {
        _uiState.update { it.copy(formIssuer = value, formError = null) }
    }

    fun onFormAccountNameChanged(value: String) {
        _uiState.update { it.copy(formAccountName = value, formError = null) }
    }

    fun onFormSecretKeyChanged(value: String) {
        val trimmed = value.trim()
        // Check if user pasted an otpauth:// URI
        if (trimmed.startsWith("otpauth://", ignoreCase = true)) {
            parseOtpAuthUri(trimmed)
        } else {
            _uiState.update { it.copy(formSecretKey = value, formError = null) }
        }
    }

    private fun parseOtpAuthUri(uriString: String) {
        try {
            // e.g. otpauth://totp/Google:john%40gmail.com?secret=JBSWY3DPEHPK3PXP&issuer=Google
            val uri = android.net.Uri.parse(uriString)
            val secret = uri.getQueryParameter("secret") ?: ""
            val issuerQuery = uri.getQueryParameter("issuer")
            val path = uri.path?.removePrefix("/") ?: ""
            val parts = path.split(":")
            val issuer = issuerQuery ?: if (parts.size > 1) parts[0] else "Custom"
            val accountName = if (parts.size > 1) parts[1] else parts[0]
            val digits = uri.getQueryParameter("digits")?.toIntOrNull() ?: 6
            val period = uri.getQueryParameter("period")?.toIntOrNull() ?: 30

            _uiState.update {
                it.copy(
                    formIssuer = URLDecoder.decode(issuer, "UTF-8"),
                    formAccountName = URLDecoder.decode(accountName, "UTF-8"),
                    formSecretKey = secret.uppercase(),
                    formDigits = digits,
                    formPeriod = period,
                    formError = null
                )
            }
        } catch (e: Exception) {
            _uiState.update { it.copy(formSecretKey = uriString) }
        }
    }

    fun generateRandomSecret() {
        val generatedKey = totpDataSource.generateSecretKey()
        _uiState.update { it.copy(formSecretKey = generatedKey, formError = null) }
    }

    fun selectPreset(presetName: String) {
        _uiState.update {
            it.copy(
                formIssuer = presetName,
                formError = null
            )
        }
    }

    fun saveNewAccount(userId: String) {
        val state = _uiState.value
        val issuer = state.formIssuer.trim()
        val accountName = state.formAccountName.trim()
        val secretKey = state.formSecretKey.replace(" ", "").trim().uppercase()

        if (issuer.isBlank()) {
            _uiState.update { it.copy(formError = "Please enter an account or service name (e.g. Google, GitHub).") }
            return
        }

        if (secretKey.isBlank() || secretKey.length < 8) {
            _uiState.update { it.copy(formError = "Please enter a valid Base32 secret key (minimum 8 characters).") }
            return
        }

        // Test if secret key is valid Base32
        try {
            totpDataSource.getCurrentTotpCode(secretKey)
        } catch (e: Exception) {
            _uiState.update { it.copy(formError = "Invalid Base32 secret key. Check for invalid characters.") }
            return
        }

        viewModelScope.launch {
            _uiState.update { it.copy(isSavingAccount = true) }

            val newAccount = AuthenticatorAccount(
                id = UUID.randomUUID().toString(),
                issuer = issuer,
                accountName = accountName.ifBlank { "Account" },
                secretKey = secretKey,
                algorithm = state.formAlgorithm,
                digits = state.formDigits,
                periodSeconds = state.formPeriod,
                createdAt = System.currentTimeMillis()
            )

            prefsManager.saveAccount(userId, newAccount)

            // Log event in History
            val historyItem = SecurityHistoryItem(
                actionType = "ACCOUNT_ADDED",
                title = "Added $issuer Account",
                description = "Added 2FA authentication for ${accountName.ifBlank { issuer }}."
            )
            prefsManager.addHistoryItem(userId, historyItem)

            val updatedAccounts = prefsManager.getAccounts(userId)
            val updatedHistory = prefsManager.getHistory(userId)

            _uiState.update {
                it.copy(
                    isSavingAccount = false,
                    isAddAccountSheetVisible = false,
                    accounts = updatedAccounts,
                    filteredAccounts = filterAccounts(updatedAccounts, it.searchQuery),
                    historyItems = updatedHistory,
                    successMessage = "Account '$issuer' added successfully!"
                )
            }
        }
    }

    fun addDemoAccount(userId: String, preset: String) {
        viewModelScope.launch {
            val secretKey = totpDataSource.generateSecretKey()
            val account = when (preset.lowercase()) {
                "google" -> AuthenticatorAccount(
                    issuer = "Google",
                    accountName = "user@gmail.com",
                    secretKey = secretKey
                )
                "github" -> AuthenticatorAccount(
                    issuer = "GitHub",
                    accountName = "developer",
                    secretKey = secretKey
                )
                "microsoft" -> AuthenticatorAccount(
                    issuer = "Microsoft",
                    accountName = "work@outlook.com",
                    secretKey = secretKey
                )
                else -> AuthenticatorAccount(
                    issuer = preset,
                    accountName = "my-account",
                    secretKey = secretKey
                )
            }

            prefsManager.saveAccount(userId, account)

            val historyItem = SecurityHistoryItem(
                actionType = "ACCOUNT_ADDED",
                title = "Added ${account.issuer} Demo",
                description = "Created demo 2FA account for testing."
            )
            prefsManager.addHistoryItem(userId, historyItem)

            val updatedAccounts = prefsManager.getAccounts(userId)
            val updatedHistory = prefsManager.getHistory(userId)

            _uiState.update {
                it.copy(
                    accounts = updatedAccounts,
                    filteredAccounts = filterAccounts(updatedAccounts, it.searchQuery),
                    historyItems = updatedHistory,
                    successMessage = "Added ${account.issuer} 2FA account!"
                )
            }
        }
    }

    // --- Copy TOTP Code ---
    fun copyTotpCode(context: Context, userId: String, account: AuthenticatorAccount, code: String) {
        if (code == "------" || code.isBlank()) return

        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        val clip = ClipData.newPlainText("2FA Code", code)
        clipboard.setPrimaryClip(clip)

        // Log into history
        val historyItem = SecurityHistoryItem(
            actionType = "CODE_COPIED",
            title = "Copied ${account.issuer} Code",
            description = "Code $code copied to clipboard for ${account.accountName.ifBlank { account.issuer }}."
        )
        prefsManager.addHistoryItem(userId, historyItem)

        val updatedHistory = prefsManager.getHistory(userId)

        _uiState.update {
            it.copy(
                historyItems = updatedHistory,
                successMessage = "Copied code for ${account.issuer} ($code)"
            )
        }
    }

    // --- Delete Account ---
    fun openDeleteAccountDialog(account: AuthenticatorAccount) {
        _uiState.update { it.copy(accountToDelete = account) }
    }

    fun closeDeleteAccountDialog() {
        _uiState.update { it.copy(accountToDelete = null) }
    }

    fun confirmDeleteAccount(userId: String) {
        val account = _uiState.value.accountToDelete ?: return
        viewModelScope.launch {
            prefsManager.deleteAccount(userId, account.id)

            val historyItem = SecurityHistoryItem(
                actionType = "ACCOUNT_DELETED",
                title = "Removed ${account.issuer} Account",
                description = "Deleted 2FA account ${account.accountName}."
            )
            prefsManager.addHistoryItem(userId, historyItem)

            val updatedAccounts = prefsManager.getAccounts(userId)
            val updatedHistory = prefsManager.getHistory(userId)

            _uiState.update {
                it.copy(
                    accountToDelete = null,
                    accounts = updatedAccounts,
                    filteredAccounts = filterAccounts(updatedAccounts, it.searchQuery),
                    historyItems = updatedHistory,
                    successMessage = "Account '${account.issuer}' removed."
                )
            }
        }
    }

    // --- View Secret Key ---
    fun openViewSecretDialog(account: AuthenticatorAccount) {
        _uiState.update { it.copy(accountToViewSecret = account) }
    }

    fun closeViewSecretDialog() {
        _uiState.update { it.copy(accountToViewSecret = null) }
    }

    // --- History Filtering & Clearing ---
    fun setHistoryFilter(filter: String) {
        _uiState.update { it.copy(historyFilter = filter) }
    }

    fun openClearHistoryDialog() {
        _uiState.update { it.copy(showClearHistoryDialog = true) }
    }

    fun closeClearHistoryDialog() {
        _uiState.update { it.copy(showClearHistoryDialog = false) }
    }

    fun confirmClearHistory(userId: String) {
        prefsManager.clearHistory(userId)
        _uiState.update {
            it.copy(
                historyItems = emptyList(),
                showClearHistoryDialog = false,
                successMessage = "History audit log cleared."
            )
        }
    }

    // --- Settings & Sessions ---
    fun toggleBiometric(enabled: Boolean) {
        prefsManager.setBiometricEnabled(enabled)
        _uiState.update {
            it.copy(
                isBiometricEnabled = enabled,
                successMessage = if (enabled) "Biometric Lock enabled." else "Biometric Lock disabled."
            )
        }
    }

    fun setSessionTimeout(minutes: Int) {
        sessionUseCase.setInactivityTimeoutMinutes(minutes)
        _uiState.update {
            it.copy(
                sessionTimeoutMinutes = minutes,
                successMessage = "Inactivity timeout set to $minutes minutes."
            )
        }
    }

    fun setInactivityAction(action: String) {
        val enumAction = if (action == "auto_logout") InactivityAction.AUTO_LOGOUT else InactivityAction.REAUTH_PROMPT
        sessionUseCase.setInactivityAction(enumAction)
        _uiState.update {
            it.copy(
                inactivityAction = action,
                successMessage = "Inactivity action updated."
            )
        }
    }

    fun loadSessions(userId: String) {
        viewModelScope.launch {
            _uiState.update { it.copy(isSessionsLoading = true) }
            val sessionsRes = userRepository.getSessions(userId)
            if (sessionsRes is Resource.Success) {
                _uiState.update {
                    it.copy(
                        sessions = sessionsRes.data,
                        isSessionsLoading = false
                    )
                }
            } else {
                _uiState.update { it.copy(isSessionsLoading = false) }
            }
        }
    }

    fun openDisable2FADialog() {
        _uiState.update {
            it.copy(
                showDisable2FADialog = true,
                disable2FAConfirmCode = "",
                errorMessage = null
            )
        }
    }

    fun closeDisable2FADialog() {
        _uiState.update { it.copy(showDisable2FADialog = false, disable2FAConfirmCode = "") }
    }

    fun onDisable2FACodeChanged(code: String) {
        _uiState.update { it.copy(disable2FAConfirmCode = code, errorMessage = null) }
    }

    fun confirmDisable2FA(userId: String) {
        val code = _uiState.value.disable2FAConfirmCode.trim()
        if (code.length != 6) {
            _uiState.update { it.copy(errorMessage = "Please enter your 6-digit TOTP code to confirm.") }
            return
        }

        viewModelScope.launch {
            _uiState.update { it.copy(isDisabling2FA = true, errorMessage = null) }
            val result = verifyTotpUseCase.disable2FA(userId, code)
            when (result) {
                is Resource.Success -> {
                    _uiState.update {
                        it.copy(
                            isDisabling2FA = false,
                            showDisable2FADialog = false,
                            isTwoFactorEnabled = false,
                            successMessage = "Two-Factor Authentication disabled successfully."
                        )
                    }
                }
                is Resource.Error -> {
                    _uiState.update {
                        it.copy(
                            isDisabling2FA = false,
                            errorMessage = result.message
                        )
                    }
                }
                Resource.Loading -> {}
            }
        }
    }

    fun clearMessages() {
        _uiState.update { it.copy(errorMessage = null, successMessage = null) }
    }

    fun logout(userId: String, onLoggedOut: () -> Unit) {
        timerJob?.cancel()
        sessionUseCase.endSession(userId)
        onLoggedOut()
    }

    override fun onCleared() {
        super.onCleared()
        timerJob?.cancel()
    }
}
