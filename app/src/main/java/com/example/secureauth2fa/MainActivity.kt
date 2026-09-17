package com.example.secureauth2fa

import android.os.Bundle
import android.widget.Toast
import androidx.activity.enableEdgeToEdge
import androidx.activity.compose.setContent
import androidx.biometric.BiometricPrompt
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity
import com.example.secureauth2fa.ui.navigation.AppNavGraph
import com.example.secureauth2fa.ui.theme.SecureAuthTheme

class MainActivity : FragmentActivity() {

    private val appContainer by lazy {
        (application as SecureAuthApp).appContainer
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        setContent {
            SecureAuthTheme {
                AppNavGraph(
                    appContainer = appContainer,
                    onBiometricUnlock = { onSuccess ->
                        showBiometricPrompt(onSuccess = onSuccess)
                    }
                )
            }
        }
    }

    override fun onUserInteraction() {
        super.onUserInteraction()
        appContainer.sessionUseCase.onUserInteracted()
    }

    private fun showBiometricPrompt(
        title: String = "Biometric Unlock",
        subtitle: String = "Authenticate using your biometric credentials",
        onSuccess: (() -> Unit)? = null
    ) {
        val executor = ContextCompat.getMainExecutor(this)
        val prompt = BiometricPrompt(
            this,
            executor,
            object : BiometricPrompt.AuthenticationCallback() {
                override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                    super.onAuthenticationSucceeded(result)
                    Toast.makeText(this@MainActivity, "Biometric authentication successful", Toast.LENGTH_SHORT).show()
                    onSuccess?.invoke()
                }

                override fun onAuthenticationError(errorCode: Int, errString: CharSequence) {
                    super.onAuthenticationError(errorCode, errString)
                    if (errorCode != BiometricPrompt.ERROR_USER_CANCELED && errorCode != BiometricPrompt.ERROR_NEGATIVE_BUTTON) {
                        Toast.makeText(this@MainActivity, "Authentication error: $errString", Toast.LENGTH_SHORT).show()
                    }
                }
            }
        )

        val promptInfo = BiometricPrompt.PromptInfo.Builder()
            .setTitle(title)
            .setSubtitle(subtitle)
            .setNegativeButtonText("Cancel")
            .build()

        prompt.authenticate(promptInfo)
    }
}
