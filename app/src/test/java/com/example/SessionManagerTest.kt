package com.example

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.example.secureauth2fa.data.local.EncryptedPrefsManager
import com.example.secureauth2fa.data.local.InactivityAction
import com.example.secureauth2fa.data.local.SessionManager
import com.example.secureauth2fa.data.local.SessionState
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class SessionManagerTest {

    private lateinit var context: Context
    private lateinit var prefsManager: EncryptedPrefsManager

    @Before
    fun setup() {
        context = ApplicationProvider.getApplicationContext()
        prefsManager = EncryptedPrefsManager(context)
    }

    private fun createSessionManager(testScope: TestScope): SessionManager {
        return SessionManager(
            prefs = prefsManager,
            scope = testScope
        )
    }

    @Test
    fun defaultInactivityTimeout_is15Minutes() = runTest {
        val sessionManager = createSessionManager(this)
        assertEquals(15, sessionManager.getInactivityTimeoutMinutes())
    }

    @Test
    fun startSession_transitionsToActive() = runTest {
        val sessionManager = createSessionManager(this)
        sessionManager.startSession("test-user-123")

        assertEquals("test-user-123", sessionManager.currentUserId.value)
        assertTrue(sessionManager.sessionState.value is SessionState.Active)
        assertFalse(sessionManager.isSessionExpired.value)
        assertFalse(sessionManager.isReauthPromptRequired.value)

        sessionManager.endSession()
    }

    @Test
    fun inactivityAction_saveAndRetrieve() = runTest {
        val sessionManager = createSessionManager(this)
        sessionManager.setInactivityAction(InactivityAction.AUTO_LOGOUT)
        assertEquals(InactivityAction.AUTO_LOGOUT, sessionManager.getInactivityAction())

        sessionManager.setInactivityAction(InactivityAction.REAUTH_PROMPT)
        assertEquals(InactivityAction.REAUTH_PROMPT, sessionManager.getInactivityAction())
    }

    @Test
    fun triggerReauthPrompt_transitionsToReauthRequired() = runTest {
        val sessionManager = createSessionManager(this)
        sessionManager.startSession("test-user-123")

        sessionManager.triggerReauthPrompt()

        assertTrue(sessionManager.isReauthPromptRequired.value)
        assertTrue(sessionManager.isSessionExpired.value)
        assertTrue(sessionManager.sessionState.value is SessionState.ReauthRequired)

        // When re-authenticated
        sessionManager.onReauthenticated()
        assertFalse(sessionManager.isReauthPromptRequired.value)
        assertFalse(sessionManager.isSessionExpired.value)
        assertTrue(sessionManager.sessionState.value is SessionState.Active)

        sessionManager.endSession()
    }

    @Test
    fun userInteraction_keepsSessionActive() = runTest {
        val sessionManager = createSessionManager(this)
        sessionManager.startSession("test-user-123")

        sessionManager.updateActivity()

        assertFalse(sessionManager.isSessionExpired.value)
        assertTrue(sessionManager.sessionState.value is SessionState.Active)

        sessionManager.endSession()
    }

    @Test
    fun endSession_transitionsToIdle() = runTest {
        val sessionManager = createSessionManager(this)
        sessionManager.startSession("test-user-123")
        sessionManager.endSession()

        assertNull(sessionManager.currentUserId.value)
        assertEquals(SessionState.Idle, sessionManager.sessionState.value)
    }
}
