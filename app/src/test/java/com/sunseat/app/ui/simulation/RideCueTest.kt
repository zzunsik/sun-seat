package com.sunseat.app.ui.simulation

import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import com.sunseat.app.domain.*
import com.sunseat.app.ui.map.*
import com.sunseat.app.ui.theme.SunSeatTheme
import androidx.compose.runtime.CompositionLocalProvider
import java.time.Duration
import java.time.Instant
import java.time.ZoneId
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * #152: 재생 중 차량과 겹치는 표식을 숨긴 뒤(#146)에도 승차·환승 지점의 노선 이름과 도착 순간의 도착 표식이 화면에 남는다.
 * 가상 좌표와 지어낸 정류장 이름을 쓴다.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(application = android.app.Application::class, sdk = [36], qualifiers = "w390dp-h844dp-xxhdpi")
class RideCueTest {
    @get:Rule val compose = createComposeRule()
    private val start = Instant.parse("2026-11-16T00:00:00Z")
    private val a = LatLng(37.50, 127.0)
    private val b = LatLng(37.55, 127.0)
    private val c = LatLng(37.55, 127.001)
    private val d = LatLng(37.60, 127.001)
    private val route = Route(listOf(
        Leg(Mode.BUS, listOf(a, b), Duration.ofMinutes(10), "숲 입구", "시청", lineName = "402"),
        Leg(Mode.WALK, listOf(b, c), Duration.ofMinutes(3), "시청", "환승역"),
        Leg(Mode.SUBWAY, listOf(c, d), Duration.ofMinutes(10), "환승역", "공원", GroundLevel.ABOVE, lineName = "2호선"),
    ))
    private val request = TripRequest(NamedPlace("숲 입구", a), NamedPlace("공원", d), TravelMode.TRANSIT, null, SunGoal.AVOID)
    private val app get() = org.robolectric.RuntimeEnvironment.getApplication()

    private fun render(minute: Long, route: Route = this.route, request: TripRequest = this.request, fontScale: Float = 1f) {
        val source = SunPositionSource { _, _, _ -> SunPosition(90.0, 15.0) }
        val advisor = TripAdvisor(ExposureAnalyzer(source))
        val timeline = TripTimeline(route, advisor.advise(route, start, SunGoal.AVOID, totalDuration = route.duration), source, advisor)
        compose.setContent { CompositionLocalProvider(LocalMapRenderer provides MapRenderer.PREVIEW, androidx.compose.ui.platform.LocalDensity provides androidx.compose.ui.unit.Density(androidx.compose.ui.platform.LocalDensity.current.density, fontScale)) {
            SunSeatTheme { SimulationScreen(request, route, timeline.result, SimulationUiState(minute * 60_000, following = true),
                timeline.at(start.plusSeconds(minute * 60)), timeline.boundaries, SimulationActions(), zone = ZoneId.of("Asia/Seoul")) }
        } }
        compose.waitForIdle()
    }

    @Test fun boardingShowsTheLineBeingRidden() {
        render(0)
        compose.onNodeWithText("402 탑승 중", substring = true).assertExists()
    }

    @Test fun transferWalkShowsTheNextLine() {
        render(11)
        compose.onNodeWithText("다음 2호선 탑승", substring = true).assertExists()
    }

    @Test fun arrivalShowsNoRideCue() {
        render(23)
        assertEquals(0, compose.onAllNodesWithText("탑승", substring = true).fetchSemanticsNodes().size)
    }

    @Test fun destinationMarkerIsNotHiddenOnArrival() {
        val metres = { x: LatLng, y: LatLng -> ExposureAnalyzer.distanceMeters(x, y).toFloat() }
        val arrived = MapState(routes = listOf(route), origin = request.origin, destination = request.destination,
            cursor = MapCursor(d, 0.0, 90.0, 15.0), followCursor = true, routeProgress = 1f)
        assertFalse("arrival must keep the destination marker visible", arrived.coveredByVehicle(d, 32f, metres))
    }

    @Test fun rideCueNamesTheCurrentOrNextLine() {
        assertEquals(RideCue(true, Mode.BUS, listOf("402")), RouteLinePolicy.rideCue(route, 0))
        assertEquals(RideCue(false, Mode.SUBWAY, listOf("2호선")), RouteLinePolicy.rideCue(route, 1))
        assertEquals(RideCue(true, Mode.SUBWAY, listOf("2호선")), RouteLinePolicy.rideCue(route, 2))
        // A walk after the last ride has nothing to board.
        val tail = route.copy(legs = route.legs + Leg(Mode.WALK, listOf(d, a), Duration.ofMinutes(2), "공원", "도착"))
        assertNull(RouteLinePolicy.rideCue(tail, 3))
    }

    @Test fun vehicleGivesWayToTheDestinationMarkerOnArrival() {
        val playing = MapState(routes = listOf(route), cursor = MapCursor(c, 0.0, 90.0, 15.0), followCursor = true, routeProgress = .5f)
        assertEquals(playing.cursor, playing.visibleCursor)
        val arrived = playing.copy(cursor = MapCursor(d, 0.0, 90.0, 15.0), routeProgress = 1f)
        assertNull(arrived.visibleCursor)
        // The camera still follows the final position.
        assertEquals(d, arrived.followTarget)
        // A cursor outside playback (result map) is always drawn.
        assertEquals(playing.cursor, playing.copy(routeProgress = null).visibleCursor)
    }
    /** DESIGN 4.4: line names are never ellipsized; only the trip text may be shortened, even on a narrow screen with large text. */
    @Config(qualifiers = "w320dp-h844dp-xxhdpi")
    @Test fun lineNameIsNeverEllipsized() {
        val long = route.copy(legs = route.legs.mapIndexed { i, leg -> if (i == 0) leg.copy(lineName = "N26 / 9401 / 742") else leg })
        val far = TripRequest(NamedPlace("가상구 예시동 아주 긴 이름의 출발 정류장", a), NamedPlace("가상구 예시동 아주 긴 이름의 도착 공원", d), TravelMode.TRANSIT, null, SunGoal.AVOID)
        render(0, long, far, fontScale = 1.5f)
        val node = compose.onNodeWithText("N26 외 2 탑승 중").fetchSemanticsNode()
        val layouts = mutableListOf<androidx.compose.ui.text.TextLayoutResult>()
        node.config[androidx.compose.ui.semantics.SemanticsActions.GetTextLayoutResult].action!!.invoke(layouts)
        val l = layouts.single()
        assertFalse("line name must not be ellipsized", (0 until l.lineCount).any { l.isLineEllipsized(it) })
        assertEquals("the whole cue is laid out", l.layoutInput.text.length, l.getLineEnd(l.lineCount - 1))
    }
}
