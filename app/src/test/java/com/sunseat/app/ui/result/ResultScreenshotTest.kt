package com.sunseat.app.ui.result

import androidx.compose.ui.test.junit4.accessibility.enableAccessibilityChecks
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.tryPerformAccessibilityChecks
import com.github.takahirom.roborazzi.captureRoboImage
import com.sunseat.app.domain.Route
import com.sunseat.app.domain.SunGoal
import com.sunseat.app.domain.TripRequest
import com.sunseat.app.ui.result.ResultFixtures.advisor
import com.sunseat.app.ui.result.ResultFixtures.carRequest
import com.sunseat.app.ui.result.ResultFixtures.carRoute
import com.sunseat.app.ui.result.ResultFixtures.departure
import com.sunseat.app.ui.result.ResultFixtures.mixedRoute
import com.sunseat.app.ui.result.ResultFixtures.transitRequest
import com.sunseat.app.ui.result.ResultFixtures.transitRoute
import com.sunseat.app.ui.result.ResultFixtures.zone
import com.sunseat.app.ui.theme.SunSeatTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** 결과 화면 스크린샷(밝은·어두운, 구간마다 다름, 해 없음, 자가용 눈부심)과 접근성 검사. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(application = android.app.Application::class, sdk = [36], qualifiers = "w390dp-h844dp-xxhdpi")
class ResultScreenshotTest {
    @get:Rule
    val compose = createComposeRule()

    private fun state(route: Route, azimuth: Double, altitude: Double, goal: SunGoal = SunGoal.AVOID): ResultUiState {
        val result = advisor(azimuth, altitude).advise(route, departure, goal)
        return ResultUiState(goal = goal, departure = departure, result = result)
    }

    private fun render(request: TripRequest, state: ResultUiState, dark: Boolean) {
        compose.setContent {
            androidx.compose.runtime.CompositionLocalProvider(com.sunseat.app.ui.map.LocalMapRenderer provides com.sunseat.app.ui.map.MapRenderer.PREVIEW) {
            SunSeatTheme(darkTheme = dark) {
                ResultScreen(request, state, ResultActions(), animateIn = false, zone = zone)
            }
            }
        }
        compose.waitForIdle()
    }

    @Test fun transitLight() { render(transitRequest, state(transitRoute, 90.0, 30.0), false); shot("result_transit_light") }
    @Test fun transitDark() { render(transitRequest, state(transitRoute, 90.0, 30.0), true); shot("result_transit_dark") }
    @Test fun mixedLight() { render(transitRequest, state(mixedRoute, 90.0, 30.0), false); shot("result_mixed_light") }
    @Test fun noSunDark() { render(transitRequest, state(transitRoute, 90.0, -15.0), true); shot("result_no_sun_dark") }
    @Test fun carGlareLight() { render(carRequest, state(carRoute, 10.0, 10.0), false); shot("result_car_glare_light") }
    @Test fun carSideLight() { render(carRequest, state(carRoute, 90.0, 30.0), false); shot("result_car_side_light") }
    @Test fun loadingLight() { render(transitRequest, ResultUiState(goal = SunGoal.AVOID, departure = departure), false); shot("result_loading_light") }

    /** 아래쪽 카드(구간별·눈부심)까지 보이도록 세로로 긴 화면 */
    @Config(qualifiers = "w390dp-h1900dp-xxhdpi")
    @Test fun transitFullLight() { render(transitRequest, state(transitRoute, 90.0, 30.0), false); shot("result_transit_full_light") }

    @Config(qualifiers = "w390dp-h1900dp-xxhdpi")
    @Test fun mixedFullLight() { render(transitRequest, state(mixedRoute, 90.0, 20.0), false); shot("result_mixed_full_light") }

    @Config(qualifiers = "w390dp-h1900dp-xxhdpi")
    @Test fun carGlareFullDark() { render(carRequest, state(carRoute, 10.0, 10.0), true); shot("result_car_glare_full_dark") }

    @Test
    fun accessibilityTransit() {
        compose.enableAccessibilityChecks()
        render(transitRequest, state(transitRoute, 90.0, 30.0), false)
        compose.onRoot().tryPerformAccessibilityChecks()
    }

    @Test
    fun accessibilityCarDark() {
        compose.enableAccessibilityChecks()
        render(carRequest, state(carRoute, 10.0, 10.0), true)
        compose.onRoot().tryPerformAccessibilityChecks()
    }

    private fun arrivalWithWaiting(dark: Boolean) {
        compose.enableAccessibilityChecks()
        val result = advisor(90.0, 30.0).advise(transitRoute, departure, SunGoal.AVOID, totalDuration = transitRoute.duration.plusMinutes(20))
        render(transitRequest, ResultUiState(SunGoal.AVOID, departure, result), dark)
        compose.onRoot().tryPerformAccessibilityChecks()
        shot("result_waiting_${if (dark) "dark" else "light"}")
    }
    @Config(qualifiers = "w390dp-h1900dp-xxhdpi")
    @Test fun arrivalWaitingLight() = arrivalWithWaiting(false)
    @Config(qualifiers = "w390dp-h1900dp-xxhdpi")
    @Test fun arrivalWaitingDark() = arrivalWithWaiting(true)

    private fun mixedGroundRoute() = Route(listOf(
        transitRoute.legs.first().copy(groundLevel = com.sunseat.app.domain.GroundLevel.BELOW),
        transitRoute.legs.last().copy(groundLevel = com.sunseat.app.domain.GroundLevel.UNKNOWN),
    ))

    @Config(qualifiers = "w390dp-h1900dp-xxhdpi")
    @Test fun mixedGroundLight() {
        compose.enableAccessibilityChecks()
        render(transitRequest, state(mixedGroundRoute(), 90.0, 30.0), false)
        compose.onRoot().tryPerformAccessibilityChecks()
        shot("result_mixed_ground_light")
    }

    @Config(qualifiers = "w390dp-h1900dp-xxhdpi")
    @Test fun mixedGroundDark() {
        compose.enableAccessibilityChecks()
        render(transitRequest, state(mixedGroundRoute(), 90.0, 30.0), true)
        compose.onRoot().tryPerformAccessibilityChecks()
        shot("result_mixed_ground_dark")
    }

    private fun excludedRoute() = Route(listOf(
        transitRoute.legs.first().copy(groundLevel = com.sunseat.app.domain.GroundLevel.BELOW),
        transitRoute.legs.first().copy(groundLevel = com.sunseat.app.domain.GroundLevel.UNKNOWN),
    ))

    @Config(qualifiers = "w390dp-h1200dp-xxhdpi")
    @Test fun excludedLight() {
        compose.enableAccessibilityChecks()
        render(transitRequest, state(excludedRoute(), 90.0, 30.0), false)
        compose.onRoot().tryPerformAccessibilityChecks()
        shot("result_excluded_light")
    }

    @Config(qualifiers = "w390dp-h1200dp-xxhdpi")
    @Test fun excludedDark() {
        compose.enableAccessibilityChecks()
        render(transitRequest, state(excludedRoute(), 90.0, 30.0), true)
        compose.onRoot().tryPerformAccessibilityChecks()
        shot("result_excluded_dark")
    }

    private fun shot(name: String) = compose.onRoot().captureRoboImage("src/test/screenshots/$name.png")
}
