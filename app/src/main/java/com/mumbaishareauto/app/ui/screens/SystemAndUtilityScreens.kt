package com.mumbaishareauto.app.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DarkMode
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.NotificationsOff
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.VerifiedUser
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.mumbaishareauto.app.UserMode
import com.mumbaishareauto.app.auth.AuthDomainValidator
import com.mumbaishareauto.app.auth.AuthUserRole
import com.mumbaishareauto.app.auth.AuthenticatedUserProfile
import com.mumbaishareauto.app.auth.FirebaseAuthSessionUser
import com.mumbaishareauto.app.data.local.AppPreferencesRepository
import com.mumbaishareauto.app.data.local.FavoriteRouteRepository
import com.mumbaishareauto.app.data.local.db.NotificationEntity
import com.mumbaishareauto.app.permissions.NotificationPermissionRationaleCard
import com.mumbaishareauto.app.permissions.rememberNotificationPermissionGranted
import com.mumbaishareauto.app.ui.components.BadgeTone
import com.mumbaishareauto.app.ui.components.DestructiveConfirmDialog
import com.mumbaishareauto.app.ui.components.EmptyStateCard
import com.mumbaishareauto.app.ui.components.LockedRulesNoticeCard
import com.mumbaishareauto.app.ui.components.ResponsiveContentColumn
import com.mumbaishareauto.app.ui.components.SectionHeader
import com.mumbaishareauto.app.ui.components.StatusPill
import com.mumbaishareauto.app.ui.components.UnavailableServiceBanner
import com.mumbaishareauto.app.ui.navigation.AppRoute
import com.mumbaishareauto.app.ui.theme.ThemePreference

/**
 * Screen 13: Notifications
 * Room-persisted device-local notice center (no simulated server/FCM alerts)
 * with wired Android 13+ POST_NOTIFICATIONS rationale & App Settings guidance.
 */
@Composable
fun NotificationsScreen(
    favoriteCount: Int,
    draftCount: Int,
    onNavigate: (AppRoute) -> Unit,
    persistedNotifications: List<NotificationEntity> = emptyList(),
    persistedBookingsCount: Int = 0,
    persistedDriverRoutesCount: Int = 0,
    isNetworkOnline: Boolean = false,
    connectionType: String = "Offline",
    isBackendConfigured: Boolean = false,
    onMarkRead: (String) -> Unit = {},
    onMarkAllRead: () -> Unit = {}
) {
    val unreadCount = persistedNotifications.count { !it.isRead }
    val notificationPermissionGranted = rememberNotificationPermissionGranted()

    ResponsiveContentColumn(modifier = Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            SectionHeader(
                title = "Notifications & System Status",
                subtitle = "Genuine device status notices stored in Room SQLite — zero simulated push alerts"
            )

            UnavailableServiceBanner(
                title = "Real-Time Push Alerts Unavailable",
                message = if (isBackendConfigured) {
                    "Device network: $connectionType. Live push alerts remain disabled until production FCM credentials are configured."
                } else {
                    "Device network: $connectionType. Firebase Cloud Messaging and server driver-acceptance push alerts are unavailable while backend URL is unconfigured."
                }
            )

            NotificationPermissionRationaleCard()

            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = MaterialTheme.shapes.medium,
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Local Room Database Summary",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.SemiBold
                        )
                        StatusPill(text = "Room v2 Active", tone = BadgeTone.EMERALD)
                    }
                    Text(
                        text = "• $favoriteCount saved station stand(s)\n" +
                            "• $draftCount local route draft(s)\n" +
                            "• $persistedBookingsCount persisted booking lifecycle record(s)\n" +
                            "• $persistedDriverRoutesCount local driver route evaluation(s)\n" +
                            "• Network state: ${if (isNetworkOnline) "Online ($connectionType)" else connectionType} • Alerts: ${if (notificationPermissionGranted) "Allowed" else "Permission Pending"}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            if (persistedNotifications.isNotEmpty()) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Local Device Notices ($unreadCount unread)",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.semantics { heading() }
                    )
                    if (unreadCount > 0) {
                        TextButton(
                            onClick = onMarkAllRead,
                            modifier = Modifier.defaultMinSize(minHeight = 48.dp)
                        ) {
                            Text("Mark All Read")
                        }
                    }
                }

                persistedNotifications.forEach { notice ->
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = MaterialTheme.shapes.medium,
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
                    ) {
                        Column(
                            modifier = Modifier.padding(16.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = notice.title,
                                    style = MaterialTheme.typography.titleSmall,
                                    fontWeight = FontWeight.SemiBold,
                                    modifier = Modifier.weight(1f)
                                )
                                StatusPill(
                                    text = if (notice.isRead) "Read" else "Local Notice",
                                    tone = if (notice.isRead) BadgeTone.NEUTRAL else BadgeTone.AMBER
                                )
                            }
                            Text(
                                text = notice.message,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            if (!notice.isRead) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.End
                                ) {
                                    TextButton(
                                        onClick = { onMarkRead(notice.id) },
                                        modifier = Modifier.defaultMinSize(minHeight = 48.dp)
                                    ) {
                                        Text("Mark Read")
                                    }
                                }
                            }
                        }
                    }
                }
            } else {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = MaterialTheme.shapes.medium,
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "RTO Capacity Verification Required",
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.SemiBold
                            )
                            StatusPill(text = "Action Deferred", tone = BadgeTone.AMBER)
                        }
                        Text(
                            text = "Vehicle capacity defaults remain unverified (isLegallyVerified = false). Commercial operations stay blocked in BookingDomain until legal verification is connected.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            EmptyStateCard(
                icon = Icons.Default.NotificationsOff,
                title = "No Live Ride Alerts",
                description = "Live driver-passenger match updates remain unavailable offline and are never fabricated locally.",
                actionLabel = "Explore Station Stands",
                onAction = { onNavigate(AppRoute.STATION_DIRECTORY) }
            )
        }
    }
}

/**
 * Screen 14: Profile and Settings
 * Persists user mode, theme preference, notification permission status, onboarding replay, and safe local data reset.
 */
@Composable
fun ProfileSettingsScreen(
    prefsRepo: AppPreferencesRepository,
    favoriteRepo: FavoriteRouteRepository,
    onReplayOnboarding: () -> Unit,
    onOpenHelp: () -> Unit,
    onOpenNotifications: () -> Unit,
    isNetworkOnline: Boolean = false,
    connectionType: String = "Offline",
    isBackendConfigured: Boolean = false,
    onModeChanged: (UserMode) -> Unit = {},
    onThemeChanged: (ThemePreference) -> Unit = {},
    onResetRoomData: () -> Unit = {},
    signedInUser: FirebaseAuthSessionUser? = null,
    authenticatedProfile: AuthenticatedUserProfile? = null,
    onOpenPhoneAuth: () -> Unit = {},
    onSignOutPhoneAuth: () -> Unit = {}
) {
    val preferredMode by prefsRepo.preferredMode.collectAsState()
    val themePreference by prefsRepo.themePreference.collectAsState()
    val favoriteKeys by favoriteRepo.favoriteKeys.collectAsState()
    val drafts by prefsRepo.localDrafts.collectAsState()
    var showResetDialog by rememberSaveable { mutableStateOf(false) }

    if (showResetDialog) {
        DestructiveConfirmDialog(
            title = "Reset Local App Data?",
            message = "This clears ${favoriteKeys.size} saved favorite stand(s), ${drafts.size} local draft(s), Room persistence records, and resets your theme and default mode.",
            confirmLabel = "Reset Data",
            onConfirm = {
                favoriteRepo.clearAllFavorites()
                prefsRepo.resetAllPreferences()
                onResetRoomData()
                showResetDialog = false
            },
            onDismiss = { showResetDialog = false }
        )
    }

    ResponsiveContentColumn(modifier = Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            SectionHeader(
                title = "Profile & Preferences",
                subtitle = "Device-local settings • Authentication connects when HTTPS backend is configured"
            )

            // Account & Firebase Phone OTP Status Card
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = MaterialTheme.shapes.medium,
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
            ) {
                Column(
                    modifier = Modifier.fillMaxWidth().padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(14.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Person,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary
                        )
                        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Text(
                                text = authenticatedProfile?.phoneNumber
                                    ?: signedInUser?.phoneNumber?.takeIf { it.isNotBlank() }
                                    ?: "Mobile OTP Account",
                                style = MaterialTheme.typography.titleSmall
                            )
                            Text(
                                text = when {
                                    authenticatedProfile != null -> buildString {
                                        append("UID: ${authenticatedProfile.uid} • Role: ${authenticatedProfile.role.label}")
                                        if (authenticatedProfile.role == AuthUserRole.DRIVER &&
                                            authenticatedProfile.driverAutoRegNumber != null
                                        ) {
                                            append(" • Auto: ${AuthDomainValidator.formatAutoRegForDisplay(authenticatedProfile.driverAutoRegNumber)}")
                                        }
                                    }
                                    signedInUser != null -> "Firebase UID: ${signedInUser.uid} • Firestore sync pending"
                                    else -> "Network: ${if (isNetworkOnline) connectionType else "Offline"}. Verify your +91 mobile number via Firebase OTP."
                                },
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        StatusPill(
                            text = when {
                                authenticatedProfile != null -> authenticatedProfile.role.label
                                signedInUser != null -> "Verified"
                                isBackendConfigured && isNetworkOnline -> "Ready"
                                else -> "Signed Out"
                            },
                            tone = if (signedInUser != null || authenticatedProfile != null) {
                                BadgeTone.EMERALD
                            } else {
                                BadgeTone.NEUTRAL
                            }
                        )
                    }
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        OutlinedButton(
                            onClick = onOpenPhoneAuth,
                            modifier = Modifier.weight(1f).defaultMinSize(minHeight = 48.dp)
                        ) {
                            Text(if (signedInUser == null) "Sign In (+91 OTP)" else "Manage Account")
                        }
                        if (signedInUser != null) {
                            TextButton(
                                onClick = onSignOutPhoneAuth,
                                modifier = Modifier.defaultMinSize(minHeight = 48.dp)
                            ) {
                                Text("Sign Out", color = MaterialTheme.colorScheme.error)
                            }
                        }
                    }
                }
            }

            NotificationPermissionRationaleCard()

            // Default Mode Setting
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = MaterialTheme.shapes.medium,
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Text(
                        text = "Default Operating Mode",
                        style = MaterialTheme.typography.titleSmall,
                        modifier = Modifier.semantics { heading() }
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        UserMode.entries.forEach { mode ->
                            FilterChip(
                                selected = preferredMode == mode,
                                onClick = {
                                    prefsRepo.setPreferredMode(mode)
                                    onModeChanged(mode)
                                },
                                label = { Text(if (mode == UserMode.PASSENGER) "Passenger" else "Driver") },
                                modifier = Modifier.defaultMinSize(minHeight = 48.dp)
                            )
                        }
                    }

                    HorizontalDivider()

                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(Icons.Default.DarkMode, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                        Text(
                            text = "App Appearance",
                            style = MaterialTheme.typography.titleSmall,
                            modifier = Modifier.semantics { heading() }
                        )
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        ThemePreference.entries.forEach { pref ->
                            FilterChip(
                                selected = themePreference == pref,
                                onClick = {
                                    prefsRepo.setThemePreference(pref)
                                    onThemeChanged(pref)
                                },
                                label = {
                                    Text(
                                        when (pref) {
                                            ThemePreference.SYSTEM -> "System"
                                            ThemePreference.LIGHT -> "Light"
                                            ThemePreference.DARK -> "Dark"
                                        }
                                    )
                                },
                                modifier = Modifier.defaultMinSize(minHeight = 48.dp)
                            )
                        }
                    }
                }
            }

            // Compliance & Capacity Guard Status
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = MaterialTheme.shapes.medium,
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(Icons.Default.VerifiedUser, contentDescription = null, tint = MaterialTheme.colorScheme.secondary)
                        Text("Vehicle Capacity Compliance", style = MaterialTheme.typography.titleSmall)
                    }
                    Text(
                        text = "Configured Evaluation Max: 3 passengers • Legal Verification: Unverified (Blocked in BookingDomain until official RTO permit check).",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            // Shortcuts
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                OutlinedButton(
                    onClick = onOpenNotifications,
                    modifier = Modifier.weight(1f).defaultMinSize(minHeight = 48.dp)
                ) {
                    Text("Notifications")
                }
                OutlinedButton(
                    onClick = onOpenHelp,
                    modifier = Modifier.weight(1f).defaultMinSize(minHeight = 48.dp)
                ) {
                    Text("Help & About")
                }
            }

            OutlinedButton(
                onClick = onReplayOnboarding,
                modifier = Modifier.fillMaxWidth().defaultMinSize(minHeight = 48.dp)
            ) {
                Icon(Icons.Default.Refresh, contentDescription = null)
                Spacer(modifier = Modifier.width(8.dp))
                Text("Replay Onboarding Walkthrough")
            }

            OutlinedButton(
                onClick = { showResetDialog = true },
                modifier = Modifier.fillMaxWidth().defaultMinSize(minHeight = 48.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.DeleteOutline,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.error
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text("Reset Local Favorites & Preferences", color = MaterialTheme.colorScheme.error)
            }
        }
    }
}

private data class FaqEntry(
    val question: String,
    val answer: String
)

/**
 * Screen 15: Help and About
 * Transparent FAQ, regulatory disclaimers, and build metadata.
 */
@Composable
fun HelpAboutScreen() {
    val faqs = listOf(
        FaqEntry(
            question = "Why is driver fare quoted as Total Fare per Booking instead of per seat?",
            answer = "Version 1 enforces total-booking transparency: drivers state one total fare for the booking party so there are never surprise per-seat fare multiplications. Passengers manage any private split offline."
        ),
        FaqEntry(
            question = "Why can't two separate passenger parties be merged?",
            answer = "BookingDomainCoordinator.canAutomaticallyMergeParties permanently returns false in Version 1 to protect agreed pickup points, total fare clarity, and party privacy."
        ),
        FaqEntry(
            question = "Why do route forms report unverified vehicle capacity?",
            answer = "Maharashtra/Mumbai auto capacity regulations require official RTO verification. The app deliberately refuses to fake legal approval and blocks commercial clearance until verified."
        ),
        FaqEntry(
            question = "Are the station stand coordinates and travel times guaranteed?",
            answer = "No. MumbaiStationDirectory entries are unverified reference orientation data only. Always confirm pickup landmarks directly at the station."
        ),
        FaqEntry(
            question = "Where are my Favorite Stands stored?",
            answer = "Favorites, preferences, local route records, and local system notices are stored on your device using Android Room SQLite (version 2) with one-time migration from SharedPreferences — no fake server driver availability is stored."
        )
    )

    ResponsiveContentColumn(modifier = Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            SectionHeader(
                title = "Help, FAQ & About",
                subtitle = "Mumbai Share Auto • Native Android"
            )

            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = MaterialTheme.shapes.medium,
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(Icons.Default.Info, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                        Text("Application Identity", style = MaterialTheme.typography.titleSmall)
                    }
                    Text(
                        text = "Package: com.mumbaishareauto.app\nTarget SDK: Android API 36 (minSdk 26)\nPersistence: Room SQLite v2 + Coroutines/StateFlow\nStack: Kotlin + Jetpack Compose + Material 3",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            SectionHeader(title = "Frequently Asked Questions")

            faqs.forEachIndexed { idx, faq ->
                var expanded by rememberSaveable { mutableStateOf(idx == 0) }
                Card(
                    onClick = { expanded = !expanded },
                    modifier = Modifier.fillMaxWidth(),
                    shape = MaterialTheme.shapes.medium,
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = faq.question,
                                style = MaterialTheme.typography.titleSmall,
                                modifier = Modifier.weight(1f)
                            )
                            Icon(
                                imageVector = if (expanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                                contentDescription = if (expanded) "Collapse answer" else "Expand answer"
                            )
                        }
                        if (expanded) {
                            Text(
                                text = faq.answer,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }

            LockedRulesNoticeCard()

            UnavailableServiceBanner(
                title = "Phase 2 Roadmap Services",
                message = "User authentication, real-time driver matching, live stand availability, and push notifications connect once an HTTPS production backend is configured."
            )
        }
    }
}
