package com.sunseat.app.ui.input

import android.app.Application
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.junit4.accessibility.enableAccessibilityChecks
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.tryPerformAccessibilityChecks
import androidx.compose.ui.unit.Density
import com.github.takahirom.roborazzi.captureRoboImage
import com.sunseat.app.R
import com.sunseat.app.domain.LatLng
import com.sunseat.app.domain.NamedPlace
import com.sunseat.app.domain.SunGoal
import com.sunseat.app.domain.TravelMode
import com.sunseat.app.ui.map.LocalMapRenderer
import com.sunseat.app.ui.map.MapRenderer
import com.sunseat.app.ui.theme.SunSeatTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.time.LocalDateTime

/** 입력 여정 헤더(#103) 스크린샷: 긴 이름·큰 글자·좁은 폭·가로·같은 장소 안내, 밝은·어두운 화면. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(application = Application::class, sdk = [36], qualifiers = "w390dp-h844dp-xxhdpi")
class JourneyHeaderScreenshotTest {
    @get:Rule val compose = createComposeRule()

    private val now = LocalDateTime.of(2026, 11, 16, 8, 0)
    private val trip = InputUiState(
        origin = Endpoint(NamedPlace("서울역", LatLng(37.55, 126.97))),
        destination = Endpoint(NamedPlace("강남역", LatLng(37.50, 127.03))),
        mode = TravelMode.TRANSIT,
        departure = LocalDateTime.of(2026, 11, 16, 9, 0),
    )
    private val longNames = trip.copy(
        origin = Endpoint(NamedPlace("가상구 예시동 견본 문화체육센터 정문 앞 버스 정류장", LatLng(37.55, 126.97))),
        destination = Endpoint(NamedPlace("견본시 예시로 아주 긴 이름의 공공도서관 별관", LatLng(37.50, 127.03))),
        departure = LocalDateTime.of(2026, 12, 24, 23, 30),
        goal = SunGoal.SEEK,
    )

    private fun shot(state: InputUiState, name: String, dark: Boolean, fontScale: Float = 1f) {
        compose.enableAccessibilityChecks()
        compose.setContent {
            val density = LocalDensity.current
            CompositionLocalProvider(LocalMapRenderer provides MapRenderer.PREVIEW, LocalDensity provides Density(density.density, fontScale)) {
                SunSeatTheme(darkTheme = dark) { InputScreen(state, InputActions(), now = now) }
            }
        }
        compose.waitForIdle()
        compose.onRoot().tryPerformAccessibilityChecks()
        compose.onRoot().captureRoboImage("src/test/screenshots/$name.png")
    }

    @Test @Config(qualifiers = "w320dp-h640dp-xxhdpi")
    fun longNamesSmallLargeTextLight() = shot(longNames, "input_header_long_small_large_text_light", false, 1.5f)
    @Test @Config(qualifiers = "w320dp-h640dp-xxhdpi")
    fun longNamesSmallLargeTextDark() = shot(longNames, "input_header_long_small_large_text_dark", true, 1.5f)
    @Test @Config(qualifiers = "w320dp-h640dp-xxhdpi")
    fun smallLight() = shot(trip, "input_header_small_light", false)
    @Test fun longNamesDark() = shot(longNames, "input_header_long_dark", true)
    @Test fun sameEndpointsLight() = shot(trip.copy(destination = trip.origin), "input_header_same_endpoints_light", false)
    @Test fun locatingDark() = shot(trip.copy(origin = null, locating = true), "input_header_locating_dark", true)
    @Test @Config(qualifiers = "w844dp-h390dp-land-xxhdpi")
    fun landscapeLight() = shot(trip, "input_header_landscape_light", false)
    @Test @Config(qualifiers = "w844dp-h390dp-land-xxhdpi")
    fun landscapeLargeTextDark() = shot(trip, "input_header_landscape_large_text_dark", true, 1.5f)
}
