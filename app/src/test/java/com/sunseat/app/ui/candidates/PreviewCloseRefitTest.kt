package com.sunseat.app.ui.candidates

import android.app.Application
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipeLeft
import com.sunseat.app.ui.map.LocalMapRenderer
import com.sunseat.app.ui.map.MapCameraRevisionKey
import com.sunseat.app.ui.map.MapRenderer
import com.sunseat.app.ui.map.MapVisibleHeightKey
import com.sunseat.app.ui.theme.SunSeatTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * #135: 미리보기(시트 낮음·지도 큼)에서 맞춘 카메라는 목록(시트 반 펼침)으로 돌아오면 선택 경로가 시트 뒤에 숨는다.
 * 미리보기를 닫으면 시트가 목록 높이에 자리 잡은 뒤 보이는 지도 띠에 한 번 다시 맞춘다.
 * 사용자가 미리보기에서 지도를 직접 움직였으면 닫기만으로 다시 맞추지 않는다(#71, DESIGN 12.3).
 */
@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class, sdk = [36], qualifiers = "w390dp-h844dp-xxhdpi")
@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
class PreviewCloseRefitTest {
    @get:Rule val compose = createComposeRule(effectContext = object : androidx.compose.ui.MotionDurationScale { override val scaleFactor = 0f })

    private fun map() = compose.onNodeWithTag("candidate_map").fetchSemanticsNode().config
    private fun revision() = map()[MapCameraRevisionKey]
    private fun settle() { compose.mainClock.advanceTimeBy(5000); compose.waitForIdle() }

    private fun render() {
        val items = CandidatesFixtures.options.toUi(java.time.LocalDateTime.of(2026, 11, 15, 9, 0))
        var state by mutableStateOf(CandidatesState.Options(items))
        compose.setContent {
            CompositionLocalProvider(LocalMapRenderer provides MapRenderer.PREVIEW) {
                SunSeatTheme { CandidatesScreen(CandidatesFixtures.transitRequest, state, CandidatesActions(onHighlight = { state = state.copy(selectedId = it.id) })) }
            }
        }
        settle()
    }

    /** 미리보기에 들어가 선택 경로 맞춤(#102)이 끝날 때까지 기다린다. */
    private fun openPreview(id: Int): Long {
        val before = revision()
        compose.onNodeWithTag("candidate_$id").performSemanticsAction(SemanticsActions.OnClick)
        compose.onNodeWithTag("candidates_preview_sheet").assertExists()
        compose.waitUntil(10_000) { revision() > before }
        settle()
        return revision()
    }

    /** 목록으로 돌아가 시트가 자리 잡고 맞춤 대기 시간(Motion.CAMERA_SETTLE_MS)이 충분히 지날 때까지 기다린다. */
    private fun closePreview() {
        val before = revision()
        compose.onAllNodesWithContentDescription("경로 목록으로 돌아가기")[0].performSemanticsAction(SemanticsActions.OnClick)
        compose.onNodeWithTag("candidates_sheet").assertExists()
        // 다시 맞추지 않는 경우도 같은 시간만큼 기다려 판정한다(실패는 아래 단언이 맡는다).
        runCatching { compose.waitUntil(3_000) { revision() > before } }
        settle()
    }

    @Test fun closingPreviewRefitsSelectedRouteToListMapStrip() {
        render()
        val listVisible = map()[MapVisibleHeightKey]
        assertTrue(listVisible > 0f)
        val inPreview = openPreview(1)
        assertTrue("preview visible map is larger than the list strip", map()[MapVisibleHeightKey] > listVisible)
        closePreview()
        // 시트가 목록 높이로 돌아온 뒤 한 번만 맞춘다(보이는 지도 띠 = 미리보기 전과 같은 높이).
        assertEquals(listVisible, map()[MapVisibleHeightKey], 0.01f)
        assertEquals("closing the preview must refit the camera once", inPreview + 1, revision())
        assertEquals(1, map()[CandidateMapSelectedRouteKey].let { map()[CandidateMapRouteIdsKey][it] })
        settle()
        assertEquals(inPreview + 1, revision())
    }

    @Test fun closingPreviewAfterManualMoveKeepsUserCamera() {
        render()
        val inPreview = openPreview(1)
        compose.onNodeWithTag("candidate_map").performTouchInput { swipeLeft() }
        closePreview()
        assertEquals(inPreview, revision())
    }

    @Test fun closingPreviewAfterExplicitFitRefitsAgain() {
        render()
        val inPreview = openPreview(1)
        compose.onNodeWithTag("candidate_map").performTouchInput { swipeLeft() }
        compose.onNodeWithText("지도 맞춤").performSemanticsAction(SemanticsActions.OnClick)
        compose.waitUntil(10_000) { revision() > inPreview }
        settle()
        val fitted = revision()
        assertEquals(inPreview + 1, fitted)
        closePreview()
        assertEquals(fitted + 1, revision())
    }
}
