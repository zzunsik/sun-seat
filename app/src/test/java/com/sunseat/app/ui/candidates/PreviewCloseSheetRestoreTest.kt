package com.sunseat.app.ui.candidates

import android.app.Application
import androidx.compose.material3.BottomSheetScaffoldState
import androidx.compose.material3.SheetValue
import androidx.compose.material3.rememberBottomSheetScaffoldState
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performScrollToKey
import androidx.compose.ui.test.performSemanticsAction
import com.sunseat.app.ui.map.LocalMapRenderer
import com.sunseat.app.ui.map.MapRenderer
import com.sunseat.app.ui.theme.SunSeatTheme
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * #145: 미리보기를 닫으면 들어가기 전 시트 높이(완전 확장/기본)로 돌아가고, 선택 카드가 화면 안에 보인다.
 * 완전 확장 상태로 돌아오면 상단은 축약 여정 바다(#101).
 */
@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class, sdk = [36], qualifiers = "w390dp-h844dp-xxhdpi")
@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
class PreviewCloseSheetRestoreTest {
    @get:Rule val compose = createComposeRule(effectContext = object : androidx.compose.ui.MotionDurationScale { override val scaleFactor = 0f })

    private lateinit var sheet: BottomSheetScaffoldState
    private lateinit var scope: CoroutineScope

    private fun settle() { compose.mainClock.advanceTimeBy(5000); compose.waitForIdle() }

    private fun render() {
        val base = CandidatesFixtures.options.toUi(java.time.LocalDateTime.of(2026, 11, 15, 9, 0)).first()
        var state by mutableStateOf(CandidatesState.Options((0..9).map { base.copy(id = it) }))
        compose.setContent {
            sheet = rememberBottomSheetScaffoldState()
            scope = rememberCoroutineScope()
            CompositionLocalProvider(LocalMapRenderer provides MapRenderer.PREVIEW) {
                SunSeatTheme { CandidatesScreen(CandidatesFixtures.transitRequest, state, CandidatesActions(onHighlight = { state = state.copy(selectedId = it.id) }), scaffoldState = sheet) }
            }
        }
        settle()
    }

    private fun openAndClose(id: Int) {
        compose.onNodeWithTag("candidate_$id").performSemanticsAction(SemanticsActions.OnClick)
        compose.onNodeWithTag("candidates_preview_sheet").assertExists()
        settle()
        compose.onAllNodesWithContentDescription("경로 목록으로 돌아가기")[0].performSemanticsAction(SemanticsActions.OnClick)
        settle()
        compose.onNodeWithTag("candidates_sheet").assertExists()
    }

    /**
     * 선택 카드의 윗부분(시간 줄)이 시트 손잡이 아래에 있고, 화면 아래끝 위로 최소 터치 높이만큼 보인다.
     * 기본 시트 높이에서는 카드 전체가 들어가지 않을 수 있으므로 카드 전체를 요구하지 않는다.
     */
    private fun assertCardOnScreen(id: Int) {
        val card = compose.onNodeWithTag("candidate_$id").fetchSemanticsNode().boundsInRoot
        val handle = compose.onNodeWithTag("candidates_sheet_handle", useUnmergedTree = true).fetchSemanticsNode().boundsInRoot
        val screen = compose.onRoot().fetchSemanticsNode().boundsInRoot
        val touch = with(compose.density) { com.sunseat.app.ui.theme.Spacing.touch.toPx() }
        assertTrue("card $id top ${card.top} is above sheet top ${handle.bottom}", card.top >= handle.bottom - 0.5f)
        assertTrue("card $id top ${card.top} is not visible above screen bottom ${screen.bottom}", card.top + touch <= screen.bottom + 0.5f)
    }

    @Test fun closingPreviewRestoresExpandedSheetAndKeepsSelectedCardVisible() {
        render()
        compose.runOnIdle { scope.launch { sheet.bottomSheetState.expand() } }
        settle()
        assertEquals(SheetValue.Expanded, sheet.bottomSheetState.currentValue)
        // 완전 확장에서만 화면 안에 들어오는 아래쪽 카드를 고른다.
        compose.onNodeWithTag("candidate_list").performScrollToKey(4)
        settle()
        assertCardOnScreen(6)
        openAndClose(6)
        assertEquals("sheet returns to the height it had before the preview", SheetValue.Expanded, sheet.bottomSheetState.currentValue)
        assertCardOnScreen(6)
        // #101: 완전 확장이므로 상단은 축약 여정 바다.
        compose.onNodeWithTag("candidates_journey_bar").assertExists()
    }

    /** 완전 확장으로 되돌리는 동안 펼친 헤더가 끼어 보이지 않는다(미리보기 바 → 축약 여정 바, #101). */
    @Test fun restoringExpandedSheetNeverFlashesFullHeader() {
        render()
        compose.runOnIdle { scope.launch { sheet.bottomSheetState.expand() } }
        settle()
        compose.onNodeWithTag("candidate_2").performSemanticsAction(SemanticsActions.OnClick)
        settle()
        compose.mainClock.autoAdvance = false
        compose.onAllNodesWithContentDescription("경로 목록으로 돌아가기")[0].performSemanticsAction(SemanticsActions.OnClick)
        repeat(40) { frame ->
            compose.mainClock.advanceTimeByFrame()
            compose.onNodeWithContentDescription("출발지와 도착지 바꾸기").assertDoesNotExist()
        }
        compose.mainClock.autoAdvance = true
        settle()
        assertEquals(SheetValue.Expanded, sheet.bottomSheetState.currentValue)
        compose.onNodeWithTag("candidates_journey_bar").assertExists()
    }

    /** 미리보기에서 자리 결과로 갔다가 Back으로 돌아와도(화면 재구성) 들어가기 전 시트 높이로 돌아간다. */
    @Test fun returningFromSeatResultRestoresExpandedSheet() {
        val base = CandidatesFixtures.options.toUi(java.time.LocalDateTime.of(2026, 11, 15, 9, 0)).first()
        var state by mutableStateOf(CandidatesState.Options((0..9).map { base.copy(id = it) }))
        val restoration = androidx.compose.ui.test.junit4.StateRestorationTester(compose)
        restoration.setContent {
            sheet = rememberBottomSheetScaffoldState()
            scope = rememberCoroutineScope()
            CompositionLocalProvider(LocalMapRenderer provides MapRenderer.PREVIEW) {
                SunSeatTheme { CandidatesScreen(CandidatesFixtures.transitRequest, state, CandidatesActions(onHighlight = { state = state.copy(selectedId = it.id) }), scaffoldState = sheet) }
            }
        }
        settle()
        compose.runOnIdle { scope.launch { sheet.bottomSheetState.expand() } }
        settle()
        compose.onNodeWithTag("candidate_list").performScrollToKey(4)
        settle()
        compose.onNodeWithTag("candidate_6").performSemanticsAction(SemanticsActions.OnClick)
        settle()
        // 자리 보기로 화면을 떠나는 순간 구성을 버리고(내비게이션) 저장 상태로 다시 만든다(Back 복귀).
        compose.mainClock.autoAdvance = false
        compose.onNodeWithTag("preview_seat").performSemanticsAction(SemanticsActions.OnClick)
        restoration.emulateSavedInstanceStateRestore()
        compose.mainClock.autoAdvance = true
        settle()
        assertEquals(SheetValue.Expanded, sheet.bottomSheetState.currentValue)
        assertCardOnScreen(6)
    }

    /** 선택 카드가 화면 밖에 있으면(예: 미리보기 중 회전) 닫은 뒤 보이는 위치로 옮긴다. 보이면 스크롤을 유지한다. */
    @Test fun closingPreviewRevealsSelectedCardOutsideScreen() {
        render()
        val screen = compose.onRoot().fetchSemanticsNode().boundsInRoot
        val touch = with(compose.density) { com.sunseat.app.ui.theme.Spacing.touch.toPx() }
        // 기본 높이 시트에서 목록이 구성했지만 화면 아래로 가려진 카드(시트 밖으로 밀린 선택 카드와 같은 조건).
        val hidden = (1..9).first { id ->
            val node = compose.onAllNodes(androidx.compose.ui.test.hasTestTag("candidate_$id")).fetchSemanticsNodes().firstOrNull()
            node != null && node.boundsInRoot.top + touch > screen.bottom
        }
        openAndClose(hidden)
        assertEquals(SheetValue.PartiallyExpanded, sheet.bottomSheetState.currentValue)
        assertCardOnScreen(hidden)
    }

    @Test fun closingPreviewKeepsDefaultSheetHeight() {
        render()
        assertEquals(SheetValue.PartiallyExpanded, sheet.bottomSheetState.currentValue)
        openAndClose(0)
        assertEquals(SheetValue.PartiallyExpanded, sheet.bottomSheetState.currentValue)
        assertCardOnScreen(0)
        compose.onNodeWithTag("candidates_journey_bar").assertDoesNotExist()
    }
}
