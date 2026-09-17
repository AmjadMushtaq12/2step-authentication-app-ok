package com.example.secureauth2fa.ui.twofa

import android.app.Activity
import android.view.WindowManager
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Key
import androidx.compose.material.icons.filled.LockClock
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.secureauth2fa.data.model.User
import com.example.secureauth2fa.ui.components.OtpInputField
import com.example.secureauth2fa.ui.components.PrimaryButton

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TwoFactorVerifyScreen(
    user: User,
    viewModel: TwoFactorViewModel,
    initialRememberDevice: Boolean = true,
    onNavigateBack: () -> Unit,
    onVerificationSuccess: () -> Unit
) {
    val uiState by viewModel.uiState.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }
    val focusManager = LocalFocusManager.current
    val context = LocalContext.current
    val activity = context as? Activity

    LaunchedEffect(initialRememberDevice) {
        viewModel.onRememberDeviceToggled(initialRememberDevice)
    }

    LaunchedEffect(uiState.errorMessage) {
        uiState.errorMessage?.let {
            snackbarHostState.showSnackbar(it)
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .statusBarsPadding()
            .navigationBarsPadding()
            .imePadding()
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
        ) {
            TopAppBar(
                title = { Text("Two-Factor Verification") },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(
                            imageVector = Icons.Default.ArrowBack,
                            contentDescription = "Back"
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = Color.Transparent
                )
            )

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp, vertical = 12.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // Header Icon
                Box(
                    modifier = Modifier
                        .size(80.dp)
                        .clip(RoundedCornerShape(24.dp))
                        .background(MaterialTheme.colorScheme.primaryContainer),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = if (uiState.isUsingRecoveryCode) Icons.Default.Key else Icons.Default.Shield,
                        contentDescription = "Verification Badge",
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(44.dp)
                    )
                }

                Spacer(modifier = Modifier.height(24.dp))

                Text(
                    text = if (uiState.isUsingRecoveryCode) "Enter Backup Code" else "Two-Factor Authentication",
                    style = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.Bold),
                    color = MaterialTheme.colorScheme.onBackground
                )

                Spacer(modifier = Modifier.height(8.dp))

                Text(
                    text = if (uiState.isUsingRecoveryCode) {
                        "Enter one of your 10-character emergency recovery codes (e.g. 4829-1053)."
                    } else {
                        "Enter the 6-digit code from your authenticator app for ${user.email}."
                    },
                    style = MaterialTheme.typography.bodyMedium.copy(textAlign = TextAlign.Center),
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                Spacer(modifier = Modifier.height(28.dp))

                AnimatedContent(
                    targetState = uiState.isUsingRecoveryCode,
                    transitionSpec = { fadeIn() togetherWith fadeOut() },
                    label = "verification_input_mode"
                ) { isRecovery ->
                    if (isRecovery) {
                        // Backup Code Input Field
                        OutlinedTextField(
                            value = uiState.recoveryCodeInput,
                            onValueChange = { viewModel.onRecoveryCodeChanged(it) },
                            label = { Text("Backup Recovery Code") },
                            placeholder = { Text("XXXX-XXXX") },
                            leadingIcon = {
                                Icon(imageVector = Icons.Default.Key, contentDescription = null)
                            },
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(
                                capitalization = KeyboardCapitalization.Characters,
                                imeAction = ImeAction.Done
                            ),
                            keyboardActions = KeyboardActions(
                                onDone = {
                                    focusManager.clearFocus()
                                    viewModel.verifyCode(user.uid, activity, onVerificationSuccess)
                                }
                            ),
                            shape = RoundedCornerShape(14.dp),
                            textStyle = MaterialTheme.typography.titleMedium.copy(
                                fontFamily = FontFamily.Monospace,
                                letterSpacing = 2.sp
                            ),
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("recovery_code_input")
                        )
                    } else {
                        // 6-Digit OTP Field & 30-Second Refresh Indicator
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            OtpInputField(
                                otpValue = uiState.enteredCode,
                                onOtpChange = { viewModel.onVerifyCodeChanged(it) },
                                isError = uiState.errorMessage != null,
                                onDone = {
                                    focusManager.clearFocus()
                                    viewModel.verifyCode(user.uid, activity, onVerificationSuccess)
                                },
                                modifier = Modifier.testTag("verify_otp_input")
                            )

                            Spacer(modifier = Modifier.height(20.dp))

                            // 30-Second Live Timer Indicator
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Box(contentAlignment = Alignment.Center, modifier = Modifier.size(24.dp)) {
                                    CircularProgressIndicator(
                                        progress = { uiState.timerProgress },
                                        strokeWidth = 2.5.dp,
                                        modifier = Modifier.size(20.dp),
                                        color = MaterialTheme.colorScheme.primary,
                                        trackColor = MaterialTheme.colorScheme.surfaceVariant
                                    )
                                }
                                Text(
                                    text = "Code refreshes in ${uiState.timerRemainingSeconds}s",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }

                            Spacer(modifier = Modifier.height(4.dp))

                            TextButton(
                                onClick = { viewModel.autoFillVerifyCode(user.uid) },
                                modifier = Modifier.testTag("autofill_verify_totp_button")
                            ) {
                                Text("Auto-fill Current Code (Emulator Testing)", style = MaterialTheme.typography.bodySmall)
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(20.dp))

                // Remember this device checkbox
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .clickable { viewModel.onRememberDeviceToggled(!uiState.rememberDevice) }
                        .padding(vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Checkbox(
                        checked = uiState.rememberDevice,
                        onCheckedChange = { viewModel.onRememberDeviceToggled(it) },
                        colors = CheckboxDefaults.colors(checkedColor = MaterialTheme.colorScheme.primary),
                        modifier = Modifier.testTag("verify_remember_device_checkbox")
                    )
                    Text(
                        text = "Remember this device for 30 days",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }

                Spacer(modifier = Modifier.height(24.dp))

                // Verify Button
                PrimaryButton(
                    text = "Verify & Sign In",
                    onClick = {
                        focusManager.clearFocus()
                        viewModel.verifyCode(user.uid, activity, onVerificationSuccess)
                    },
                    isLoading = uiState.isLoading,
                    enabled = if (uiState.isUsingRecoveryCode) uiState.recoveryCodeInput.isNotBlank() else uiState.enteredCode.length == 6,
                    testTag = "verify_submit_button"
                )

                Spacer(modifier = Modifier.height(16.dp))

                // Toggle Recovery Code Button
                TextButton(
                    onClick = { viewModel.toggleUseRecoveryCode(!uiState.isUsingRecoveryCode) },
                    modifier = Modifier.testTag("toggle_recovery_code_button")
                ) {
                    Text(
                        text = if (uiState.isUsingRecoveryCode) "Use 6-digit Authenticator code" else "Lost authenticator? Use backup recovery code",
                        style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold),
                        color = MaterialTheme.colorScheme.primary
                    )
                }
            }
        }

        SnackbarHost(
            hostState = snackbarHostState,
            modifier = Modifier.align(Alignment.BottomCenter)
        )
    }
}
