package com.sunseat.app.ui.candidates

import androidx.compose.ui.test.junit4.accessibility.enableAccessibilityChecks
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.tryPerformAccessibilityChecks
import com.github.takahirom.roborazzi.captureRoboImage
import com.sunseat.app.R
import com.sunseat.app.ui.candidates.CandidatesFixtures.options
import com.sunseat.app.ui.candidates.CandidatesFixtures.transitRequest
import com.sunseat.app.ui.theme.SunSeatTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** 경로 후보 화면 스크린샷(조회 중·목록·경로 없음·오류)과 접근성 검사. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [36], qualifiers = "w390dp-h844dp-xxhdpi")
class CandidatesScreenshotTest {
    @get:Rule
    val compose = createComposeRule()

    private val list = CandidatesState.Options(options.toUi(java.time.LocalDateTime.of(2026, 11, 15, 9, 0)))
    private val noRoute = R.string.candidates_no_transit.let { CandidatesState.NoRoute(it, R.string.candidates_no_transit_detail, suggestCar = true) }

    private fun render(state: CandidatesState, dark: Boolean) {
        compose.mainClock.autoAdvance = false
        compose.setContent {
            SunSeatTheme(darkTheme = dark) {
                CandidatesScreen(transitRequest, state, CandidatesActions(), animateLoader = false)
            }
        }
        compose.mainClock.advanceTimeBy(500)
    }

    @Test fun loadingLight() { render(CandidatesState.Loading(com.sunseat.app.domain.TravelMode.TRANSIT), false); shot("candidates_loading_light") }
    @Test fun loadingDark() { render(CandidatesState.Loading(com.sunseat.app.domain.TravelMode.TRANSIT), true); shot("candidates_loading_dark") }
    @Test fun loadingCarLight() { render(CandidatesState.Loading(com.sunseat.app.domain.TravelMode.CAR), false); shot("candidates_loading_car_light") }
    @Test fun listLight() { render(list, false); shot("candidates_list_light") }
    @Test fun listDark() { render(list, true); shot("candidates_list_dark") }
    @Test fun noRouteLight() { render(noRoute, false); shot("candidates_no_route_light") }
    @Test fun errorDark() { render(CandidatesState.Error(R.string.search_error_network, R.drawable.ic_ms_wifi_off, canRetry = true), true); shot("candidates_error_dark") }

    @Test
    fun accessibilityList() {
        compose.enableAccessibilityChecks()
        render(list, false)
        compose.onRoot().tryPerformAccessibilityChecks()
    }

    @Test
    fun accessibilityNoRoute() {
        compose.enableAccessibilityChecks()
        render(noRoute, true)
        compose.onRoot().tryPerformAccessibilityChecks()
    }

    private fun shot(name: String) = compose.onRoot().captureRoboImage("src/test/screenshots/$name.png")
}
