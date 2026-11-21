package com.sunseat.app.ui.simulation

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipeUp
import androidx.compose.ui.unit.Density
import com.sunseat.app.R
import com.sunseat.app.domain.*
import com.sunseat.app.ui.map.LocalMapRenderer
import com.sunseat.app.ui.map.MapRenderer
import com.sunseat.app.ui.theme.SunSeatTheme
import java.time.Duration
import java.time.Instant
import java.time.ZoneId
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * #155: 햇빛 따라가기 본문이 재생 조작 패널 위에서 끝날 때 경계에서 글자가 반쯤 잘려 보이지 않는다.
 * 더 내릴 내용이 있으면 경계에 구분선과 흐림 띠를 두고, 끝까지 내리면 마지막 문장이 패널 위에 온전히 보인다.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(application = android.app.Application::class, sdk = [36], qualifiers = "w390dp-h844dp-xxhdpi")
class SimulationDetailsEdgeTest {
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
    private val app get() = org.robolectric.RuntimeEnvironment.getApplication()

    private fun render(minute: Long = 6, fontScale: Float = 1f) {
        val source = SunPositionSource { _, _, _ -> SunPosition(90.0, 15.0) }
        val advisor = TripAdvisor(ExposureAnalyzer(source))
        val timeline = TripTimeline(route, advisor.advise(route, start, SunGoal.AVOID, totalDuration = route.duration.plusMinutes(2)), source, advisor)
        compose.setContent { CompositionLocalProvider(LocalMapRenderer provides MapRenderer.PREVIEW, LocalDensity provides Density(LocalDensity.current.density, fontScale)) {
            SunSeatTheme { SimulationScreen(request, route, timeline.result, SimulationUiState(minute * 60_000, following = true),
                timeline.at(start.plusSeconds(minute * 60)), timeline.boundaries, SimulationActions(), zone = ZoneId.of("Asia/Seoul")) }
        } }
        compose.waitForIdle()
    }

    private fun checkEdgeThenFullyScrolled() {
        // At rest the body continues under the controls: the boundary is marked rather than showing a bare half-cut line.
        assertEquals("more content below must be marked at the control boundary", 1, compose.onAllNodesWithTag(DETAILS_MORE_TAG).fetchSemanticsNodes().size)
        // Scroll to the very end; the arrival note is the body's last item.
        val note = "대기·접근 시간은 재생에서 제외해요."
        repeat(4) { compose.onNodeWithTag(DETAILS_TAG).performTouchInput { swipeUp() } }
        compose.waitForIdle()
        val viewport = compose.onNodeWithTag(DETAILS_TAG).fetchSemanticsNode().boundsInRoot
        val last = compose.onNodeWithText(note, substring = true).fetchSemanticsNode().boundsInRoot
        val slider = compose.onNodeWithText(app.getString(R.string.simulation_scrub_time, "09:06")).fetchSemanticsNode().boundsInRoot
        assertTrue("the last sentence must end above the controls: $last vs $viewport", last.bottom <= viewport.bottom && viewport.bottom <= slider.top)
        assertEquals("no boundary mark once the end is reached", 0, compose.onAllNodesWithTag(DETAILS_MORE_TAG).fetchSemanticsNodes().size)
    }

    @Test fun portraitBodyEndsAboveControls() { render(); checkEdgeThenFullyScrolled() }

    @Config(qualifiers = "w320dp-h844dp-xxhdpi")
    @Test fun portraitLargeTextBodyEndsAboveControls() { render(fontScale = 1.5f); checkEdgeThenFullyScrolled() }

    @Config(qualifiers = "w844dp-h390dp-xxhdpi")
    @Test fun landscapeBodyEndsAboveControls() { render(); checkEdgeThenFullyScrolled() }

    private companion object {
        const val DETAILS_TAG = "simulation_details"
        const val DETAILS_MORE_TAG = "simulation_details_more"
    }
}
