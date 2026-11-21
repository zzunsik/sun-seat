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
import androidx.compose.ui.test.assertIsFocused
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.click
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performScrollToKey
import androidx.compose.ui.test.performTouchInput
import com.sunseat.app.ui.map.LocalMapRenderer
import com.sunseat.app.ui.map.MapRenderer
import com.sunseat.app.ui.theme.SunSeatTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * #158: 화면 읽기를 켠 채 미리보기를 닫기 버튼이나 Back으로 닫으면 초점이 선택했던 카드로 돌아온다.
 * 루트로 돌아가면 화면 읽기 사용자가 목록을 처음부터 다시 훑어야 한다. 카드 누름·끌기는 실제 터치로 보낸다.
 */
@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class, sdk = [36], qualifiers = "w390dp-h844dp-xxhdpi")
@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
class PreviewCloseFocusTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()
    private lateinit var sheet: BottomSheetScaffoldState

    /** 화면 읽기(TalkBack 터치 탐색)를 켠 상태. 끄면 터치 모드에서 카드는 초점을 받지 않는다(보이는 초점 표시 없음). */
    @org.junit.Before fun screenReaderOn() {
        val manager = androidx.test.core.app.ApplicationProvider.getApplicationContext<Application>().getSystemService(android.view.accessibility.AccessibilityManager::class.java)
        org.robolectric.Shadows.shadowOf(manager).setTouchExplorationEnabled(true)
    }

    private fun settle() { compose.mainClock.advanceTimeBy(5000); compose.waitForIdle() }

    private fun render() {
        val base = CandidatesFixtures.options.toUi(java.time.LocalDateTime.of(2026, 11, 15, 9, 0)).first()
        var state by mutableStateOf(CandidatesState.Options((0..9).map { base.copy(id = it) }))
        compose.setContent {
            sheet = rememberBottomSheetScaffoldState()
            CompositionLocalProvider(LocalMapRenderer provides MapRenderer.PREVIEW) {
                SunSeatTheme { CandidatesScreen(CandidatesFixtures.transitRequest, state, CandidatesActions(onHighlight = { state = state.copy(selectedId = it.id) }), scaffoldState = sheet) }
            }
        }
        settle()
    }

    private fun open(id: Int) {
        compose.onNodeWithTag("candidate_$id").performTouchInput { click() }
        settle()
        compose.onNodeWithTag("candidates_preview_sheet").assertExists()
    }

    private fun closeWithButton() {
        compose.onAllNodesWithContentDescription("경로 목록으로 돌아가기")[0].performTouchInput { click() }
        settle()
        compose.onNodeWithTag("candidates_sheet").assertExists()
    }

    private fun closeWithBack() {
        compose.runOnUiThread { compose.activity.onBackPressedDispatcher.onBackPressed() }
        settle()
        compose.onNodeWithTag("candidates_sheet").assertExists()
    }

    @Test fun closeButtonReturnsFocusToSelectedCard() {
        render()
        open(1)
        closeWithButton()
        compose.onNodeWithTag("candidate_1").assertIsFocused()
        compose.onAllNodes(androidx.compose.ui.test.isFocused()).assertCountEquals(1)
    }

    @Test fun backReturnsFocusToSelectedCard() {
        render()
        open(2)
        closeWithBack()
        compose.onNodeWithTag("candidate_2").assertIsFocused()
    }

    /** 끌어 완전 확장한 목록 아래쪽 카드도 닫은 뒤 그 카드가 초점을 받는다(시트 높이 복원 #145 → 초점 순서). */
    @Test fun focusReturnsAfterDragExpandedPreviewClose() {
        render()
        compose.onNodeWithTag("candidates_sheet_handle", useUnmergedTree = true).performTouchInput {
            down(center)
            repeat(30) { moveBy(Offset(0f, -100f), delayMillis = 25) }
            up()
        }
        settle()
        assertEquals(SheetValue.Expanded, sheet.bottomSheetState.currentValue)
        compose.onNodeWithTag("candidate_list").performScrollToKey(4)
        settle()
        open(6)
        closeWithBack()
        assertEquals(SheetValue.Expanded, sheet.bottomSheetState.currentValue)
        compose.onNodeWithTag("candidate_6").assertIsFocused()
    }

    /** 미리보기에서 자리 결과로 갔다가 Back으로 돌아와도(새 구성) 선택 카드가 초점을 받는다. */
    @Test fun returningFromSeatResultFocusesSelectedCard() {
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
        open(1)
        compose.onNodeWithTag("preview_seat").performTouchInput { click() }
        restoration.emulateSavedInstanceStateRestore()
        settle()
        compose.onNodeWithTag("candidates_sheet").assertExists()
        compose.onNodeWithTag("candidate_1").assertIsFocused()
    }
}
