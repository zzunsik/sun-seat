package com.sunseat.app.location

import com.sunseat.app.R
import com.sunseat.app.location.LocationPermissionLevel.Approximate
import com.sunseat.app.location.LocationPermissionLevel.None
import com.sunseat.app.location.LocationPermissionLevel.Precise
import com.sunseat.app.location.LocationPermissionStatus.ApproximateOnly
import com.sunseat.app.location.LocationPermissionStatus.Denied
import com.sunseat.app.location.LocationPermissionStatus.NotRequested
import com.sunseat.app.location.LocationPermissionStatus.PermanentlyDenied
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

class LocationPermissionLogicTest {

    private fun after(level: LocationPermissionLevel, denied: Boolean, before: Boolean, afterR: Boolean) =
        LocationPermissionLogic.afterRequest(level, denied, before, afterR)

    @Test
    fun current_states() {
        assertEquals(LocationPermissionStatus.Precise, LocationPermissionLogic.current(Precise, true, false))
        assertEquals(ApproximateOnly, LocationPermissionLogic.current(Approximate, false, false))
        assertEquals(NotRequested, LocationPermissionLogic.current(None, false, false))
        assertEquals(Denied, LocationPermissionLogic.current(None, true, true))
        assertEquals(PermanentlyDenied, LocationPermissionLogic.current(None, true, false))
    }

    @Test
    fun allow_precise() {
        assertEquals(LocationPermissionStatus.Precise, after(Precise, false, false, false).status)
    }

    @Test
    fun allow_approximateOnly() {
        assertEquals(ApproximateOnly, after(Approximate, false, false, false).status)
        assertEquals(true, ApproximateOnly.isGranted)
    }

    @Test
    fun firstDenial_canAskAgain() {
        val r = after(None, denied = false, before = false, afterR = true)
        assertEquals(Denied, r.status)
        assertEquals(true, r.deniedBefore)
    }

    @Test
    fun secondDenial_becomesDontAskAgain() {
        val r = after(None, denied = true, before = true, afterR = false)
        assertEquals(PermanentlyDenied, r.status)
        assertEquals(true, r.deniedBefore)
    }

    @Test
    fun dialogDismissedOnFirstRequest_isNotPermanent() {
        val r = after(None, denied = false, before = false, afterR = false)
        assertEquals(NotRequested, r.status)
        assertEquals(false, r.deniedBefore)
    }

    @Test
    fun requestWhileBlocked_staysPermanent() {
        assertEquals(PermanentlyDenied, after(None, denied = true, before = false, afterR = false).status)
    }

    @Test
    fun grantedAfterSettings_keepsHistory() {
        val r = after(Precise, denied = true, before = false, afterR = false)
        assertEquals(LocationPermissionStatus.Precise, r.status)
        assertEquals(true, r.deniedBefore)
    }

    @Test
    fun messages_pointToPlaceSearchOnFailure() {
        assertNull(LocationPermissionStatus.Precise.messageRes())
        assertEquals(R.string.location_permission_permanently_denied, PermanentlyDenied.messageRes())
        assertNull(LocationResult.Success(0.0, 0.0, null, false).messageRes())
        listOf(
            LocationResult.PermissionDenied,
            LocationResult.LocationDisabled,
            LocationResult.Timeout,
            LocationResult.Unavailable,
        ).forEach { assertNotNull(it.messageRes()) }
    }
}

class LocationActionTest {
    @Test
    fun actions() {
        assertEquals(LocationAction.OpenAppSettings, PermanentlyDenied.action())
        assertEquals(LocationAction.RequestPermission, Denied.action())
        assertNull(ApproximateOnly.action())
        assertEquals(LocationAction.OpenLocationSettings, LocationResult.LocationDisabled.action())
        assertEquals(LocationAction.Retry, LocationResult.Timeout.action())
    }
}
