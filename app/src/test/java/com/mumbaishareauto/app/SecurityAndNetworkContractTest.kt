package com.mumbaishareauto.app

import com.mumbaishareauto.app.data.remote.AuthRequestDto
import com.mumbaishareauto.app.data.remote.HttpUrlConnectionMumbaiShareAutoApiService
import com.mumbaishareauto.app.data.remote.NetworkResult
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class SecurityAndNetworkContractTest {

    @Test
    fun httpApiService_returnsNetworkUnavailableWhenBaseUrlUnconfigured() = runTest {
        val service = HttpUrlConnectionMumbaiShareAutoApiService(baseUrl = "")
        assertFalse(service.isBackendConfigured)

        val res = service.authenticateUser(
            AuthRequestDto(email = "commuter@mumbai.in", passwordHash = "sha256_hash")
        )
        assertTrue(res is NetworkResult.NetworkUnavailable)
    }

    @Test
    fun httpApiService_rejectsCleartextHttpBaseUrlBeforeOpeningSocket() = runTest {
        val service = HttpUrlConnectionMumbaiShareAutoApiService(baseUrl = "http://api.mumbaishareauto.example")
        assertTrue(service.isBackendConfigured)

        val res = service.getBookingStatus(authToken = "valid_bearer_token", bookingId = "BK_101")
        assertTrue(res is NetworkResult.Error)
        assertEquals(400, (res as NetworkResult.Error).statusCode)
        assertTrue(res.message.contains("HTTPS", ignoreCase = true))
    }

    @Test
    fun httpApiService_rejectsMissingBearerTokenForAuthenticatedRoutes() = runTest {
        val service = HttpUrlConnectionMumbaiShareAutoApiService(baseUrl = "https://api.mumbaishareauto.example")
        val res = service.getDriverProfile(authToken = "   ", driverId = "DRV_1")
        assertTrue(res is NetworkResult.Error)
        assertEquals(401, (res as NetworkResult.Error).statusCode)
    }

    @Test
    fun normalizeDeepLinkBookingId_handlesNullBlankAndTrimmedIdsCleanly() {
        assertNull(normalizeDeepLinkBookingId(null))
        assertNull(normalizeDeepLinkBookingId("   "))
        assertEquals("DRAFT_PAX_12", normalizeDeepLinkBookingId("  DRAFT_PAX_12  "))
    }
}
