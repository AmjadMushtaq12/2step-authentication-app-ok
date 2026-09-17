package com.example.secureauth2fa

import android.app.Application
import com.example.secureauth2fa.ads.AdManager
import com.example.secureauth2fa.di.AppContainer

class SecureAuthApp : Application() {

    lateinit var appContainer: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        instance = this
        appContainer = AppContainer(this)

        // Initialize Ad-free state from encrypted preferences
        val adFreeUntil = appContainer.prefsManager.getAdFreeExpiry()
        AdManager.getInstance().setAdFreeUntil(adFreeUntil)

        // Initialize Google Mobile Ads SDK
        AdManager.getInstance().initialize(this)
    }

    companion object {
        lateinit var instance: SecureAuthApp
            private set
    }
}
