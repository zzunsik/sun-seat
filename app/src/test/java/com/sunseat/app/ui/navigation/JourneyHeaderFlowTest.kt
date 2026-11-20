package com.sunseat.app.ui.navigation

import android.app.Application
import androidx.activity.ComponentActivity
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performImeAction
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.test.performTextInput
import androidx.test.core.app.ApplicationProvider
import com.sunseat.app.R
import com.sunseat.app.SunSeatContent
import com.sunseat.app.ui.input.JOURNEY_HEADER_TAG
import com.sunseat.app.ui.map.LocalMapRenderer
import com.sunseat.app.ui.map.MapRenderer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * 여정 헤더(#103) 흐름: 후보 목록의 펼친 헤더에서 바꾼 햇빛 목적이 경로를 다시 찾지 않고 한 번 적용되며,
 * 입력·후보·미리보기·결과와 뒤로가기·재생성 사이에서 같은 여정으로 남는다. 응답은 모두 직접 만든 값이다.
 */
@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class, sdk = [36], qualifiers = "w390dp-h844dp-xxhdpi")
class JourneyHeaderFlowTest {
    @get:Rule val temporary = TemporaryFolder()
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()

    private var graph: FlowTestGraph? = null

    @After fun close() { graph?.close() }

    private fun text(id: Int): String = compose.activity.getString(id)

    private fun waitForText(value: String) =
        compose.waitUntil(10_000) { compose.onAllNodesWithText(value).fetchSemanticsNodes().isNotEmpty() }

    private fun SemanticsNodeInteraction.tap(): SemanticsNodeInteraction = performSemanticsAction(SemanticsActions.OnClick).also { compose.waitForIdle() }
    private fun clickText(value: String) = compose.onNode(hasClickAction() and hasText(value)).tap()
    private fun headerGoal(id: Int) = compose.onNode(hasClickAction() and hasText(text(id)) and hasAnyAncestor(hasTestTag(JOURNEY_HEADER_TAG)))

    private fun back() {
        compose.runOnUiThread { compose.activity.onBackPressedDispatcher.onBackPressed() }
        compose.waitForIdle()
    }

    private fun search(placeholder: String, query: String, choose: String) {
        clickText(placeholder)
        compose.onNode(hasSetTextAction()).performTextInput(query)
        compose.onNode(hasSetTextAction()).performImeAction()
        waitForText(choose)
        clickText(choose)
    }

    private fun enterTrip() {
        search(text(R.string.input_origin_placeholder), "예시", ORIGIN)
        search(text(R.string.input_destination_placeholder), "견본", DESTINATION)
        waitForText("30분")
    }

    private fun seekSunFromCandidatesHeader() {
        headerGoal(R.string.input_goal_chip_avoid).tap()
        clickText(text(R.string.input_goal_seek))
        waitForText(text(R.string.input_goal_chip_seek))
    }

    @Test fun candidateGoalEditAppliesOnceAndSurvivesPreviewResultAndBack() {
        val graph = FlowTestGraph(ApplicationProvider.getApplicationContext(), temporary.newFolder(), FakeKakaoServer()).also { graph = it }
        compose.setContent { CompositionLocalProvider(LocalMapRenderer provides MapRenderer.PREVIEW) { SunSeatContent(graph, compose.activity.window) } }
        compose.waitForIdle()
        enterTrip()
        val routeCalls = graph.server.transitRequests
        seekSunFromCandidatesHeader()
        headerGoal(R.string.input_goal_chip_seek).assertExists()
        assertEquals(routeCalls, graph.server.transitRequests)

        // 미리보기 → 자리 결과 → 뒤로: 후보 헤더는 같은 여정(햇빛 받기)을 그대로 보인다.
        compose.onNode(hasClickAction() and hasText("30분")).tap()
        compose.onNodeWithTag("candidates_journey_bar").assertExists()
        clickText(text(R.string.candidates_view_seat))
        waitForText(text(R.string.result_legs))
        back()
        waitForText("30분")
        headerGoal(R.string.input_goal_chip_seek).assertExists()
        back()
        compose.onNodeWithTag(JOURNEY_HEADER_TAG).assertExists()
        headerGoal(R.string.input_goal_chip_seek).assertExists()
        compose.onNode(hasText(ORIGIN) and hasAnyAncestor(hasTestTag(JOURNEY_HEADER_TAG))).assertExists()
        compose.onNode(hasText(DESTINATION) and hasAnyAncestor(hasTestTag(JOURNEY_HEADER_TAG))).assertExists()
        assertEquals(routeCalls, graph.server.transitRequests)
    }

    @Test fun candidateHeaderEditsSurviveRecreation() {
        val graph = FlowTestGraph(ApplicationProvider.getApplicationContext(), temporary.newFolder(), FakeKakaoServer()).also { graph = it }
        val restoration = androidx.compose.ui.test.junit4.StateRestorationTester(compose)
        restoration.setContent { CompositionLocalProvider(LocalMapRenderer provides MapRenderer.PREVIEW) { SunSeatContent(graph, compose.activity.window) } }
        compose.waitForIdle()
        enterTrip()
        seekSunFromCandidatesHeader()
        restoration.emulateSavedInstanceStateRestore()
        compose.waitForIdle()
        waitForText("30분")
        headerGoal(R.string.input_goal_chip_seek).assertExists()
        compose.onNode(hasText(ORIGIN) and hasAnyAncestor(hasTestTag(JOURNEY_HEADER_TAG))).assertExists()
        back()
        headerGoal(R.string.input_goal_chip_seek).assertExists()
    }

    private companion object {
        const val ORIGIN = "예시역 1호선"
        const val DESTINATION = "견본 카페"
    }
}
