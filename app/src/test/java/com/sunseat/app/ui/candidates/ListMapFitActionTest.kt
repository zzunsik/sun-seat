package com.sunseat.app.ui.candidates

import android.app.Application
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.tryPerformAccessibilityChecks
import androidx.compose.ui.test.junit4.accessibility.enableAccessibilityChecks
import com.github.takahirom.roborazzi.captureRoboImage
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipe
import androidx.compose.ui.test.swipeLeft
import com.sunseat.app.ui.map.LocalMapRenderer
import com.sunseat.app.ui.map.MapCameraRevisionKey
import com.sunseat.app.ui.map.MapRenderer
import com.sunseat.app.ui.theme.SunSeatTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * #141: 미리보기에서 지도를 직접 움직인 뒤 닫으면 그 카메라를 유지한다(#71, #135). 그 상태에서 선택 경로가 목록 시트 뒤에 있어도
 * 목록에서는 다시 맞출 행동이 없었다. 지도를 직접 움직인 뒤에는 목록 지도 띠에도 "지도 맞춤"이 보이고, 누르면 보이는 띠에 다시 맞춘다.
 * 카메라가 맞춰져 있는 동안에는 낮은 띠를 가리지 않게 보이지 않는다.
 */
@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class, sdk = [36], qualifiers = "w390dp-h844dp-xxhdpi")
@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
class ListMapFitActionTest {
    @get:Rule val compose = createComposeRule(effectContext = object : androidx.compose.ui.MotionDurationScale { override val scaleFactor = 0f })

    private fun revision() = compose.onNodeWithTag("candidate_map").fetchSemanticsNode().config[MapCameraRevisionKey]
    private fun settle() { compose.mainClock.advanceTimeBy(5000); compose.waitForIdle() }
    private fun fitActions() = compose.onAllNodesWithText("지도 맞춤").fetchSemanticsNodes().size

    private fun render(dark: Boolean = false) {
        val items = CandidatesFixtures.options.toUi(java.time.LocalDateTime.of(2026, 11, 15, 9, 0))
        var state by mutableStateOf(CandidatesState.Options(items))
        compose.setContent {
            CompositionLocalProvider(LocalMapRenderer provides MapRenderer.PREVIEW) {
                SunSeatTheme(darkTheme = dark) { CandidatesScreen(CandidatesFixtures.transitRequest, state, CandidatesActions(onHighlight = { state = state.copy(selectedId = it.id) })) }
            }
        }
        settle()
    }

    @Test fun listOffersFitAfterPreviewMoveAndRefitsTheStrip() {
        render()
        assertEquals("a fitted list strip shows no fit action", 0, fitActions())
        val before = revision()
        compose.onNodeWithTag("candidate_1").performSemanticsAction(SemanticsActions.OnClick)
        compose.waitUntil(10_000) { revision() > before }
        settle()
        compose.onNodeWithTag("candidate_map").performTouchInput { swipeLeft() }
        compose.onAllNodesWithContentDescription("경로 목록으로 돌아가기")[0].performSemanticsAction(SemanticsActions.OnClick)
        settle()
        val kept = revision()
        compose.onNodeWithTag("candidates_sheet").assertExists()
        assertEquals("after a manual move the list must offer the fit action", 1, fitActions())
        compose.onNodeWithText("지도 맞춤").performSemanticsAction(SemanticsActions.OnClick)
        compose.waitUntil(10_000) { revision() > kept }
        settle()
        assertEquals(kept + 1, revision())
        assertEquals("the action goes away once the strip is fitted again", 0, fitActions())
    }

    @Test fun manualMoveInListOffersFit() {
        render()
        // 목록 상태의 지도는 시트 밑까지 차지하므로 보이는 띠(지도 위쪽)를 끈다.
        compose.onNodeWithTag("candidate_map").performTouchInput { swipe(androidx.compose.ui.geometry.Offset(width * 0.8f, height * 0.05f), androidx.compose.ui.geometry.Offset(width * 0.2f, height * 0.05f)) }
        compose.waitForIdle()
        assertEquals("a manual move in the list strip must offer the fit action", 1, fitActions())
        val moved = revision()
        // 고른 후보가 없으면 모든 후보 개요에 맞춘다는 이름으로 읽는다.
        compose.onNodeWithText("지도 맞춤").fetchSemanticsNode().config[SemanticsActions.OnClick].label.let { assertEquals("후보 경로가 모두 보이게 지도 맞추기", it) }
        compose.onNodeWithText("지도 맞춤").performSemanticsAction(SemanticsActions.OnClick)
        compose.waitUntil(10_000) { revision() > moved }
        settle()
        assertEquals(0, fitActions())
    }

    /** 지도를 끈 뒤 목록 지도 띠의 "지도 맞춤"(밝은·어두운 화면, 접근성 검사). */
    private fun listFitShot(dark: Boolean) {
        compose.enableAccessibilityChecks()
        render(dark)
        compose.onNodeWithTag("candidate_map").performTouchInput { swipe(androidx.compose.ui.geometry.Offset(width * 0.8f, height * 0.05f), androidx.compose.ui.geometry.Offset(width * 0.6f, height * 0.05f)) }
        settle()
        compose.onRoot().tryPerformAccessibilityChecks()
        compose.onRoot().captureRoboImage("src/test/screenshots/candidates_list_fit_action_${if (dark) "dark" else "light"}.png")
    }
    @Test fun listFitActionLight() = listFitShot(false)
    @Test fun listFitActionDark() = listFitShot(true)
}
