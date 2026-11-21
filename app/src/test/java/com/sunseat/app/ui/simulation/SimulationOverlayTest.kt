package com.sunseat.app.ui.simulation

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import com.sunseat.app.R
import com.sunseat.app.domain.*
import com.sunseat.app.ui.assertNoTextUnderOverlays
import com.sunseat.app.ui.map.LocalMapRenderer
import com.sunseat.app.ui.map.MapMarkBoxesKey
import com.sunseat.app.ui.map.MapRenderer
import com.sunseat.app.ui.map.MapVisibleAreaKey
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
 * #161·#172: 햇빛 따라가기 지도 위 정보 카드(오버레이)는 측정한 높이만큼 지도 보이는 영역에서 빠지고, 큰 글자에서도 지도 표식을 덮지 않는다.
 * 버튼·라벨은 단어 중간에서 꺾이지 않는다. 따라가기를 끈(일시정지 화면) 지도 띠에도 경로를 알아볼 보이는 영역이 남는다.
 * 환경 행렬: 세로 390dp, 320dp·1.5배, 가로 832×384dp + 시스템 바 인셋 24dp.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(application = android.app.Application::class, sdk = [36], qualifiers = "w390dp-h844dp-xxhdpi")
class SimulationOverlayTest {
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

    private fun render(following: Boolean, fontScale: Float = 1f, bars: Boolean = false, minute: Long = 6) {
        val source = SunPositionSource { _, _, _ -> SunPosition(90.0, 15.0) }
        val advisor = TripAdvisor(ExposureAnalyzer(source))
        val timeline = TripTimeline(route, advisor.advise(route, start, SunGoal.AVOID, totalDuration = route.duration.plusMinutes(2)), source, advisor)
        compose.setContent { CompositionLocalProvider(LocalMapRenderer provides MapRenderer.PREVIEW, LocalDensity provides Density(LocalDensity.current.density, fontScale)) {
            SunSeatTheme {
                Box(if (bars) Modifier.windowInsetsPadding(WindowInsets(top = 24.dp, bottom = 24.dp)) else Modifier) {
                    SimulationScreen(request, route, timeline.result, SimulationUiState(minute * 60_000, following = following),
                        timeline.at(start.plusSeconds(minute * 60)), timeline.boundaries, SimulationActions(), zone = ZoneId.of("Asia/Seoul"))
                }
            }
        } }
        compose.waitForIdle()
    }

    private fun mapNode() = compose.onNodeWithTag("simulation_map").fetchSemanticsNode()
    private fun markBoxes(): List<Pair<String, Rect>> {
        val map = compose.onNode(androidx.compose.ui.test.SemanticsMatcher.keyIsDefined(MapMarkBoxesKey), useUnmergedTree = true).fetchSemanticsNode()
        val origin = map.boundsInRoot.topLeft
        return map.config[MapMarkBoxesKey].map { (kind, b) -> kind to Rect(origin.x + b.left, origin.y + b.top, origin.x + b.right, origin.y + b.bottom) }
    }

    /** 정보 카드가 보이는 지도 표식을 덮지 않고, 지도 보이는 영역은 카드 아래에서 시작한다. */
    private fun checkInfoCard() {
        val card = compose.onNodeWithTag("simulation_info_card").fetchSemanticsNode().boundsInRoot
        val marks = markBoxes()
        assertTrue("precondition: the map shows marks", marks.isNotEmpty())
        marks.forEach { (kind, box) ->
            val i = box.intersect(card)
            assertTrue("map mark $kind $box must not sit under the info card $card", i.width <= 0.5f || i.height <= 0.5f)
        }
        val map = compose.onNode(androidx.compose.ui.test.SemanticsMatcher.keyIsDefined(MapVisibleAreaKey), useUnmergedTree = true).fetchSemanticsNode()
        val visibleTop = map.boundsInRoot.top + map.config[MapVisibleAreaKey].top
        assertTrue("visible map area ($visibleTop) starts below the measured info card (${card.bottom})", visibleTop >= card.bottom - 0.5f)
        compose.assertNoTextUnderOverlays(listOf("simulation_details"))
    }

    /** 텍스트 [text]의 [word]가 한 줄 안에 있다(단어 중간 줄바꿈 없음). */
    private fun assertWordNotBroken(text: String, word: String) {
        val node = compose.onNodeWithText(text, substring = text != word, useUnmergedTree = true).fetchSemanticsNode()
        val results = mutableListOf<TextLayoutResult>()
        node.config[SemanticsActions.GetTextLayoutResult].action?.invoke(results)
        val layout = results.first()
        val full = layout.layoutInput.text.text
        val at = full.indexOf(word)
        assertTrue("'$word' in '$full'", at >= 0)
        assertEquals("'$word' must not break across lines in '$full'", layout.getLineForOffset(at), layout.getLineForOffset(at + word.length - 1))
    }

    @Test fun pausedPortrait() { render(following = false); checkInfoCard(); assertMapStripShowsRoute() }
    @Test fun followingPortrait() { render(following = true); checkInfoCard() }

    @Config(qualifiers = "w320dp-h844dp-xxhdpi")
    @Test fun pausedSmallLargeText() {
        render(following = false, fontScale = 1.5f)
        checkInfoCard()
        assertMapStripShowsRoute()
        assertWordNotBroken(app.getString(R.string.simulation_play), app.getString(R.string.simulation_play))
        assertWordNotBroken("표준 시내버스", "시내버스(추정)")
    }

    @Config(qualifiers = "w832dp-h384dp-xxhdpi")
    @Test fun pausedLandscapeWithSystemBars() { render(following = false, bars = true); checkInfoCard(); assertMapStripShowsRoute() }

    /**
     * 따라가기를 끈 지도는 경로 전체에 맞춘다. 보이는 영역이 너무 낮으면 정수 줌 맞춤이 경로를 차량 표식 밑으로 모은다(#168 PR #176 관찰).
     * 정보 카드 아래 보이는 지도 높이가 `Sizes.simulationMapMinVisible` 이상이다.
     */
    private fun assertMapStripShowsRoute() {
        val map = compose.onNode(androidx.compose.ui.test.SemanticsMatcher.keyIsDefined(MapVisibleAreaKey), useUnmergedTree = true).fetchSemanticsNode()
        val visible = map.config[MapVisibleAreaKey]
        val heightDp = (visible.bottom - visible.top) / compose.density.density
        assertTrue("visible map below the info card is only ${heightDp}dp", heightDp >= com.sunseat.app.ui.theme.Sizes.simulationMapMinVisible.value - 0.5f)
    }

}
