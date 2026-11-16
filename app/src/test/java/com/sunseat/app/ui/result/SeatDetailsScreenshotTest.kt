package com.sunseat.app.ui.result

import androidx.compose.foundation.layout.*
import androidx.compose.material3.Text
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.junit4.accessibility.enableAccessibilityChecks
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.tryPerformAccessibilityChecks
import com.github.takahirom.roborazzi.captureRoboImage
import com.sunseat.app.domain.*
import com.sunseat.app.ui.components.SectionCard
import com.sunseat.app.ui.theme.SunSeatTheme
import com.sunseat.app.ui.text.label
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.time.Duration

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(application = android.app.Application::class, sdk = [36], qualifiers = "w390dp-h1000dp-xxhdpi")
class SeatDetailsScreenshotTest {
    @get:Rule val compose = createComposeRule()
    private fun shot(mode: Mode, dark: Boolean, angle: Double = 45.0, goal: SunGoal = SunGoal.AVOID) {
        val departure = ResultFixtures.departure
        val leg = Leg(mode, listOf(LatLng(37.0, 127.0), LatLng(37.1, 127.0)), Duration.ofMinutes(20), "성수", "건대입구", GroundLevel.ABOVE)
        val travel = if (mode == Mode.CAR) TravelMode.TAXI else TravelMode.TRANSIT
        val result = ResultFixtures.advisor(angle, 20.0).advise(Route(listOf(leg)), departure, goal, travelMode = travel)
        val detail = result.legs.single()
        compose.enableAccessibilityChecks()
        compose.setContent { SunSeatTheme(darkTheme = dark) {
            Column(Modifier.fillMaxSize()) {
                SectionCard(title = if (mode == Mode.CAR) "택시 · 좌석 안내" else if (mode == Mode.BUS) "버스 · 좌석 안내" else "지하철 · 벤치 안내") {
                    val advice = detail.advice as LegAdvice.Sit
                    if (mode != Mode.BUS) Text(if (mode == Mode.CAR) advice.carSeats.label() else advice.spot.label())
                    SeatDetails(detail, advice, travel, departure, ResultFixtures.zone)
                }
            }
        } }
        compose.onRoot().tryPerformAccessibilityChecks()
        compose.onRoot().captureRoboImage("src/test/screenshots/seat_details_${mode.name.lowercase()}_${goal.name.lowercase()}_${if (dark) "dark" else "light"}.png")
    }
    @Test fun busLight() = shot(Mode.BUS, false)
    @Test fun busDark() = shot(Mode.BUS, true)
    @Test fun subwayLight() = shot(Mode.SUBWAY, false, 90.0)
    @Test fun subwayDark() = shot(Mode.SUBWAY, true, 90.0)
    @Test fun taxiLight() = shot(Mode.CAR, false, 90.0)
    @Test fun taxiDark() = shot(Mode.CAR, true, 90.0)
    @Test fun seekLight() = shot(Mode.BUS, false, goal = SunGoal.SEEK)
    private fun switchShot(dark: Boolean) {
        val departure = ResultFixtures.departure
        val leg = Leg(Mode.BUS, listOf(LatLng(37.0, 127.0), LatLng(37.1, 127.0), LatLng(37.2, 127.0)), Duration.ofMinutes(20), "A", "B")
        val advisor = TripAdvisor(ExposureAnalyzer { _, _, time -> SunPosition(if (time < departure.plusSeconds(600)) 90.0 else 270.0, 50.0) })
        val detail = advisor.advise(Route(listOf(leg)), departure, SunGoal.AVOID).legs.single()
        compose.enableAccessibilityChecks()
        compose.setContent { SunSeatTheme(darkTheme = dark) { SectionCard { SeatDetails(detail, detail.advice as LegAdvice.Sit, TravelMode.TRANSIT, departure, ResultFixtures.zone) } } }
        compose.onNodeWithText("출발 후 10분부터 오른쪽 · 창가").assertExists()
        compose.onRoot().tryPerformAccessibilityChecks()
        compose.onRoot().captureRoboImage("src/test/screenshots/seat_switch_${if (dark) "dark" else "light"}.png")
    }
    @Test fun switchLight() = switchShot(false)
    @Test fun switchDark() = switchShot(true)
}
