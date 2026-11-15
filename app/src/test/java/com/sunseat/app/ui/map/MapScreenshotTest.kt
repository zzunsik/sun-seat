package com.sunseat.app.ui.map

import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.junit4.accessibility.enableAccessibilityChecks
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.tryPerformAccessibilityChecks
import com.github.takahirom.roborazzi.captureRoboImage
import com.sunseat.app.domain.LatLng
import com.sunseat.app.ui.result.ResultFixtures
import com.sunseat.app.ui.theme.Sizes
import com.sunseat.app.ui.theme.SunSeatTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(application = android.app.Application::class, sdk = [36], qualifiers = "w390dp-h844dp-xxhdpi")
class MapScreenshotTest {
    @get:Rule val compose = createComposeRule()
    private fun render(dark: Boolean, altitude: Double, preview: Boolean = true) {
        compose.setContent {
            SunSeatTheme(darkTheme = dark) {
                CompositionLocalProvider(LocalMapRenderer provides if (preview) MapRenderer.PREVIEW else MapRenderer.SDK) {
                    SunSeatMap(MapState(
                        routes = listOf(ResultFixtures.transitRoute),
                        origin = ResultFixtures.transitRequest.origin,
                        destination = ResultFixtures.transitRequest.destination,
                        cursor = MapCursor(LatLng(37.6, 127.0), 45.0, 120.0, altitude),
                    ), Modifier.fillMaxWidth().height(Sizes.mapHeight))
                }
            }
        }
        compose.waitForIdle()
    }
    @Test fun dayLight() { render(false, 18.0); compose.onRoot().captureRoboImage("src/test/screenshots/map_day_light.png") }
    @Test fun dayDark() { render(true, 18.0); compose.onRoot().captureRoboImage("src/test/screenshots/map_day_dark.png") }
    @Test fun nightDark() { render(true, -5.0); compose.onRoot().captureRoboImage("src/test/screenshots/map_night_dark.png") }
    @Test fun missingKeyLight() {
        compose.setContent { SunSeatTheme { NativeMap(MapState(), Modifier.fillMaxWidth().height(Sizes.mapHeight), keyAvailable = false) } }
        compose.onRoot().captureRoboImage("src/test/screenshots/map_missing_key_light.png")
    }
    @Test fun errorDark() {
        compose.setContent { SunSeatTheme(darkTheme = true) { MapNotice(com.sunseat.app.R.string.map_error, Modifier.fillMaxWidth().height(Sizes.mapHeight)) } }
        compose.onRoot().captureRoboImage("src/test/screenshots/map_error_dark.png")
    }
    @Test fun accessibility() { compose.enableAccessibilityChecks(); render(false, 18.0); compose.onRoot().tryPerformAccessibilityChecks() }
}
