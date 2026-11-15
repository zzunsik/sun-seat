package com.sunseat.app.location

import android.Manifest
import android.app.Application
import android.content.Intent
import android.provider.Settings
import org.robolectric.RuntimeEnvironment
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf

@org.robolectric.annotation.Config(application = android.app.Application::class)
@RunWith(RobolectricTestRunner::class)
class FusedLocationSourceTest {

    private val app: Application = RuntimeEnvironment.getApplication()

    @Test
    fun permissionLevel_followsGrantedPermissions() {
        assertEquals(LocationPermissionLevel.None, locationPermissionLevel(app))
        shadowOf(app).grantPermissions(Manifest.permission.ACCESS_COARSE_LOCATION)
        assertEquals(LocationPermissionLevel.Approximate, locationPermissionLevel(app))
        shadowOf(app).grantPermissions(Manifest.permission.ACCESS_FINE_LOCATION)
        assertEquals(LocationPermissionLevel.Precise, locationPermissionLevel(app))
    }

    @Test
    fun withoutPermission_returnsPermissionDeniedWithoutCrash() = runTest {
        assertEquals(LocationResult.PermissionDenied, LocationProvider(app).currentLocation())
    }

    @Test
    fun appSettingsIntent_targetsThisApp() {
        val intent = appSettingsIntent(app)
        assertEquals(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, intent.action)
        assertEquals("package:${app.packageName}", intent.dataString)
        assertEquals(Intent.FLAG_ACTIVITY_NEW_TASK, intent.flags and Intent.FLAG_ACTIVITY_NEW_TASK)
    }
}
