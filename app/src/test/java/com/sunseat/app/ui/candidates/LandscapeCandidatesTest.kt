package com.sunseat.app.ui.candidates

import android.app.Application
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performSemanticsAction
import com.sunseat.app.ui.map.LocalMapRenderer
import com.sunseat.app.ui.map.MapCameraRevisionKey
import com.sunseat.app.ui.map.MapRenderer
import com.sunseat.app.ui.map.MapVisibleHeightKey
import com.sunseat.app.ui.theme.Sizes
import com.sunseat.app.ui.theme.SunSeatTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * #144: 가로 화면 후보 목록 기본 상태에서 후보 카드가 최소 한 장 보이고, 지도는 보이는 영역에 후보 경로를 맞춘다.
 * 펼친 헤더는 조각 없이 통째로 보인다(#101, #103).
 */
@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class, sdk = [36], qualifiers = "w844dp-h390dp-xxhdpi")
@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
class LandscapeCandidatesTest {
    @get:Rule val compose = createComposeRule(effectContext = object : androidx.compose.ui.MotionDurationScale { override val scaleFactor = 0f })

    private fun settle() { compose.mainClock.advanceTimeBy(5000); compose.waitForIdle() }
    private fun map() = compose.onNodeWithTag("candidate_map").fetchSemanticsNode().config

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

    private fun assertWholeCardVisible(id: Int) {
        val card = compose.onNodeWithTag("candidate_$id").fetchSemanticsNode().boundsInRoot
        val screen = compose.onRoot().fetchSemanticsNode().boundsInRoot
        assertTrue("card $id ($card) must be fully on screen ($screen)", card.top >= screen.top && card.bottom <= screen.bottom + 0.5f && card.left >= screen.left && card.right <= screen.right + 0.5f)
        compose.onNodeWithTag("candidate_action_$id").assertIsDisplayed()
    }

    @Test fun defaultLandscapeListShowsACardAndFitsMap() {
        render()
        assertWholeCardVisible(0)
        // 펼친 헤더가 통째로 보인다(편집·맞바꾸기).
        compose.onNodeWithContentDescription("출발지와 도착지 바꾸기").assertIsDisplayed()
        val visible = map()[MapVisibleHeightKey]
        assertTrue("landscape list map must leave a fit-able visible area, was $visible", visible > Sizes.mapInset.value * 2)
        assertTrue("landscape list map must be fitted to the candidates", map()[MapCameraRevisionKey] > 0)
    }

    @Test fun landscapePreviewAndBackKeepCardAndMap() {
        render()
        val listRevision = map()[MapCameraRevisionKey]
        compose.onNodeWithTag("candidate_0").performSemanticsAction(SemanticsActions.OnClick)
        compose.onNodeWithTag("candidates_preview_sheet").assertExists()
        settle()
        compose.onNodeWithTag("preview_seat").assertIsDisplayed()
        assertTrue("preview fits the selected route", map()[MapCameraRevisionKey] > listRevision)
        compose.onAllNodesWithContentDescription("경로 목록으로 돌아가기")[0].performSemanticsAction(SemanticsActions.OnClick)
        settle()
        compose.onNodeWithTag("candidates_sheet").assertExists()
        // 선택 카드는 구간 요약으로 길어져 패널보다 클 수 있다. 카드 머리(총시간)가 화면 안에 보이면 된다.
        compose.onNodeWithTag("candidate_time_0", useUnmergedTree = true).assertIsDisplayed()
        compose.onNodeWithTag("candidate_0").fetchSemanticsNode().config.let { assertEquals(true, it[androidx.compose.ui.semantics.SemanticsProperties.Selected]) }
        assertTrue(map()[MapVisibleHeightKey] > Sizes.mapInset.value * 2)
    }
}
