package com.sunseat.app.ui.candidates

import android.app.Application
import androidx.activity.ComponentActivity
import androidx.compose.material3.BottomSheetScaffoldState
import androidx.compose.material3.SheetValue
import androidx.compose.material3.rememberBottomSheetScaffoldState
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.click
import androidx.compose.ui.test.junit4.StateRestorationTester
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performScrollToIndex
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.foundation.layout.requiredSize
import com.sunseat.app.ui.map.LocalMapRenderer
import com.sunseat.app.ui.map.MapCameraRevisionKey
import com.sunseat.app.ui.map.MapRenderer
import com.sunseat.app.ui.theme.SunSeatTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * #171: 후보 화면 전환 테스트. 끌기·누르기는 실제 터치로 보내고(의미 동작은 제스처 상태를 건너뛴다, #162),
 * 전환마다 시트 자리·선택·스크롤·카메라 맞춤 요청을 함께 단언한다(docs/bug-analysis-2026-11.md 4.2·5.1).
 */
@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class, sdk = [36], qualifiers = "w390dp-h844dp-xxhdpi")
@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
class CandidatesTransitionTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()
    private lateinit var sheet: BottomSheetScaffoldState
    private var state by mutableStateOf(CandidatesState.Options(emptyList()))
    private var request by mutableStateOf(CandidatesFixtures.transitRequest)

    private fun settle() { compose.mainClock.advanceTimeBy(5000); compose.waitForIdle() }
    private fun revision() = compose.onNodeWithTag("candidate_map").fetchSemanticsNode().config[MapCameraRevisionKey]
    private fun scroll() = compose.onNodeWithTag("candidate_list").fetchSemanticsNode().config[SemanticsProperties.VerticalScrollAxisRange].value()

    private fun items(count: Int = 10): List<CandidateUi> {
        val now = java.time.LocalDateTime.of(2026, 11, 15, 9, 0)
        return (0 until count).map { (if (it % 2 == 0) CandidatesFixtures.subwayOnly else CandidatesFixtures.busOnly).toUi(it, now, it == 0) }
    }

    /**
     * 방향 전환 테스트용 화면 틀. 정사각 창 안에 세로(390×844dp)·가로(844×390dp) 화면을 두고 같은 크기의 구성(LocalConfiguration)을 준다.
     * Robolectric에서 한정자를 바꾸면 활동이 다시 만들어져 테스트 내용이 사라지므로, 구성 재생성은 StateRestorationTester로 흉내 낸다.
     */
    private var frame by mutableStateOf<Pair<Int, Int>?>(null)

    @androidx.compose.runtime.Composable private fun Content() {
        sheet = rememberBottomSheetScaffoldState()
        val screen: @androidx.compose.runtime.Composable () -> Unit = {
            CompositionLocalProvider(LocalMapRenderer provides MapRenderer.PREVIEW) {
                SunSeatTheme {
                    CandidatesScreen(request, state, CandidatesActions(
                    onHighlight = { state = state.copy(selectedId = it.id) },
                    onCategory = { state = state.copy(category = it) },
                    onSort = { state = state.copy(sort = it) },
                ), scaffoldState = sheet)
                }
            }
        }
        val size = frame
        if (size == null) screen() else {
            val base = androidx.compose.ui.platform.LocalConfiguration.current
            val configuration = android.content.res.Configuration(base).apply {
                screenWidthDp = size.first; screenHeightDp = size.second
                orientation = if (size.first > size.second) android.content.res.Configuration.ORIENTATION_LANDSCAPE else android.content.res.Configuration.ORIENTATION_PORTRAIT
            }
            CompositionLocalProvider(androidx.compose.ui.platform.LocalConfiguration provides configuration) {
                androidx.compose.foundation.layout.Box(androidx.compose.ui.Modifier.wrapContentSize(androidx.compose.ui.Alignment.TopStart, unbounded = true)
                    .requiredSize(androidx.compose.ui.unit.DpSize(size.first.dp, size.second.dp)).testTag("frame")) { screen() }
            }
        }
    }

    private fun rotate(restoration: StateRestorationTester, landscape: Boolean) {
        frame = if (landscape) 844 to 390 else 390 to 844
        restoration.emulateSavedInstanceStateRestore()
        settle()
    }

    private fun render() {
        state = CandidatesState.Options(items())
        compose.setContent { Content() }
        settle()
    }

    private fun renderRestorable(): StateRestorationTester {
        state = CandidatesState.Options(items())
        frame = 390 to 844
        val restoration = StateRestorationTester(compose)
        restoration.setContent { Content() }
        settle()
        return restoration
    }

    private fun dragHandle(dy: Float) {
        compose.onNodeWithTag("candidates_sheet_handle", useUnmergedTree = true).performTouchInput {
            down(center)
            repeat(30) { moveBy(Offset(0f, dy / 30), delayMillis = 25) }
            up()
        }
        settle()
    }

    private fun tap(tag: String) { compose.onNodeWithTag(tag).performTouchInput { click() }; settle() }
    private fun closeWithButton() {
        compose.onAllNodesWithContentDescription("경로 목록으로 돌아가기")[0].performTouchInput { click() }
        settle()
    }
    private fun closeWithBack() {
        compose.runOnUiThread { compose.activity.onBackPressedDispatcher.onBackPressed() }
        settle()
    }

    private fun assertSheet(value: SheetValue) {
        assertEquals(value, sheet.bottomSheetState.currentValue)
        assertEquals("sheet must settle", value, sheet.bottomSheetState.targetValue)
        assertFalse("sheet must settle", sheet.bottomSheetState.isAnimationRunning)
    }

    /** 선택 카드 윗부분(시간 줄)이 시트 손잡이 아래, 화면 아래끝 위로 터치 높이만큼 보인다. */
    private fun assertCardVisible(id: Int) {
        val time = compose.onNodeWithTag("candidate_time_$id", useUnmergedTree = true).fetchSemanticsNode().boundsInRoot
        val screen = (compose.onAllNodes(androidx.compose.ui.test.hasTestTag("frame")).fetchSemanticsNodes().firstOrNull() ?: compose.onRoot().fetchSemanticsNode()).boundsInRoot
        val touch = with(compose.density) { com.sunseat.app.ui.theme.Spacing.touch.toPx() }
        compose.onAllNodes(androidx.compose.ui.test.hasTestTag("candidates_sheet_handle"), useUnmergedTree = true).fetchSemanticsNodes().firstOrNull()?.let { handle ->
            assertTrue("card $id top ${time.top} is under the sheet handle ${handle.boundsInRoot.bottom}", time.top >= handle.boundsInRoot.bottom - 0.5f)
        }
        assertTrue("card $id top ${time.top} must be visible above ${screen.bottom}", time.top + touch <= screen.bottom + 0.5f)
    }

    /** 끌어 완전 확장 → 카드 누름(미리보기) → 닫기: 완전 확장·선택 카드·축약 여정 바로 돌아온다(#162 #145 #101). */
    @Test fun dragExpandTapPreviewCloseRestoresExpandedList() {
        render()
        dragHandle(-3000f)
        assertSheet(SheetValue.Expanded)
        compose.onNodeWithTag("candidate_list").performScrollToIndex(6)
        settle()
        val before = scroll()
        tap("candidate_5")
        compose.onNodeWithTag("candidates_preview_sheet").assertExists()
        assertSheet(SheetValue.PartiallyExpanded)
        closeWithButton()
        assertSheet(SheetValue.Expanded)
        compose.onNodeWithTag("candidates_journey_bar").assertExists()
        compose.onNodeWithTag("candidate_5").assertIsSelected()
        assertCardVisible(5)
        assertEquals("a visible selected card keeps the list position (#99)", before, scroll(), 0.5f)
        // 복원 뒤에도 시트가 정착해 누름이 먹는다.
        tap("candidate_6")
        compose.onNodeWithTag("candidates_preview_sheet").assertExists()
    }

    /** 기본 높이에서 카드 누름 → Back: 기본 높이·선택 카드, 지도 띠에 한 번 다시 맞춘다(#135). */
    @Test fun defaultTapPreviewBackRefitsListStrip() {
        render()
        tap("candidate_1")
        compose.onNodeWithTag("candidates_preview_sheet").assertExists()
        val inPreview = revision()
        closeWithBack()
        compose.onNodeWithTag("candidates_sheet").assertExists()
        assertSheet(SheetValue.PartiallyExpanded)
        compose.onNodeWithTag("candidate_1").assertIsSelected()
        assertCardVisible(1)
        assertEquals("closing refits the list map strip once", inPreview + 1, revision())
    }

    /** 시트를 올렸다 내린 뒤 카드 누름 → 닫기 → 다른 카드 누름이 모두 먹는다(제스처 상태에 멈추지 않음, #162). */
    @Test fun dragUpDownThenPreviewRoundTripKeepsTapsWorking() {
        render()
        dragHandle(-3000f)
        dragHandle(3000f)
        assertSheet(SheetValue.PartiallyExpanded)
        tap("candidate_0")
        compose.onNodeWithTag("candidates_preview_sheet").assertExists()
        closeWithButton()
        assertSheet(SheetValue.PartiallyExpanded)
        tap("candidate_2")
        compose.onNodeWithTag("candidates_preview_sheet").assertExists()
        compose.onNodeWithTag("candidate_map").fetchSemanticsNode().config[CandidateMapRouteIdsKey].let { assertEquals(listOf(2), it) }
    }

    /** 범주·정렬을 실제 터치로 바꾸면 맨 위부터, 같은 범주를 다시 누르면 위치를 유지한다(#136 #83). */
    @Test fun categoryAndSortTouchesScrollToTop() {
        render()
        dragHandle(-3000f)
        compose.onNodeWithTag("candidate_list").performScrollToIndex(6)
        settle()
        assertTrue(scroll() > 0f)
        compose.onNodeWithContentDescription("버스 경로 5개").performTouchInput { click() }
        settle()
        assertEquals(RouteCategory.BUS, state.category)
        assertEquals(0f, scroll(), 0.5f)
        compose.onNodeWithTag("candidate_list").performScrollToIndex(4)
        settle()
        val kept = scroll()
        assertTrue(kept > 0f)
        compose.onNodeWithContentDescription("버스 경로 5개").performTouchInput { click() }
        settle()
        assertEquals("same category keeps the position", kept, scroll(), 0.5f)
        compose.onNodeWithTag("candidates_sort").performTouchInput { click() }
        settle()
        compose.onNodeWithText("최소 시간").performTouchInput { click() }
        settle()
        assertEquals(CandidateSort.TIME, state.sort)
        assertEquals(0f, scroll(), 0.5f)
        assertSheet(SheetValue.Expanded)
    }

    @Config(qualifiers = "w844dp-h844dp-xxhdpi")
    /** 미리보기 중 가로로 돌리면 같은 미리보기를 가로 배치로 그리고, 닫으면 선택 카드가 보이는 목록 패널로 돌아온다(#144). 세로로 돌아와도 같다. */
    @Test fun rotatingWithPreviewKeepsStateAcrossLayouts() {
        val restoration = renderRestorable()
        tap("candidate_3")
        compose.onNodeWithTag("candidates_preview_sheet").assertExists()
        rotate(restoration, landscape = true)
        compose.onNodeWithTag("candidates_preview_sheet").assertExists()
        compose.onNodeWithTag("candidates_sheet_handle", useUnmergedTree = true).assertDoesNotExist()
        compose.onNodeWithTag("preview_seat").assertIsDisplayed()
        closeWithBack()
        compose.onNodeWithTag("candidates_sheet").assertExists()
        compose.onNodeWithTag("candidate_3").assertIsSelected()
        assertCardVisible(3)
        rotate(restoration, landscape = false)
        compose.onNodeWithTag("candidates_sheet_handle", useUnmergedTree = true).assertExists()
        assertSheet(SheetValue.PartiallyExpanded)
        compose.onNodeWithTag("candidate_3").assertIsSelected()
        tap("candidate_4")
        compose.onNodeWithTag("candidates_preview_sheet").assertExists()
    }

    @Config(qualifiers = "w844dp-h844dp-xxhdpi")
    /** 세로 미리보기 → 가로 → 세로로 돌아오면 미리보기는 요약 높이로 열려 있다. */
    @Test fun rotatingBackToPortraitKeepsPreviewAtSummaryHeight() {
        val restoration = renderRestorable()
        dragHandle(-3000f)
        tap("candidate_2")
        rotate(restoration, landscape = true)
        rotate(restoration, landscape = false)
        compose.onNodeWithTag("candidates_preview_sheet").assertExists()
        assertSheet(SheetValue.PartiallyExpanded)
        // 들어가기 전 완전 확장은 회전 뒤에도 기억한다(#145).
        closeWithButton()
        assertSheet(SheetValue.Expanded)
        assertCardVisible(2)
    }

    /** 화면 읽기가 꺼져 있으면 닫은 뒤 초점을 옮기지 않는다(초점을 받은 카드는 목록을 스크롤하므로 보던 위치 #99를 지킨다). */
    @Test fun closingWithoutScreenReaderLeavesFocusAndScroll() {
        render()
        compose.onNodeWithTag("candidate_list").performScrollToIndex(3)
        settle()
        val before = scroll()
        tap("candidate_2")
        closeWithBack()
        compose.onAllNodes(androidx.compose.ui.test.isFocused()).assertCountEquals(0)
        assertEquals(before, scroll(), 0.5f)
    }

    /**
     * 완전 확장으로 되돌리는 도중 다른 카드를 열면 앞 닫기의 남은 효과(복원·카드 보이기·닫기 맞춤)를 버리고 새 미리보기를 요약 높이로 연다.
     * 움직이는 시트 위의 손가락 누름은 시트가 먼저 받으므로, 애니메이션 도중에도 들어오는 화면 읽기의 두 번 누름(접근성 누름 동작)으로 연다.
     */
    @Test fun tappingAnotherCardDuringRestoreOpensItsPreview() {
        render()
        dragHandle(-3000f)
        tap("candidate_1")
        compose.mainClock.autoAdvance = false
        compose.onAllNodesWithContentDescription("경로 목록으로 돌아가기")[0].performTouchInput { click() }
        var frames = 0
        while (frames < 30 && compose.onAllNodes(androidx.compose.ui.test.hasTestTag("candidate_2")).fetchSemanticsNodes().isEmpty()) { compose.mainClock.advanceTimeByFrame(); frames++ }
        compose.mainClock.advanceTimeByFrame()
        compose.onNodeWithTag("candidate_2").performSemanticsAction(androidx.compose.ui.semantics.SemanticsActions.OnClick)
        compose.mainClock.autoAdvance = true
        settle()
        compose.onNodeWithTag("candidates_preview_sheet").assertExists()
        assertEquals(listOf(2), compose.onNodeWithTag("candidate_map").fetchSemanticsNode().config[CandidateMapRouteIdsKey])
        assertSheet(SheetValue.PartiallyExpanded)
        closeWithButton()
        assertSheet(SheetValue.Expanded)
        compose.onNodeWithTag("candidates_journey_bar").assertExists()
        assertCardVisible(2)
    }

    /** 같은 화면에서 여정을 바꾼 뒤에도 완전 확장 → 미리보기 → 닫기 → 축약 여정 바 누름이 펼친 헤더로 돌아온다(복원 상태가 남지 않음). */
    @Test fun tripChangeOnSameScreenKeepsTransitionsWorking() {
        render()
        request = request.copy(destination = request.destination.copy(name = "견본 도착지"))
        settle()
        dragHandle(-3000f)
        tap("candidate_1")
        closeWithButton()
        assertSheet(SheetValue.Expanded)
        tap("candidates_journey_bar")
        assertSheet(SheetValue.PartiallyExpanded)
        compose.onNodeWithContentDescription("출발지와 도착지 바꾸기").assertExists()
    }
}
