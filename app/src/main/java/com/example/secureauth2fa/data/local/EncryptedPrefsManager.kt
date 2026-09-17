package com.example.secureauth2fa.data.local

import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import com.example.secureauth2fa.data.model.AuthenticatorAccount
import com.example.secureauth2fa.data.model.SecurityHistoryItem
import com.example.secureauth2fa.utils.Constants
import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID

class EncryptedPrefsManager(context: Context) {

    private val prefs: SharedPreferences = try {
        val masterKey = MasterKey.Builder(context)
            .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
            .build()

        EncryptedSharedPreferences.create(
            context,
            "secure_auth_encrypted_prefs",
            masterKey,
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
        )
    } catch (e: Exception) {
        Log.e("EncryptedPrefsManager", "Fallback to standard preferences due to encryption init issue: ${e.message}")
        context.getSharedPreferences("secure_auth_fallback_prefs", Context.MODE_PRIVATE)
    }

    companion object {
        private const val KEY_TOTP_SECRET_PREFIX = "totp_secret_"
        private const val KEY_BIOMETRIC_ENABLED = "biometric_enabled"
        private const val KEY_SESSION_TIMEOUT_MINUTES = "session_timeout_minutes"
        private const val KEY_INACTIVITY_ACTION = "inactivity_action"
        private const val KEY_DEVICE_ID = "device_id"
        private const val KEY_REMEMBERED_TOKEN_PREFIX = "remember_token_"
        private const val KEY_REMEMBERED_EXPIRY_PREFIX = "remember_expiry_"
        private const val KEY_AD_FREE_EXPIRY = "ad_free_until"
        private const val KEY_ONBOARDING_COMPLETED = "onboarding_completed"
        private const val KEY_SAVED_EMAIL = "saved_email"
    }

    // --- TOTP Secret Storage (Encrypted) ---
    fun saveTotpSecret(userId: String, secret: String) {
        prefs.edit().putString(KEY_TOTP_SECRET_PREFIX + userId, secret).apply()
    }

    fun getTotpSecret(userId: String): String? {
        return prefs.getString(KEY_TOTP_SECRET_PREFIX + userId, null)
    }

    fun clearTotpSecret(userId: String) {
        prefs.edit().remove(KEY_TOTP_SECRET_PREFIX + userId).apply()
    }

    // --- Biometric Setting ---
    fun setBiometricEnabled(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_BIOMETRIC_ENABLED, enabled).apply()
    }

    fun isBiometricEnabled(): Boolean {
        return prefs.getBoolean(KEY_BIOMETRIC_ENABLED, false)
    }

    // --- Inactivity Timeout ---
    fun setInactivityTimeoutMinutes(minutes: Int) {
        prefs.edit().putInt(KEY_SESSION_TIMEOUT_MINUTES, minutes).apply()
    }

    fun getInactivityTimeoutMinutes(): Int {
        return prefs.getInt(KEY_SESSION_TIMEOUT_MINUTES, Constants.DEFAULT_INACTIVITY_TIMEOUT_MINUTES)
    }

    fun setInactivityAction(action: String) {
        prefs.edit().putString(KEY_INACTIVITY_ACTION, action).apply()
    }

    fun getInactivityAction(): String {
        return prefs.getString(KEY_INACTIVITY_ACTION, "reauth_prompt") ?: "reauth_prompt"
    }

    // --- Device ID ---
    fun getOrCreateDeviceId(): String {
        var deviceId = prefs.getString(KEY_DEVICE_ID, null)
        if (deviceId == null) {
            deviceId = UUID.randomUUID().toString()
            prefs.edit().putString(KEY_DEVICE_ID, deviceId).apply()
        }
        return deviceId
    }

    // --- Remember This Device ---
    fun saveRememberedDeviceToken(userId: String, token: String, expiryMs: Long) {
        prefs.edit()
            .putString(KEY_REMEMBERED_TOKEN_PREFIX + userId, token)
            .putLong(KEY_REMEMBERED_EXPIRY_PREFIX + userId, expiryMs)
            .apply()
    }

    fun isDeviceRemembered(userId: String): Boolean {
        val expiry = prefs.getLong(KEY_REMEMBERED_EXPIRY_PREFIX + userId, 0L)
        val token = prefs.getString(KEY_REMEMBERED_TOKEN_PREFIX + userId, null)
        return !token.isNullOrBlank() && expiry > System.currentTimeMillis()
    }

    fun clearRememberedDevice(userId: String) {
        prefs.edit()
            .remove(KEY_REMEMBERED_TOKEN_PREFIX + userId)
            .remove(KEY_REMEMBERED_EXPIRY_PREFIX + userId)
            .apply()
    }

    // --- Ad Free Expiry ---
    fun saveAdFreeExpiry(expiryMs: Long) {
        prefs.edit().putLong(KEY_AD_FREE_EXPIRY, expiryMs).apply()
    }

    fun getAdFreeExpiry(): Long {
        return prefs.getLong(KEY_AD_FREE_EXPIRY, 0L)
    }

    // --- Onboarding ---
    fun setOnboardingCompleted(completed: Boolean) {
        prefs.edit().putBoolean(KEY_ONBOARDING_COMPLETED, completed).apply()
    }

    fun isOnboardingCompleted(): Boolean {
        return prefs.getBoolean(KEY_ONBOARDING_COMPLETED, false)
    }

    // --- Last Logged-in Email ---
    fun saveLastEmail(email: String) {
        prefs.edit().putString(KEY_SAVED_EMAIL, email).apply()
    }

    fun getLastEmail(): String? {
        return prefs.getString(KEY_SAVED_EMAIL, null)
    }

    // --- Authenticator Accounts (2FA) ---
    fun getAccounts(userId: String): List<AuthenticatorAccount> {
        val rawJson = prefs.getString("authenticator_accounts_$userId", null) ?: return emptyList()
        return try {
            val jsonArray = JSONArray(rawJson)
            val list = mutableListOf<AuthenticatorAccount>()
            for (i in 0 until jsonArray.length()) {
                val obj = jsonArray.getJSONObject(i)
                list.add(
                    AuthenticatorAccount(
                        id = obj.optString("id", UUID.randomUUID().toString()),
                        issuer = obj.optString("issuer", "Custom"),
                        accountName = obj.optString("accountName", ""),
                        secretKey = obj.optString("secretKey", ""),
                        algorithm = obj.optString("algorithm", "SHA1"),
                        digits = obj.optInt("digits", 6),
                        periodSeconds = obj.optInt("periodSeconds", 30),
                        createdAt = obj.optLong("createdAt", System.currentTimeMillis())
                    )
                )
            }
            list
        } catch (e: Exception) {
            emptyList()
        }
    }

    fun saveAccount(userId: String, account: AuthenticatorAccount) {
        val currentAccounts = getAccounts(userId).toMutableList()
        val index = currentAccounts.indexOfFirst { it.id == account.id }
        if (index >= 0) {
            currentAccounts[index] = account
        } else {
            currentAccounts.add(0, account)
        }
        val jsonArray = JSONArray()
        for (item in currentAccounts) {
            val obj = JSONObject()
            obj.put("id", item.id)
            obj.put("issuer", item.issuer)
            obj.put("accountName", item.accountName)
            obj.put("secretKey", item.secretKey)
            obj.put("algorithm", item.algorithm)
            obj.put("digits", item.digits)
            obj.put("periodSeconds", item.periodSeconds)
            obj.put("createdAt", item.createdAt)
            jsonArray.put(obj)
        }
        prefs.edit().putString("authenticator_accounts_$userId", jsonArray.toString()).apply()
    }

    fun deleteAccount(userId: String, accountId: String) {
        val currentAccounts = getAccounts(userId).filter { it.id != accountId }
        val jsonArray = JSONArray()
        for (item in currentAccounts) {
            val obj = JSONObject()
            obj.put("id", item.id)
            obj.put("issuer", item.issuer)
            obj.put("accountName", item.accountName)
            obj.put("secretKey", item.secretKey)
            obj.put("algorithm", item.algorithm)
            obj.put("digits", item.digits)
            obj.put("periodSeconds", item.periodSeconds)
            obj.put("createdAt", item.createdAt)
            jsonArray.put(obj)
        }
        prefs.edit().putString("authenticator_accounts_$userId", jsonArray.toString()).apply()
    }

    // --- Security History Logs ---
    fun getHistory(userId: String): List<SecurityHistoryItem> {
        val rawJson = prefs.getString("security_history_$userId", null) ?: return emptyList()
        return try {
            val jsonArray = JSONArray(rawJson)
            val list = mutableListOf<SecurityHistoryItem>()
            for (i in 0 until jsonArray.length()) {
                val obj = jsonArray.getJSONObject(i)
                list.add(
                    SecurityHistoryItem(
                        id = obj.optString("id", UUID.randomUUID().toString()),
                        timestamp = obj.optLong("timestamp", System.currentTimeMillis()),
                        actionType = obj.optString("actionType", "SECURITY_CHANGE"),
                        title = obj.optString("title", "Event"),
                        description = obj.optString("description", "")
                    )
                )
            }
            list
        } catch (e: Exception) {
            emptyList()
        }
    }

    fun addHistoryItem(userId: String, item: SecurityHistoryItem) {
        val currentHistory = getHistory(userId).toMutableList()
        currentHistory.add(0, item)
        // Keep latest 100 entries
        val trimmed = if (currentHistory.size > 100) currentHistory.take(100) else currentHistory
        val jsonArray = JSONArray()
        for (h in trimmed) {
            val obj = JSONObject()
            obj.put("id", h.id)
            obj.put("timestamp", h.timestamp)
            obj.put("actionType", h.actionType)
            obj.put("title", h.title)
            obj.put("description", h.description)
            jsonArray.put(obj)
        }
        prefs.edit().putString("security_history_$userId", jsonArray.toString()).apply()
    }

    fun clearHistory(userId: String) {
        prefs.edit().remove("security_history_$userId").apply()
    }

    fun clearAll(userId: String? = null) {
        if (userId != null) {
            clearTotpSecret(userId)
            clearRememberedDevice(userId)
        }
    }
}
