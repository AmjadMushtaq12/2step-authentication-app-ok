# SecureAuth 2FA — Android Application

A production-ready Android application built with **Kotlin** and **Jetpack Compose (Material 3)** demonstrating two-factor authentication (TOTP-based, compatible with Google Authenticator, Authy, and Microsoft Authenticator) alongside Google AdMob monetization.

---

## 🎯 Features

- **Email & Password Authentication**: Firebase Auth registration with real-time password strength validation (min 8 chars, uppercase, digit, special character).
- **Two-Factor Authentication (TOTP - RFC 6238)**:
  - Base32 secret key generation (20 bytes cryptographically secure).
  - QR Code generation using ZXing for instant authenticator app pairing.
  - Standard 6-digit rolling code verification (30-second interval with ±1 window drift tolerance).
  - Live 30-second countdown indicator in the Compose UI.
- **Emergency Recovery Backup Codes**:
  - Generates 10 one-time recovery codes (`XXXX-XXXX`).
  - Hashed using **SHA-256** before saving to Firestore.
- **Device & Session Management**:
  - "Remember this device for 30 days" option.
  - Configurable auto-logout inactivity timeout (5, 15, 30, 60 minutes).
  - Biometric quick unlock option via Android `BiometricPrompt`.
  - Active device sessions tracked in Firestore.
- **Security Protections**:
  - `FLAG_SECURE` window policy on sensitive 2FA & backup code screens to prevent screenshots and screen recording.
  - Secrets stored using Android Jetpack Security (`EncryptedSharedPreferences` backed by Android Keystore AES-256 GCM).
  - Never logs secret keys or raw tokens.
- **Google AdMob Integration**:
  - **Banner Ad**: Embedded at the bottom of Dashboard and Settings screens.
  - **Interstitial Ad**: Triggered after successful 2FA verification (with rate-limiting cooldown).
  - **App Open Ad**: Preloaded and shown on cold start.
  - **Rewarded Ad**: "Unlock 7 days of ad-free experience" feature in Settings.
  - Configured with official Google test ad unit IDs.

---

## 🚀 Running the App

1. Clone or download the repository.
2. Open the project in **Android Studio Hedgehog** (or newer).
3. Ensure JDK 17+ and Android SDK 34 are configured.
4. Run `./gradlew assembleDebug` or click **Run 'app'** in Android Studio.

---

## 🔥 Firebase Setup Instructions

The application contains an automatic offline/sandbox fallback so all features, QR code generation, TOTP verification, and ads run immediately out of the box. To connect your live Firebase project:

1. Go to the [Firebase Console](https://console.firebase.google.com/).
2. Create a new Firebase project (or use an existing one).
3. Add an Android app with package name:
   ```
   com.example.secureauth2fa
   ```
4. Download the generated `google-services.json` file and place it in the `app/` root directory:
   ```
   app/google-services.json
   ```
5. In the Firebase Console:
   - Navigate to **Authentication** > **Sign-in method** and enable **Email/Password**.
   - Navigate to **Firestore Database** > **Create database** (start in production mode).
   - Navigate to the **Rules** tab in Firestore and deploy the contents of `firestore.rules`:
     ```
     rules_version = '2';
     service cloud.firestore {
       match /databases/{database}/documents {
         match /users/{userId} {
           allow read, write: if request.auth != null && request.auth.uid == userId;
           match /backup_codes/{codeHash} {
             allow read, write: if request.auth != null && request.auth.uid == userId;
           }
           match /sessions/{sessionId} {
             allow read, write: if request.auth != null && request.auth.uid == userId;
           }
         }
       }
     }
     ```

---

## 📢 Replacing AdMob Test IDs with Production IDs

All AdMob Ad Unit IDs and the AdMob Application ID are centralized for quick replacement:

1. **AdMob Application ID**:
   - Located in `app/src/main/res/values/strings.xml`:
     ```xml
     <string name="admob_app_id">ca-app-pub-3940256099942544~3347511713</string>
     ```
   - Replace with your production AdMob Application ID before release.

2. **Ad Unit IDs (Banner, Interstitial, Rewarded, App Open)**:
   - Located in `app/src/main/java/com/example/secureauth2fa/ads/AdIds.kt`:
     ```kotlin
     object AdIds {
         // 🔁 REPLACE ME: Replace with production banner ad unit ID
         const val BANNER = "ca-app-pub-3940256099942544/6300978111"

         // 🔁 REPLACE ME: Replace with production interstitial ad unit ID
         const val INTERSTITIAL = "ca-app-pub-3940256099942544/1033173712"

         // 🔁 REPLACE ME: Replace with production rewarded ad unit ID
         const val REWARDED = "ca-app-pub-3940256099942544/5224354917"

         // 🔁 REPLACE ME: Replace with production app open ad unit ID
         const val APP_OPEN = "ca-app-pub-3940256099942544/9257395921"
     }
     ```

---

## 🏗️ Architecture

- **Clean Architecture & MVVM**:
  - `data/`: Local storage (`EncryptedPrefsManager`, `SessionManager`), remote sources (`FirebaseAuthDataSource`, `TotpDataSource`), repositories.
  - `domain/`: Domain models, repository contracts, isolated use cases (`LoginUseCase`, `RegisterUseCase`, `Setup2FAUseCase`, `VerifyTotpUseCase`, `SessionUseCase`).
  - `ui/`: Jetpack Compose Material 3 screens, ViewModels, navigation graph, custom components (`OtpInputField`, `PrimaryButton`, `BannerAdView`).
- **Encrypted Local Storage**: Android Jetpack Security Crypto library with AES-256 SIV for keys and AES-256 GCM for values.
