package com.sunseat.app.ui.candidates

import android.app.Application
import androidx.compose.material3.BottomSheetScaffoldState
import androidx.compose.material3.SheetValue
import androidx.compose.material3.rememberBottomSheetScaffoldState
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.test.click
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.performTouchInput
import com.sunseat.app.ui.map.LocalMapRenderer
import com.sunseat.app.ui.map.MapRenderer
import com.sunseat.app.ui.theme.SunSeatTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * #162: 손잡이로 시트를 끌어 완전 확장하면 끄는 도중 상단이 축약 여정 바로 바뀐다. 이때 시트 높이(앵커)가 바뀌면 시트가 드래그 상태에 멈춰
 * 이후 카드·탭·정렬·여정 바 누름이 모두 먹지 않았다. 의미 동작(performSemanticsAction)은 이 결함을 지나치므로 실제 터치로 누른다.
 */
@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class, sdk = [36], qualifiers = "w390dp-h844dp-xxhdpi")
@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
class SheetDragTapTest {
    @get:Rule val compose = createComposeRule()
    private lateinit var sheet: BottomSheetScaffoldState
    private var sorts = 0

    private fun settle() { compose.mainClock.advanceTimeBy(5000); compose.waitForIdle() }

    private fun render(fontScale: Float = 1f, request: com.sunseat.app.domain.TripRequest = CandidatesFixtures.transitRequest) {
        val base = CandidatesFixtures.options.toUi(java.time.LocalDateTime.of(2026, 11, 15, 9, 0)).first()
        var state by mutableStateOf(CandidatesState.Options((0..9).map { base.copy(id = it) }))
        compose.setContent {
            sheet = rememberBottomSheetScaffoldState()
            val density = androidx.compose.ui.platform.LocalDensity.current
            CompositionLocalProvider(LocalMapRenderer provides MapRenderer.PREVIEW,
                androidx.compose.ui.platform.LocalDensity provides androidx.compose.ui.unit.Density(density.density, fontScale)) {
                SunSeatTheme { CandidatesScreen(request, state, CandidatesActions(onHighlight = { state = state.copy(selectedId = it.id) }, onSort = { sorts++ }), scaffoldState = sheet) }
            }
        }
        settle()
    }

    /** 손잡이를 손가락처럼 천천히 끈다(끝에서 멈춰 손을 뗀다). */
    private fun dragHandle(dy: Float) {
        compose.onNodeWithTag("candidates_sheet_handle", useUnmergedTree = true).performTouchInput {
            down(center)
            repeat(30) { moveBy(Offset(0f, dy / 30), delayMillis = 25) }
            up()
        }
        settle()
    }

    private fun assertSettled(value: SheetValue) {
        assertEquals(value, sheet.bottomSheetState.currentValue)
        assertEquals("sheet must not stay in a drag/settle state", value, sheet.bottomSheetState.targetValue)
        assertFalse("sheet must not stay in a drag/settle state", sheet.bottomSheetState.isAnimationRunning)
    }

    private fun dragUpThenTap() {
        dragHandle(-3000f)
        assertSettled(SheetValue.Expanded)
        compose.onNodeWithTag("candidates_journey_bar").assertExists()
        // 정렬 메뉴가 열린다(누름이 시트 드래그에 먹히지 않는다).
        compose.onNodeWithTag("candidates_sort").performTouchInput { click() }
        settle()
        compose.onNodeWithText("최소 시간").performTouchInput { click() }
        settle()
        assertEquals(1, sorts)
        // 카드를 누르면 미리보기가 열린다.
        compose.onNodeWithTag("candidate_1").performTouchInput { click() }
        settle()
        compose.onNodeWithTag("candidates_preview_sheet").assertExists()
    }

    @Test fun tapsWorkAfterDraggingSheetToFullyExpanded() {
        render()
        dragUpThenTap()
    }

    @Test fun tapsWorkAfterDraggingSheetUpAndBackDown() {
        render()
        dragHandle(-3000f)
        assertSettled(SheetValue.Expanded)
        dragHandle(3000f)
        assertSettled(SheetValue.PartiallyExpanded)
        compose.onNodeWithTag("candidate_0").performTouchInput { click() }
        settle()
        compose.onNodeWithTag("candidates_preview_sheet").assertExists()
    }

    @Test fun journeyBarTapWorksAfterDrag() {
        render()
        dragHandle(-3000f)
        assertSettled(SheetValue.Expanded)
        compose.onNodeWithTag("candidates_journey_bar").performTouchInput { click() }
        settle()
        assertSettled(SheetValue.PartiallyExpanded)
    }

    @Test @Config(qualifiers = "w320dp-h640dp-xxhdpi")
    fun tapsWorkAfterDragSmallScreenLargeText() {
        render(fontScale = 1.5f)
        dragUpThenTap()
    }

    /** 두 줄로 감기는 긴 장소명에서도 축약 여정 바 높이를 미리 재어 끄는 도중 시트 높이가 바뀌지 않는다. */
    @Test fun tapsWorkAfterDragWithLongNames() {
        val request = CandidatesFixtures.transitRequest
        render(request = request.copy(origin = request.origin.copy(name = "아주 긴 견본 출발지 이름과 부가 설명"), destination = request.destination.copy(name = "아주 긴 견본 목적지 이름과 부가 설명")))
        dragUpThenTap()
    }

    /** 끄는 동안 미뤄 둔 축약 여정 바 높이는 시트가 멈춘 뒤 반영되어, 완전 확장 시트 위끝이 바 바로 아래에 붙는다(틈·겹침 없음, #101). */
    @Test fun expandedSheetMeetsJourneyBarAfterDrag() {
        render()
        compose.onNodeWithContentDescription("출발지와 도착지 바꾸기").assertExists()
        dragHandle(-3000f)
        assertSettled(SheetValue.Expanded)
        val header = compose.onNodeWithTag("candidates_header").fetchSemanticsNode().boundsInRoot
        val handle = compose.onNodeWithTag("candidates_sheet_handle", useUnmergedTree = true).fetchSemanticsNode().boundsInRoot
        assertEquals("expanded sheet top meets the compact journey bar", header.bottom, handle.top, 1f)
        compose.onNodeWithContentDescription("출발지와 도착지 바꾸기").assertDoesNotExist()
    }

    /** 완전 확장에서 미리보기 → 자리 결과 → Back(새 구성)으로 시트가 완전 확장으로 돌아온 뒤에도 정착하고 누름이 먹는다(#145 복원 경로). */
    @Test fun tapsWorkAfterExpandedRestoreFromSeatResult() {
        val base = CandidatesFixtures.options.toUi(java.time.LocalDateTime.of(2026, 11, 15, 9, 0)).first()
        var state by mutableStateOf(CandidatesState.Options((0..9).map { base.copy(id = it) }))
        val restoration = androidx.compose.ui.test.junit4.StateRestorationTester(compose)
        restoration.setContent {
            sheet = rememberBottomSheetScaffoldState()
            CompositionLocalProvider(LocalMapRenderer provides MapRenderer.PREVIEW) {
                SunSeatTheme { CandidatesScreen(CandidatesFixtures.transitRequest, state, CandidatesActions(onHighlight = { state = state.copy(selectedId = it.id) }), scaffoldState = sheet) }
            }
        }
        settle()
        dragHandle(-3000f)
        assertSettled(SheetValue.Expanded)
        compose.onNodeWithTag("candidate_1").performTouchInput { click() }
        settle()
        compose.onNodeWithTag("preview_seat").performTouchInput { click() }
        restoration.emulateSavedInstanceStateRestore()
        settle()
        assertSettled(SheetValue.Expanded)
        compose.onNodeWithTag("candidate_2").performTouchInput { click() }
        settle()
        compose.onNodeWithTag("candidates_preview_sheet").assertExists()
    }
}
