package com.sunseat.app.location

import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

class DefaultLocationProviderTest {

    private class FakeSource(
        var level: LocationPermissionLevel = LocationPermissionLevel.Precise,
        var enabled: Boolean = true,
        var current: suspend (Boolean) -> LocationFix? = { null },
        var last: suspend () -> LocationFix? = { null },
    ) : LocationSource {
        var requestedPrecise: Boolean? = null
        override fun permissionLevel() = level
        override fun isLocationEnabled() = enabled
        override suspend fun fetchCurrent(precise: Boolean): LocationFix? {
            requestedPrecise = precise
            return current(precise)
        }
        override suspend fun lastKnown() = last()
    }

    private val seoul = LocationFix(37.5547, 126.9707, 12f, ageMillis = 0)

    private fun provider(source: FakeSource) = DefaultLocationProvider(source, timeoutMillis = 1_000)

    @Test
    fun noPermission_returnsPermissionDenied() = runTest {
        val source = FakeSource(level = LocationPermissionLevel.None)
        assertEquals(LocationResult.PermissionDenied, provider(source).currentLocation())
    }

    @Test
    fun locationOff_returnsLocationDisabled() = runTest {
        val source = FakeSource(enabled = false)
        assertEquals(LocationResult.LocationDisabled, provider(source).currentLocation())
    }

    @Test
    fun precise_success() = runTest {
        val source = FakeSource(current = { seoul })
        assertEquals(
            LocationResult.Success(37.5547, 126.9707, 12f, isApproximate = false),
            provider(source).currentLocation(),
        )
        assertEquals(true, source.requestedPrecise)
    }

    @Test
    fun approximateOnly_successMarkedApproximate() = runTest {
        val source = FakeSource(level = LocationPermissionLevel.Approximate, current = { seoul })
        val result = provider(source).currentLocation() as LocationResult.Success
        assertEquals(true, result.isApproximate)
        assertEquals(false, source.requestedPrecise)
    }

    @Test
    fun hangingRequest_timesOut() = runTest {
        val source = FakeSource(current = { awaitCancellation() })
        assertEquals(LocationResult.Timeout, provider(source).currentLocation())
    }

    @Test
    fun timeout_fallsBackToRecentLastLocation() = runTest {
        val source = FakeSource(
            current = { awaitCancellation() },
            last = { seoul.copy(ageMillis = 30_000) },
        )
        assertEquals(37.5547, (provider(source).currentLocation() as LocationResult.Success).latitude, 0.0)
    }

    @Test
    fun staleLastLocation_isIgnored() = runTest {
        val source = FakeSource(last = { seoul.copy(ageMillis = LocationDefaults.MAX_LAST_LOCATION_AGE_MILLIS + 1) })
        assertEquals(LocationResult.Unavailable, provider(source).currentLocation())
    }

    @Test
    fun nullLocation_returnsUnavailable() = runTest {
        assertEquals(LocationResult.Unavailable, provider(FakeSource()).currentLocation())
    }

    @Test
    fun playServicesError_returnsUnavailableWithoutThrowing() = runTest {
        val source = FakeSource(
            current = { throw IllegalStateException("api unavailable") },
            last = { throw IllegalStateException("api unavailable") },
        )
        assertEquals(LocationResult.Unavailable, provider(source).currentLocation())
    }

    @Test
    fun permissionRevokedMidRequest_returnsPermissionDenied() = runTest {
        val source = FakeSource(current = { throw SecurityException("revoked") })
        assertEquals(LocationResult.PermissionDenied, provider(source).currentLocation())
    }

    @Test
    fun defaultTimeoutIsBounded() {
        assertEquals(true, LocationDefaults.TIMEOUT_MILLIS in 1..30_000)
    }
}
