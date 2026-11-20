package com.sunseat.app.ui.candidates

import android.app.Application
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performScrollToIndex
import androidx.compose.ui.test.performSemanticsAction
import com.sunseat.app.ui.map.LocalMapRenderer
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
 * #136: 범주 탭을 바꾸면 정렬 변경(#83)처럼 목록을 맨 위(새 범주의 1순위)부터 보인다.
 * 같은 탭을 다시 누르면 목록 위치를 바꾸지 않는다.
 */
@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class, sdk = [36], qualifiers = "w390dp-h844dp-xxhdpi")
@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
class CategoryScrollTest {
    @get:Rule val compose = createComposeRule(effectContext = object : androidx.compose.ui.MotionDurationScale { override val scaleFactor = 0f })

    private fun scroll() = compose.onNodeWithTag("candidate_list").fetchSemanticsNode().config[SemanticsProperties.VerticalScrollAxisRange].value()

    @Test fun categoryChangeShowsNewFirstCandidateFromTop() {
        val now = java.time.LocalDateTime.of(2026, 11, 15, 9, 0)
        val items = (0..11).map { (if (it % 2 == 0) CandidatesFixtures.subwayOnly else CandidatesFixtures.busOnly).toUi(it, now, it == 0) }
        var state by mutableStateOf(CandidatesState.Options(items))
        compose.setContent {
            CompositionLocalProvider(LocalMapRenderer provides MapRenderer.PREVIEW) {
                SunSeatTheme { CandidatesScreen(CandidatesFixtures.transitRequest, state, CandidatesActions(onCategory = { state = state.copy(category = it) })) }
            }
        }
        compose.mainClock.advanceTimeBy(5000)
        compose.waitForIdle()
        compose.onNodeWithTag("candidate_list").performScrollToIndex(8)
        compose.waitForIdle()
        assertTrue("precondition: list scrolled", scroll() > 0f)

        compose.onNodeWithContentDescription("지하철 경로 6개").performSemanticsAction(SemanticsActions.OnClick)
        compose.mainClock.advanceTimeBy(1000)
        compose.waitForIdle()
        assertEquals(RouteCategory.SUBWAY, state.category)
        assertEquals("category change must scroll the list to the top", 0f, scroll(), 0.001f)
        val first = state.visible.first().id
        val listTop = compose.onNodeWithTag("candidate_list").getUnclippedBoundsInRoot().top
        val firstTotal = compose.onNodeWithTag("candidate_time_$first", useUnmergedTree = true).getUnclippedBoundsInRoot()
        assertTrue("first candidate total must not be cut at the top", firstTotal.top >= listTop)

        // 같은 범주를 다시 누르면 기준이 바뀌지 않으므로 사용자가 내린 위치를 유지한다.
        compose.onNodeWithTag("candidate_list").performScrollToIndex(4)
        compose.waitForIdle()
        val scrolled = scroll()
        assertTrue(scrolled > 0f)
        compose.onNodeWithContentDescription("지하철 경로 6개").performSemanticsAction(SemanticsActions.OnClick)
        compose.mainClock.advanceTimeBy(1000)
        compose.waitForIdle()
        assertEquals(scrolled, scroll(), 0.001f)

        // "전체" 탭으로 돌아갈 때도 맨 위부터.
        compose.onNodeWithContentDescription("전체 경로 12개").performSemanticsAction(SemanticsActions.OnClick)
        compose.mainClock.advanceTimeBy(1000)
        compose.waitForIdle()
        assertEquals(RouteCategory.ALL, state.category)
        assertEquals(0f, scroll(), 0.001f)

        // 빈 범주의 "전체 보기"로 돌아와도 맨 위부터.
        compose.onNodeWithTag("candidate_list").performScrollToIndex(8)
        compose.waitForIdle()
        assertTrue(scroll() > 0f)
        compose.onNodeWithContentDescription("혼합 경로 0개").performSemanticsAction(SemanticsActions.OnClick)
        compose.mainClock.advanceTimeBy(1000)
        compose.waitForIdle()
        compose.onNodeWithText("전체 보기").performSemanticsAction(SemanticsActions.OnClick)
        compose.mainClock.advanceTimeBy(1000)
        compose.waitForIdle()
        assertEquals(RouteCategory.ALL, state.category)
        assertEquals(0f, scroll(), 0.001f)
    }
}
