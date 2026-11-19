package com.sunseat.app.ui.simulation

import androidx.compose.runtime.*
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.accessibility.enableAccessibilityChecks
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import com.github.takahirom.roborazzi.captureRoboImage
import com.sunseat.app.domain.*
import com.sunseat.app.R
import com.sunseat.app.ui.map.*
import com.sunseat.app.ui.theme.SunSeatTheme
import java.time.Duration
import java.time.Instant
import java.time.ZoneId
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(application = android.app.Application::class, sdk = [36], qualifiers = "w390dp-h844dp-xxhdpi")
class SimulationScreenshotTest {
    @get:Rule val compose = createComposeRule()
    private val start = Instant.parse("2026-11-16T00:00:00Z")
    private val a = LatLng(37.50, 127.0)
    private val b = LatLng(37.55, 127.0)
    private val c = LatLng(37.55, 127.001)
    private val d = LatLng(37.60, 127.001)
    private val route = Route(listOf(
        Leg(Mode.BUS, listOf(a, b), Duration.ofMinutes(10), "숲 입구", "시청"),
        Leg(Mode.WALK, listOf(b, c), Duration.ofMinutes(3), "시청", "환승역"),
        Leg(Mode.SUBWAY, listOf(c, d), Duration.ofMinutes(10), "환승역", "공원", GroundLevel.ABOVE),
    ))
    private val request = TripRequest(NamedPlace("숲 입구", a), NamedPlace("공원", d), TravelMode.TRANSIT, null, SunGoal.AVOID)
    private val zone = ZoneId.of("Asia/Seoul")
    private fun timeline(altitude: Double = 15.0, route: Route = this.route): TripTimeline {
        val source = SunPositionSource { _, _, _ -> SunPosition(90.0, altitude) }
        val advisor = TripAdvisor(ExposureAnalyzer(source))
        return TripTimeline(route, advisor.advise(route, start, SunGoal.AVOID, totalDuration = route.duration.plusMinutes(2)), source, advisor)
    }
    private fun render(minute: Long, dark: Boolean, altitude: Double = 15.0, route: Route = this.route) {
        val timeline = timeline(altitude, route)
        compose.enableAccessibilityChecks()
        compose.setContent { CompositionLocalProvider(LocalMapRenderer provides MapRenderer.PREVIEW) {
            SunSeatTheme(darkTheme = dark) {
                SimulationScreen(request, route, timeline.result, SimulationUiState(minute * 60_000), timeline.at(start.plusSeconds(minute * 60)), timeline.boundaries, SimulationActions(), zone = zone)
            }
        } }
        compose.waitForIdle()
        compose.onRoot().tryPerformAccessibilityChecks()
    }
    private fun shot(name: String) = compose.onRoot().captureRoboImage("src/test/screenshots/$name.png")
    @Test fun startLight() { render(0, false); shot("simulation_start_light") }
    @Test fun startDark() { render(0, true); shot("simulation_start_dark") }
    @Test fun middleLight() { render(6, false); shot("simulation_middle_light") }
    @Test fun middleDark() { render(6, true); shot("simulation_middle_dark") }
    @Test fun transferLight() { render(10, false); shot("simulation_transfer_light") }
    @Test fun transferDark() { render(10, true); shot("simulation_transfer_dark") }
    @Test fun endLight() { render(23, false); shot("simulation_end_light") }
    @Test fun endDark() { render(23, true); shot("simulation_end_dark") }
    @Config(qualifiers = "w844dp-h390dp-xxhdpi")
    @Test fun landscapeLight() {
        render(6, false)
        val app=org.robolectric.RuntimeEnvironment.getApplication()
        val map=compose.onNodeWithContentDescription(app.getString(R.string.map_description,"숲 입구","공원")).fetchSemanticsNode().boundsInRoot
        val detail=compose.onNodeWithText(app.getString(R.string.simulation_cabin_profile,app.getString(R.string.simulation_bus))).fetchSemanticsNode().boundsInRoot
        assertTrue("landscape must place map beside details, not squeeze it above",map.right<=detail.left)
        assertTrue(map.height>0)
        val scene=compose.onNode(hasContentDescription("3D 차량",substring=true)).fetchSemanticsNode().boundsInRoot
        assertTrue("landscape must show the whole 3D viewport above playback controls",scene.height>=com.sunseat.app.ui.theme.Sizes.cabinLandscapeHeight.value*compose.density.density-1)
        val slider=compose.onNodeWithContentDescription(app.getString(R.string.simulation_slider)).fetchSemanticsNode().boundsInRoot
        assertTrue("#75: enlarged landscape scene must still fit above playback controls without scrolling",scene.bottom<=slider.top)
        shot("simulation_landscape_light")
    }
    @Test fun nightDark() { render(6, true, -10.0); shot("simulation_night_dark") }
    @Test fun undergroundLight() { render(6, false, route = route.copy(legs = route.legs.map { it.copy(mode = Mode.SUBWAY, groundLevel = GroundLevel.BELOW) })); shot("simulation_underground_light") }

    @Test fun sliderAndPlaybackControlsApplyImmediately() {
        val timeline = timeline()
        val vm = SimulationViewModel(timeline)
        compose.setContent { CompositionLocalProvider(LocalMapRenderer provides MapRenderer.PREVIEW) {
            SunSeatTheme {
                val state by vm.state.collectAsState()
                SimulationScreen(request, route, timeline.result, state, timeline.at(start.plusMillis(state.elapsedMillis)), timeline.boundaries,
                    SimulationActions(seek = vm::seek, toggle = vm::toggle, speed = vm::setSpeed), zone = zone)
            }
        } }
        compose.onNodeWithText("2배").performClick()
        compose.onNodeWithText("재생").performClick()
        compose.runOnIdle { vm.advance(1000) }
        assertEquals(120_000L, vm.state.value.elapsedMillis)
        compose.onNodeWithContentDescription("재생 시각").assert(SemanticsMatcher.expectValue(SemanticsProperties.StateDescription, "09:02"))
        compose.onNodeWithContentDescription("재생 시각").performSemanticsAction(SemanticsActions.SetProgress) { it(0.5f) }
        assertEquals(690_000L, vm.state.value.elapsedMillis)
        assertFalse(vm.state.value.playing)
    }
    /** #76: the shown bus model is labelled as an estimate with its basis and can be switched for this leg only. */
    private fun renderBusModel(dark: Boolean, busType: String?): SimulationViewModel {
        val route = route.copy(legs = listOf(route.legs[0].copy(busType = busType)) + route.legs.drop(1))
        val timeline = timeline(route = route)
        val vm = SimulationViewModel(timeline)
        vm.seek(6f / 25)
        compose.enableAccessibilityChecks()
        compose.setContent { CompositionLocalProvider(LocalMapRenderer provides MapRenderer.PREVIEW) {
            SunSeatTheme(darkTheme = dark) {
                val state by vm.state.collectAsState()
                SimulationScreen(request, route, timeline.result, state, timeline.at(start.plusMillis(state.elapsedMillis)), timeline.boundaries,
                    SimulationActions(seat = vm::seat, busModel = vm::busModel), zone = zone)
            }
        } }
        compose.waitForIdle()
        compose.onRoot().tryPerformAccessibilityChecks()
        return vm
    }
    @Test fun coachEstimateFromRouteTypeDark() {
        val app = org.robolectric.RuntimeEnvironment.getApplication()
        renderBusModel(true, "광역")
        compose.onNodeWithText(app.getString(R.string.simulation_cabin_profile, app.getString(R.string.simulation_coach))).assertExists()
        compose.onNodeWithText(app.getString(R.string.simulation_bus_basis_coach, "광역")).assertExists()
        compose.onNodeWithText(app.getString(R.string.simulation_bus_model_estimated)).performScrollTo().assertIsDisplayed()
        compose.onNodeWithText(app.getString(R.string.simulation_coach_note)).assertExists()
        shot("simulation_coach_estimate_dark")
    }
    @Test fun switchingModelAppliesToThisLegAndAsksToReselectWithoutAnEquivalentSeat() {
        val app = org.robolectric.RuntimeEnvironment.getApplication()
        val vm = renderBusModel(false, null)
        compose.onNodeWithText(app.getString(R.string.simulation_cabin_profile, app.getString(R.string.simulation_bus))).assertExists()
        compose.onNodeWithText(app.getString(R.string.simulation_bus_basis_none)).assertExists()
        compose.onNodeWithText(app.getString(R.string.simulation_change_model)).performScrollTo().performClick()
        compose.onNodeWithText(app.getString(R.string.simulation_model_coach_option)).performClick()
        assertEquals(mapOf(0 to CabinKind.COACH), vm.state.value.models)
        compose.onNodeWithText(app.getString(R.string.simulation_cabin_profile, app.getString(R.string.simulation_coach))).assertExists()
        compose.onNodeWithText(app.getString(R.string.simulation_bus_model_manual)).assertExists()
        // Choose a left aisle seat that the city model does not have, then switch back.
        compose.onNodeWithText(app.getString(R.string.simulation_choose_seat)).performScrollTo().performClick()
        compose.onNodeWithText("왼쪽 · 앞 · 통로 · 모형 2번").performClick()
        assertEquals("coach-2", vm.state.value.seats[0])
        compose.onNodeWithText(app.getString(R.string.simulation_change_model)).performScrollTo().performClick()
        compose.onNodeWithText(app.getString(R.string.simulation_model_city_option)).performClick()
        assertEquals(mapOf(0 to CabinKind.BUS), vm.state.value.models)
        assertNull("no silent move to another seat", vm.state.value.seats[0])
        compose.onNodeWithText(app.getString(R.string.simulation_seat_not_carried, "왼쪽 · 앞 · 통로")).performScrollTo().assertIsDisplayed()
        shot("simulation_bus_model_switched_light")
    }
    @Test fun reducedMotionOffersScrubberOnly() {
        android.provider.Settings.Global.putFloat(org.robolectric.RuntimeEnvironment.getApplication().contentResolver, android.provider.Settings.Global.ANIMATOR_DURATION_SCALE, 0f)
        render(6, false)
        compose.onNodeWithText("재생").assertDoesNotExist()
        compose.onNodeWithContentDescription("재생 시각").assertExists()
    }
}
