package com.example.secureauth2fa.ui.dashboard

import android.app.Activity
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Key
import androidx.compose.material.icons.filled.Logout
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.VpnKey
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.secureauth2fa.ads.AdManager
import com.example.secureauth2fa.ads.BannerAdView
import com.example.secureauth2fa.data.model.User
import com.example.secureauth2fa.ui.components.PrimaryButton
import com.example.secureauth2fa.ui.theme.CyanAccent
import com.example.secureauth2fa.ui.theme.ErrorRed
import com.example.secureauth2fa.ui.theme.SuccessGreen
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DashboardScreen(
    user: User,
    viewModel: DashboardViewModel,
    onNavigateToSettings: () -> Unit,
    onNavigateToSetup2FA: (User) -> Unit,
    onLogout: () -> Unit,
    onNavigateToBackupCodes: (() -> Unit)? = null
) {
    val context = LocalContext.current
    val uiState by viewModel.uiState.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    val adManager = remember { AdManager.getInstance() }
    val isAdFree by adManager.isAdFreeActive.collectAsState()
    val adFreeUntil by adManager.adFreeUntilTimestamp.collectAsState()

    val adFreeRemainingText = remember(isAdFree, adFreeUntil) {
        if (isAdFree && adFreeUntil > System.currentTimeMillis()) {
            val hoursLeft = ((adFreeUntil - System.currentTimeMillis()) / 3600000L).coerceAtLeast(1)
            "Active ($hoursLeft hours left)"
        } else {
            "Disabled (Standard mode)"
        }
    }

    LaunchedEffect(user.uid) {
        viewModel.loadDashboard(user.uid)
    }

    LaunchedEffect(uiState.errorMessage, uiState.successMessage) {
        uiState.errorMessage?.let {
            snackbarHostState.showSnackbar(it)
            viewModel.clearMessages()
        }
        uiState.successMessage?.let {
            snackbarHostState.showSnackbar(it)
            viewModel.clearMessages()
        }
    }

    Scaffold(
        modifier = Modifier
            .fillMaxSize()
            .statusBarsPadding(),
        containerColor = MaterialTheme.colorScheme.background,
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier
                                .size(32.dp)
                                .clip(CircleShape)
                                .background(MaterialTheme.colorScheme.primaryContainer),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = when (uiState.selectedTab) {
                                    DashboardTab.HOME -> Icons.Default.VpnKey
                                    DashboardTab.HISTORY -> Icons.Default.History
                                    DashboardTab.SETTINGS -> Icons.Default.Settings
                                },
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                        Spacer(modifier = Modifier.width(10.dp))
                        Text(
                            text = when (uiState.selectedTab) {
                                DashboardTab.HOME -> "2-Step Authenticator"
                                DashboardTab.HISTORY -> "Activity History"
                                DashboardTab.SETTINGS -> "Settings & Security"
                            },
                            style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
                            color = MaterialTheme.colorScheme.onBackground
                        )
                    }
                },
                actions = {
                    // Top "+" button to add account (prominently available on Home tab)
                    if (uiState.selectedTab == DashboardTab.HOME) {
                        IconButton(
                            onClick = { viewModel.openAddAccountSheet() },
                            modifier = Modifier.testTag("add_account_top_button")
                        ) {
                            Icon(
                                imageVector = Icons.Default.Add,
                                contentDescription = "Add Account",
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(28.dp)
                            )
                        }
                    }

                    IconButton(
                        onClick = { viewModel.loadDashboard(user.uid) },
                        modifier = Modifier.testTag("refresh_dashboard_button")
                    ) {
                        Icon(
                            imageVector = Icons.Default.Refresh,
                            contentDescription = "Refresh",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background
                )
            )
        },
        floatingActionButton = {
            if (uiState.selectedTab == DashboardTab.HOME && uiState.accounts.isNotEmpty()) {
                FloatingActionButton(
                    onClick = { viewModel.openAddAccountSheet() },
                    containerColor = MaterialTheme.colorScheme.primary,
                    contentColor = Color.White,
                    shape = CircleShape,
                    modifier = Modifier.testTag("fab_add_account")
                ) {
                    Icon(
                        imageVector = Icons.Default.Add,
                        contentDescription = "Add Account",
                        modifier = Modifier.size(24.dp)
                    )
                }
            }
        },
        bottomBar = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .navigationBarsPadding()
            ) {
                // Banner Ad
                BannerAdView()

                // Bottom Navigation Bar with 3 tabs: Home, History, Settings
                NavigationBar(
                    containerColor = MaterialTheme.colorScheme.surface,
                    tonalElevation = 8.dp,
                    modifier = Modifier.testTag("bottom_navigation_bar")
                ) {
                    NavigationBarItem(
                        selected = uiState.selectedTab == DashboardTab.HOME,
                        onClick = { viewModel.selectTab(DashboardTab.HOME) },
                        icon = {
                            Icon(
                                imageVector = Icons.Default.VpnKey,
                                contentDescription = "Home"
                            )
                        },
                        label = { Text("Home") },
                        colors = NavigationBarItemDefaults.colors(
                            selectedIconColor = MaterialTheme.colorScheme.primary,
                            selectedTextColor = MaterialTheme.colorScheme.primary,
                            indicatorColor = MaterialTheme.colorScheme.primaryContainer
                        ),
                        modifier = Modifier.testTag("tab_home")
                    )

                    NavigationBarItem(
                        selected = uiState.selectedTab == DashboardTab.HISTORY,
                        onClick = { viewModel.selectTab(DashboardTab.HISTORY) },
                        icon = {
                            Icon(
                                imageVector = Icons.Default.History,
                                contentDescription = "History"
                            )
                        },
                        label = { Text("History") },
                        colors = NavigationBarItemDefaults.colors(
                            selectedIconColor = MaterialTheme.colorScheme.primary,
                            selectedTextColor = MaterialTheme.colorScheme.primary,
                            indicatorColor = MaterialTheme.colorScheme.primaryContainer
                        ),
                        modifier = Modifier.testTag("tab_history")
                    )

                    NavigationBarItem(
                        selected = uiState.selectedTab == DashboardTab.SETTINGS,
                        onClick = { viewModel.selectTab(DashboardTab.SETTINGS) },
                        icon = {
                            Icon(
                                imageVector = Icons.Default.Settings,
                                contentDescription = "Settings"
                            )
                        },
                        label = { Text("Settings") },
                        colors = NavigationBarItemDefaults.colors(
                            selectedIconColor = MaterialTheme.colorScheme.primary,
                            selectedTextColor = MaterialTheme.colorScheme.primary,
                            indicatorColor = MaterialTheme.colorScheme.primaryContainer
                        ),
                        modifier = Modifier.testTag("tab_settings")
                    )
                }
            }
        }
    ) { paddingValues ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            AnimatedContent(
                targetState = uiState.selectedTab,
                transitionSpec = { fadeIn() togetherWith fadeOut() },
                label = "DashboardTabContent"
            ) { tab ->
                when (tab) {
                    DashboardTab.HOME -> {
                        HomeTabContent(
                            uiState = uiState,
                            userId = user.uid,
                            viewModel = viewModel
                        )
                    }
                    DashboardTab.HISTORY -> {
                        HistoryTabContent(
                            uiState = uiState,
                            userId = user.uid,
                            viewModel = viewModel
                        )
                    }
                    DashboardTab.SETTINGS -> {
                        SettingsTabContent(
                            uiState = uiState,
                            userId = user.uid,
                            viewModel = viewModel,
                            onNavigateTo2FASetup = { onNavigateToSetup2FA(user) },
                            onNavigateToBackupCodes = { onNavigateToBackupCodes?.invoke() ?: onNavigateToSettings() },
                            onLogout = { viewModel.logout(user.uid, onLogout) },
                            onWatchRewardedAd = {
                                (context as? Activity)?.let { act ->
                                    adManager.showRewardedAd(
                                        activity = act,
                                        onUserRewarded = { viewModel.loadDashboard(user.uid) },
                                        onAdClosed = { viewModel.loadDashboard(user.uid) },
                                        onError = { /* fallback */ }
                                    )
                                }
                            },
                            adFreeRemainingText = adFreeRemainingText
                        )
                    }
                }
            }
        }
    }

    // Modal Add Account Sheet
    if (uiState.isAddAccountSheetVisible) {
        AddAccountSheet(
            sheetState = sheetState,
            uiState = uiState,
            userId = user.uid,
            viewModel = viewModel,
            onDismiss = { viewModel.closeAddAccountSheet() }
        )
    }

    // Delete Account Confirmation Dialog
    uiState.accountToDelete?.let { account ->
        AlertDialog(
            onDismissRequest = { viewModel.closeDeleteAccountDialog() },
            title = { Text("Delete 2FA Account") },
            text = {
                Text(
                    "Are you sure you want to delete ${account.issuer} (${account.accountName})? You will no longer generate verification codes for this service from this app."
                )
            },
            confirmButton = {
                TextButton(
                    onClick = { viewModel.confirmDeleteAccount(user.uid) },
                    modifier = Modifier.testTag("confirm_delete_account_button")
                ) {
                    Text("Delete", color = ErrorRed)
                }
            },
            dismissButton = {
                TextButton(onClick = { viewModel.closeDeleteAccountDialog() }) {
                    Text("Cancel")
                }
            }
        )
    }

    // View Secret Key Dialog
    uiState.accountToViewSecret?.let { account ->
        AlertDialog(
            onDismissRequest = { viewModel.closeViewSecretDialog() },
            title = { Text("${account.issuer} Secret Key") },
            text = {
                Column {
                    Text(
                        text = "Account: ${account.accountName.ifBlank { account.issuer }}",
                        style = MaterialTheme.typography.bodyMedium
                    )
                    Spacer(modifier = Modifier.height(10.dp))
                    Text(
                        text = "Base32 Secret:",
                        style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(8.dp))
                            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
                            .padding(12.dp)
                    ) {
                        Text(
                            text = account.secretKey,
                            style = MaterialTheme.typography.bodyMedium.copy(
                                fontFamily = FontFamily.Monospace,
                                fontWeight = FontWeight.SemiBold
                            ),
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                        val clip = ClipData.newPlainText("Secret Key", account.secretKey)
                        clipboard.setPrimaryClip(clip)
                        viewModel.closeViewSecretDialog()
                    },
                    modifier = Modifier.testTag("copy_secret_dialog_button")
                ) {
                    Icon(Icons.Default.ContentCopy, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Copy Key")
                }
            },
            dismissButton = {
                TextButton(onClick = { viewModel.closeViewSecretDialog() }) {
                    Text("Close")
                }
            }
        )
    }

    // Clear History Dialog
    if (uiState.showClearHistoryDialog) {
        AlertDialog(
            onDismissRequest = { viewModel.closeClearHistoryDialog() },
            title = { Text("Clear Security Log") },
            text = { Text("Are you sure you want to clear all history records? This cannot be undone.") },
            confirmButton = {
                TextButton(
                    onClick = { viewModel.confirmClearHistory(user.uid) },
                    modifier = Modifier.testTag("confirm_clear_history_button")
                ) {
                    Text("Clear", color = ErrorRed)
                }
            },
            dismissButton = {
                TextButton(onClick = { viewModel.closeClearHistoryDialog() }) {
                    Text("Cancel")
                }
            }
        )
    }

    // Disable 2FA Dialog
    if (uiState.showDisable2FADialog) {
        AlertDialog(
            onDismissRequest = { viewModel.closeDisable2FADialog() },
            icon = {
                Icon(
                    imageVector = Icons.Default.Warning,
                    contentDescription = null,
                    tint = ErrorRed,
                    modifier = Modifier.size(36.dp)
                )
            },
            title = { Text("Disable Primary 2FA?") },
            text = {
                Column {
                    Text(
                        text = "Disabling Primary Two-Factor Authentication leaves your account sign-in unprotected.\n\nEnter your current 6-digit TOTP code to confirm:",
                        style = MaterialTheme.typography.bodyMedium
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    OutlinedTextField(
                        value = uiState.disable2FAConfirmCode,
                        onValueChange = { viewModel.onDisable2FACodeChanged(it) },
                        label = { Text("6-digit TOTP code") },
                        singleLine = true,
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("disable_2fa_otp_field")
                    )
                }
            },
            confirmButton = {
                PrimaryButton(
                    text = "Confirm Disable",
                    onClick = { viewModel.confirmDisable2FA(user.uid) },
                    isLoading = uiState.isDisabling2FA,
                    testTag = "confirm_disable_2fa_button"
                )
            },
            dismissButton = {
                TextButton(onClick = { viewModel.closeDisable2FADialog() }) {
                    Text("Cancel")
                }
            }
        )
    }
}
