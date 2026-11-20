package com.mumbaishareauto.app.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.mumbaishareauto.app.FareNegotiationSession
import com.mumbaishareauto.app.auth.AuthUserRole
import com.mumbaishareauto.app.ui.FareNegotiationUiState
import com.mumbaishareauto.app.ui.MumbaiShareAutoViewModel
import com.mumbaishareauto.app.ui.PhoneAuthUiState

@Composable
fun FareNegotiationPanel(
    viewModel: MumbaiShareAutoViewModel,
    authState: PhoneAuthUiState,
    state: FareNegotiationUiState,
    modifier: Modifier = Modifier
) {
    var routeId by rememberSaveable { mutableStateOf("") }
    var passengerCount by rememberSaveable { mutableStateOf("1") }
    var pickup by rememberSaveable { mutableStateOf("") }
    var destination by rememberSaveable { mutableStateOf("") }
    var fare by rememberSaveable { mutableStateOf("") }
    var counterFare by rememberSaveable { mutableStateOf("") }

    Card(modifier = modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp)) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(9.dp)
        ) {
            Text("Live Group Fare Negotiation", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            Text("Passenger enters one total fare for the complete group (1–3 passengers). Driver is the separate fourth seat.", style = MaterialTheme.typography.bodySmall)
            if (authState.signedInUser == null || authState.authenticatedProfile == null) {
                Text("Sign in and complete your Passenger or Driver profile to use live fare negotiation.", color = MaterialTheme.colorScheme.error)
            } else if (authState.authenticatedProfile.role == AuthUserRole.PASSENGER) {
                OutlinedTextField(routeId, { routeId = it.trim() }, label = { Text("Driver live route ID") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(passengerCount, { passengerCount = it.filter(Char::isDigit).take(1) }, label = { Text("Passengers (1–3)") }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), singleLine = true, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(pickup, { pickup = it }, label = { Text("Driver pickup stand") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(destination, { destination = it }, label = { Text("Destination") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(fare, { fare = it.filter { ch -> ch.isDigit() || ch == '.' }.take(9) }, label = { Text("Total group fare (₹)") }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal), singleLine = true, modifier = Modifier.fillMaxWidth())
                Button(
                    onClick = { viewModel.createPassengerFareOffer(routeId, passengerCount.toIntOrNull() ?: 0, pickup, destination, fare.toDoubleOrNull() ?: 0.0) },
                    enabled = !state.isLoading && routeId.isNotBlank() && passengerCount.toIntOrNull()?.let { it in 1..3 } == true && pickup.length >= 3 && destination.length >= 3 && fare.toDoubleOrNull()?.let { it > 0 } == true,
                    modifier = Modifier.fillMaxWidth()
                ) { Text("Send Total Fare Offer") }
            }
            state.message?.let { Text(it, color = MaterialTheme.colorScheme.primary) }
            state.error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
            if (state.isLoading) Text("Updating negotiation…")
            Text(if (authState.authenticatedProfile?.role == AuthUserRole.DRIVER) "Incoming passenger offers" else "Your fare negotiations", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
            if (state.sessions.isEmpty()) Text("No live fare offers yet.", style = MaterialTheme.typography.bodySmall)
            state.sessions.take(3).forEach { session ->
                FareNegotiationSessionCard(
                    session = session,
                    role = authState.authenticatedProfile?.role,
                    counterFare = counterFare,
                    onCounterFareChange = { counterFare = it },
                    onOpen = { viewModel.observeFareNegotiation(session.bookingId) },
                    onDriverAccept = { viewModel.driverAcceptFare(session.bookingId) },
                    onDriverDecline = { viewModel.driverDeclineFare(session.bookingId) },
                    onDriverCounter = { viewModel.driverCounterFare(session.bookingId, counterFare.toDoubleOrNull() ?: 0.0) },
                    onPassengerAccept = { viewModel.passengerAcceptCounter(session.bookingId) },
                    onPassengerDecline = { viewModel.passengerDeclineCounter(session.bookingId) },
                    onCancel = { viewModel.cancelFareNegotiation(session.bookingId) }
                )
            }
            state.activeSession?.let { session ->
                Text("Selected booking: ${session.bookingId} • ${session.status}", style = MaterialTheme.typography.bodySmall)
                Text("Agreed total: ${session.agreedTotalFareInr?.let { "₹%.2f".format(it) } ?: "Not agreed"}", fontWeight = FontWeight.Bold)
            }
        }
    }
}

@Composable
private fun FareNegotiationSessionCard(
    session: FareNegotiationSession,
    role: AuthUserRole?,
    counterFare: String,
    onCounterFareChange: (String) -> Unit,
    onOpen: () -> Unit,
    onDriverAccept: () -> Unit,
    onDriverDecline: () -> Unit,
    onDriverCounter: () -> Unit,
    onPassengerAccept: () -> Unit,
    onPassengerDecline: () -> Unit,
    onCancel: () -> Unit
) {
    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)) {
        Column(modifier = Modifier.fillMaxWidth().padding(10.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text("${session.pickupStand} → ${session.destination}", fontWeight = FontWeight.SemiBold)
            Text("${session.passengerCount} passenger(s) • Whole-group offer: ₹${session.currentProposedTotalFareInr ?: 0.0} • ${session.status}")
            Text("Passenger and driver both see the same agreed total fare.", style = MaterialTheme.typography.bodySmall)
            OutlinedButton(onClick = onOpen, modifier = Modifier.fillMaxWidth()) { Text("Open live negotiation") }
            if (role == AuthUserRole.DRIVER && session.status.name == "OPEN") {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(onClick = onDriverAccept, modifier = Modifier.weight(1f)) { Text("Accept") }
                    OutlinedButton(onClick = onDriverDecline, modifier = Modifier.weight(1f)) { Text("Decline") }
                }
                OutlinedTextField(counterFare, onCounterFareChange, label = { Text("Counter total fare ₹") }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal), singleLine = true, modifier = Modifier.fillMaxWidth())
                Button(onClick = onDriverCounter, enabled = counterFare.toDoubleOrNull()?.let { it > 0 } == true, modifier = Modifier.fillMaxWidth()) { Text("Send Counter Offer") }
            }
            if (role == AuthUserRole.PASSENGER && session.status.name == "COUNTERED") {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(onClick = onPassengerAccept, modifier = Modifier.weight(1f)) { Text("Accept Counter") }
                    OutlinedButton(onClick = onPassengerDecline, modifier = Modifier.weight(1f)) { Text("Decline") }
                }
            }
            if (role == AuthUserRole.PASSENGER && session.status.name == "AGREED" && session.agreedTotalFareInr != null) {
                Text("Driver accepted. Final group fare: ₹${session.agreedTotalFareInr}", fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                Button(
                    onClick = { viewModel.lockSeatOnLiveRoute(session.routeId, session.passengerCount, session.agreedTotalFareInr) },
                    enabled = !state.isLoading,
                    modifier = Modifier.fillMaxWidth()
                ) { Text("Confirm & Reserve ${session.passengerCount} Seat(s)") }
            }
            if (session.status.name == "OPEN" || session.status.name == "COUNTERED") {
                OutlinedButton(onClick = onCancel, modifier = Modifier.fillMaxWidth()) { Text("Cancel Offer") }
            }
        }
    }
}
