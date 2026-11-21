package com.sunseat.app.ui.candidates

import android.app.Application
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.click
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipe
import com.sunseat.app.ui.map.LocalMapRenderer
import com.sunseat.app.ui.map.MapCameraRevisionKey
import com.sunseat.app.ui.map.MapFootprintBoxesKey
import com.sunseat.app.ui.map.MapRenderer
import com.sunseat.app.ui.map.MapVisibleAreaKey
import com.sunseat.app.ui.map.ScreenBox
import com.sunseat.app.ui.theme.SunSeatTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * #168: 후보 화면 지도 카메라를 테스트 지도(정책과 같은 가운데·정수 줌 투영)의 발자국 상자로 본다.
 * 맞춘 뒤 모든 표식·이름표가 보이는 지도 띠 안에 있고(#138 #143 #151), 시트를 끌어 보이는 영역만 바뀌면 화면 내용이 그대로다(#148).
 * 제스처와 그 뒤 누름은 실제 터치로 보낸다.
 */
@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class, sdk = [36], qualifiers = "w390dp-h844dp-xxhdpi")
@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
class CandidatesMapCameraTest {
    @get:Rule val compose = createComposeRule()

    private fun settle() { compose.mainClock.advanceTimeBy(5000); compose.waitForIdle() }
    private fun camera() = compose.onNode(SemanticsMatcher.keyIsDefined(MapFootprintBoxesKey)).fetchSemanticsNode().config
    private fun boxes() = camera()[MapFootprintBoxesKey]
    private fun visible() = camera()[MapVisibleAreaKey]
    private fun revision() = compose.onNodeWithTag("candidate_map").fetchSemanticsNode().config[MapCameraRevisionKey]

    private fun render(fontScale: Float = 1f) {
        val items = CandidatesFixtures.options.toUi(java.time.LocalDateTime.of(2026, 11, 15, 9, 0))
        var state by mutableStateOf(CandidatesState.Options(items))
        compose.setContent {
            val density = androidx.compose.ui.platform.LocalDensity.current
            CompositionLocalProvider(LocalMapRenderer provides MapRenderer.PREVIEW,
                androidx.compose.ui.platform.LocalDensity provides androidx.compose.ui.unit.Density(density.density, fontScale)) {
                SunSeatTheme { CandidatesScreen(CandidatesFixtures.transitRequest, state, CandidatesActions(onHighlight = { state = state.copy(selectedId = it.id) })) }
            }
        }
        settle()
    }

    private fun assertInside(boxes: List<ScreenBox>, area: ScreenBox, what: String) {
        assertTrue("$what: no footprints", boxes.isNotEmpty())
        boxes.forEach { box ->
            assertTrue("$what: $box must be inside the visible map $area", box.left >= area.left - 0.5f && box.right <= area.right + 0.5f && box.top >= area.top - 0.5f && box.bottom <= area.bottom + 0.5f)
        }
    }

    private fun listAndPreviewInside(fontScale: Float) {
        render(fontScale)
        // 작은 화면·큰 글자에서는 기본 시트가 지도를 모두 덮어 보이는 띠가 없다(맞춤은 띠가 보일 때까지 미룬다).
        if (visible().let { it.bottom - it.top } > 0f) assertInside(boxes(), visible(), "list strip x$fontScale")
        compose.onNodeWithTag("candidate_1").performTouchInput { click() }
        settle()
        assertInside(boxes(), visible(), "preview x$fontScale")
    }

    @Test fun listStripAndPreviewKeepEveryMarkerInsideTheVisibleMap() = listAndPreviewInside(1f)
    @Test @Config(qualifiers = "w320dp-h640dp-xxhdpi") fun smallScreenLargeTextKeepsMarkersInside() = listAndPreviewInside(1.5f)
    @Test @Config(qualifiers = "w832dp-h384dp-xxhdpi") fun landscapeKeepsMarkersInside() = listAndPreviewInside(1f)

    /** #148: 손잡이를 약 60dp 올렸다가 같은 손가락으로 되돌려 놓는다. 보이는 영역만 바뀌므로 카메라·표식 화면 위치가 그대로다. */
    @Test fun sheetDragUpAndBackLeavesTheMapContentInPlace() {
        render()
        val before = boxes()
        val fits = revision()
        val step = 60f * compose.density.density / 20
        compose.onNodeWithTag("candidates_sheet_handle", useUnmergedTree = true).performTouchInput {
            down(center)
            repeat(20) { moveBy(Offset(0f, -step), delayMillis = 40) }
            repeat(20) { moveBy(Offset(0f, step), delayMillis = 40) }
            up()
        }
        settle()
        assertEquals(before, boxes())
        assertEquals("a sheet drag must not refit the camera", fits, revision())
        // 시트를 실제로 펼쳤다 되돌려도(보이는 띠가 사라졌다 다시 생김) 같다.
        compose.onNodeWithTag("candidates_sheet_handle", useUnmergedTree = true).performTouchInput { swipe(center, center + Offset(0f, -2000f), 600) }
        settle()
        compose.onNodeWithTag("candidates_sheet_handle", useUnmergedTree = true).performTouchInput { swipe(center, center + Offset(0f, 2000f), 600) }
        settle()
        assertEquals(before, boxes())
        assertEquals(fits, revision())
    }

    /** #141: 미리보기에서 지도를 끌고(실제 터치) 닫으면 목록 지도 띠에 "지도 맞춤"이 보이고, 누르면 선택 경로가 보이는 띠 안에 다시 맞는다. */
    @Test fun previewMoveThenCloseOffersListFitThatRefitsTheStrip() {
        render()
        compose.onNodeWithTag("candidate_1").performTouchInput { click() }
        settle()
        compose.onNodeWithTag("candidate_map").performTouchInput { swipe(center, center + Offset(width * 0.4f, 0f), 600) }
        settle()
        compose.onAllNodesWithContentDescription("경로 목록으로 돌아가기")[0].performTouchInput { click() }
        settle()
        compose.onNodeWithTag("candidates_sheet").assertExists()
        assertEquals("the moved camera offers the fit action in the list", 1, compose.onAllNodesWithText("지도 맞춤").fetchSemanticsNodes().size)
        val kept = revision()
        compose.onNodeWithText("지도 맞춤").performTouchInput { click() }
        settle()
        assertEquals(kept + 1, revision())
        assertEquals(0, compose.onAllNodesWithText("지도 맞춤").fetchSemanticsNodes().size)
        assertInside(boxes(), visible(), "refitted list strip")
    }
}
