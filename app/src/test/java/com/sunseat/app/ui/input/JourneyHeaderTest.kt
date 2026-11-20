package com.sunseat.app.ui.input

import android.app.Application
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.accessibility.enableAccessibilityChecks
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.test.tryPerformAccessibilityChecks
import androidx.compose.ui.unit.Density
import androidx.test.core.app.ApplicationProvider
import com.sunseat.app.R
import com.sunseat.app.domain.LatLng
import com.sunseat.app.domain.NamedPlace
import com.sunseat.app.domain.SunGoal
import com.sunseat.app.domain.TravelMode
import com.sunseat.app.domain.TripRequest
import com.sunseat.app.ui.candidates.CandidatesActions
import com.sunseat.app.ui.candidates.CandidatesFixtures
import com.sunseat.app.ui.candidates.CandidatesScreen
import com.sunseat.app.ui.candidates.CandidatesState
import com.sunseat.app.ui.candidates.toUi
import com.sunseat.app.ui.map.LocalMapRenderer
import com.sunseat.app.ui.map.MapCameraRevisionKey
import com.sunseat.app.ui.map.MapRenderer
import com.sunseat.app.ui.map.MapTopPaddingKey
import com.sunseat.app.ui.theme.SunSeatTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.time.LocalDateTime

/**
 * 여정 헤더(#103, DESIGN 5.1): 입력·후보 목록이 같은 부품으로 출발 → 도착 → 맞바꾸기 → 수단 → 출발 시각 → 햇빛 목적을
 * 한 표면에서 읽고 고친다. 입력 지도 여백은 헤더의 실제 측정 높이다.
 */
@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class, sdk = [36], qualifiers = "w390dp-h844dp-xxhdpi")
@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
class JourneyHeaderTest {
    @get:Rule val compose = createComposeRule()

    private val context = ApplicationProvider.getApplicationContext<android.content.Context>()
    private fun text(id: Int) = context.getString(id)
    private val now = LocalDateTime.of(2026, 11, 16, 8, 0)
    private val filled = InputUiState(
        origin = Endpoint(NamedPlace("서울역", LatLng(37.55, 126.97))),
        destination = Endpoint(NamedPlace("강남역", LatLng(37.50, 127.03))),
        mode = TravelMode.TRANSIT,
        departure = LocalDateTime.of(2026, 11, 16, 9, 0),
    )

    private fun inHeader(matcher: SemanticsMatcher) = compose.onNode(matcher and hasAnyAncestor(hasTestTag(JOURNEY_HEADER_TAG)))
    private fun bounds(matcher: SemanticsMatcher): Rect = inHeader(matcher).fetchSemanticsNode().boundsInRoot
    private fun header(): Rect = compose.onNodeWithTag(JOURNEY_HEADER_TAG).fetchSemanticsNode().boundsInRoot

    private fun renderInput(state: () -> InputUiState, fontScale: Float = 1f, actions: InputActions = InputActions()) {
        compose.setContent {
            val density = LocalDensity.current
            CompositionLocalProvider(LocalMapRenderer provides MapRenderer.PREVIEW, LocalDensity provides Density(density.density, fontScale)) {
                SunSeatTheme { InputScreen(state(), actions, now = now) }
            }
        }
        compose.waitForIdle()
    }

    private fun assertInside(outer: Rect, inner: Rect, what: String) {
        assertTrue("$what $inner must be inside header $outer", inner.left >= outer.left - 0.5f && inner.right <= outer.right + 0.5f &&
            inner.top >= outer.top - 0.5f && inner.bottom <= outer.bottom + 0.5f)
    }

    /** 펼친 헤더의 모든 조각이 한 표면 안에 있고 5.1의 읽기 순서를 따른다. */
    private fun assertExpandedHeader(settings: Boolean) {
        val outer = header()
        val origin = bounds(hasText("서울역"))
        val destination = bounds(hasText("강남역"))
        val swap = bounds(hasContentDescription(text(R.string.input_swap)))
        val mode = bounds(hasText(text(R.string.input_mode_transit)))
        val time = bounds(hasClickAction() and hasText("09:00 출발", substring = true))
        val goal = bounds(hasClickAction() and hasText(text(R.string.input_goal_chip_avoid)))
        listOf("origin" to origin, "destination" to destination, "swap" to swap, "mode" to mode, "time" to time, "goal" to goal)
            .forEach { (name, rect) -> assertInside(outer, rect, name) }
        assertTrue("origin above destination", origin.bottom <= destination.top)
        assertTrue("places above mode", destination.bottom <= mode.top + 0.5f)
        assertTrue("mode above time", mode.bottom <= time.top + 0.5f)
        assertTrue("time before goal", time.top <= goal.top + 0.5f && (time.right <= goal.left + 0.5f || time.bottom <= goal.top + 0.5f))
        assertTrue("swap sits beside the place rows", swap.left >= origin.right - 0.5f && swap.top < destination.bottom && swap.bottom > origin.top)
        val settingsNodes = compose.onAllNodes(hasContentDescription(text(R.string.settings_title)) and hasAnyAncestor(hasTestTag(JOURNEY_HEADER_TAG))).fetchSemanticsNodes()
        assertEquals(if (settings) 1 else 0, settingsNodes.size)
        if (settings) {
            val gear = settingsNodes.single().boundsInRoot
            assertInside(outer, gear, "settings")
            // 설정은 장소 행의 누름 영역 밖 오른쪽 위에 있어 장소 편집으로 읽히지 않는다. 맞바꾸기는 그 아래다.
            assertTrue("settings outside the place rows", gear.left >= origin.right - 0.5f && gear.left >= destination.right - 0.5f)
            assertTrue("settings above swap", gear.bottom <= swap.top + 0.5f)
            // 390dp·1.0에서 시각과 햇빛 목적은 한 줄에 놓인다(불필요한 줄바꿈으로 헤더를 키우지 않는다).
            if (context.resources.configuration.screenWidthDp >= 390 && compose.onRoot().fetchSemanticsNode().layoutInfo.density.fontScale == 1f) {
                assertEquals("time and goal on one line", time.top, goal.top, 0.5f)
            }
        }
    }

    @Test fun inputHeaderIsOneSurfaceInJourneyReadingOrder() {
        renderInput({ filled })
        assertExpandedHeader(settings = true)
    }

    @Test fun candidatesHeaderUsesTheSameHeaderWithoutSettings() {
        val request = CandidatesFixtures.transitRequest.copy(departure = LocalDateTime.of(2026, 11, 16, 9, 0))
        val state = CandidatesState.Options(CandidatesFixtures.options.toUi(now))
        compose.setContent {
            CompositionLocalProvider(LocalMapRenderer provides MapRenderer.PREVIEW) {
                SunSeatTheme { CandidatesScreen(request, state, CandidatesActions(), now = now) }
            }
        }
        compose.waitForIdle()
        assertExpandedHeader(settings = false)
        inHeader(hasContentDescription(text(R.string.common_back))).fetchSemanticsNode()
    }

    @Test fun candidatesGoalEditAppliesOnceAndKeepsTheRestOfTheTrip() {
        val request = CandidatesFixtures.transitRequest
        val changes = mutableListOf<TripRequest>()
        val state = CandidatesState.Options(CandidatesFixtures.options.toUi(now))
        compose.setContent {
            CompositionLocalProvider(LocalMapRenderer provides MapRenderer.PREVIEW) {
                SunSeatTheme { CandidatesScreen(request, state, CandidatesActions(onRequestChange = { changes += it }), now = now) }
            }
        }
        compose.waitForIdle()
        inHeader(hasClickAction() and hasText(text(R.string.input_goal_chip_avoid))).performSemanticsAction(SemanticsActions.OnClick)
        compose.waitForIdle()
        compose.onNode(hasClickAction() and hasText(text(R.string.input_goal_seek))).performSemanticsAction(SemanticsActions.OnClick)
        compose.waitForIdle()
        assertEquals(listOf(request.copy(goal = SunGoal.SEEK)), changes)
    }

    @Test fun choosingTheSameGoalChangesNothing() {
        val changes = mutableListOf<SunGoal>()
        renderInput({ filled }, actions = InputActions(setGoal = { changes += it }))
        inHeader(hasClickAction() and hasText(text(R.string.input_goal_chip_avoid))).performSemanticsAction(SemanticsActions.OnClick)
        compose.waitForIdle()
        compose.onNode(hasClickAction() and hasText(text(R.string.input_goal_avoid))).performSemanticsAction(SemanticsActions.OnClick)
        compose.waitForIdle()
        assertEquals(emptyList<SunGoal>(), changes)
    }

    private fun mapTopPadding(): Float = compose.onNodeWithTag(INPUT_MAP_TAG).fetchSemanticsNode().config[MapTopPaddingKey]

    private fun assertMapPaddingCoversTopPanel(bottomPx: Float) {
        val densityValue = context.resources.displayMetrics.density
        assertEquals(bottomPx / densityValue, mapTopPadding(), 1f)
    }

    @Test fun inputMapPaddingIsTheMeasuredHeaderHeight() {
        renderInput({ filled })
        assertMapPaddingCoversTopPanel(header().bottom)
    }

    @Test @Config(qualifiers = "w320dp-h640dp-xxhdpi")
    fun inputMapPaddingFollowsLargeTextHeader() {
        renderInput({ filled }, fontScale = 1.5f)
        assertMapPaddingCoversTopPanel(header().bottom)
    }

    @Test fun inputMapPaddingIncludesTheLocationNotice() {
        renderInput({ filled.copy(locationMessageRes = R.string.location_permission_permanently_denied, locationAction = com.sunseat.app.location.LocationAction.OpenAppSettings) })
        val notice = compose.onNode(hasText(text(R.string.location_permission_permanently_denied))).fetchSemanticsNode().boundsInRoot
        assertTrue(notice.top >= header().bottom)
        assertTrue(mapTopPadding() * context.resources.displayMetrics.density >= notice.bottom - 1f)
    }

    /** 공간이 부족해도 출발 시각·햇빛 목적 둘 다 헤더 안에 보인다(가로로 밀려 숨지 않는다). */
    @Test @Config(qualifiers = "w320dp-h640dp-xxhdpi")
    fun timeAndGoalReflowInsideTheHeaderAtLargeText() {
        compose.enableAccessibilityChecks()
        renderInput({ filled.copy(departure = LocalDateTime.of(2026, 12, 24, 23, 30)) }, fontScale = 1.5f)
        val outer = header()
        assertInside(outer, bounds(hasClickAction() and hasText("23:30 출발", substring = true)), "time")
        assertInside(outer, bounds(hasClickAction() and hasText(text(R.string.input_goal_chip_avoid))), "goal")
        assertInside(outer, bounds(hasContentDescription(text(R.string.settings_title))), "settings")
        compose.onRoot().tryPerformAccessibilityChecks()
    }

    /** 알림이 생겨 헤더 아래 여백이 바뀌어도 카메라를 다시 맞추지 않고, 끝점이 바뀔 때만 맞춘다. */
    @Test fun noticeDoesNotRefitTheMapButANewEndpointDoes() {
        val state = mutableStateOf(filled)
        renderInput({ state.value })
        fun revision() = compose.onNodeWithTag(INPUT_MAP_TAG).fetchSemanticsNode().config[MapCameraRevisionKey]
        val first = revision()
        val before = mapTopPadding()
        state.value = filled.copy(locationMessageRes = R.string.location_permission_permanently_denied)
        compose.waitForIdle()
        assertTrue(mapTopPadding() > before)
        assertEquals(first, revision())
        state.value = filled.copy(destination = Endpoint(NamedPlace("시청", LatLng(37.57, 126.98))))
        compose.waitForIdle()
        assertEquals(first + 1, revision())
    }
}
