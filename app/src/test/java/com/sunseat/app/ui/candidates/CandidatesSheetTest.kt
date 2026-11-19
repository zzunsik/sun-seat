package com.sunseat.app.ui.candidates

import android.app.Application
import androidx.compose.runtime.*
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import com.sunseat.app.ui.map.LocalMapRenderer
import com.sunseat.app.ui.map.MapRenderer
import com.sunseat.app.ui.map.points
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

    /** #99: 선택 미리보기 지도에는 선택한 경로만 그려 다른 후보가 현재 여정의 분기처럼 보이지 않게 한다. */
    @Test fun previewDrawsOnlySelectedRouteAndOverviewRestoresAllCandidates() {
        val base = CandidatesFixtures.options.toUi(java.time.LocalDateTime.of(2026, 11, 15, 9, 0)).first()
        val origin = CandidatesFixtures.transitRequest.origin.location
        val destination = CandidatesFixtures.transitRequest.destination.location
        // 서로 멀리 갈라지는 합성 후보: 같은 출발·도착을 공유해 다른 후보가 선택 경로의 분기처럼 보이는 조건.
        fun branch(id: Int, latOffset: Double, lngOffset: Double) = base.copy(id = id, chosen = base.chosen.copy(route = com.sunseat.app.domain.Route(listOf(
            com.sunseat.app.domain.Leg(com.sunseat.app.domain.Mode.BUS, listOf(origin, com.sunseat.app.domain.LatLng(origin.latitude + latOffset, origin.longitude + lngOffset), destination), java.time.Duration.ofMinutes(30), "출발", "도착"),
        ))))
        // 목록 순서(2,0,1)와 지도 순서(id)를 다르게 해 예전 index 재사용 오류도 잡는다.
        var state by mutableStateOf(CandidatesState.Options(listOf(branch(2, -0.2, 0.3), branch(0, 0.0, 0.0), branch(1, 0.25, -0.2))))
        compose.setContent {
            CompositionLocalProvider(LocalMapRenderer provides MapRenderer.PREVIEW) {
                SunSeatTheme { CandidatesScreen(CandidatesFixtures.transitRequest, state, CandidatesActions(onHighlight = { state = state.copy(selectedId = it.id) })) }
            }
        }
        fun map() = compose.onNodeWithTag("candidate_map").fetchSemanticsNode().config
        fun shownIds() = map()[CandidateMapRouteIdsKey]
        fun shownSelected() = map()[CandidateMapSelectedRouteKey]
        compose.waitForIdle()
        assertEquals(listOf(0, 1, 2), shownIds())
        for (id in listOf(0, 1, 2)) {
            compose.onNodeWithTag("candidate_list").performScrollToKey(id)
            compose.onNodeWithTag("candidate_$id").performSemanticsAction(SemanticsActions.OnClick)
            compose.onNodeWithText("선택한 경로").assertExists()
            compose.waitForIdle()
            assertEquals("미리보기 #$id 는 선택 경로만 그린다", listOf(id), shownIds())
            assertEquals(0, shownSelected())
            // '전체 경로 보기'는 선택 경로 전체 구간에 카메라를 맞출 뿐 다른 후보를 다시 섞지 않는다.
            compose.onNodeWithText("전체 경로 보기").performSemanticsAction(SemanticsActions.OnClick)
            compose.waitForIdle()
            assertEquals(listOf(id), shownIds())
            compose.onAllNodesWithContentDescription("경로 목록으로 돌아가기")[0].performSemanticsAction(SemanticsActions.OnClick)
            compose.waitForIdle()
            // 목록 개요는 비교용 전체 후보를 유지하고 선택 index가 같은 후보를 가리킨다.
            assertEquals(listOf(0, 1, 2), shownIds())
            assertEquals(id, shownSelected())
            compose.onNodeWithTag("candidate_$id").assertIsSelected()
        }
    }

    /** #99: 미리보기 route 목록·선택 index·카메라 맞춤 좌표·출발/도착 마커가 같은 선택 후보를 가리킨다. */
    @Test fun previewRouteSetIndexAndFitPointsMatchSelectedCandidate() {
        val base = CandidatesFixtures.options.toUi(java.time.LocalDateTime.of(2026, 11, 15, 9, 0)).first()
        val request = CandidatesFixtures.transitRequest
        fun candidate(id: Int, offset: Double) = base.copy(id = id, chosen = base.chosen.copy(route = com.sunseat.app.domain.Route(listOf(
            com.sunseat.app.domain.Leg(com.sunseat.app.domain.Mode.BUS, listOf(request.origin.location, com.sunseat.app.domain.LatLng(37.5 + offset, 127.0 + offset), request.destination.location), java.time.Duration.ofMinutes(20), "출발", "도착"),
        ))))
        val visible = listOf(candidate(2, 0.3), candidate(0, 0.0), candidate(1, -0.3))
        for (selected in visible) {
            val preview = candidateMapRoutes(visible, selected.id, preview = true)
            assertEquals(listOf(selected.id), preview.ids)
            assertEquals(listOf(selected.chosen.route), preview.routes)
            assertEquals(0, preview.selectedRoute)
            val map = com.sunseat.app.ui.map.MapState(routes = preview.routes, selectedRoute = preview.selectedRoute, origin = request.origin, destination = request.destination)
            assertEquals(selected.chosen.route.legs.flatMap { it.path } + listOf(request.origin.location, request.destination.location), map.points())
            val overview = candidateMapRoutes(visible, selected.id, preview = false)
            assertEquals(listOf(0, 1, 2), overview.ids)
            assertEquals(selected.chosen.route, overview.routes[overview.selectedRoute])
        }
        // 선택이 없으면 개요는 모든 후보를 그리되 선택 index는 없다.
        assertEquals(-1, candidateMapRoutes(visible, null, preview = false).selectedRoute)
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
        // Reopening the same card requests one settled route fit, preserving the list.
        compose.onNodeWithTag("candidate_5").performSemanticsAction(SemanticsActions.OnClick)
        compose.onNodeWithText("선택한 경로").assertExists()
        compose.waitUntil(10_000) { compose.onNodeWithTag("candidate_map").fetchSemanticsNode().config[com.sunseat.app.ui.map.MapCameraRevisionKey] > revision }
        compose.mainClock.advanceTimeBy(1000)
        compose.waitForIdle()
        assertEquals(revision + 1, compose.onNodeWithTag("candidate_map").fetchSemanticsNode().config[com.sunseat.app.ui.map.MapCameraRevisionKey])
        revision++
        compose.onAllNodesWithContentDescription("경로 목록으로 돌아가기")[0].performSemanticsAction(SemanticsActions.OnClick)
        compose.waitForIdle()
        assertEquals(before, compose.onNodeWithTag("candidate_list").fetchSemanticsNode().config[SemanticsProperties.VerticalScrollAxisRange].value(), 0.001f)
        compose.onNodeWithText("최소 시간").performSemanticsAction(SemanticsActions.OnClick)
        compose.mainClock.advanceTimeBy(1000)
        compose.waitForIdle()
        assertEquals(revision, compose.onNodeWithTag("candidate_map").fetchSemanticsNode().config[com.sunseat.app.ui.map.MapCameraRevisionKey])
    }
}
