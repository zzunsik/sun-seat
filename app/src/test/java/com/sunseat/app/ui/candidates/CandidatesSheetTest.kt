package com.sunseat.app.ui.candidates

import android.app.Application
import androidx.compose.runtime.*
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.unit.dp
import com.sunseat.app.ui.map.LocalMapRenderer
import com.sunseat.app.ui.map.MapRenderer
import com.sunseat.app.ui.theme.SunSeatTheme
import org.junit.Assert.assertTrue
import kotlinx.coroutines.launch
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class, sdk = [36], qualifiers = "w390dp-h844dp-xxhdpi")
@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
class CandidatesSheetTest {
    @get:Rule val compose = createComposeRule(effectContext = object : androidx.compose.ui.MotionDurationScale { override val scaleFactor = 0f })

    @Test fun expandedSheetCoversMapAndCollapsedSheetRestoresMeasuredViewport() {
        lateinit var sheet: androidx.compose.material3.BottomSheetScaffoldState
        lateinit var scope: kotlinx.coroutines.CoroutineScope
        val state = CandidatesState.Options(CandidatesFixtures.options.toUi(java.time.LocalDateTime.of(2026, 11, 15, 9, 0)))
        compose.setContent {
            sheet = androidx.compose.material3.rememberBottomSheetScaffoldState()
            scope = rememberCoroutineScope()
            CompositionLocalProvider(LocalMapRenderer provides MapRenderer.PREVIEW) {
                SunSeatTheme { CandidatesScreen(CandidatesFixtures.transitRequest, state, CandidatesActions(), scaffoldState = sheet) }
            }
        }
        compose.mainClock.advanceTimeBy(5000)
        compose.waitForIdle()
        fun visibleHeight() = compose.onNodeWithTag("candidate_map").fetchSemanticsNode().config[com.sunseat.app.ui.map.MapVisibleHeightKey]
        val initial = visibleHeight()
        assertTrue(initial > 0)
        compose.runOnIdle { scope.launch { sheet.bottomSheetState.expand() } }
        compose.mainClock.advanceTimeBy(5000)
        compose.waitForIdle()
        assertEquals(0f, visibleHeight(), 0.01f)
        compose.runOnIdle { scope.launch { sheet.bottomSheetState.partialExpand() } }
        compose.mainClock.advanceTimeBy(5000)
        compose.waitForIdle()
        assertEquals(initial, visibleHeight(), 0.01f)
    }

    @Test fun previewCloseRestoresSelectionSortFiltersAndNonzeroScroll() {
        val base = CandidatesFixtures.options.toUi(java.time.LocalDateTime.of(2026, 11, 15, 9, 0)).first()
        var state by mutableStateOf(CandidatesState.Options((0..9).map { base.copy(id = it) }, sort = CandidateSort.WALK, filters = setOf(base.type)))
        val restoration = androidx.compose.ui.test.junit4.StateRestorationTester(compose)
        restoration.setContent {
            CompositionLocalProvider(LocalMapRenderer provides MapRenderer.PREVIEW) {
                SunSeatTheme { CandidatesScreen(CandidatesFixtures.transitRequest, state, CandidatesActions(onHighlight = { state = state.copy(selectedId = it.id) }, onSort = { state = state.copy(sort = it) })) }
            }
        }
        compose.onNodeWithTag("candidate_list").performScrollToIndex(5)
        compose.waitForIdle()
        val before = compose.onNodeWithTag("candidate_list").fetchSemanticsNode().config[SemanticsProperties.VerticalScrollAxisRange].value()
        compose.onNodeWithTag("candidate_5").performSemanticsAction(SemanticsActions.OnClick)
        compose.onNodeWithText("선택한 경로").assertExists()
        compose.onNodeWithText("전체 경로 보기").assertExists()
        restoration.emulateSavedInstanceStateRestore()
        compose.onNodeWithText("선택한 경로").assertExists()
        compose.waitUntil(10_000) { compose.onNodeWithTag("candidate_map").fetchSemanticsNode().config[com.sunseat.app.ui.map.MapCameraRevisionKey] > 0 }
        var revision = compose.onNodeWithTag("candidate_map").fetchSemanticsNode().config[com.sunseat.app.ui.map.MapCameraRevisionKey]
        compose.onNodeWithTag("candidate_map").performTouchInput { swipeLeft() }
        compose.onNodeWithText("전체 경로 보기").performSemanticsAction(SemanticsActions.OnClick)
        compose.waitUntil(10_000) { compose.onNodeWithTag("candidate_map").fetchSemanticsNode().config[com.sunseat.app.ui.map.MapCameraRevisionKey] > revision }
        revision = compose.onNodeWithTag("candidate_map").fetchSemanticsNode().config[com.sunseat.app.ui.map.MapCameraRevisionKey]
        compose.onNodeWithTag("candidate_map").performTouchInput { swipeLeft() }
        compose.onAllNodesWithContentDescription("경로 목록으로 돌아가기")[0].performSemanticsAction(SemanticsActions.OnClick)
        compose.waitForIdle()
        compose.onNodeWithTag("candidate_5").assertIsSelected()
        val after = compose.onNodeWithTag("candidate_list").fetchSemanticsNode().config[SemanticsProperties.VerticalScrollAxisRange].value()
        assertEquals(before, after, 0.001f)
        assertEquals(CandidateSort.WALK, state.sort)
        assertEquals(setOf(base.type), state.filters)
        // Reopening the same card requests one settled route fit, preserving the list.
        compose.onNodeWithTag("candidate_5").performSemanticsAction(SemanticsActions.OnClick)
        compose.onNodeWithText("선택한 경로").assertExists()
        compose.waitUntil(10_000) { compose.onNodeWithTag("candidate_map").fetchSemanticsNode().config[com.sunseat.app.ui.map.MapCameraRevisionKey] > revision }
        compose.mainClock.advanceTimeBy(1000)
        compose.waitForIdle()
        assertEquals(revision + 1, compose.onNodeWithTag("candidate_map").fetchSemanticsNode().config[com.sunseat.app.ui.map.MapCameraRevisionKey])
        revision++
        compose.onAllNodesWithContentDescription("경로 목록으로 돌아가기")[0].performSemanticsAction(SemanticsActions.OnClick)
        compose.waitForIdle()
        assertEquals(before, compose.onNodeWithTag("candidate_list").fetchSemanticsNode().config[SemanticsProperties.VerticalScrollAxisRange].value(), 0.001f)
        compose.onNodeWithText("최소 시간").performSemanticsAction(SemanticsActions.OnClick)
        compose.mainClock.advanceTimeBy(1000)
        compose.waitForIdle()
        assertEquals(revision, compose.onNodeWithTag("candidate_map").fetchSemanticsNode().config[com.sunseat.app.ui.map.MapCameraRevisionKey])
    }

    private fun bounds(tag: String) = compose.onNodeWithTag(tag, useUnmergedTree = true).fetchSemanticsNode().boundsInRoot

    /** 상단 헤더(펼친 헤더·축약 여정 바)가 시트와 겹쳐 글줄 조각으로 남지 않는다(#101). */
    private fun assertHeaderNotCut(where: String) {
        val header = bounds("candidates_header")
        val sheet = bounds("candidates_sheet_handle")
        assertTrue("$where: header bottom ${header.bottom} overlaps sheet top ${sheet.top}", header.bottom <= sheet.top + 0.5f)
    }

    private fun renderSheet(request: com.sunseat.app.domain.TripRequest = CandidatesFixtures.transitRequest, fontScale: Float = 1f, onBack: () -> Unit = {}): Pair<androidx.compose.material3.BottomSheetScaffoldState, kotlinx.coroutines.CoroutineScope> {
        lateinit var sheet: androidx.compose.material3.BottomSheetScaffoldState
        lateinit var scope: kotlinx.coroutines.CoroutineScope
        val state = CandidatesState.Options(CandidatesFixtures.options.toUi(java.time.LocalDateTime.of(2026, 11, 15, 9, 0)))
        compose.setContent {
            sheet = androidx.compose.material3.rememberBottomSheetScaffoldState()
            scope = rememberCoroutineScope()
            val density = androidx.compose.ui.platform.LocalDensity.current
            CompositionLocalProvider(LocalMapRenderer provides MapRenderer.PREVIEW, androidx.compose.ui.platform.LocalDensity provides androidx.compose.ui.unit.Density(density.density, fontScale)) {
                SunSeatTheme { CandidatesScreen(request, state, CandidatesActions(onBack = onBack), scaffoldState = sheet) }
            }
        }
        compose.mainClock.advanceTimeBy(5000)
        compose.waitForIdle()
        return sheet to scope
    }

    private fun expandAndCheckCompactBar(request: com.sunseat.app.domain.TripRequest = CandidatesFixtures.transitRequest, fontScale: Float = 1f) {
        var backs = 0
        val (sheet, scope) = renderSheet(request, fontScale) { backs++ }
        assertHeaderNotCut("collapsed")
        compose.onNodeWithText("출발", useUnmergedTree = true).assertExists()
        compose.runOnIdle { scope.launch { sheet.bottomSheetState.expand() } }
        compose.mainClock.advanceTimeBy(5000)
        compose.waitForIdle()
        assertEquals(androidx.compose.material3.SheetValue.Expanded, sheet.bottomSheetState.currentValue)
        assertHeaderNotCut("expanded")
        // 가려진 펼친 헤더 컨트롤은 접근성 트리에도 남지 않는다.
        compose.onNodeWithText("출발", useUnmergedTree = true).assertDoesNotExist()
        compose.onNodeWithContentDescription("출발지와 도착지 바꾸기").assertDoesNotExist()
        // 축약 여정 바: 완전한 여정 확인 + 뒤로 + 누르면 펼친 헤더로 복귀(편집 경로 유지).
        compose.onNodeWithText("${request.origin.name} → ${request.destination.name}", useUnmergedTree = true).assertIsDisplayed()
        compose.onNodeWithText("대중교통 · 지금 출발", useUnmergedTree = true).assertIsDisplayed()
        compose.onNodeWithContentDescription("뒤로").assertIsDisplayed().performSemanticsAction(SemanticsActions.OnClick)
        assertEquals(1, backs)
        compose.onNodeWithTag("candidates_journey_bar").assertHasClickAction().performSemanticsAction(SemanticsActions.OnClick)
        compose.mainClock.advanceTimeBy(5000)
        compose.waitForIdle()
        assertEquals(androidx.compose.material3.SheetValue.PartiallyExpanded, sheet.bottomSheetState.currentValue)
        assertHeaderNotCut("restored")
        compose.onNodeWithContentDescription("출발지와 도착지 바꾸기").assertIsDisplayed()
        compose.onNodeWithText("지금 출발", useUnmergedTree = true).assertIsDisplayed()
    }

    @Test fun expandedSheetShowsCompactJourneyBarInsteadOfHeaderFragment() = expandAndCheckCompactBar()

    @Test @Config(qualifiers = "w320dp-h640dp-xxhdpi")
    fun expandedSheetSmallScreenLargeTextLongNames() = expandAndCheckCompactBar(
        CandidatesFixtures.transitRequest.copy(
            origin = CandidatesFixtures.transitRequest.origin.copy(name = "아주 긴 견본 출발지 이름과 부가 설명"),
            destination = CandidatesFixtures.transitRequest.destination.copy(name = "아주 긴 견본 목적지 이름과 부가 설명"),
        ), fontScale = 1.5f)

    @Test @Config(qualifiers = "w844dp-h390dp-xxhdpi")
    fun expandedSheetLandscape() = expandAndCheckCompactBar()

    @Test fun draggingSheetNeverLeavesHeaderFragment() {
        renderSheet()
        val step = with(androidx.compose.ui.unit.Density(3f)) { 40.dp.toPx() }
        compose.onNodeWithTag("candidate_list").performTouchInput { down(topCenter + androidx.compose.ui.geometry.Offset(0f, 10f)) }
        repeat(10) { index ->
            compose.onNodeWithTag("candidate_list").performTouchInput { moveBy(androidx.compose.ui.geometry.Offset(0f, -step)) }
            compose.waitForIdle()
            assertHeaderNotCut("drag step $index")
        }
        compose.onNodeWithTag("candidates_journey_bar").assertExists()
        compose.onNodeWithTag("candidate_list").performTouchInput { up() }
        compose.mainClock.advanceTimeBy(5000)
        compose.waitForIdle()
        assertHeaderNotCut("drag settled")
    }

    @Test fun expandedSheetSurvivesRecreation() {
        val restoration = androidx.compose.ui.test.junit4.StateRestorationTester(compose)
        lateinit var sheet: androidx.compose.material3.BottomSheetScaffoldState
        lateinit var scope: kotlinx.coroutines.CoroutineScope
        val state = CandidatesState.Options(CandidatesFixtures.options.toUi(java.time.LocalDateTime.of(2026, 11, 15, 9, 0)))
        restoration.setContent {
            sheet = androidx.compose.material3.rememberBottomSheetScaffoldState()
            scope = rememberCoroutineScope()
            CompositionLocalProvider(LocalMapRenderer provides MapRenderer.PREVIEW) {
                SunSeatTheme { CandidatesScreen(CandidatesFixtures.transitRequest, state, CandidatesActions(), scaffoldState = sheet) }
            }
        }
        compose.mainClock.advanceTimeBy(5000)
        compose.runOnIdle { scope.launch { sheet.bottomSheetState.expand() } }
        compose.mainClock.advanceTimeBy(5000)
        compose.waitForIdle()
        restoration.emulateSavedInstanceStateRestore()
        compose.mainClock.advanceTimeBy(5000)
        compose.waitForIdle()
        assertEquals(androidx.compose.material3.SheetValue.Expanded, sheet.bottomSheetState.currentValue)
        assertHeaderNotCut("recreated")
        compose.onNodeWithTag("candidates_journey_bar").assertIsDisplayed()
    }
}
