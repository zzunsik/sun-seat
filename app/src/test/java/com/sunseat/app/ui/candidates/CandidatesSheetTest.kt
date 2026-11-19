package com.sunseat.app.ui.candidates

import android.app.Application
import androidx.compose.runtime.*
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import com.sunseat.app.ui.map.LocalMapRenderer
import com.sunseat.app.ui.map.MapRenderer
import com.sunseat.app.ui.theme.SunSeatTheme
import org.junit.Assert.assertTrue
import kotlinx.coroutines.launch
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class, sdk = [36], qualifiers = "w390dp-h844dp-xxhdpi")
@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
class CandidatesSheetTest {
    @get:Rule val compose = createComposeRule(effectContext = object : androidx.compose.ui.MotionDurationScale { override val scaleFactor = 0f })

    @Test fun expandedSheetCoversMapAndCollapsedSheetRestoresMeasuredViewport() {
        lateinit var sheet: androidx.compose.material3.BottomSheetScaffoldState
        lateinit var scope: kotlinx.coroutines.CoroutineScope
        val state = CandidatesState.Options(CandidatesFixtures.options.toUi(java.time.LocalDateTime.of(2026, 11, 15, 9, 0)))
        compose.setContent {
            sheet = androidx.compose.material3.rememberBottomSheetScaffoldState()
            scope = rememberCoroutineScope()
            CompositionLocalProvider(LocalMapRenderer provides MapRenderer.PREVIEW) {
                SunSeatTheme { CandidatesScreen(CandidatesFixtures.transitRequest, state, CandidatesActions(), scaffoldState = sheet) }
            }
        }
        compose.mainClock.advanceTimeBy(5000)
        compose.waitForIdle()
        fun visibleHeight() = compose.onNodeWithTag("candidate_map").fetchSemanticsNode().config[com.sunseat.app.ui.map.MapVisibleHeightKey]
        val initial = visibleHeight()
        assertTrue(initial > 0)
        compose.runOnIdle { scope.launch { sheet.bottomSheetState.expand() } }
        compose.mainClock.advanceTimeBy(5000)
        compose.waitForIdle()
        assertEquals(0f, visibleHeight(), 0.01f)
        compose.runOnIdle { scope.launch { sheet.bottomSheetState.partialExpand() } }
        compose.mainClock.advanceTimeBy(5000)
        compose.waitForIdle()
        assertEquals(initial, visibleHeight(), 0.01f)
    }

    @Test fun previewCloseRestoresSelectionSortFiltersAndNonzeroScroll() {
        val base = CandidatesFixtures.options.toUi(java.time.LocalDateTime.of(2026, 11, 15, 9, 0)).first()
        var state by mutableStateOf(CandidatesState.Options((0..9).map { base.copy(id = it) }, sort = CandidateSort.WALK, filters = setOf(base.type)))
        val restoration = androidx.compose.ui.test.junit4.StateRestorationTester(compose)
        restoration.setContent {
            CompositionLocalProvider(LocalMapRenderer provides MapRenderer.PREVIEW) {
                SunSeatTheme { CandidatesScreen(CandidatesFixtures.transitRequest, state, CandidatesActions(onHighlight = { state = state.copy(selectedId = it.id) }, onSort = { state = state.copy(sort = it) })) }
            }
        }
        compose.onNodeWithTag("candidate_list").performScrollToIndex(5)
        compose.waitForIdle()
        val before = compose.onNodeWithTag("candidate_list").fetchSemanticsNode().config[SemanticsProperties.VerticalScrollAxisRange].value()
        compose.onNodeWithTag("candidate_5").performSemanticsAction(SemanticsActions.OnClick)
        compose.onNodeWithText("선택한 경로").assertExists()
        compose.onNodeWithText("전체 경로 보기").assertExists()
        restoration.emulateSavedInstanceStateRestore()
        compose.onNodeWithText("선택한 경로").assertExists()
        compose.waitUntil(10_000) { compose.onNodeWithTag("candidate_map").fetchSemanticsNode().config[com.sunseat.app.ui.map.MapCameraRevisionKey] > 0 }
        var revision = compose.onNodeWithTag("candidate_map").fetchSemanticsNode().config[com.sunseat.app.ui.map.MapCameraRevisionKey]
        compose.onNodeWithTag("candidate_map").performTouchInput { swipeLeft() }
        compose.onNodeWithText("전체 경로 보기").performSemanticsAction(SemanticsActions.OnClick)
        compose.waitUntil(10_000) { compose.onNodeWithTag("candidate_map").fetchSemanticsNode().config[com.sunseat.app.ui.map.MapCameraRevisionKey] > revision }
        revision = compose.onNodeWithTag("candidate_map").fetchSemanticsNode().config[com.sunseat.app.ui.map.MapCameraRevisionKey]
        compose.onNodeWithTag("candidate_map").performTouchInput { swipeLeft() }
        compose.onAllNodesWithContentDescription("경로 목록으로 돌아가기")[0].performSemanticsAction(SemanticsActions.OnClick)
        compose.waitForIdle()
        compose.onNodeWithTag("candidate_5").assertIsSelected()
        val after = compose.onNodeWithTag("candidate_list").fetchSemanticsNode().config[SemanticsProperties.VerticalScrollAxisRange].value()
        assertEquals(before, after, 0.001f)
        assertEquals(CandidateSort.WALK, state.sort)
        assertEquals(setOf(base.type), state.filters)
        compose.onNodeWithText("최소 시간").performSemanticsAction(SemanticsActions.OnClick)
        compose.mainClock.advanceTimeBy(1000)
        compose.waitForIdle()
        assertEquals(revision, compose.onNodeWithTag("candidate_map").fetchSemanticsNode().config[com.sunseat.app.ui.map.MapCameraRevisionKey])
    }
}
