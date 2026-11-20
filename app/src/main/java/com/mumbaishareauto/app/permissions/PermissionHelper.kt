package com.mumbaishareauto.app.permissions

import android.Manifest
import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.NotificationsActive
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.LifecycleOwner
import com.mumbaishareauto.app.ui.components.BadgeTone
import com.mumbaishareauto.app.ui.components.StatusPill

/**
 * Android 13+ Notification Permission State & Rationale Component.
 *
 * Enforces:
 * - Request permissions ONLY when genuine booking updates require them.
 * - Explains why the notification permission is needed before prompting.
 * - Graceful denial handling with direct App Settings navigation guidance.
 * - Live ON_RESUME permission refresh when returning from system Settings.
 * - Zero unnecessary location, contacts, microphone, or background tracking permissions.
 */
object PermissionHelper {
    fun hasNotificationPermission(context: Context): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.POST_NOTIFICATIONS
            ) == PackageManager.PERMISSION_GRANTED
        } else {
            true
        }
    }
}

private tailrec fun Context.findLifecycleOwner(): LifecycleOwner? = when (this) {
    is LifecycleOwner -> this
    is ContextWrapper -> baseContext.findLifecycleOwner()
    else -> null
}

@Composable
fun rememberNotificationPermissionGranted(): Boolean {
    val context = LocalContext.current
    val lifecycleOwner = remember(context) { context.findLifecycleOwner() }
    var isGranted by remember { mutableStateOf(PermissionHelper.hasNotificationPermission(context)) }

    DisposableEffect(lifecycleOwner, context) {
        isGranted = PermissionHelper.hasNotificationPermission(context)
        if (lifecycleOwner == null) {
            onDispose {}
        } else {
            val observer = LifecycleEventObserver { _, event ->
                if (event == Lifecycle.Event.ON_RESUME) {
                    isGranted = PermissionHelper.hasNotificationPermission(context)
                }
            }
            lifecycleOwner.lifecycle.addObserver(observer)
            onDispose {
                lifecycleOwner.lifecycle.removeObserver(observer)
            }
        }
    }
    return isGranted
}

@Composable
fun NotificationPermissionRationaleCard(
    onPermissionGranted: () -> Unit = {}
) {
    val context = LocalContext.current
    val systemGranted = rememberNotificationPermissionGranted()
    var localGranted by rememberSaveable { mutableStateOf(systemGranted) }
    var hasDeclinedOnce by rememberSaveable { mutableStateOf(false) }

    val launcher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        localGranted = granted
        if (granted) {
            hasDeclinedOnce = false
            onPermissionGranted()
        } else {
            hasDeclinedOnce = true
        }
    }

    if (systemGranted || localGranted) {
        return
    }

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
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Default.NotificationsActive,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary
                    )
                    Text(
                        text = "Enable Booking Alerts",
                        style = MaterialTheme.typography.titleSmall
                    )
                }
                StatusPill(text = "Recommended", tone = BadgeTone.AMBER)
            }

            Text(
                text = "Mumbai Share Auto requests notification permission ONLY to alert you when a driver accepts your booking request or when booking status updates. We do not track location or contacts.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End,
                verticalAlignment = Alignment.CenterVertically
            ) {
                OutlinedButton(
                    onClick = {
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                            launcher.launch(Manifest.permission.POST_NOTIFICATIONS)
                        } else {
                            localGranted = true
                            onPermissionGranted()
                        }
                    },
                    modifier = Modifier.defaultMinSize(minHeight = 48.dp)
                ) {
                    Text(if (hasDeclinedOnce) "Try Again" else "Allow Notifications")
                }
            }
        }
    }

    if (hasDeclinedOnce) {
        PermissionDeniedSettingsCard()
    }
}

@Composable
fun PermissionDeniedSettingsCard() {
    val context = LocalContext.current
    val isGranted = rememberNotificationPermissionGranted()

    if (isGranted) return

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.medium,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.error)
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(
                text = "Notification Permission Disabled",
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onErrorContainer
            )
            Text(
                text = "Booking lifecycle notifications cannot be posted until permission is granted. You can enable notifications anytime in Android App Settings.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onErrorContainer
            )
            OutlinedButton(
                onClick = {
                    val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                        data = Uri.fromParts("package", context.packageName, null)
                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    }
                    context.startActivity(intent)
                },
                modifier = Modifier.defaultMinSize(minHeight = 48.dp)
            ) {
                Text("Open App Settings", color = MaterialTheme.colorScheme.onErrorContainer)
            }
        }
    }
}
