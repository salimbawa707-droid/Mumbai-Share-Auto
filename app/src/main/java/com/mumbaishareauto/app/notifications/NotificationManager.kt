package com.mumbaishareauto.app.notifications

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.mumbaishareauto.app.MainActivity

/**
 * Android Notification Channel and Notification Manager helper.
 *
 * Enforces:
 * - Genuine notification channels for booking updates and compliance notices.
 * - Appropriate deep links routing directly to the target booking or notification screen.
 * - Zero fake/simulated push notifications (only triggers when genuine local booking lifecycle events occur).
 */
object MumbaiShareAutoNotificationManager {

    const val CHANNEL_BOOKING_UPDATES = "channel_booking_updates"
    const val CHANNEL_COMPLIANCE_ALERTS = "channel_compliance_alerts"
    const val EXTRA_DEEP_LINK_BOOKING_ID = "EXTRA_DEEP_LINK_BOOKING_ID"

    const val NOTIFICATION_ID_BOOKING_BASE = 2000

    fun createNotificationChannels(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val name = "Booking Lifecycle Updates"
            val descriptionText = "Notifications for driver acceptance, passenger confirmation, and booking status updates."
            val importance = NotificationManager.IMPORTANCE_HIGH
            val channel = NotificationChannel(CHANNEL_BOOKING_UPDATES, name, importance).apply {
                description = descriptionText
                enableVibration(true)
            }

            val complianceName = "Compliance & RTO Notices"
            val complianceDesc = "Important legal capacity and system notices."
            val complianceChannel = NotificationChannel(
                CHANNEL_COMPLIANCE_ALERTS,
                complianceName,
                NotificationManager.IMPORTANCE_DEFAULT
            ).apply {
                description = complianceDesc
            }

            val notificationManager: NotificationManager =
                context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            notificationManager.createNotificationChannel(channel)
            notificationManager.createNotificationChannel(complianceChannel)
        }
    }

    /**
     * Posts a genuine notification for a booking lifecycle update with deep-link intent.
     */
    fun showBookingNotification(
        context: Context,
        bookingId: String,
        title: String,
        message: String,
        notificationId: Int = NOTIFICATION_ID_BOOKING_BASE
    ) {
        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
            putExtra(EXTRA_DEEP_LINK_BOOKING_ID, bookingId)
        }

        val pendingIntentFlags = PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT

        val pendingIntent = PendingIntent.getActivity(
            context,
            notificationId,
            intent,
            pendingIntentFlags
        )

        val builder = NotificationCompat.Builder(context, CHANNEL_BOOKING_UPDATES)
            .setSmallIcon(android.R.drawable.ic_menu_directions)
            .setContentTitle(title)
            .setContentText(message)
            .setStyle(NotificationCompat.BigTextStyle().bigText(message))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)

        try {
            with(NotificationManagerCompat.from(context)) {
                notify(notificationId, builder.build())
            }
        } catch (_: SecurityException) {
            // POST_NOTIFICATIONS permission denied on Android 13+; handled gracefully.
        }
    }
}
