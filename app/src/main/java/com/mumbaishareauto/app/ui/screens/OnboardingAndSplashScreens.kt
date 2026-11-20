package com.mumbaishareauto.app.ui.screens

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
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.CloudOff
import androidx.compose.material.icons.filled.DirectionsCar
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.VerifiedUser
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.mumbaishareauto.app.ui.components.BadgeTone
import com.mumbaishareauto.app.ui.components.ResponsiveContentColumn
import com.mumbaishareauto.app.ui.components.StatusPill
import com.mumbaishareauto.app.ui.theme.MumbaiColors
import kotlinx.coroutines.delay

/**
 * Screen 1: Splash Screen
 * Brief restrained launch screen initializing local directory & preferences.
 */
@Composable
fun SplashScreen(
    routeCount: Int,
    onSplashFinished: () -> Unit
) {
    LaunchedEffect(Unit) {
        delay(900L)
        onSplashFinished()
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MumbaiColors.DeepNavy)
            .padding(24.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Surface(
                color = MumbaiColors.AutoYellow,
                shape = RoundedCornerShape(22.dp),
                modifier = Modifier.size(80.dp)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = Icons.Default.DirectionsCar,
                        contentDescription = null,
                        tint = MumbaiColors.DeepNavy,
                        modifier = Modifier.size(42.dp)
                    )
                }
            }

            Text(
                text = "Mumbai Share Auto",
                style = MaterialTheme.typography.headlineMedium,
                color = MumbaiColors.LightSurface,
                modifier = Modifier.semantics { heading() }
            )

            Text(
                text = "Designated Station Stands & Total-Fare Coordination",
                style = MaterialTheme.typography.bodyMedium,
                color = MumbaiColors.AmberContainerLight
            )

            Spacer(modifier = Modifier.height(8.dp))

            StatusPill(
                text = "$routeCount Reference Stands Loaded Offline",
                tone = BadgeTone.AMBER
            )

            LinearProgressIndicator(
                modifier = Modifier.width(180.dp),
                color = MumbaiColors.AutoYellow,
                trackColor = MumbaiColors.DeepNavyLight
            )
        }
    }
}

private data class OnboardingSlide(
    val badge: String,
    val title: String,
    val body: String,
    val icon: ImageVector,
    val points: List<String>
)

/**
 * Screen 2: Onboarding
 * Introduces Version 1 rules transparently without claiming unavailable live services.
 */
@Composable
fun OnboardingScreen(
    onCompleteOnboarding: () -> Unit
) {
    var stepIndex by rememberSaveable { mutableIntStateOf(0) }
    val slides = listOf(
        OnboardingSlide(
            badge = "Rule 1 of 3 • Pricing Integrity",
            title = "Total Fare per Booking, Never per Seat",
            body = "Drivers post one transparent total fare for the booking. One booking represents your single passenger party — separate parties are never automatically merged.",
            icon = Icons.Default.CheckCircle,
            points = listOf(
                "No hidden per-seat multipliers",
                "Passengers handle any private split offline",
                "Fare locks only after Driver Accept + Passenger Confirm"
            )
        ),
        OnboardingSlide(
            badge = "Rule 2 of 3 • Designated Stands",
            title = "Station Stand Orientation Reference",
            body = "Browse designated auto stand landmarks across Western, Central, and Harbour railway stations, save frequent stands locally, and open coordinates in Maps.",
            icon = Icons.Default.LocationOn,
            points = listOf(
                "Driver waits at the designated station pickup stand",
                "Reference times & coordinates are orientation guides only",
                "Local favorites saved directly on your device"
            )
        ),
        OnboardingSlide(
            badge = "Rule 3 of 3 • Honest Phase 1 Scope",
            title = "Transparent Offline Validation & Safety",
            body = "Live driver matching, backend sync, and payments are not yet active. Capacity limits enforce central domain rules and require official RTO legal verification.",
            icon = Icons.Default.CloudOff,
            points = listOf(
                "Zero fabricated live drivers or mock bookings",
                "Central domain validation cannot be bypassed by UI",
                "No unnecessary location or contacts permissions"
            )
        )
    )

    val slide = slides[stepIndex]

    ResponsiveContentColumn(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(20.dp),
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(20.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    StatusPill(text = slide.badge, tone = BadgeTone.NAVY)
                    TextButton(
                        onClick = onCompleteOnboarding,
                        modifier = Modifier.defaultMinSize(minHeight = 48.dp)
                    ) {
                        Text("Skip")
                    }
                }

                Surface(
                    color = MaterialTheme.colorScheme.secondaryContainer,
                    shape = RoundedCornerShape(20.dp),
                    modifier = Modifier.size(68.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = slide.icon,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSecondaryContainer,
                            modifier = Modifier.size(34.dp)
                        )
                    }
                }

                Text(
                    text = slide.title,
                    style = MaterialTheme.typography.headlineMedium,
                    color = MaterialTheme.colorScheme.onBackground,
                    modifier = Modifier.semantics { heading() }
                )

                Text(
                    text = slide.body,
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

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
                        slide.points.forEach { point ->
                            Row(
                                horizontalArrangement = Arrangement.spacedBy(10.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    imageVector = Icons.Default.VerifiedUser,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.tertiary,
                                    modifier = Modifier.size(18.dp)
                                )
                                Text(
                                    text = point,
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                            }
                        }
                    }
                }
            }

            Column(
                modifier = Modifier.fillMaxWidth().padding(top = 24.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                LinearProgressIndicator(
                    progress = { (stepIndex + 1).toFloat() / slides.size.toFloat() },
                    modifier = Modifier.fillMaxWidth()
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    if (stepIndex > 0) {
                        OutlinedButton(
                            onClick = { stepIndex -= 1 },
                            modifier = Modifier
                                .weight(1f)
                                .defaultMinSize(minHeight = 52.dp)
                        ) {
                            Text("Previous")
                        }
                    }

                    Button(
                        onClick = {
                            if (stepIndex < slides.lastIndex) {
                                stepIndex += 1
                            } else {
                                onCompleteOnboarding()
                            }
                        },
                        modifier = Modifier
                            .weight(1f)
                            .defaultMinSize(minHeight = 52.dp)
                    ) {
                        Text(if (stepIndex < slides.lastIndex) "Continue" else "Enter Home Dashboard")
                    }
                }
            }
        }
    }
}
