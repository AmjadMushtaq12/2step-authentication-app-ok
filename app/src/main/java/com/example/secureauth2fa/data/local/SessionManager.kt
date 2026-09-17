package com.example.secureauth2fa.data.local

import android.util.Log
import com.example.secureauth2fa.utils.Constants
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlin.math.max

enum class InactivityAction(val value: String, val title: String, val description: String) {
    REAUTH_PROMPT("reauth_prompt", "Re-Authentication Prompt", "Prompt for credentials or biometrics after 15m inactivity"),
    AUTO_LOGOUT("auto_logout", "Auto-Logout", "Instantly log out and return to the login screen after 15m inactivity")
}

sealed class SessionState {
    object Idle : SessionState()
    data class Active(val userId: String, val lastActiveTime: Long, val timeoutMinutes: Int) : SessionState()
    data class InactivityWarning(val userId: String, val remainingSeconds: Long) : SessionState()
    data class ReauthRequired(val userId: String, val idleMinutes: Long) : SessionState()
    data class ExpiredAndLoggedOut(val reason: String) : SessionState()
}

class SessionManager(
    private val prefs: EncryptedPrefsManager,
    private val scope: CoroutineScope = CoroutineScope(Dispatchers.Default + SupervisorJob())
) {
    private val tag = "SessionManager"

    private var lastUserActivityTime: Long = System.currentTimeMillis()
    private var monitorJob: Job? = null

    private val _currentUserId = MutableStateFlow<String?>(null)
    val currentUserId: StateFlow<String?> = _currentUserId.asStateFlow()

    private val _isSessionExpired = MutableStateFlow(false)
    val isSessionExpired: StateFlow<Boolean> = _isSessionExpired.asStateFlow()

    private val _isReauthPromptRequired = MutableStateFlow(false)
    val isReauthPromptRequired: StateFlow<Boolean> = _isReauthPromptRequired.asStateFlow()

    private val _remainingTimeMs = MutableStateFlow(Constants.DEFAULT_INACTIVITY_TIMEOUT_MINUTES * 60 * 1000L)
    val remainingTimeMs: StateFlow<Long> = _remainingTimeMs.asStateFlow()

    private val _sessionState = MutableStateFlow<SessionState>(SessionState.Idle)
    val sessionState: StateFlow<SessionState> = _sessionState.asStateFlow()

    private val _sessionEvent = MutableSharedFlow<SessionEvent>(extraBufferCapacity = 1)
    val sessionEvent: SharedFlow<SessionEvent> = _sessionEvent.asSharedFlow()

    sealed class SessionEvent {
        data class ReauthPromptTriggered(val userId: String, val idleMinutes: Long) : SessionEvent()
        data class LoggedOutDueToInactivity(val reason: String) : SessionEvent()
        object SessionResumed : SessionEvent()
    }

    init {
        val timeoutMinutes = prefs.getInactivityTimeoutMinutes().coerceAtLeast(1)
        _remainingTimeMs.value = timeoutMinutes * 60 * 1000L
    }

    /**
     * Updates activity timestamp on user interaction (touch, tap, keypress).
     * Active user touches keep the session alive. If locked on re-auth prompt,
     * passive touch does NOT bypass the prompt.
     */
    fun updateActivity() {
        if (_isReauthPromptRequired.value) {
            return
        }
        lastUserActivityTime = System.currentTimeMillis()
        val timeoutMs = getTimeoutMillis()
        _remainingTimeMs.value = timeoutMs
        _isSessionExpired.value = false
        val userId = _currentUserId.value
        if (userId != null) {
            _sessionState.value = SessionState.Active(
                userId = userId,
                lastActiveTime = lastUserActivityTime,
                timeoutMinutes = prefs.getInactivityTimeoutMinutes()
            )
        }
    }

    /**
     * Starts monitoring for an authenticated user session.
     */
    fun startSession(userId: String) {
        _currentUserId.value = userId
        lastUserActivityTime = System.currentTimeMillis()
        _isSessionExpired.value = false
        _isReauthPromptRequired.value = false
        val timeoutMinutes = prefs.getInactivityTimeoutMinutes()
        _remainingTimeMs.value = timeoutMinutes * 60 * 1000L
        _sessionState.value = SessionState.Active(
            userId = userId,
            lastActiveTime = lastUserActivityTime,
            timeoutMinutes = timeoutMinutes
        )

        startInactivityMonitor()
        Log.d(tag, "Started session for $userId with ${timeoutMinutes}m inactivity timeout")
    }

    /**
     * Ends the user session and terminates the background monitor.
     */
    fun endSession() {
        monitorJob?.cancel()
        monitorJob = null
        _currentUserId.value = null
        _isSessionExpired.value = false
        _isReauthPromptRequired.value = false
        _sessionState.value = SessionState.Idle
        _remainingTimeMs.value = getTimeoutMillis()
        lastUserActivityTime = System.currentTimeMillis()
        Log.d(tag, "Session ended. Inactivity monitoring stopped.")
    }

    /**
     * Synchronously checks if the inactivity threshold has been exceeded.
     */
    fun checkInactivity(): Boolean {
        val timeoutMs = getTimeoutMillis()
        val idleDuration = System.currentTimeMillis() - lastUserActivityTime
        val expired = idleDuration >= timeoutMs
        if (expired && _currentUserId.value != null && !_isReauthPromptRequired.value) {
            handleInactivityTimeout()
        }
        return expired
    }

    /**
     * Called when the user successfully re-authenticates (password, biometrics, or 2FA).
     * Clears the re-auth prompt and resets inactivity timers.
     */
    fun onReauthenticated() {
        val userId = _currentUserId.value ?: return
        lastUserActivityTime = System.currentTimeMillis()
        _isSessionExpired.value = false
        _isReauthPromptRequired.value = false
        val timeoutMinutes = prefs.getInactivityTimeoutMinutes()
        _remainingTimeMs.value = timeoutMinutes * 60 * 1000L
        _sessionState.value = SessionState.Active(
            userId = userId,
            lastActiveTime = lastUserActivityTime,
            timeoutMinutes = timeoutMinutes
        )
        _sessionEvent.tryEmit(SessionEvent.SessionResumed)
        Log.i(tag, "Re-authentication verified. Resuming active session for $userId.")
    }

    /**
     * Immediately triggers the re-authentication prompt.
     * Useful for manual screen lock, security testing, or sensitive operations.
     */
    fun triggerReauthPrompt() {
        val userId = _currentUserId.value ?: return
        _isSessionExpired.value = true
        _isReauthPromptRequired.value = true
        val idleMinutes = (System.currentTimeMillis() - lastUserActivityTime) / 60000L
        _sessionState.value = SessionState.ReauthRequired(userId, max(1L, idleMinutes))
        _sessionEvent.tryEmit(SessionEvent.ReauthPromptTriggered(userId, idleMinutes))
        Log.w(tag, "Re-authentication prompt manually or programmatically triggered for $userId.")
    }

    fun setInactivityTimeoutMinutes(minutes: Int) {
        prefs.setInactivityTimeoutMinutes(minutes)
        updateActivity()
    }

    fun getInactivityTimeoutMinutes(): Int = prefs.getInactivityTimeoutMinutes()

    fun getInactivityAction(): InactivityAction {
        val saved = prefs.getInactivityAction()
        return if (saved == InactivityAction.AUTO_LOGOUT.value) {
            InactivityAction.AUTO_LOGOUT
        } else {
            InactivityAction.REAUTH_PROMPT
        }
    }

    fun setInactivityAction(action: InactivityAction) {
        prefs.setInactivityAction(action.value)
    }

    fun resetSessionExpiry() {
        _isSessionExpired.value = false
        _isReauthPromptRequired.value = false
        lastUserActivityTime = System.currentTimeMillis()
    }

    /**
     * Active background coroutine loop verifying elapsed inactivity time.
     */
    private fun startInactivityMonitor() {
        monitorJob?.cancel()
        monitorJob = scope.launch {
            while (isActive) {
                delay(1000L)

                val userId = _currentUserId.value ?: continue

                if (_isReauthPromptRequired.value) {
                    // Safety check: if re-auth prompt is left open with no action for > 5 minutes, auto-logout
                    val promptIdleDuration = System.currentTimeMillis() - lastUserActivityTime
                    val maxAllowedIdle = getTimeoutMillis() + (5 * 60 * 1000L)
                    if (promptIdleDuration >= maxAllowedIdle) {
                        Log.w(tag, "Re-auth prompt unattended for over 5 minutes. Logging user out.")
                        logoutDueToInactivity("Session timed out due to prolonged inactivity.")
                        break
                    }
                    continue
                }

                val idleDuration = System.currentTimeMillis() - lastUserActivityTime
                val timeoutMs = getTimeoutMillis()
                val remaining = max(0L, timeoutMs - idleDuration)
                _remainingTimeMs.value = remaining

                // Near-timeout warning (last 60 seconds)
                if (remaining in 1..60000L) {
                    _sessionState.value = SessionState.InactivityWarning(userId, remaining / 1000L)
                }

                if (idleDuration >= timeoutMs) {
                    handleInactivityTimeout()
                }
            }
        }
    }

    private fun handleInactivityTimeout() {
        val userId = _currentUserId.value ?: return
        val action = getInactivityAction()
        val idleMinutes = (System.currentTimeMillis() - lastUserActivityTime) / 60000L

        if (action == InactivityAction.AUTO_LOGOUT) {
            Log.w(tag, "Inactivity timeout of ${getInactivityTimeoutMinutes()}m exceeded. Auto-logging out.")
            logoutDueToInactivity("Logged out automatically after ${getInactivityTimeoutMinutes()} minutes of inactivity.")
        } else {
            Log.w(tag, "Inactivity timeout of ${getInactivityTimeoutMinutes()}m exceeded. Triggering re-authentication prompt.")
            _isSessionExpired.value = true
            _isReauthPromptRequired.value = true
            _sessionState.value = SessionState.ReauthRequired(userId, max(1L, idleMinutes))
            _sessionEvent.tryEmit(SessionEvent.ReauthPromptTriggered(userId, idleMinutes))
        }
    }

    fun logoutDueToInactivity(reason: String) {
        monitorJob?.cancel()
        monitorJob = null
        _currentUserId.value = null
        _isSessionExpired.value = true
        _isReauthPromptRequired.value = false
        _sessionState.value = SessionState.ExpiredAndLoggedOut(reason)
        _sessionEvent.tryEmit(SessionEvent.LoggedOutDueToInactivity(reason))
        Log.w(tag, "Session terminated: $reason")
    }

    private fun getTimeoutMillis(): Long {
        val minutes = prefs.getInactivityTimeoutMinutes().coerceAtLeast(1)
        return minutes * 60 * 1000L
    }
}

