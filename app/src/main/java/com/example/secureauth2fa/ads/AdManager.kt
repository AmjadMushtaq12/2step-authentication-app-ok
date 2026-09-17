package com.example.secureauth2fa.ads

import android.app.Activity
import android.content.Context
import android.util.Log
import com.example.secureauth2fa.utils.Constants
import com.google.android.gms.ads.AdError
import com.google.android.gms.ads.AdRequest
import com.google.android.gms.ads.FullScreenContentCallback
import com.google.android.gms.ads.LoadAdError
import com.google.android.gms.ads.MobileAds
import com.google.android.gms.ads.appopen.AppOpenAd
import com.google.android.gms.ads.interstitial.InterstitialAd
import com.google.android.gms.ads.interstitial.InterstitialAdLoadCallback
import com.google.android.gms.ads.rewarded.RewardedAd
import com.google.android.gms.ads.rewarded.RewardedAdLoadCallback
import com.google.android.ump.ConsentInformation
import com.google.android.ump.ConsentRequestParameters
import com.google.android.ump.UserMessagingPlatform
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class AdManager private constructor() {

    private var interstitialAd: InterstitialAd? = null
    private var isInterstitialLoading = false
    private var lastInterstitialShownTime: Long = 0

    private var rewardedAd: RewardedAd? = null
    private var isRewardedLoading = false

    private var appOpenAd: AppOpenAd? = null
    private var isAppOpenLoading = false
    private var appOpenLoadTime: Long = 0

    private val _isAdFreeActive = MutableStateFlow(false)
    val isAdFreeActive: StateFlow<Boolean> = _isAdFreeActive.asStateFlow()

    private val _adFreeUntilTimestamp = MutableStateFlow(0L)
    val adFreeUntilTimestamp: StateFlow<Long> = _adFreeUntilTimestamp.asStateFlow()

    companion object {
        private const val TAG = "AdManager"
        @Volatile
        private var instance: AdManager? = null

        fun getInstance(): AdManager {
            return instance ?: synchronized(this) {
                instance ?: AdManager().also { instance = it }
            }
        }

        fun isEmulator(): Boolean {
            val fingerprint = android.os.Build.FINGERPRINT ?: ""
            val model = android.os.Build.MODEL ?: ""
            val manufacturer = android.os.Build.MANUFACTURER ?: ""
            val brand = android.os.Build.BRAND ?: ""
            val device = android.os.Build.DEVICE ?: ""
            val product = android.os.Build.PRODUCT ?: ""
            val hardware = android.os.Build.HARDWARE ?: ""

            return fingerprint.startsWith("generic")
                    || fingerprint.startsWith("unknown")
                    || model.contains("google_sdk", ignoreCase = true)
                    || model.contains("Emulator", ignoreCase = true)
                    || model.contains("Android SDK built for x86", ignoreCase = true)
                    || manufacturer.contains("Genymotion", ignoreCase = true)
                    || (brand.startsWith("generic") && device.startsWith("generic"))
                    || "google_sdk".equals(product, ignoreCase = true)
                    || hardware.contains("goldfish", ignoreCase = true)
                    || hardware.contains("ranchu", ignoreCase = true)
                    || hardware.contains("cutf", ignoreCase = true)
                    || product.contains("sdk", ignoreCase = true)
                    || product.contains("emulator", ignoreCase = true)
                    || product.contains("simulator", ignoreCase = true)
        }
    }

    /**
     * Initializes MobileAds SDK and requests UMP consent if applicable.
     */
    fun initialize(context: Context, onInitialized: () -> Unit = {}) {
        if (isEmulator()) {
            Log.d(TAG, "Running in emulator/preview environment. Disabling AdMob SDK and measurement service.")
            _isAdFreeActive.value = true
            onInitialized()
            return
        }

        try {
            if (context is Activity) {
                // Setup User Messaging Platform (UMP) Consent if called with Activity
                val params = ConsentRequestParameters.Builder()
                    .setTagForUnderAgeOfConsent(false)
                    .build()

                val consentInformation: ConsentInformation = UserMessagingPlatform.getConsentInformation(context)
                consentInformation.requestConsentInfoUpdate(
                    context,
                    params,
                    {
                        UserMessagingPlatform.loadAndShowConsentFormIfRequired(context) { formError ->
                            if (formError != null) {
                                Log.w(TAG, "UMP consent form error: ${formError.message}")
                            }
                            initializeMobileAds(context, onInitialized)
                        }
                    },
                    { requestConsentError ->
                        Log.w(TAG, "UMP consent error: ${requestConsentError.message}")
                        initializeMobileAds(context, onInitialized)
                    }
                )
            } else {
                // Application-level initialization
                initializeMobileAds(context, onInitialized)
            }
        } catch (e: Exception) {
            Log.e(TAG, "AdManager initialization error: ${e.message}", e)
            initializeMobileAds(context, onInitialized)
        }
    }

    private fun initializeMobileAds(context: Context, onInitialized: () -> Unit) {
        try {
            val configuration = MobileAds.getRequestConfiguration().toBuilder()
                .setTestDeviceIds(listOf(AdRequest.DEVICE_ID_EMULATOR))
                .build()
            MobileAds.setRequestConfiguration(configuration)

            MobileAds.initialize(context) {
                Log.d(TAG, "MobileAds SDK initialized successfully.")
                preloadAppOpen(context)
                preloadInterstitial(context)
                onInitialized()
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed initializing MobileAds: ${e.message}", e)
            onInitialized()
        }
    }

    /**
     * Preloads Interstitial, Rewarded, and App Open ads.
     */
    fun preloadAllAds(context: Context) {
        if (isEmulator() || isAdFree()) return
        preloadInterstitial(context)
        preloadRewarded(context)
        preloadAppOpen(context)
    }

    fun setAdFreeUntil(timestamp: Long) {
        _adFreeUntilTimestamp.value = timestamp
        val isNowAdFree = timestamp > System.currentTimeMillis()
        _isAdFreeActive.value = isNowAdFree
    }

    fun isAdFree(): Boolean {
        if (isEmulator()) {
            return true
        }
        val until = _adFreeUntilTimestamp.value
        val active = until > System.currentTimeMillis()
        if (_isAdFreeActive.value != active) {
            _isAdFreeActive.value = active
        }
        return active
    }

    // ========================================================================
    // INTERSTITIAL AD
    // ========================================================================
    fun preloadInterstitial(context: Context) {
        if (isAdFree() || interstitialAd != null || isInterstitialLoading) return

        isInterstitialLoading = true
        val adRequest = AdRequest.Builder().build()
        InterstitialAd.load(
            context,
            AdIds.INTERSTITIAL,
            adRequest,
            object : InterstitialAdLoadCallback() {
                override fun onAdLoaded(ad: InterstitialAd) {
                    interstitialAd = ad
                    isInterstitialLoading = false
                    Log.d(TAG, "Interstitial ad loaded successfully.")
                }

                override fun onAdFailedToLoad(error: LoadAdError) {
                    interstitialAd = null
                    isInterstitialLoading = false
                    Log.w(TAG, "Interstitial failed to load: ${error.message}")
                }
            }
        )
    }

    /**
     * Shows Interstitial ad after successful 2FA verification.
     * Respects rate limit: maximum once every 3 minutes.
     */
    fun showInterstitialAfter2FA(activity: Activity, onAdDismissed: () -> Unit = {}) {
        if (isAdFree()) {
            onAdDismissed()
            return
        }

        val currentTime = System.currentTimeMillis()
        if (currentTime - lastInterstitialShownTime < Constants.INTERSTITIAL_COOLDOWN_MS) {
            Log.d(TAG, "Interstitial skipped due to cooldown policy (max once per 3 minutes).")
            onAdDismissed()
            return
        }

        val ad = interstitialAd
        if (ad != null) {
            ad.fullScreenContentCallback = object : FullScreenContentCallback() {
                override fun onAdDismissedFullScreenContent() {
                    interstitialAd = null
                    lastInterstitialShownTime = System.currentTimeMillis()
                    preloadInterstitial(activity)
                    onAdDismissed()
                }

                override fun onAdFailedToShowFullScreenContent(error: AdError) {
                    interstitialAd = null
                    Log.w(TAG, "Interstitial failed to show: ${error.message}")
                    preloadInterstitial(activity)
                    onAdDismissed()
                }
            }
            ad.show(activity)
        } else {
            Log.d(TAG, "No Interstitial ad ready to show.")
            preloadInterstitial(activity)
            onAdDismissed()
        }
    }

    // ========================================================================
    // REWARDED AD
    // ========================================================================
    fun preloadRewarded(context: Context) {
        if (rewardedAd != null || isRewardedLoading) return

        isRewardedLoading = true
        val adRequest = AdRequest.Builder().build()
        RewardedAd.load(
            context,
            AdIds.REWARDED,
            adRequest,
            object : RewardedAdLoadCallback() {
                override fun onAdLoaded(ad: RewardedAd) {
                    rewardedAd = ad
                    isRewardedLoading = false
                    Log.d(TAG, "Rewarded ad loaded successfully.")
                }

                override fun onAdFailedToLoad(error: LoadAdError) {
                    rewardedAd = null
                    isRewardedLoading = false
                    Log.w(TAG, "Rewarded ad failed to load: ${error.message}")
                }
            }
        )
    }

    /**
     * Shows Rewarded ad to grant "7 days of ad-free experience".
     */
    fun showRewardedAd(
        activity: Activity,
        onUserRewarded: (Long) -> Unit,
        onAdClosed: () -> Unit,
        onError: (String) -> Unit
    ) {
        if (isEmulator()) {
            val newExpiry = System.currentTimeMillis() + Constants.REWARDED_AD_FREE_DURATION_MS
            setAdFreeUntil(newExpiry)
            onUserRewarded(newExpiry)
            onAdClosed()
            return
        }

        val ad = rewardedAd
        if (ad != null) {
            ad.fullScreenContentCallback = object : FullScreenContentCallback() {
                override fun onAdDismissedFullScreenContent() {
                    rewardedAd = null
                    preloadRewarded(activity)
                    onAdClosed()
                }

                override fun onAdFailedToShowFullScreenContent(error: AdError) {
                    rewardedAd = null
                    Log.w(TAG, "Rewarded ad failed to show: ${error.message}")
                    preloadRewarded(activity)
                    onError(error.message)
                }
            }

            ad.show(activity) { _ ->
                val newExpiry = System.currentTimeMillis() + Constants.REWARDED_AD_FREE_DURATION_MS
                setAdFreeUntil(newExpiry)
                onUserRewarded(newExpiry)
            }
        } else {
            preloadRewarded(activity)
            onError("Ad is loading. Please try again in a moment.")
        }
    }

    // ========================================================================
    // APP OPEN AD
    // ========================================================================
    fun preloadAppOpen(context: Context) {
        if (isAdFree() || isAppOpenLoading || isAppOpenAdAvailable()) return

        isAppOpenLoading = true
        val adRequest = AdRequest.Builder().build()
        AppOpenAd.load(
            context,
            AdIds.APP_OPEN,
            adRequest,
            object : AppOpenAd.AppOpenAdLoadCallback() {
                override fun onAdLoaded(ad: AppOpenAd) {
                    appOpenAd = ad
                    appOpenLoadTime = System.currentTimeMillis()
                    isAppOpenLoading = false
                    Log.d(TAG, "App Open ad loaded.")
                }

                override fun onAdFailedToLoad(error: LoadAdError) {
                    appOpenAd = null
                    isAppOpenLoading = false
                    Log.w(TAG, "App Open ad failed to load: ${error.message}")
                }
            }
        )
    }

    private fun isAppOpenAdAvailable(): Boolean {
        return appOpenAd != null && (System.currentTimeMillis() - appOpenLoadTime < 4 * 3600 * 1000L)
    }

    fun showAppOpenAdIfAvailable(activity: Activity, onAdFinished: () -> Unit = {}) {
        if (isAdFree()) {
            onAdFinished()
            return
        }

        if (isAppOpenAdAvailable()) {
            val ad = appOpenAd
            ad?.fullScreenContentCallback = object : FullScreenContentCallback() {
                override fun onAdDismissedFullScreenContent() {
                    appOpenAd = null
                    preloadAppOpen(activity)
                    onAdFinished()
                }

                override fun onAdFailedToShowFullScreenContent(error: AdError) {
                    appOpenAd = null
                    preloadAppOpen(activity)
                    onAdFinished()
                }
            }
            ad?.show(activity)
        } else {
            preloadAppOpen(activity)
            onAdFinished()
        }
    }
}
