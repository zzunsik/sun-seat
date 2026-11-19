package com.sunseat.app.ui.map

import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.junit4.accessibility.enableAccessibilityChecks
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.onNodeWithContentDescription
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
    /** #100: line colours, low-contrast outline, dotted walk, stop/transfer markers and text place markers on the test renderer. */
    private val transferRoute = com.sunseat.app.domain.Route(listOf(
        com.sunseat.app.domain.Leg(com.sunseat.app.domain.Mode.SUBWAY, listOf(LatLng(37.556, 126.972), LatLng(37.530, 126.975), LatLng(37.500, 126.980)), java.time.Duration.ofMinutes(15), "서울역", "사당", lineName = "4호선", transitStepIndex = 0),
        com.sunseat.app.domain.Leg(com.sunseat.app.domain.Mode.WALK, listOf(LatLng(37.500, 126.980), LatLng(37.498, 126.986)), java.time.Duration.ofMinutes(3), "사당", "사당"),
        com.sunseat.app.domain.Leg(com.sunseat.app.domain.Mode.SUBWAY, listOf(LatLng(37.498, 126.986), LatLng(37.502, 127.010), LatLng(37.498, 127.027)), java.time.Duration.ofMinutes(10), "사당", "강남", lineName = "2호선", transitStepIndex = 2),
        com.sunseat.app.domain.Leg(com.sunseat.app.domain.Mode.BUS, listOf(LatLng(37.498, 127.027), LatLng(37.510, 127.040)), java.time.Duration.ofMinutes(6), "강남", "역삼", lineName = "402", transitStepIndex = 3),
    ))
    private fun renderTransfer(dark: Boolean, progress: Float? = null) {
        compose.setContent {
            SunSeatTheme(darkTheme = dark) {
                CompositionLocalProvider(LocalMapRenderer provides MapRenderer.PREVIEW) {
                    SunSeatMap(MapState(routes = listOf(transferRoute), origin = com.sunseat.app.domain.NamedPlace("서울역", LatLng(37.556, 126.972)),
                        destination = com.sunseat.app.domain.NamedPlace("역삼", LatLng(37.510, 127.040)), routeProgress = progress), Modifier.fillMaxWidth().height(Sizes.mapHeight * 2))
                }
            }
        }
        compose.waitForIdle()
    }
    @Test fun transferRouteLight() { renderTransfer(false); compose.onRoot().captureRoboImage("src/test/screenshots/map_transfer_light.png") }
    @Test fun transferRouteDark() { renderTransfer(true); compose.onRoot().captureRoboImage("src/test/screenshots/map_transfer_dark.png") }
    @Test fun transferPlaybackLight() { renderTransfer(false, .4f); compose.onRoot().captureRoboImage("src/test/screenshots/map_transfer_playback_light.png") }
    @Test fun selectedRouteSummaryIsReadAloudBesideTheMapDescription() {
        renderTransfer(false)
        compose.onNodeWithContentDescription("출발 서울역, 도착 역삼 경로 지도").assertExists()
        compose.onNodeWithContentDescription("선택 경로: 4호선 서울역 승차 → 사당 2호선 환승 → 강남 402 환승 → 역삼 하차").assertExists()
    }
    @Test fun accessibility() { compose.enableAccessibilityChecks(); render(false, 18.0); compose.onRoot().tryPerformAccessibilityChecks() }
}
