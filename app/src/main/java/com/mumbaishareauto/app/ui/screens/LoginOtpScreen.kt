package com.mumbaishareauto.app.ui.screens

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.DirectionsCar
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Phone
import androidx.compose.material.icons.filled.VerifiedUser
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.mumbaishareauto.app.auth.AuthDomainValidator
import com.mumbaishareauto.app.auth.AuthFieldValidation
import com.mumbaishareauto.app.auth.AuthUserRole
import com.mumbaishareauto.app.ui.PhoneAuthStep
import com.mumbaishareauto.app.ui.PhoneAuthUiState
import com.mumbaishareauto.app.ui.components.BadgeTone
import com.mumbaishareauto.app.ui.components.ResponsiveContentColumn
import com.mumbaishareauto.app.ui.components.SectionHeader
import com.mumbaishareauto.app.ui.components.StatusPill
import com.mumbaishareauto.app.ui.components.UnavailableServiceBanner
import com.mumbaishareauto.app.ui.components.ValidationFeedbackCard

/**
 * Clean Firebase Phone Number (+91) OTP Login & Verification Screen.
 * Supports:
 * - Role selection: 'Passenger' or 'Driver'
 * - 10-digit Indian mobile number (+91 prefix)
 * - Driver auto registration number when role is Driver
 * - 6-digit SMS OTP verification & Cloud Firestore `/users/{uid}` profile persistence
 */
@Composable
fun LoginOtpScreen(
    authState: PhoneAuthUiState,
    onSelectRole: (AuthUserRole) -> Unit,
    onPhoneDigitsChange: (String) -> Unit,
    onDriverAutoRegChange: (String) -> Unit,
    onOtpCodeChange: (String) -> Unit,
    onSendOtp: (Activity) -> Unit,
    onResendOtp: (Activity) -> Unit,
    onVerifyOtp: () -> Unit,
    onChangePhoneNumber: () -> Unit,
    onRetrySaveProfile: () -> Unit,
    onSignOut: () -> Unit,
    onContinueToHome: () -> Unit
) {
    val context = LocalContext.current
    val activity = context.findActivity()

    ResponsiveContentColumn(modifier = Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Hero Header Card
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = MaterialTheme.shapes.large,
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primary)
            ) {
                Column(
                    modifier = Modifier.padding(18.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        StatusPill(
                            text = when (authState.step) {
                                PhoneAuthStep.ENTER_DETAILS -> "Step 1 of 2 • Mobile & Role"
                                PhoneAuthStep.ENTER_OTP -> "Step 2 of 2 • OTP Verify"
                                PhoneAuthStep.AUTHENTICATED -> "Verified Account"
                            },
                            tone = if (authState.step == PhoneAuthStep.AUTHENTICATED) {
                                BadgeTone.EMERALD
                            } else {
                                BadgeTone.AMBER
                            }
                        )
                        StatusPill(
                            text = if (authState.isFirebaseConfigured) "Firebase Ready" else "Config Required",
                            tone = if (authState.isFirebaseConfigured) BadgeTone.EMERALD else BadgeTone.NEUTRAL
                        )
                    }
                    Text(
                        text = when (authState.step) {
                            PhoneAuthStep.ENTER_DETAILS -> "Sign in with +91 Mobile OTP"
                            PhoneAuthStep.ENTER_OTP -> "Enter 6-Digit SMS OTP"
                            PhoneAuthStep.AUTHENTICATED -> "Mobile Number Verified"
                        },
                        style = MaterialTheme.typography.headlineMedium,
                        color = MaterialTheme.colorScheme.onPrimary
                    )
                    Text(
                        text = "Your UID, +91 phone number, operating role, and Driver auto plate (when applicable) sync securely to Cloud Firestore.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.85f)
                    )
                }
            }

            if (!authState.isFirebaseConfigured) {
                UnavailableServiceBanner(
                    title = "Firebase Project Configuration Required",
                    message = "Add google-services.json inside MumbaiShareAuto/app/, register your Android SHA-1 & SHA-256 certificates, and enable Phone Number sign-in in Firebase Console to receive real SMS OTPs."
                )
            }

            if (authState.errorMessage != null) {
                ValidationFeedbackCard(
                    message = authState.errorMessage,
                    isSuccess = false
                )
            } else if (authState.statusMessage != null) {
                ValidationFeedbackCard(
                    message = authState.statusMessage,
                    isSuccess = true
                )
            }

            when (authState.step) {
                PhoneAuthStep.ENTER_DETAILS -> {
                    EnterDetailsStepCard(
                        authState = authState,
                        activityAvailable = activity != null,
                        onSelectRole = onSelectRole,
                        onPhoneDigitsChange = onPhoneDigitsChange,
                        onDriverAutoRegChange = onDriverAutoRegChange,
                        onSendOtp = {
                            if (activity != null) {
                                onSendOtp(activity)
                            }
                        }
                    )
                }

                PhoneAuthStep.ENTER_OTP -> {
                    VerifyOtpStepCard(
                        authState = authState,
                        activityAvailable = activity != null,
                        onOtpCodeChange = onOtpCodeChange,
                        onVerifyOtp = onVerifyOtp,
                        onResendOtp = {
                            if (activity != null) {
                                onResendOtp(activity)
                            }
                        },
                        onChangePhoneNumber = onChangePhoneNumber
                    )
                }

                PhoneAuthStep.AUTHENTICATED -> {
                    AuthenticatedProfileCard(
                        authState = authState,
                        onSelectRole = onSelectRole,
                        onDriverAutoRegChange = onDriverAutoRegChange,
                        onSaveRoleUpdate = onRetrySaveProfile,
                        onContinueToHome = onContinueToHome,
                        onSignOut = onSignOut
                    )
                }
            }

            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = MaterialTheme.shapes.medium,
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
            ) {
                Column(
                    modifier = Modifier.padding(14.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.VerifiedUser,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(18.dp)
                        )
                        Text(
                            text = "Privacy & Verification Disclosure",
                            style = MaterialTheme.typography.titleSmall
                        )
                    }
                    Text(
                        text = "• Phone OTP verifies ownership of your +91 mobile number via Firebase Authentication.\n" +
                            "• User profile data is restricted to owner UID under Firestore security rules.\n" +
                            "• Driver auto registration numbers remain self-declared until official RTO permit verification connects.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}

@Composable
private fun EnterDetailsStepCard(
    authState: PhoneAuthUiState,
    activityAvailable: Boolean,
    onSelectRole: (AuthUserRole) -> Unit,
    onPhoneDigitsChange: (String) -> Unit,
    onDriverAutoRegChange: (String) -> Unit,
    onSendOtp: () -> Unit
) {
    val phoneValidation = if (authState.phoneDigitsInput.isNotEmpty()) {
        AuthDomainValidator.validateIndianMobileDigits(authState.phoneDigitsInput)
    } else {
        AuthFieldValidation.Valid
    }
    val regValidation = if (
        authState.selectedRole == AuthUserRole.DRIVER &&
        authState.driverAutoRegInput.isNotEmpty()
    ) {
        AuthDomainValidator.validateDriverAutoRegNumber(authState.driverAutoRegInput)
    } else {
        AuthFieldValidation.Valid
    }
    val canSubmit = !authState.isLoading &&
        activityAvailable &&
        AuthDomainValidator.validateSendOtpRequest(
            role = authState.selectedRole,
            tenDigitMobile = authState.phoneDigitsInput,
            driverAutoRegInput = authState.driverAutoRegInput
        ).isValid

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.medium,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            SectionHeader(
                title = "1. Select Your Role",
                subtitle = "Choose whether you are riding or operating a shared auto"
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                RoleSelectionCard(
                    role = AuthUserRole.PASSENGER,
                    subtitle = "Commuter party",
                    selected = authState.selectedRole == AuthUserRole.PASSENGER,
                    onClick = { onSelectRole(AuthUserRole.PASSENGER) },
                    modifier = Modifier.weight(1f)
                )
                RoleSelectionCard(
                    role = AuthUserRole.DRIVER,
                    subtitle = "Auto-rickshaw",
                    selected = authState.selectedRole == AuthUserRole.DRIVER,
                    onClick = { onSelectRole(AuthUserRole.DRIVER) },
                    modifier = Modifier.weight(1f)
                )
            }

            HorizontalDivider()

            SectionHeader(
                title = "2. Indian Mobile Number (+91)",
                subtitle = "Enter your 10-digit mobile number for Firebase SMS OTP"
            )

            OutlinedTextField(
                value = authState.phoneDigitsInput,
                onValueChange = onPhoneDigitsChange,
                label = { Text("10-Digit Mobile Number") },
                placeholder = { Text("e.g. 9820123456") },
                leadingIcon = {
                    Row(
                        modifier = Modifier.padding(start = 12.dp, end = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Phone,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp)
                        )
                        Text(
                            text = "+91",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                },
                singleLine = true,
                isError = phoneValidation is AuthFieldValidation.Invalid,
                supportingText = {
                    when (phoneValidation) {
                        is AuthFieldValidation.Invalid -> Text(phoneValidation.message)
                        AuthFieldValidation.Valid -> Text("${authState.phoneDigitsInput.length}/10 digits • Format: +91XXXXXXXXXX")
                    }
                },
                keyboardOptions = KeyboardOptions(
                    keyboardType = KeyboardType.Phone,
                    imeAction = if (authState.selectedRole == AuthUserRole.DRIVER) ImeAction.Next else ImeAction.Done
                ),
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .semantics { contentDescription = "Indian 10 digit mobile number" }
            )

            if (authState.selectedRole == AuthUserRole.DRIVER) {
                OutlinedTextField(
                    value = authState.driverAutoRegInput,
                    onValueChange = onDriverAutoRegChange,
                    label = { Text("Driver Auto Registration Number (MH RTO)") },
                    placeholder = { Text("e.g. MH 02 AB 1234") },
                    leadingIcon = {
                        Icon(imageVector = Icons.Default.DirectionsCar, contentDescription = null)
                    },
                    singleLine = true,
                    isError = regValidation is AuthFieldValidation.Invalid,
                    supportingText = {
                        when (regValidation) {
                            is AuthFieldValidation.Invalid -> Text(regValidation.message)
                            AuthFieldValidation.Valid -> Text("Saved to Firestore profile; official RTO permit check remains separate.")
                        }
                    },
                    keyboardOptions = KeyboardOptions(
                        capitalization = KeyboardCapitalization.Characters,
                        imeAction = ImeAction.Done
                    ),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .semantics { contentDescription = "Driver auto registration number" }
                )
            }

            Button(
                onClick = onSendOtp,
                enabled = canSubmit,
                modifier = Modifier
                    .fillMaxWidth()
                    .defaultMinSize(minHeight = 52.dp)
            ) {
                if (authState.isLoading) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(18.dp),
                        strokeWidth = 2.dp,
                        color = MaterialTheme.colorScheme.onPrimary
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Sending OTP...")
                } else {
                    Text("Send OTP (+91 ${authState.phoneDigitsInput.ifBlank { "Mobile" }})")
                }
            }
        }
    }
}

@Composable
private fun VerifyOtpStepCard(
    authState: PhoneAuthUiState,
    activityAvailable: Boolean,
    onOtpCodeChange: (String) -> Unit,
    onVerifyOtp: () -> Unit,
    onResendOtp: () -> Unit,
    onChangePhoneNumber: () -> Unit
) {
    val formattedPhone = AuthDomainValidator.normalizeIndianE164(authState.phoneDigitsInput)
        ?: "+91${authState.phoneDigitsInput}"
    val otpValidation = if (authState.otpCodeInput.isNotEmpty()) {
        AuthDomainValidator.validateOtpCode(authState.otpCodeInput)
    } else {
        AuthFieldValidation.Valid
    }

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.medium,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Verifying $formattedPhone",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = buildString {
                            append("Role: ${authState.selectedRole.label}")
                            if (authState.selectedRole == AuthUserRole.DRIVER && authState.driverAutoRegInput.isNotBlank()) {
                                append(" • Auto: ${AuthDomainValidator.formatAutoRegForDisplay(authState.driverAutoRegInput)}")
                            }
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                TextButton(
                    onClick = onChangePhoneNumber,
                    enabled = !authState.isLoading,
                    modifier = Modifier.defaultMinSize(minHeight = 48.dp)
                ) {
                    Text("Edit")
                }
            }

            OutlinedTextField(
                value = authState.otpCodeInput,
                onValueChange = onOtpCodeChange,
                label = { Text("6-Digit OTP Code") },
                placeholder = { Text("Enter 6 digits") },
                leadingIcon = {
                    Icon(imageVector = Icons.Default.Lock, contentDescription = null)
                },
                singleLine = true,
                isError = otpValidation is AuthFieldValidation.Invalid,
                supportingText = {
                    when (otpValidation) {
                        is AuthFieldValidation.Invalid -> Text(otpValidation.message)
                        AuthFieldValidation.Valid -> Text("${authState.otpCodeInput.length}/6 digits entered")
                    }
                },
                keyboardOptions = KeyboardOptions(
                    keyboardType = KeyboardType.Number,
                    imeAction = ImeAction.Done
                ),
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .semantics { contentDescription = "6 digit SMS OTP code" }
            )

            Button(
                onClick = onVerifyOtp,
                enabled = !authState.isLoading && AuthDomainValidator.validateOtpCode(authState.otpCodeInput).isValid,
                modifier = Modifier
                    .fillMaxWidth()
                    .defaultMinSize(minHeight = 52.dp)
            ) {
                if (authState.isLoading) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(18.dp),
                        strokeWidth = 2.dp,
                        color = MaterialTheme.colorScheme.onPrimary
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Verifying OTP...")
                } else {
                    Text("Verify OTP & Save Profile")
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                OutlinedButton(
                    onClick = onResendOtp,
                    enabled = !authState.isLoading && authState.resendCooldownSeconds == 0 && activityAvailable,
                    modifier = Modifier.defaultMinSize(minHeight = 48.dp)
                ) {
                    Text(
                        if (authState.resendCooldownSeconds > 0) {
                            "Resend in ${authState.resendCooldownSeconds}s"
                        } else {
                            "Resend OTP"
                        }
                    )
                }

                TextButton(
                    onClick = onChangePhoneNumber,
                    enabled = !authState.isLoading,
                    modifier = Modifier.defaultMinSize(minHeight = 48.dp)
                ) {
                    Text("Change Number")
                }
            }
        }
    }
}

@Composable
private fun AuthenticatedProfileCard(
    authState: PhoneAuthUiState,
    onSelectRole: (AuthUserRole) -> Unit,
    onDriverAutoRegChange: (String) -> Unit,
    onSaveRoleUpdate: () -> Unit,
    onContinueToHome: () -> Unit,
    onSignOut: () -> Unit
) {
    val user = authState.signedInUser
    val profile = authState.authenticatedProfile
    val needsSaveOrUpdate = profile == null ||
        profile.role != authState.selectedRole ||
        (authState.selectedRole == AuthUserRole.DRIVER &&
            profile.driverAutoRegNumber != AuthDomainValidator.normalizeAutoRegNumber(authState.driverAutoRegInput))

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.medium,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.CheckCircle,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.tertiary
                    )
                    Text(
                        text = "Authenticated Session",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                }
                StatusPill(
                    text = (profile?.role ?: authState.selectedRole).label,
                    tone = if ((profile?.role ?: authState.selectedRole) == AuthUserRole.DRIVER) {
                        BadgeTone.AMBER
                    } else {
                        BadgeTone.NAVY
                    }
                )
            }

            if (user != null) {
                Text(
                    text = "Phone: ${profile?.phoneNumber ?: user.phoneNumber}",
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.SemiBold
                )
                Text(
                    text = "Firebase UID: ${user.uid}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            if (profile?.driverAutoRegNumber != null) {
                Text(
                    text = "Driver Auto Plate: ${AuthDomainValidator.formatAutoRegForDisplay(profile.driverAutoRegNumber)}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.SemiBold
                )
            }

            HorizontalDivider()

            Text(
                text = "Role & Profile in Cloud Firestore",
                style = MaterialTheme.typography.labelLarge
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                RoleSelectionCard(
                    role = AuthUserRole.PASSENGER,
                    subtitle = "Commuter",
                    selected = authState.selectedRole == AuthUserRole.PASSENGER,
                    onClick = { onSelectRole(AuthUserRole.PASSENGER) },
                    modifier = Modifier.weight(1f)
                )
                RoleSelectionCard(
                    role = AuthUserRole.DRIVER,
                    subtitle = "Auto Driver",
                    selected = authState.selectedRole == AuthUserRole.DRIVER,
                    onClick = { onSelectRole(AuthUserRole.DRIVER) },
                    modifier = Modifier.weight(1f)
                )
            }

            if (authState.selectedRole == AuthUserRole.DRIVER) {
                OutlinedTextField(
                    value = authState.driverAutoRegInput,
                    onValueChange = onDriverAutoRegChange,
                    label = { Text("Driver Auto Registration Number") },
                    placeholder = { Text("e.g. MH 02 AB 1234") },
                    singleLine = true,
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth()
                )
            }

            if (needsSaveOrUpdate) {
                Button(
                    onClick = onSaveRoleUpdate,
                    enabled = !authState.isLoading,
                    modifier = Modifier
                        .fillMaxWidth()
                        .defaultMinSize(minHeight = 48.dp)
                ) {
                    Text(if (profile == null) "Save Profile to Cloud Firestore" else "Update Firestore Profile")
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                OutlinedButton(
                    onClick = onSignOut,
                    enabled = !authState.isLoading,
                    colors = ButtonDefaults.outlinedButtonColors(
                        contentColor = MaterialTheme.colorScheme.error
                    ),
                    modifier = Modifier
                        .weight(1f)
                        .defaultMinSize(minHeight = 48.dp)
                ) {
                    Text("Sign Out")
                }
                Button(
                    onClick = onContinueToHome,
                    enabled = !authState.isLoading,
                    modifier = Modifier
                        .weight(1f)
                        .defaultMinSize(minHeight = 48.dp)
                ) {
                    Text("Home")
                }
            }
        }
    }
}

@Composable
private fun RoleSelectionCard(
    role: AuthUserRole,
    subtitle: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val containerColor = if (selected) {
        MaterialTheme.colorScheme.primaryContainer
    } else {
        MaterialTheme.colorScheme.surfaceVariant
    }
    val borderColor = if (selected) {
        MaterialTheme.colorScheme.primary
    } else {
        MaterialTheme.colorScheme.outline
    }
    val icon = if (role == AuthUserRole.PASSENGER) Icons.Default.Person else Icons.Default.DirectionsCar

    Surface(
        onClick = onClick,
        color = containerColor,
        shape = RoundedCornerShape(12.dp),
        border = BorderStroke(if (selected) 2.dp else 1.dp, borderColor),
        modifier = modifier
            .defaultMinSize(minHeight = 64.dp)
            .semantics {
                this.selected = selected
                contentDescription = "${role.label} role"
            }
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(36.dp)
                    .background(
                        color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surface,
                        shape = RoundedCornerShape(10.dp)
                    ),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = if (selected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(20.dp)
                )
            }
            Column {
                Text(
                    text = role.label,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

private tailrec fun Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}
