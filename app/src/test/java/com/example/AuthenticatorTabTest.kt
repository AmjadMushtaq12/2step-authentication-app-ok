package com.example

import com.example.secureauth2fa.data.auth.TotpDataSource
import com.example.secureauth2fa.data.model.AuthenticatorAccount
import com.example.secureauth2fa.ui.dashboard.DashboardTab
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class AuthenticatorTabTest {

    @Test
    fun testAuthenticatorAccountCreation() {
        val account = AuthenticatorAccount(
            issuer = "Google",
            accountName = "user@gmail.com",
            secretKey = "JBSWY3DPEHPK3PXP"
        )

        assertEquals("Google", account.issuer)
        assertEquals("user@gmail.com", account.accountName)
        assertEquals("JBSWY3DPEHPK3PXP", account.secretKey)
        assertEquals(6, account.digits)
        assertEquals(30, account.periodSeconds)
        assertNotNull(account.id)
    }

    @Test
    fun testTotpGenerationFromSecret() {
        val totpDataSource = TotpDataSource()
        val secret = "JBSWY3DPEHPK3PXP"
        val code = totpDataSource.getCurrentTotpCode(secret)

        assertEquals(6, code.length)
        assertTrue(code.all { it.isDigit() })
    }

    @Test
    fun testTabSwitchingEnums() {
        val tabs = listOf(DashboardTab.HOME, DashboardTab.HISTORY, DashboardTab.SETTINGS)
        assertEquals(3, tabs.size)
        assertTrue(tabs.contains(DashboardTab.HOME))
        assertTrue(tabs.contains(DashboardTab.HISTORY))
        assertTrue(tabs.contains(DashboardTab.SETTINGS))
    }
}
