package com.example.secureauth2fa.ads

/**
 * ============================================================================
 * GOOGLE ADMOB AD UNIT CONFIGURATION
 * ============================================================================
 *
 * HOW TO SWAP TEST AD IDS FOR REAL PRODUCTION ADMOB IDS:
 * 1. Log in to your Google AdMob console (https://admob.google.com).
 * 2. Create your Android App entry and obtain your official AdMob App ID.
 * 3. Update the `APPLICATION_ID` meta-data tag in `AndroidManifest.xml`.
 * 4. Create ad units for Banner, Interstitial, Rewarded, Native, and App Open.
 * 5. Replace each constant value below with your actual production Ad Unit ID.
 *
 * NOTE: The IDs below are Google's official, risk-free test ad unit IDs.
 * They are safe for testing and development without triggering account suspension.
 * ============================================================================
 */
object AdIds {
    // 🔁 REPLACE ME: Replace with your real AdMob App ID in AndroidManifest.xml and here
    const val APP_ID = "ca-app-pub-3940256099942544~3347511713"

    // 🔁 REPLACE ME: Replace with your production Banner Ad Unit ID
    const val BANNER = "ca-app-pub-3940256099942544/6300978111"

    // 🔁 REPLACE ME: Replace with your production Interstitial Ad Unit ID
    const val INTERSTITIAL = "ca-app-pub-3940256099942544/1033173712"

    // 🔁 REPLACE ME: Replace with your production Rewarded Ad Unit ID
    const val REWARDED = "ca-app-pub-3940256099942544/5224354917"

    // 🔁 REPLACE ME: Replace with your production Native Advanced Ad Unit ID
    const val NATIVE = "ca-app-pub-3940256099942544/2247696110"

    // 🔁 REPLACE ME: Replace with your production App Open Ad Unit ID
    const val APP_OPEN = "ca-app-pub-3940256099942544/9257395921"
}
