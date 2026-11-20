package com.mumbaishareauto.app.ui.share

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import com.mumbaishareauto.app.data.MumbaiStationDirectory
import com.mumbaishareauto.app.model.StationStandRoute

/**
 * Utility functions for sharing stand details and launching map locations.
 * Handles unavailable coordinates/metrics honestly without fabricating map pins or distances.
 */
object ShareUtils {

    fun openStandOnMap(context: Context, route: StationStandRoute) {
        if (!route.hasAvailableCoordinates) {
            Toast.makeText(
                context,
                MumbaiStationDirectory.UNAVAILABLE_LOCATION_MESSAGE,
                Toast.LENGTH_LONG
            ).show()
            return
        }

        val lat = route.standLatitude!!
        val lng = route.standLongitude!!
        val label = "${route.stationName} ${route.side.label} (${route.standName})"
        try {
            val uri = Uri.parse("geo:$lat,$lng?q=$lat,$lng(" + Uri.encode(label) + ")")
            val mapIntent = Intent(Intent.ACTION_VIEW, uri).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK
            }
            context.startActivity(mapIntent)
        } catch (_: Exception) {
            val browserUri = Uri.parse("https://www.google.com/maps/search/?api=1&query=$lat,$lng")
            try {
                context.startActivity(
                    Intent(Intent.ACTION_VIEW, browserUri).apply {
                        flags = Intent.FLAG_ACTIVITY_NEW_TASK
                    }
                )
            } catch (_: Exception) {
                Toast.makeText(context, "Google Maps is not available on this device", Toast.LENGTH_SHORT).show()
            }
        }
    }

    fun formatShareStandMessage(route: StationStandRoute): String = buildString {
        appendLine("Mumbai Share Auto — Designated Station Stand (Unverified Reference)")
        appendLine("Station: ${route.stationName} (${route.side.label} Exit) • ${route.line.displayName}")
        appendLine("Stand: ${route.standName}")
        appendLine("Destination: ${route.destination}")
        appendLine("Landmark: ${route.standLandmark}")
        if (route.viaPoints.isNotEmpty()) {
            appendLine("Via Points: ${route.viaPoints.joinToString(" -> ")}")
        }
        appendLine("Reference Estimate: ${route.formattedDistanceAndTimeOrUnavailable}")
        if (route.hasAvailableCoordinates) {
            appendLine("Unverified Map Pin: https://maps.google.com/?q=${route.standLatitude},${route.standLongitude}")
        } else {
            appendLine("Map Location: Currently unavailable")
        }
        append("Note: Actual fares are posted per complete booking by drivers (not per seat).")
    }

    fun shareStandDetails(context: Context, route: StationStandRoute) {
        val shareMessage = formatShareStandMessage(route)
        val sendIntent = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_SUBJECT, "Mumbai Share Auto Stand - ${route.stationName} (${route.side.label})")
            putExtra(Intent.EXTRA_TEXT, shareMessage)
            flags = Intent.FLAG_ACTIVITY_NEW_TASK
        }
        try {
            context.startActivity(Intent.createChooser(sendIntent, "Share Stand Info"))
        } catch (_: Exception) {
            Toast.makeText(context, "Unable to share stand info", Toast.LENGTH_SHORT).show()
        }
    }
}
