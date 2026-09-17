package com.example.secureauth2fa.utils

object Constants {
    const val APP_NAME = "SecureAuth 2FA"
    const val ISSUER = "SecureAuth 2FA"
    
    // TOTP configuration
    const val TOTP_DIGITS = 6
    const val TOTP_PERIOD_SECONDS = 30
    const val TOTP_TOLERANCE_WINDOWS = 1 // ±1 window tolerance (90s window total)
    const val BACKUP_CODES_COUNT = 10
    
    // Session & Device
    const val DEFAULT_INACTIVITY_TIMEOUT_MINUTES = 15
    const val REMEMBER_DEVICE_DAYS = 30
    const val REMEMBER_DEVICE_DURATION_MS = REMEMBER_DEVICE_DAYS * 24L * 60L * 60L * 1000L
    
    // Ad Interstitial cooldown: max once every 3 minutes
    const val INTERSTITIAL_COOLDOWN_MS = 3 * 60 * 1000L
    // Rewarded ad free pass duration: 7 days
    const val REWARDED_AD_FREE_DURATION_MS = 7L * 24 * 60 * 60 * 1000L

    // Firestore Collections
    const val COLLECTION_USERS = "users"
    const val COLLECTION_SESSIONS = "sessions"
    const val COLLECTION_BACKUP_CODES = "backup_codes"
}
