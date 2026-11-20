package com.mumbaishareauto.app.data.remote

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import com.mumbaishareauto.app.BuildConfig
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.BufferedReader
import java.io.InputStreamReader
import java.io.OutputStreamWriter
import java.net.HttpURLConnection
import java.net.URL
import java.nio.charset.StandardCharsets
import javax.net.ssl.HttpsURLConnection

/**
 * Production Android connectivity monitor backed by system [ConnectivityManager].
 */
class AndroidNetworkConnectivityManager(
    context: Context
) : NetworkConnectivityManager {

    private val connectivityManager =
        context.applicationContext.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager

    private val _isConnected = MutableStateFlow(computeConnected())
    override val isConnected: StateFlow<Boolean> = _isConnected.asStateFlow()

    private val _connectionType = MutableStateFlow(computeConnectionType())
    override val connectionType: StateFlow<String> = _connectionType.asStateFlow()

    private var callbackRegistered = false

    private val networkCallback = object : ConnectivityManager.NetworkCallback() {
        override fun onAvailable(network: Network) {
            refreshState()
        }

        override fun onLost(network: Network) {
            refreshState()
        }

        override fun onCapabilitiesChanged(network: Network, networkCapabilities: NetworkCapabilities) {
            refreshState()
        }
    }

    init {
        registerCallbackSafely()
    }

    private fun registerCallbackSafely() {
        val cm = connectivityManager ?: return
        try {
            val request = NetworkRequest.Builder()
                .addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
                .build()
            cm.registerNetworkCallback(request, networkCallback)
            callbackRegistered = true
        } catch (_: SecurityException) {
            callbackRegistered = false
        } catch (_: RuntimeException) {
            callbackRegistered = false
        }
    }

    fun unregister() {
        val cm = connectivityManager ?: return
        if (callbackRegistered) {
            try {
                cm.unregisterNetworkCallback(networkCallback)
            } catch (_: IllegalArgumentException) {
                // Already unregistered
            } finally {
                callbackRegistered = false
            }
        }
    }

    override suspend fun checkConnectivityNow(): Boolean {
        refreshState()
        return _isConnected.value
    }

    private fun refreshState() {
        _isConnected.value = computeConnected()
        _connectionType.value = computeConnectionType()
    }

    private fun computeConnected(): Boolean {
        val cm = connectivityManager ?: return false
        return try {
            val network = cm.activeNetwork ?: return false
            val caps = cm.getNetworkCapabilities(network) ?: return false
            caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) &&
                caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)
        } catch (_: SecurityException) {
            false
        }
    }

    private fun computeConnectionType(): String {
        val cm = connectivityManager ?: return "Unavailable"
        return try {
            val network = cm.activeNetwork ?: return "Offline"
            val caps = cm.getNetworkCapabilities(network) ?: return "Offline"
            when {
                caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) -> "Wi-Fi"
                caps.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) -> "Cellular"
                caps.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET) -> "Ethernet"
                caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) -> "Connected"
                else -> "Offline"
            }
        } catch (_: SecurityException) {
            "Offline"
        }
    }
}

/**
 * Real HTTPS REST client for Mumbai Share Auto backend endpoints.
 *
 * Security & honesty invariants:
 * - Zero fake JWTs, fake drivers, or fabricated `isLegallyVerified = true` payloads.
 * - Returns [NetworkResult.NetworkUnavailable] when [baseUrl] is blank / unconfigured.
 * - Rejects cleartext `http://` URLs before any network socket is opened.
 */
class HttpUrlConnectionMumbaiShareAutoApiService(
    private val baseUrl: String = BuildConfig.API_BASE_URL,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
    private val connectTimeoutMs: Int = 10_000,
    private val readTimeoutMs: Int = 15_000
) : MumbaiShareAutoApiService {

    val isBackendConfigured: Boolean
        get() = baseUrl.trim().isNotEmpty()

    override suspend fun registerUser(request: RegisterRequestDto): NetworkResult<AuthResponseDto> {
        val body = JSONObject().apply {
            put("email", request.email.trim())
            put("passwordHash", request.passwordHash)
            put("fullName", request.fullName.trim())
            put("role", request.role.trim().uppercase())
        }
        return executeJsonRequest(
            method = "POST",
            path = "/api/v1/auth/register",
            authToken = null,
            requestBody = body.toString(),
            parser = { jsonText -> parseAuthResponse(JSONObject(jsonText)) }
        )
    }

    override suspend fun authenticateUser(request: AuthRequestDto): NetworkResult<AuthResponseDto> {
        val body = JSONObject().apply {
            put("email", request.email.trim())
            put("passwordHash", request.passwordHash)
        }
        return executeJsonRequest(
            method = "POST",
            path = "/api/v1/auth/login",
            authToken = null,
            requestBody = body.toString(),
            parser = { jsonText -> parseAuthResponse(JSONObject(jsonText)) }
        )
    }

    override suspend fun getDriverProfile(authToken: String, driverId: String): NetworkResult<DriverProfileDto> {
        if (authToken.isBlank()) {
            return NetworkResult.Error("Bearer authentication token is required.", 401)
        }
        if (driverId.isBlank()) {
            return NetworkResult.Error("Driver ID cannot be blank.", 400)
        }
        return executeJsonRequest(
            method = "GET",
            path = "/api/v1/drivers/${driverId.trim()}",
            authToken = authToken,
            requestBody = null,
            parser = { jsonText ->
                val obj = JSONObject(jsonText)
                DriverProfileDto(
                    driverId = obj.getString("driverId"),
                    licenseNumber = obj.optString("licenseNumber", ""),
                    vehicleRegistration = obj.optString("vehicleRegistration", ""),
                    maxPermittedPassengers = obj.optInt("maxPermittedPassengers", 0),
                    isLegallyVerified = obj.optBoolean("isLegallyVerified", false),
                    permitExpiryEpochMs = obj.optLong("permitExpiryEpochMs", 0L)
                )
            }
        )
    }

    override suspend fun postDriverRoute(
        authToken: String,
        request: RoutePostingRequestDto
    ): NetworkResult<RoutePostingResponseDto> {
        if (authToken.isBlank()) {
            return NetworkResult.Error("Bearer authentication token is required.", 401)
        }
        val body = JSONObject().apply {
            put("driverId", request.driverId)
            put("designatedPickupStand", request.designatedPickupStand)
            put("destinationStand", request.destinationStand)
            put("viaPointsSummary", request.viaPointsSummary)
            put("totalBookingFare", request.totalBookingFare)
            put("availableSeats", request.availableSeats)
            put("maxPermittedPassengers", request.maxPermittedPassengers)
            put("isLegallyVerified", request.isLegallyVerified)
        }
        return executeJsonRequest(
            method = "POST",
            path = "/api/v1/routes/post",
            authToken = authToken,
            requestBody = body.toString(),
            parser = { jsonText -> parseRoutePosting(JSONObject(jsonText)) }
        )
    }

    override suspend fun searchActiveRoutes(
        authToken: String,
        query: RouteSearchQueryDto
    ): NetworkResult<List<RoutePostingResponseDto>> {
        if (authToken.isBlank()) {
            return NetworkResult.Error("Bearer authentication token is required.", 401)
        }
        val body = JSONObject().apply {
            put("pickupQuery", query.pickupQuery)
            put("destinationQuery", query.destinationQuery)
            put("railwayLine", query.railwayLine)
            put("eastWestFilter", query.eastWestFilter)
        }
        return executeJsonRequest(
            method = "POST",
            path = "/api/v1/routes/search",
            authToken = authToken,
            requestBody = body.toString(),
            parser = { jsonText ->
                val trimmed = jsonText.trim()
                val arr = if (trimmed.startsWith("[")) {
                    JSONArray(trimmed)
                } else {
                    JSONObject(trimmed).optJSONArray("routes") ?: JSONArray()
                }
                buildList {
                    for (i in 0 until arr.length()) {
                        add(parseRoutePosting(arr.getJSONObject(i)))
                    }
                }
            }
        )
    }

    override suspend fun createBookingRequest(
        authToken: String,
        request: BookingRequestDto
    ): NetworkResult<BookingResponseDto> {
        if (authToken.isBlank()) {
            return NetworkResult.Error("Bearer authentication token is required.", 401)
        }
        val body = JSONObject().apply {
            put("postingId", request.postingId)
            put("passengerPartyId", request.passengerPartyId)
            put("passengerCount", request.passengerCount)
            put("contactRef", request.contactRef)
            put("proposedTotalFare", request.proposedTotalFare)
        }
        return executeJsonRequest(
            method = "POST",
            path = "/api/v1/bookings/request",
            authToken = authToken,
            requestBody = body.toString(),
            parser = { jsonText -> parseBookingResponse(JSONObject(jsonText)) }
        )
    }

    override suspend fun transitionBookingState(
        authToken: String,
        request: StateTransitionRequestDto
    ): NetworkResult<BookingResponseDto> {
        if (authToken.isBlank()) {
            return NetworkResult.Error("Bearer authentication token is required.", 401)
        }
        val body = JSONObject().apply {
            put("bookingId", request.bookingId)
            put("targetState", request.targetState)
            put("reason", request.reason)
            put("clientTimestampMs", request.clientTimestampMs)
        }
        return executeJsonRequest(
            method = "POST",
            path = "/api/v1/bookings/${request.bookingId.trim()}/transition",
            authToken = authToken,
            requestBody = body.toString(),
            parser = { jsonText -> parseBookingResponse(JSONObject(jsonText)) }
        )
    }

    override suspend fun getBookingStatus(
        authToken: String,
        bookingId: String
    ): NetworkResult<BookingResponseDto> {
        if (authToken.isBlank()) {
            return NetworkResult.Error("Bearer authentication token is required.", 401)
        }
        return executeJsonRequest(
            method = "GET",
            path = "/api/v1/bookings/${bookingId.trim()}",
            authToken = authToken,
            requestBody = null,
            parser = { jsonText -> parseBookingResponse(JSONObject(jsonText)) }
        )
    }

    override suspend fun getNotifications(authToken: String): NetworkResult<List<NotificationResponseDto>> {
        if (authToken.isBlank()) {
            return NetworkResult.Error("Bearer authentication token is required.", 401)
        }
        return executeJsonRequest(
            method = "GET",
            path = "/api/v1/notifications",
            authToken = authToken,
            requestBody = null,
            parser = { jsonText ->
                val trimmed = jsonText.trim()
                val arr = if (trimmed.startsWith("[")) {
                    JSONArray(trimmed)
                } else {
                    JSONObject(trimmed).optJSONArray("notifications") ?: JSONArray()
                }
                buildList {
                    for (i in 0 until arr.length()) {
                        val obj = arr.getJSONObject(i)
                        add(
                            NotificationResponseDto(
                                notificationId = obj.getString("notificationId"),
                                category = obj.optString("category", "SYSTEM"),
                                title = obj.getString("title"),
                                message = obj.getString("message"),
                                isRead = obj.optBoolean("isRead", false),
                                createdAtEpochMs = obj.optLong("createdAtEpochMs", 0L)
                            )
                        )
                    }
                }
            }
        )
    }

    private suspend fun <T> executeJsonRequest(
        method: String,
        path: String,
        authToken: String?,
        requestBody: String?,
        parser: (String) -> T
    ): NetworkResult<T> = withContext(ioDispatcher) {
        val cleanBase = baseUrl.trim().trimEnd('/')
        if (cleanBase.isEmpty()) {
            return@withContext NetworkResult.NetworkUnavailable(
                "Backend server URL not configured. Offline Room cache is active."
            )
        }
        if (!cleanBase.startsWith("https://", ignoreCase = true)) {
            return@withContext NetworkResult.Error(
                message = "Insecure base URL rejected: only https:// endpoints are permitted.",
                statusCode = 400
            )
        }

        var connection: HttpURLConnection? = null
        try {
            val endpointUrl = URL("$cleanBase$path")
            val rawConn = endpointUrl.openConnection()
            if (rawConn !is HttpsURLConnection) {
                return@withContext NetworkResult.Error(
                    message = "TLS connection required: non-HTTPS connection rejected.",
                    statusCode = 400
                )
            }
            connection = rawConn.apply {
                requestMethod = method
                connectTimeout = connectTimeoutMs
                readTimeout = readTimeoutMs
                useCaches = false
                setRequestProperty("Accept", "application/json")
                setRequestProperty("Content-Type", "application/json; charset=UTF-8")
                if (!authToken.isNullOrBlank()) {
                    setRequestProperty("Authorization", "Bearer ${authToken.trim()}")
                }
                if (requestBody != null) {
                    doOutput = true
                }
            }

            if (requestBody != null) {
                OutputStreamWriter(connection.outputStream, StandardCharsets.UTF_8).use { writer ->
                    writer.write(requestBody)
                    writer.flush()
                }
            }

            val code = connection.responseCode
            val stream = if (code in 200..299) connection.inputStream else connection.errorStream
            val responseText = if (stream != null) {
                BufferedReader(InputStreamReader(stream, StandardCharsets.UTF_8)).use { it.readText() }
            } else {
                ""
            }

            if (code in 200..299) {
                NetworkResult.Success(data = parser(responseText), statusCode = code)
            } else {
                val parsedError = runCatching {
                    JSONObject(responseText).optString("error").ifBlank {
                        JSONObject(responseText).optString("message")
                    }
                }.getOrNull()
                NetworkResult.Error(
                    message = parsedError?.takeIf { it.isNotBlank() }
                        ?: "Server returned HTTP $code",
                    statusCode = code
                )
            }
        } catch (e: java.io.IOException) {
            NetworkResult.NetworkUnavailable(
                "Unable to reach Mumbai Share Auto server (${e.message ?: "network error"})."
            )
        } catch (e: Exception) {
            NetworkResult.Error(
                message = e.message ?: "Unexpected network client error",
                statusCode = -1,
                cause = e
            )
        } finally {
            connection?.disconnect()
        }
    }

    private fun parseAuthResponse(obj: JSONObject): AuthResponseDto = AuthResponseDto(
        tokenType = obj.optString("tokenType", "Bearer"),
        accessToken = obj.getString("accessToken"),
        refreshToken = obj.optString("refreshToken", ""),
        expiresAtEpochMs = obj.optLong("expiresAtEpochMs", 0L),
        userId = obj.getString("userId"),
        email = obj.getString("email"),
        role = obj.getString("role"),
        driverVerified = obj.optBoolean("driverVerified", false)
    )

    private fun parseRoutePosting(obj: JSONObject): RoutePostingResponseDto = RoutePostingResponseDto(
        postingId = obj.getString("postingId"),
        driverId = obj.getString("driverId"),
        designatedPickupStand = obj.getString("designatedPickupStand"),
        destinationStand = obj.getString("destinationStand"),
        totalBookingFare = obj.getDouble("totalBookingFare"),
        availableSeats = obj.getInt("availableSeats"),
        createdAtEpochMs = obj.optLong("createdAtEpochMs", 0L),
        isCurrentlyActive = obj.optBoolean("isCurrentlyActive", false)
    )

    private fun parseBookingResponse(obj: JSONObject): BookingResponseDto = BookingResponseDto(
        bookingId = obj.getString("bookingId"),
        postingId = obj.optString("postingId", ""),
        passengerPartyId = obj.optString("passengerPartyId", ""),
        passengerCount = obj.getInt("passengerCount"),
        agreedTotalFare = obj.getDouble("agreedTotalFare"),
        designatedStationStand = obj.getString("designatedStationStand"),
        destination = obj.getString("destination"),
        state = obj.getString("state"),
        lockedFareInr = if (obj.isNull("lockedFareInr")) null else obj.optDouble("lockedFareInr"),
        cancellationReason = if (obj.isNull("cancellationReason")) null else obj.optString("cancellationReason"),
        updatedAtEpochMs = obj.optLong("updatedAtEpochMs", 0L)
    )
}
