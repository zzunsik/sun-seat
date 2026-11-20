package com.sunseat.app.ui.candidates

import androidx.compose.runtime.*
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import com.sunseat.app.domain.Leg
import com.sunseat.app.domain.Mode
import com.sunseat.app.domain.Route
import com.sunseat.app.domain.TravelMode
import com.sunseat.app.domain.TripRequest
import com.sunseat.app.ui.map.LocalMapRenderer
import com.sunseat.app.ui.map.MapRenderer
import com.sunseat.app.ui.theme.SunSeatTheme
import java.time.Duration
import java.time.LocalDateTime
import kotlinx.coroutines.launch
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * #102: 선택 경로 미리보기의 축약 여정 바와 하단 요약.
 * 접힌 시트는 총시간·환승 수·시간 막대+라벨·노선 조합·자리 보기, 펼친 시트는 시각·요금·전 구간 타임라인(DESIGN 12.1, 16.2).
 */
@RunWith(RobolectricTestRunner::class)
@Config(application = android.app.Application::class, sdk = [36], qualifiers = "w390dp-h844dp-xxhdpi")
@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
class RoutePreviewTest {
    @get:Rule val compose = createComposeRule(effectContext = object : androidx.compose.ui.MotionDurationScale { override val scaleFactor = 0f })

    private val now = LocalDateTime.of(2026, 11, 15, 9, 0)
    private lateinit var sheet: androidx.compose.material3.BottomSheetScaffoldState
    private lateinit var scope: kotlinx.coroutines.CoroutineScope

    private fun open(item: CandidateUi, request: TripRequest = CandidatesFixtures.transitRequest, fontScale: Float = 1f, onSelect: (CandidateUi) -> Unit = {}) {
        var state by mutableStateOf(CandidatesState.Options(listOf(item)))
        compose.setContent {
            sheet = androidx.compose.material3.rememberBottomSheetScaffoldState()
            scope = rememberCoroutineScope()
            val density = androidx.compose.ui.platform.LocalDensity.current
            CompositionLocalProvider(LocalMapRenderer provides MapRenderer.PREVIEW,
                androidx.compose.ui.platform.LocalDensity provides androidx.compose.ui.unit.Density(density.density, fontScale)) {
                SunSeatTheme { CandidatesScreen(request, state, CandidatesActions(onHighlight = { state = state.copy(selectedId = it.id) }, onSelect = onSelect), now = now, scaffoldState = sheet) }
            }
        }
        compose.mainClock.advanceTimeBy(5000)
        compose.onNodeWithTag("candidate_${item.id}").performSemanticsAction(SemanticsActions.OnClick)
        compose.mainClock.advanceTimeBy(5000)
        compose.waitForIdle()
        compose.onNodeWithTag("candidates_preview_sheet").assertExists()
    }

    private fun expand() {
        compose.runOnIdle { scope.launch { sheet.bottomSheetState.expand() } }
        compose.mainClock.advanceTimeBy(5000)
        compose.waitForIdle()
    }

    private fun lines() = compose.onNodeWithTag("preview_lines", useUnmergedTree = true)

    @Test fun collapsedSummaryShowsTimeTransfersBarAndLineCombinationOnly() {
        open(CandidatesFixtures.subwayOnly.toUi(0, now, false))
        compose.onNodeWithText("38분", useUnmergedTree = true).assertIsDisplayed()
        compose.onNodeWithText("환승 1회", useUnmergedTree = true).assertIsDisplayed()
        compose.onNodeWithTag("route_bar_0", useUnmergedTree = true).assertIsDisplayed()
        compose.onNodeWithTag("route_bar_0", useUnmergedTree = true).assert(hasContentDescription("1호선 12분 · 도보 3분 · 2호선 18분", substring = true))
        lines().assertIsDisplayed().assertTextEquals("1호선 → 2호선")
        compose.onNodeWithText("자리 보기").assertIsDisplayed()
        // 시각·요금·전 구간 상세는 접힌 상태에 강제하지 않는다(11-19 실기기 비교).
        compose.onNodeWithText("09:00 → 09:38", useUnmergedTree = true).assertIsNotDisplayed()
        compose.onNodeWithTag("route_timeline_0", useUnmergedTree = true).assertIsNotDisplayed()
        // 예전 제목 "선택한 경로"와 시트 안 별도 닫기 X는 없다.
        compose.onNodeWithText("선택한 경로").assertDoesNotExist()
        compose.onAllNodesWithContentDescription("경로 목록으로 돌아가기").assertCountEquals(1)
    }

    @Test fun expandedSheetShowsScheduleFareEndpointsAndBoardingTimeline() {
        open(CandidatesFixtures.subwayOnly.toUi(0, now, false))
        expand()
        compose.onNodeWithText("09:00 → 09:38", useUnmergedTree = true).performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("1,550원", useUnmergedTree = true).performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("서울역 승차", useUnmergedTree = true).performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("시청 하차", useUnmergedTree = true).assertExists()
        compose.onNodeWithText("강남 하차", useUnmergedTree = true).performScrollTo().assertIsDisplayed()
        // 축약 제목이 잘려도 전체 장소명을 하단 상세에서 확인한다.
        compose.onNodeWithText("출발 서울역", useUnmergedTree = true).assertExists()
        compose.onNodeWithText("도착 강남역", useUnmergedTree = true).assertExists()
    }

    @Test fun busLineKeepsModeWordAndZeroTransfers() {
        open(CandidatesFixtures.busOnly.toUi(0, now, false))
        lines().assertTextEquals("버스 140")
        compose.onNodeWithText("환승 0회", useUnmergedTree = true).assertIsDisplayed()
    }

    @Test fun alternateLinesMultipleTransfersAndMissingDataAreNotInvented() {
        open(CandidatesFixtures.readable.toUi(0, null, false))
        lines().assertTextEquals("버스 견본 광역급행 아주 긴 노선 이름 12345 / 대체노선 67890 → 2호선 → 버스")
        compose.onNodeWithText("환승 2회", useUnmergedTree = true).assertIsDisplayed()
        compose.onNodeWithText("자리 보기").assertIsDisplayed()
        expand()
        // 요금·도착 시각이 없으면 만들지 않는다.
        compose.onAllNodesWithText("원", substring = true, useUnmergedTree = true).assertCountEquals(0)
        compose.onAllNodesWithText("→ ", substring = true, useUnmergedTree = true).filter(hasText(":", substring = true)).assertCountEquals(0)
        compose.onNodeWithText("견본 출발 정류장 이름이 아주 긴 곳 승차", useUnmergedTree = true).assertExists()
    }

    @Test fun walkingOnlyRouteHasNoLineCombinationOrTransferCount() {
        val base = CandidatesFixtures.subwayOnly.toUi(0, now, false)
        val walk = Leg(Mode.WALK, base.chosen.route.legs.first().path, Duration.ofMinutes(25), "", "")
        open(base.copy(transfers = 0, chips = listOf(LegChip(Mode.WALK)), boardAt = "", alightAt = "", chosen = base.chosen.copy(route = Route(listOf(walk)))))
        compose.onNodeWithText("38분", useUnmergedTree = true).assertIsDisplayed()
        lines().assertDoesNotExist()
        compose.onAllNodesWithText("환승", substring = true, useUnmergedTree = true).assertCountEquals(0)
        compose.onNodeWithText("자리 보기").assertIsDisplayed()
    }

    @Test fun taxiShowsModeAndTaxiFareWithoutTransitWording() {
        val base = CandidatesFixtures.subwayOnly.toUi(0, now, false)
        val car = Leg(Mode.CAR, base.chosen.route.legs.first().path, Duration.ofMinutes(25), "", "")
        open(base.copy(totalDuration = Duration.ofMinutes(25), transfers = 0, fare = 18000, chips = listOf(LegChip(Mode.CAR)), boardAt = "", alightAt = "",
            chosen = base.chosen.copy(route = Route(listOf(car)))), CandidatesFixtures.transitRequest.copy(mode = TravelMode.TAXI))
        compose.onNodeWithTag("route_bar_0", useUnmergedTree = true).assertIsDisplayed().assert(hasContentDescription("택시 25분", substring = true))
        lines().assertDoesNotExist()
        compose.onAllNodesWithText("환승", substring = true, useUnmergedTree = true).assertCountEquals(0)
        compose.onNodeWithText("택시 · 지금 출발", useUnmergedTree = true).assertIsDisplayed()
        expand()
        compose.onNodeWithText("예상 택시 요금 18,000원", useUnmergedTree = true).performScrollTo().assertIsDisplayed()
    }

    @Test fun seatActionStillOpensResultAndFitOnlyMovesCamera() {
        var selected: CandidateUi? = null
        val item = CandidatesFixtures.subwayOnly.toUi(0, now, false)
        open(item) { selected = it }
        fun map() = compose.onNodeWithTag("candidate_map").fetchSemanticsNode().config
        val ids = map()[CandidateMapRouteIdsKey]
        val revision = map()[com.sunseat.app.ui.map.MapCameraRevisionKey]
        compose.onNodeWithText("지도 맞춤").assertIsDisplayed().performSemanticsAction(SemanticsActions.OnClick)
        compose.mainClock.advanceTimeBy(5000)
        compose.waitForIdle()
        assertTrue(map()[com.sunseat.app.ui.map.MapCameraRevisionKey] > revision)
        assertEquals(ids, map()[CandidateMapRouteIdsKey])
        compose.onNodeWithTag("candidates_preview_sheet").assertExists()
        compose.onNodeWithText("자리 보기").performSemanticsAction(SemanticsActions.OnClick)
        assertEquals(item, selected)
    }

    /** 상단: 뒤로 하나 + 여정 2줄까지. 맞춤 행동은 지도 위에 있어 제목 폭을 빼앗지 않는다. */
    private fun titleKeepsWidth(origin: String, destination: String, fontScale: Float) {
        val request = CandidatesFixtures.transitRequest.copy(origin = CandidatesFixtures.transitRequest.origin.copy(name = origin),
            destination = CandidatesFixtures.transitRequest.destination.copy(name = destination))
        open(CandidatesFixtures.readable.toUi(0, null, false), request, fontScale)
        val title = compose.onNodeWithText("$origin → $destination", useUnmergedTree = true).assertIsDisplayed()
        val header = compose.onNodeWithTag("candidates_header").fetchSemanticsNode().boundsInRoot
        val titleBounds = compose.onNodeWithTag("candidates_journey_bar", useUnmergedTree = true).fetchSemanticsNode().boundsInRoot
        val touch = with(compose.density) { com.sunseat.app.ui.theme.Spacing.touch.toPx() }
        // 제목은 뒤로 버튼과 여백을 뺀 폭을 거의 모두 쓴다(예전에는 '전체 경로 보기'가 큰 폭을 차지했다).
        assertTrue("title ${titleBounds.right} should reach near ${header.right}", titleBounds.right >= header.right - touch)
        compose.onNodeWithText("지도 맞춤").assertIsDisplayed()
        val fit = compose.onNodeWithText("지도 맞춤").fetchSemanticsNode().boundsInRoot
        assertTrue("fit action sits on the map below the header", fit.top >= header.bottom)
        val layout = mutableListOf<androidx.compose.ui.text.TextLayoutResult>()
        title.fetchSemanticsNode().config[SemanticsActions.GetTextLayoutResult].action!!.invoke(layout)
        assertTrue(layout.first().lineCount <= 2)
        compose.onNodeWithText("자리 보기").assertIsDisplayed()
        compose.onNodeWithText("환승 2회", useUnmergedTree = true).assertIsDisplayed()
        // 접힌 시트는 상단 아래 영역의 절반을 넘지 않아 지도를 남긴다.
        val handle = compose.onNodeWithTag("candidates_sheet_handle", useUnmergedTree = true).fetchSemanticsNode().boundsInRoot
        val root = compose.onRoot().fetchSemanticsNode().boundsInRoot
        assertTrue("sheet ${handle.top} leaves map above half of ${header.bottom}..${root.bottom}", root.bottom - handle.top <= (root.bottom - header.bottom) / 2 + 1f)
    }

    @Test fun titleUsesWidthAndFitActionMovesToMap() = titleKeepsWidth("서울역", "강남역", 1f)

    @Test @Config(qualifiers = "w320dp-h640dp-xxhdpi")
    fun smallScreenLargeTextLongNames() = titleKeepsWidth("아주 긴 견본 출발지 이름과 부가 설명", "아주 긴 견본 목적지 이름과 부가 설명", 1.5f)

    @Test @Config(qualifiers = "w844dp-h390dp-xxhdpi")
    fun landscapeKeepsSeatActionAndSummary() = titleKeepsWidth("서울역", "강남역", 1f)
}
