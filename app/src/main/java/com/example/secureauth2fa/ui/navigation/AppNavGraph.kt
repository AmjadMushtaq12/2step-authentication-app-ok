package com.example.secureauth2fa.ui.navigation

import android.widget.Toast
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.example.secureauth2fa.data.local.SessionState
import com.example.secureauth2fa.data.model.User
import com.example.secureauth2fa.di.AppContainer
import com.example.secureauth2fa.ui.auth.AuthViewModel
import com.example.secureauth2fa.ui.auth.EmailVerificationScreen
import com.example.secureauth2fa.ui.auth.ForgotPasswordScreen
import com.example.secureauth2fa.ui.auth.LoginScreen
import com.example.secureauth2fa.ui.auth.RegisterScreen
import com.example.secureauth2fa.ui.components.ReauthPromptDialog
import com.example.secureauth2fa.ui.dashboard.DashboardScreen
import com.example.secureauth2fa.ui.dashboard.DashboardViewModel
import com.example.secureauth2fa.ui.onboarding.OnboardingScreen
import com.example.secureauth2fa.ui.settings.SettingsScreen
import com.example.secureauth2fa.ui.settings.SettingsViewModel
import com.example.secureauth2fa.ui.splash.SplashScreen
import com.example.secureauth2fa.ui.twofa.BackupCodesScreen
import com.example.secureauth2fa.ui.twofa.TwoFactorSetupScreen
import com.example.secureauth2fa.ui.twofa.TwoFactorVerifyScreen
import com.example.secureauth2fa.ui.twofa.TwoFactorViewModel

@Composable
fun AppNavGraph(
    appContainer: AppContainer,
    navController: NavHostController = rememberNavController(),
    onBiometricUnlock: (((() -> Unit)?) -> Unit)? = null
) {
    val context = LocalContext.current

    // Current authenticated user state across the flow
    var activeUser by remember {
        mutableStateOf(appContainer.authRepository.getCurrentUser() ?: User(email = "user@example.com", uid = "default-user"))
    }
    var rememberDeviceState by remember { mutableStateOf(true) }

    val isReauthPromptRequired by appContainer.sessionUseCase.isReauthPromptRequired.collectAsState()
    val sessionState by appContainer.sessionUseCase.sessionState.collectAsState()

    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = navBackStackEntry?.destination?.route

    // Handle auto-logout due to inactivity
    LaunchedEffect(sessionState) {
        if (sessionState is SessionState.ExpiredAndLoggedOut) {
            val reason = (sessionState as SessionState.ExpiredAndLoggedOut).reason
            Toast.makeText(context, reason, Toast.LENGTH_LONG).show()
            navController.navigate(Routes.LOGIN) {
                popUpTo(0) { inclusive = true }
            }
        }
    }

    val authViewModel: AuthViewModel = viewModel {
        AuthViewModel(
            loginUseCase = appContainer.loginUseCase,
            registerUseCase = appContainer.registerUseCase,
            forgotPasswordUseCase = appContainer.forgotPasswordUseCase,
            sessionUseCase = appContainer.sessionUseCase,
            loginWithGoogleUseCase = appContainer.loginWithGoogleUseCase,
            googleAuthManager = appContainer.googleAuthManager
        )
    }

    val twoFactorViewModel: TwoFactorViewModel = viewModel {
        TwoFactorViewModel(
            setup2FAUseCase = appContainer.setup2FAUseCase,
            verifyTotpUseCase = appContainer.verifyTotpUseCase,
            sessionUseCase = appContainer.sessionUseCase
        )
    }

    val dashboardViewModel: DashboardViewModel = viewModel {
        DashboardViewModel(
            userRepository = appContainer.userRepository,
            sessionUseCase = appContainer.sessionUseCase,
            verifyTotpUseCase = appContainer.verifyTotpUseCase,
            prefsManager = appContainer.prefsManager,
            totpDataSource = appContainer.totpDataSource
        )
    }

    val settingsViewModel: SettingsViewModel = viewModel {
        SettingsViewModel(
            sessionUseCase = appContainer.sessionUseCase
        )
    }

    val isUserInAuthenticatedScreen = currentRoute in listOf(
        Routes.DASHBOARD,
        Routes.SETTINGS,
        Routes.TWO_FACTOR_SETUP,
        Routes.BACKUP_CODES
    )

    if (isReauthPromptRequired && isUserInAuthenticatedScreen) {
        ReauthPromptDialog(
            user = activeUser,
            idleMinutes = appContainer.sessionUseCase.getInactivityTimeoutMinutes().toLong(),
            isBiometricEnabled = appContainer.prefsManager.isBiometricEnabled(),
            onBiometricUnlock = {
                onBiometricUnlock?.invoke {
                    appContainer.sessionUseCase.onReauthenticated()
                }
            },
            onPasswordUnlock = { pwd ->
                appContainer.sessionUseCase.reauthenticateWithPassword(activeUser.email, pwd)
            },
            onTotpUnlock = { totp ->
                appContainer.sessionUseCase.reauthenticateWithTotp(activeUser.uid, totp)
            },
            onLogout = {
                appContainer.sessionUseCase.endSession(activeUser.uid)
                navController.navigate(Routes.LOGIN) {
                    popUpTo(0) { inclusive = true }
                }
            },
            onAutoLogout = {
                appContainer.sessionUseCase.logoutDueToInactivity("Logged out after exceeding 15 minutes of inactivity.")
                navController.navigate(Routes.LOGIN) {
                    popUpTo(0) { inclusive = true }
                }
            }
        )
    }

    NavHost(
        navController = navController,
        startDestination = Routes.SPLASH
    ) {
        composable(Routes.SPLASH) {
            val isOnboardingDone = appContainer.prefsManager.isOnboardingCompleted()
            val currentUser = appContainer.authRepository.getCurrentUser()
            val isSessionActive = appContainer.sessionUseCase.isSessionActive()
            val isRemembered = currentUser != null && appContainer.sessionUseCase.isDeviceRemembered(currentUser.uid)
            val isLoggedIn = currentUser != null && (isSessionActive || isRemembered)

            SplashScreen(
                isOnboardingDone = isOnboardingDone,
                isUserLoggedIn = isLoggedIn,
                onNavigateToOnboarding = {
                    navController.navigate(Routes.ONBOARDING) {
                        popUpTo(Routes.SPLASH) { inclusive = true }
                    }
                },
                onNavigateToLogin = {
                    navController.navigate(Routes.LOGIN) {
                        popUpTo(Routes.SPLASH) { inclusive = true }
                    }
                },
                onNavigateToDashboard = {
                    if (currentUser != null) {
                        activeUser = currentUser
                        appContainer.sessionUseCase.startSession(currentUser.uid)
                    }
                    navController.navigate(Routes.DASHBOARD) {
                        popUpTo(Routes.SPLASH) { inclusive = true }
                    }
                }
            )
        }

        composable(Routes.ONBOARDING) {
            OnboardingScreen(
                onFinishOnboarding = {
                    appContainer.prefsManager.setOnboardingCompleted(true)
                    navController.navigate(Routes.LOGIN) {
                        popUpTo(Routes.ONBOARDING) { inclusive = true }
                    }
                }
            )
        }

        composable(Routes.LOGIN) {
            LoginScreen(
                viewModel = authViewModel,
                onNavigateToRegister = {
                    navController.navigate(Routes.REGISTER)
                },
                onNavigateToForgotPassword = {
                    navController.navigate(Routes.FORGOT_PASSWORD)
                },
                onNavigateTo2FA = { user, rememberDevice ->
                    activeUser = user
                    rememberDeviceState = rememberDevice
                    navController.navigate(Routes.TWO_FACTOR_VERIFY)
                },
                onNavigateToSetup2FA = { user ->
                    activeUser = user
                    navController.navigate(Routes.DASHBOARD) {
                        popUpTo(Routes.LOGIN) { inclusive = true }
                    }
                },
                onNavigateToDashboard = { user ->
                    activeUser = user
                    navController.navigate(Routes.DASHBOARD) {
                        popUpTo(Routes.LOGIN) { inclusive = true }
                    }
                },
                onBiometricUnlock = if (appContainer.prefsManager.isBiometricEnabled() && onBiometricUnlock != null) {
                    { onBiometricUnlock(null) }
                } else null
            )
        }

        composable(Routes.REGISTER) {
            RegisterScreen(
                viewModel = authViewModel,
                onNavigateBack = { navController.popBackStack() },
                onNavigateToSetup2FA = { user ->
                    activeUser = user
                    navController.navigate(Routes.DASHBOARD) {
                        popUpTo(Routes.LOGIN) { inclusive = true }
                    }
                },
                onNavigateToEmailVerification = { user ->
                    activeUser = user
                    navController.navigate(Routes.EMAIL_VERIFICATION)
                },
                onNavigateToDashboard = { user ->
                    activeUser = user
                    navController.navigate(Routes.DASHBOARD) {
                        popUpTo(Routes.LOGIN) { inclusive = true }
                    }
                }
            )
        }

        composable(Routes.FORGOT_PASSWORD) {
            ForgotPasswordScreen(
                viewModel = authViewModel,
                onNavigateBack = { navController.popBackStack() }
            )
        }

        composable(Routes.EMAIL_VERIFICATION) {
            EmailVerificationScreen(
                user = activeUser,
                onProceedTo2FA = {
                    appContainer.sessionUseCase.startSession(activeUser.uid)
                    navController.navigate(Routes.DASHBOARD) {
                        popUpTo(Routes.LOGIN) { inclusive = true }
                    }
                },
                onResendEmail = {
                    authViewModel.forgotPassword()
                }
            )
        }

        composable(Routes.TWO_FACTOR_SETUP) {
            TwoFactorSetupScreen(
                user = activeUser,
                viewModel = twoFactorViewModel,
                onNavigateBack = { navController.popBackStack() },
                onSetupSuccess = {
                    navController.navigate(Routes.BACKUP_CODES) {
                        popUpTo(Routes.TWO_FACTOR_SETUP) { inclusive = true }
                    }
                }
            )
        }

        composable(Routes.TWO_FACTOR_VERIFY) {
            TwoFactorVerifyScreen(
                user = activeUser,
                viewModel = twoFactorViewModel,
                initialRememberDevice = rememberDeviceState,
                onNavigateBack = { navController.popBackStack() },
                onVerificationSuccess = {
                    navController.navigate(Routes.DASHBOARD) {
                        popUpTo(Routes.LOGIN) { inclusive = true }
                    }
                }
            )
        }

        composable(Routes.BACKUP_CODES) {
            BackupCodesScreen(
                viewModel = twoFactorViewModel,
                onProceedToDashboard = {
                    navController.navigate(Routes.DASHBOARD) {
                        popUpTo(Routes.LOGIN) { inclusive = true }
                    }
                }
            )
        }

        composable(Routes.DASHBOARD) {
            DashboardScreen(
                user = activeUser,
                viewModel = dashboardViewModel,
                onNavigateToSettings = {
                    navController.navigate(Routes.SETTINGS)
                },
                onNavigateToSetup2FA = { user ->
                    activeUser = user
                    navController.navigate(Routes.TWO_FACTOR_SETUP)
                },
                onLogout = {
                    navController.navigate(Routes.LOGIN) {
                        popUpTo(0) { inclusive = true }
                    }
                },
                onNavigateToBackupCodes = {
                    navController.navigate(Routes.BACKUP_CODES)
                }
            )
        }

        composable(Routes.SETTINGS) {
            SettingsScreen(
                viewModel = settingsViewModel,
                onNavigateBack = { navController.popBackStack() }
            )
        }
    }
}
