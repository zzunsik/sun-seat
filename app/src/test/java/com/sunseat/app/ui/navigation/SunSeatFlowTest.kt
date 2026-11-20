package com.sunseat.app.ui.navigation

import android.Manifest
import android.app.Application
import android.content.pm.PackageManager
import androidx.activity.ComponentActivity
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.test.assertIsNotSelected
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performImeAction
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.test.performTextReplacement
import androidx.compose.ui.test.performTextInput
import androidx.core.view.WindowCompat
import androidx.test.core.app.ApplicationProvider
import com.sunseat.app.R
import com.sunseat.app.SunSeatContent
import com.sunseat.app.data.favorites.FavoriteKind
import com.sunseat.app.data.place.Place
import com.sunseat.app.data.settings.Settings
import com.sunseat.app.domain.LatLng
import com.sunseat.app.ui.map.LocalMapRenderer
import com.sunseat.app.ui.map.MapRenderer
import java.time.Instant
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

/**
 * 화면 흐름 회귀 테스트(#60): 입력 → 경로 후보 → 결과 → 햇빛 따라가기, 즐겨찾기·설정, 오류 상태.
 * 카카오 응답은 직접 만든 값만 쓴다. 지도는 네이티브 SDK 대신 테스트 렌더러로 그린다.
 */
@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class, sdk = [36], qualifiers = "w390dp-h844dp-xxhdpi")
class SunSeatFlowTest {
    @get:Rule val temporary = TemporaryFolder()
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()

    private var graph: FlowTestGraph? = null

    @After fun close() { graph?.close() }

    private fun text(id: Int, vararg args: Any): String = compose.activity.getString(id, *args)

    private fun launch(settings: Settings = Settings.DEFAULT, now: Instant = Instant.parse("2026-11-17T01:00:00Z"), server: FakeKakaoServer = FakeKakaoServer(), location: com.sunseat.app.location.LocationProvider = com.sunseat.app.location.LocationProvider { com.sunseat.app.location.LocationResult.Unavailable }): FlowTestGraph {
        val created = FlowTestGraph(ApplicationProvider.getApplicationContext(), temporary.newFolder(), server, InMemorySettings(settings), now, location)
        graph = created
        compose.setContent {
            CompositionLocalProvider(LocalMapRenderer provides MapRenderer.PREVIEW) {
                SunSeatContent(created, compose.activity.window)
            }
        }
        compose.waitForIdle()
        return created
    }

    private fun waitForText(value: String, timeoutMillis: Long = 10_000) {
        compose.waitUntil(timeoutMillis) { compose.onAllNodesWithText(value).fetchSemanticsNodes().isNotEmpty() }
    }

    private fun waitForTextGone(value: String, timeoutMillis: Long = 10_000) {
        compose.waitUntil(timeoutMillis) { compose.onAllNodesWithText(value).fetchSemanticsNodes().isEmpty() }
    }

    /** 화면 밖(아래 시트·가로 스크롤)에 있어도 클릭 동작을 바로 실행한다. */
    private fun SemanticsNodeInteraction.tap(): SemanticsNodeInteraction = performSemanticsAction(SemanticsActions.OnClick).also { compose.waitForIdle() }

    private fun clickText(value: String) = compose.onNode(hasClickAction() and hasText(value)).tap()

    private fun scrubLabel(): String = compose.onNodeWithContentDescription(text(R.string.simulation_slider))
        .fetchSemanticsNode().config[SemanticsProperties.StateDescription]

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

    /** 예시역 1호선 → 견본 카페(직접 만든 장소 응답). 둘 다 정하면 입력 화면이 바로 경로 후보로 넘어간다. */
    private fun enterTrip() {
        search(text(R.string.input_origin_placeholder), "예시", ORIGIN)
        search(text(R.string.input_destination_placeholder), "견본", DESTINATION)
    }

    @Test fun backRemovesOutgoingSettingsWithinFourFrames() {
        launch()
        compose.onNodeWithContentDescription(text(R.string.settings_title)).tap()
        compose.onNodeWithText(text(R.string.settings_title)).assertExists()
        compose.mainClock.autoAdvance = false
        compose.runOnUiThread { compose.activity.onBackPressedDispatcher.onBackPressed() }
        compose.waitForIdle()
        repeat(4) {
            compose.mainClock.advanceTimeByFrame()
            compose.waitForIdle()
        }
        compose.onNodeWithText(text(R.string.settings_title)).assertDoesNotExist()
        compose.mainClock.autoAdvance = true
        compose.onNodeWithText(text(R.string.input_destination_placeholder)).assertExists()
    }

    @Test fun predictiveBackCancelPreservesSettingsAndCommitPopsOnlyOnce() {
        launch()
        compose.onNodeWithContentDescription(text(R.string.settings_title)).tap()
        val dispatcher = compose.activity.onBackPressedDispatcher
        fun gesture(progress: Float) = androidx.activity.BackEventCompat(0f, 200f, progress, androidx.activity.BackEventCompat.EDGE_LEFT)
        compose.runOnUiThread {
            dispatcher.dispatchOnBackStarted(gesture(0f))
            dispatcher.dispatchOnBackProgressed(gesture(0.5f))
            dispatcher.dispatchOnBackCancelled()
        }
        compose.waitForIdle()
        compose.onNodeWithText(text(R.string.settings_title)).assertExists()
        compose.runOnUiThread {
            dispatcher.dispatchOnBackStarted(gesture(0f))
            dispatcher.dispatchOnBackProgressed(gesture(0.8f))
            dispatcher.onBackPressed()
        }
        compose.waitForIdle()
        compose.onNodeWithText(text(R.string.settings_title)).assertDoesNotExist()
        compose.onNodeWithText(text(R.string.input_destination_placeholder)).assertExists()
    }

    @Test fun inputCandidatesResultSimulationAndBackKeepEachScreenState() {
        launch()
        enterTrip()

        // 경로 후보: 직접 만든 응답의 두 경로(30분 지하철, 40분 버스+지하철)
        waitForText("30분")
        waitForText("40분")
        clickText(text(R.string.candidates_sort_walk))
        clickText(text(R.string.candidates_filter_subway))
        waitForTextGone("40분")
        compose.onNode(hasClickAction() and hasText("30분")).tap()
        compose.onNodeWithTag("candidates_preview_sheet").assertExists()
        clickText(text(R.string.candidates_view_seat))

        // 결과 → 햇빛 따라가기
        val ride = text(R.string.simulation_title)
        waitForText(text(R.string.result_legs))
        compose.mainClock.autoAdvance = false
        compose.onNode(hasClickAction() and hasText(ride)).performSemanticsAction(SemanticsActions.OnClick)
        compose.waitUntil(10_000) {
            compose.mainClock.advanceTimeByFrame()
            compose.onAllNodesWithText(text(R.string.simulation_pause)).fetchSemanticsNodes().isNotEmpty()
        }
        // #56: 진입하면 자동으로 이동한다. 자동 시계를 멈춘 채 프레임을 직접 넘긴다.
        compose.onNode(hasClickAction() and hasText(text(R.string.simulation_pause))).assertExists()
        val before = scrubLabel()
        compose.mainClock.advanceTimeBy(2_000)
        compose.onNode(hasClickAction() and hasText(text(R.string.simulation_pause))).performSemanticsAction(SemanticsActions.OnClick)
        compose.mainClock.advanceTimeByFrame()
        compose.mainClock.autoAdvance = true
        compose.onNode(hasClickAction() and hasText(text(R.string.simulation_play))).assertExists()
        assertNotEquals(before, scrubLabel())

        // 뒤로: 결과 → 후보(정렬·필터·선택 유지) → 입력(출발·도착 유지)
        back()
        waitForText(text(R.string.result_legs))
        compose.onNodeWithText(text(R.string.simulation_play)).assertDoesNotExist()
        back()
        waitForText("30분")
        compose.onNodeWithText(text(R.string.candidates_sort_walk)).assertIsSelected()
        compose.onNodeWithText(text(R.string.candidates_sort_recommended)).assertIsNotSelected()
        compose.onNodeWithText(text(R.string.candidates_filter_subway)).assertIsSelected()
        compose.onNodeWithText("40분").assertDoesNotExist()
        compose.onNode(hasClickAction() and hasText("30분")).assertIsSelected()
        back()
        compose.onNodeWithText(ORIGIN).assertExists()
        compose.onNodeWithText(DESTINATION).assertExists()
        compose.onNodeWithText(text(R.string.candidates_view_seat)).assertDoesNotExist()
    }

    @Test fun repeatedSelectedCardAlwaysPreviewsUntilExplicitSeatAction() {
        val graph = launch()
        enterTrip()
        waitForText("30분")
        clickText(text(R.string.candidates_sort_walk))
        clickText(text(R.string.candidates_filter_subway))
        repeat(3) {
            compose.onNode(hasClickAction() and hasText("30분")).tap()
            compose.onNodeWithTag("candidates_preview_sheet").assertExists()
            compose.onNodeWithText(text(R.string.result_legs)).assertDoesNotExist()
            assertEquals(1, graph.server.transitRequests)
            back()
            compose.onNodeWithText(text(R.string.candidates_sort_walk)).assertIsSelected()
            compose.onNodeWithText(text(R.string.candidates_filter_subway)).assertIsSelected()
            compose.onNode(hasClickAction() and hasText("30분")).assertIsSelected()
        }
        compose.onNode(hasClickAction() and hasText("30분")).tap()
        clickText(text(R.string.candidates_view_seat))
        waitForText(text(R.string.result_legs))
        assertEquals(1, graph.server.transitRequests)
    }

    @Test fun failedCurrentLocationEditDoesNotConfirmOldLocationOnDismiss() {
        shadowOf(compose.activity.application).grantPermissions(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION)
        var calls = 0
        val graph = launch(location = com.sunseat.app.location.LocationProvider {
            if (++calls == 1) com.sunseat.app.location.LocationResult.Success(37.5, 127.0, 20f, false)
            else com.sunseat.app.location.LocationResult.Unavailable
        })
        waitForText(text(R.string.location_use_current))
        search(text(R.string.input_destination_placeholder), "견본", DESTINATION)
        waitForText("30분")
        clickText(text(R.string.location_use_current))
        clickText(text(R.string.location_use_current))
        waitForText(text(R.string.location_unavailable))
        compose.onNodeWithContentDescription(text(R.string.input_dismiss)).tap()
        compose.onNode(hasSetTextAction()).assertExists()
        assertEquals(1, graph.server.transitRequests)
        back()
        compose.onNodeWithText(DESTINATION).assertExists()
        assertEquals(1, graph.server.transitRequests)
    }

    @Test fun candidateEndpointEditCancelSwapAndBackPreserveConfirmedTrip() {
        val graph = launch()
        enterTrip()
        waitForText("30분")
        clickText(text(R.string.candidates_sort_walk))
        clickText(text(R.string.candidates_filter_subway))
        compose.onNode(hasClickAction() and hasText("30분")).tap()
        val placeCalls = graph.server.placeRequests
        back() // Close selected-route preview, retaining the chosen card.
        clickText(ORIGIN)
        compose.onNode(hasSetTextAction() and hasText(ORIGIN)).assertExists()
        assertEquals(placeCalls, graph.server.placeRequests)
        compose.onNodeWithContentDescription(text(R.string.input_search_clear)).tap()
        back()
        compose.onNodeWithText(text(R.string.candidates_sort_walk)).assertIsSelected()
        compose.onNodeWithText(text(R.string.candidates_filter_subway)).assertIsSelected()
        compose.onNode(hasClickAction() and hasText("30분")).assertIsSelected()
        assertEquals(1, graph.server.transitRequests)

        // Same-place selection is rejected; then choose a synthetic different endpoint.
        clickText(DESTINATION)
        compose.onNode(hasSetTextAction()).performTextReplacement("예시")
        compose.onNode(hasSetTextAction()).performImeAction()
        compose.waitUntil(10_000) { compose.onAllNodes(hasClickAction() and hasText(ORIGIN)).fetchSemanticsNodes().isNotEmpty() }
        compose.onNode(hasClickAction() and hasText(ORIGIN)).tap()
        waitForText(text(R.string.input_same_endpoints))
        assertEquals(1, graph.server.transitRequests)
        back()
        clickText(ORIGIN)
        compose.onNode(hasSetTextAction()).performTextReplacement("")
        // Recent places are local, even when used from the candidates editor.
        compose.onNode(hasClickAction() and hasText(ORIGIN)).tap()
        assertEquals(1, graph.server.transitRequests)
        compose.onNodeWithContentDescription(text(R.string.input_swap)).tap()
        compose.waitUntil(10_000) { graph.server.transitRequests == 2 }
        waitForText("30분")
        compose.onNodeWithContentDescription(text(R.string.input_swap)).tap()
        compose.waitUntil(10_000) { graph.server.transitRequests == 3 }
        waitForText("30분")
        back()
        compose.onNodeWithText(ORIGIN).assertExists()
        compose.onNodeWithText(DESTINATION).assertExists()
        assertEquals(3, graph.server.transitRequests)
    }

    @Test fun candidateEndpointSelectionChangesOnlyChosenFieldAndQueriesOnce() {
        val graph = launch()
        enterTrip()
        waitForText("30분")
        val alternate = Place("alternate", "새 견본 도착지", "합성 주소", "", LatLng(37.61, 127.12), "")
        runBlocking { graph.favorites.put(alternate, FavoriteKind.HOME) }
        clickText(DESTINATION)
        compose.onNode(hasSetTextAction() and hasText(DESTINATION)).assertExists()
        compose.onAllNodes(hasClickAction() and hasText(text(R.string.favorites_home)))[0].tap()
        compose.waitUntil(10_000) { graph.server.transitRequests == 2 }
        waitForText("30분")
        compose.onNodeWithText(ORIGIN).assertExists()
        compose.onNodeWithText(alternate.name).assertExists()
        assertEquals(2, graph.server.placeRequests)
        back()
        compose.onNodeWithText(ORIGIN).assertExists()
        compose.onNodeWithText(alternate.name).assertExists()
        assertEquals(2, graph.server.transitRequests)
    }

    @Test fun choosingAFavoriteFillsTheInputImmediately() {
        val graph = launch()
        runBlocking { graph.favorites.put(Place("home", "우리집", "가상구 1", "", LatLng(37.51, 127.02), ""), FavoriteKind.HOME) }
        search(text(R.string.input_origin_placeholder), "예시", ORIGIN)

        // 도착 검색에서 회사 설정 → 즐겨찾기 화면에서 저장 → 돌아와 바로 고른다
        clickText(text(R.string.input_destination_placeholder))
        waitForText(text(R.string.favorites_home))
        clickText(text(R.string.favorites_set_work))
        waitForText(text(R.string.favorites_search_work))
        compose.onNode(hasSetTextAction()).performTextInput("견본")
        compose.onNode(hasSetTextAction()).performImeAction()
        waitForText(DESTINATION)
        clickText(DESTINATION)
        waitForTextGone(text(R.string.favorites_search_work))
        waitForTextGone(text(R.string.favorites_set_work))
        back()

        waitForText(text(R.string.favorites_work))
        // 검색 화면의 칩과 목록 두 곳에 같은 즐겨찾기가 있다
        compose.onAllNodes(hasClickAction() and hasText(text(R.string.favorites_work)))[0].tap()
        assertEquals(2, graph.server.placeRequests)
        // 즐겨찾기를 고르면 검색 없이 도착이 정해지고 바로 경로 후보로 넘어간다
        waitForText("30분")
        compose.onNodeWithText(ORIGIN).assertExists()
        compose.onNodeWithText(DESTINATION).assertExists()
        back()
        compose.onNodeWithText(DESTINATION).assertExists()
    }

    @Test fun settingsChangesReachThemeInputAndNextScreens() {
        launch()
        val controller = WindowCompat.getInsetsController(compose.activity.window, compose.activity.window.decorView)
        assertTrue(controller.isAppearanceLightStatusBars)

        compose.onNodeWithContentDescription(text(R.string.settings_title)).tap()
        clickText(text(R.string.settings_dark))
        clickText(text(R.string.common_car))
        clickText(text(R.string.input_goal_seek))
        assertFalse(controller.isAppearanceLightStatusBars)
        back()

        // 입력 화면: 기본 수단·햇빛 선호가 바로 반영된다
        compose.onNode(hasText(text(R.string.common_car)) and hasClickAction()).assertIsSelected()
        compose.onNodeWithText(text(R.string.input_goal_chip_seek)).assertExists()

        // 자가용은 후보 없이 결과로 바로 가고, 뒤로 가면 입력으로 돌아온다
        enterTrip()
        waitForText(text(R.string.result_legs))
        compose.onNodeWithText(text(R.string.result_label_seek)).assertExists()
        compose.onNodeWithText(text(R.string.common_car) + " · ", substring = true).assertExists()
        back()
        compose.onNodeWithText(ORIGIN).assertExists()
        compose.onNodeWithText(text(R.string.result_legs)).assertDoesNotExist()
    }

    @Test fun subwayBenchPreferenceRecomputesTheOpenResult() {
        val server = FakeKakaoServer().apply { transit = { FakeKakaoServer.json(ABOVE_GROUND_SUBWAY) } }
        // 12월 아침(해 고도 약 8°): 낮은 해가 반대쪽 벤치 정면까지 든다
        launch(now = Instant.parse("2026-12-16T23:30:00Z"), server = server)
        enterTrip()
        waitForText(text(R.string.candidates_view_seat))
        clickText(text(R.string.candidates_view_seat))
        val left = text(R.string.result_sit_side, text(R.string.common_left))
        val right = text(R.string.result_sit_side, text(R.string.common_right))
        waitForText(left)

        clickText(text(R.string.settings_bench_change))
        clickText(text(R.string.settings_avoid_front))
        back()

        waitForText(right)
        compose.onNodeWithText(left).assertDoesNotExist()
    }

    @Test fun networkErrorRetryAndNoRouteFallBackToCar() {
        val server = FakeKakaoServer().apply { transit = { FakeKakaoServer.disconnect() } }
        launch(server = server)
        enterTrip()

        waitForText(text(R.string.search_error_network))
        server.transit = { FakeKakaoServer.json("""{"status":"NO_RESULTS","properties":{"total":0}}""") }
        clickText(text(R.string.search_retry))
        waitForText(text(R.string.candidates_no_transit))

        // 경로 없음 → 자가용 보기: 후보 화면을 결과로 바꿔 끼워 뒤로 가면 입력으로 간다
        clickText(text(R.string.candidates_search_by_car))
        waitForText(text(R.string.result_legs))
        back()
        compose.onNodeWithText(ORIGIN).assertExists()
        compose.onNodeWithText(DESTINATION).assertExists()
        compose.onNodeWithText(text(R.string.candidates_no_transit)).assertDoesNotExist()
    }

    @Test fun errorEditInputReturnsToFilledInput() {
        val server = FakeKakaoServer().apply { transit = { FakeKakaoServer.json("""{"status":"INVALID_REQUEST","properties":{"total":0}}""") } }
        launch(server = server)
        enterTrip()
        waitForText(text(R.string.candidates_error_unknown))
        clickText(text(R.string.candidates_edit_input))
        compose.onNodeWithText(ORIGIN).assertExists()
        compose.onNodeWithText(DESTINATION).assertExists()
        compose.onNodeWithText(text(R.string.candidates_error_unknown)).assertDoesNotExist()
    }

    @Test fun deniedLocationPermissionShowsNoticeAndManualSearch() {
        launch()
        compose.onNodeWithContentDescription(text(R.string.location_use_current)).tap()
        val request = shadowOf(compose.activity).lastRequestedPermission
        assertTrue(Manifest.permission.ACCESS_FINE_LOCATION in request.requestedPermissions)
        shadowOf(compose.activity.packageManager).setShouldShowRequestPermissionRationale(Manifest.permission.ACCESS_FINE_LOCATION, true)
        compose.runOnUiThread {
            @Suppress("DEPRECATION")
            compose.activity.onRequestPermissionsResult(request.requestCode, request.requestedPermissions, IntArray(request.requestedPermissions.size) { PackageManager.PERMISSION_DENIED })
        }
        compose.waitForIdle()

        waitForText(text(R.string.location_permission_denied))
        compose.onNodeWithText(text(R.string.location_request_permission)).assertExists()
        clickText(text(R.string.input_search_manually))
        compose.onNodeWithText(text(R.string.input_search_hint_origin)).assertExists()
    }

    @Test fun mapLocationKeepsManualEndpointsWithoutSearchingAgain() {
        shadowOf(compose.activity.application).grantPermissions(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION)
        val graph = launch(location = com.sunseat.app.location.LocationProvider { com.sunseat.app.location.LocationResult.Success(37.5, 127.0, 20f, false) })
        waitForText(text(R.string.location_use_current))
        search(text(R.string.location_use_current), "예시", ORIGIN)
        search(text(R.string.input_destination_placeholder), "견본", DESTINATION)
        waitForText("30분")
        back()
        val routeCalls = graph.server.transitRequests
        compose.onNodeWithContentDescription(text(R.string.location_use_current)).tap()
        compose.waitForIdle()
        compose.onNodeWithText(ORIGIN).assertExists()
        compose.onNodeWithText(DESTINATION).assertExists()
        compose.onNodeWithText("30분").assertDoesNotExist()
        assertEquals(routeCalls, graph.server.transitRequests)
    }

    @Test fun inputTimeChangeAfterBackKeepsInputAndDoesNotRefetch() {
        val graph = launch()
        enterTrip()
        waitForText("30분")
        back()
        clickText(text(R.string.input_now_depart))
        compose.onNodeWithTag("schedule_minute").performSemanticsAction(SemanticsActions.ScrollToIndex) { it(37) }
        compose.waitForIdle()
        val selectedLabel = java.time.LocalDateTime.now(graph.clock).withMinute(37)
            .format(java.time.format.DateTimeFormatter.ofPattern("HH:mm")) + " 출발"
        clickText(text(R.string.input_time_apply))
        compose.onNodeWithText("30분").assertDoesNotExist()
        compose.onNodeWithText(ORIGIN).assertExists()
        compose.onNodeWithText(DESTINATION).assertExists()
        compose.onNodeWithText(selectedLabel, substring = true).assertExists()
        assertEquals(1, graph.server.transitRequests)
        compose.onNode(hasClickAction() and hasText(selectedLabel, substring = true)).tap()
        compose.onNodeWithTag("schedule_minute").assertExists()
        back()
        compose.onNodeWithText(selectedLabel, substring = true).assertExists()
        assertEquals(1, graph.server.transitRequests)
    }

    @Test fun candidateTimeChangeSurvivesBackWithoutRefetch() {
        val graph = launch()
        enterTrip()
        waitForText("30분")
        val routeCalls = graph.server.transitRequests
        clickText(text(R.string.input_now_depart))
        compose.onNodeWithTag("schedule_date").performSemanticsAction(SemanticsActions.ScrollToIndex) { it(1) }
        compose.waitForIdle()
        clickText(text(R.string.input_time_apply))
        waitForText("30분")
        back()
        compose.onNodeWithText(" 출발", substring = true).assertExists()
        compose.onNodeWithText(text(R.string.input_now_depart)).assertDoesNotExist()
        assertEquals(routeCalls, graph.server.transitRequests)
    }

    @Test fun resultTimeChangeUpdatesCandidatesAndInputWithoutRefetch() {
        val graph = launch()
        enterTrip()
        waitForText("30분")
        clickText(text(R.string.candidates_sort_walk))
        clickText(text(R.string.candidates_filter_subway))
        compose.onNode(hasClickAction() and hasText("30분")).tap()
        clickText(text(R.string.candidates_view_seat))
        waitForText(text(R.string.result_legs))
        val routeCalls = graph.server.transitRequests
        compose.onNodeWithContentDescription(text(R.string.input_time_title)).tap()
        listOf("schedule_period" to 1, "schedule_hour" to 10, "schedule_minute" to 37).forEach { (tag, index) ->
            compose.onNodeWithTag(tag).performSemanticsAction(SemanticsActions.ScrollToIndex) { it(index) }
            compose.waitForIdle()
        }
        clickText(text(R.string.input_time_apply))
        waitForText(text(R.string.result_legs))
        back()
        waitForText("30분")
        compose.onNode(hasText("23:37 출발", substring = true) and !hasText("30분", substring = true)).assertExists()
        compose.onNodeWithText(text(R.string.candidates_sort_walk)).assertIsSelected()
        compose.onNodeWithText(text(R.string.candidates_filter_subway)).assertIsSelected()
        compose.onNode(hasClickAction() and hasText("30분")).assertIsSelected()
        back()
        compose.onNode(hasText("23:37 출발", substring = true) and !hasText("30분", substring = true)).assertExists()
        assertEquals(routeCalls, graph.server.transitRequests)
    }

    @Test fun taxiSelectionSurvivesBackAndDoesNotAutomaticallyReenter() {
        val graph = launch()
        enterTrip()
        waitForText("30분")
        clickText(text(R.string.common_taxi))
        compose.waitUntil(10_000) { graph.server.carRequests == 1 }
        compose.onNode(hasText(text(R.string.common_taxi)) and androidx.compose.ui.test.SemanticsMatcher.expectValue(androidx.compose.ui.semantics.SemanticsProperties.Role, androidx.compose.ui.semantics.Role.Tab)).assertIsSelected()
        back()
        compose.onNode(hasText(text(R.string.common_taxi)) and androidx.compose.ui.test.SemanticsMatcher.expectValue(androidx.compose.ui.semantics.SemanticsProperties.Role, androidx.compose.ui.semantics.Role.Tab)).assertIsSelected()
        compose.onNodeWithText(ORIGIN).assertExists()
        compose.onNodeWithText(DESTINATION).assertExists()
        assertEquals(1, graph.server.transitRequests)
        assertEquals(1, graph.server.carRequests)
    }

    @Test fun confirmingNowAgainUsesFreshClockInsteadOfAnEarlierResult() {
        val graph = launch()
        enterTrip()
        waitForText("30분")
        clickText(text(R.string.candidates_filter_subway))
        clickText(text(R.string.candidates_view_seat))
        waitForText(text(R.string.result_legs))
        compose.onNodeWithContentDescription(text(R.string.input_time_title)).tap()
        clickText(text(R.string.input_time_now))
        waitForText(text(R.string.result_legs))
        val next = graph.clock.instant().plusSeconds(3600)
        graph.clock = java.time.Clock.fixed(next, graph.clock.zone)
        compose.onNodeWithContentDescription(text(R.string.input_time_title)).tap()
        clickText(text(R.string.input_time_now))
        val departure = next.atZone(graph.clock.zone).format(java.time.format.DateTimeFormatter.ofPattern("HH:mm"))
        compose.waitUntil(10_000) { compose.onAllNodesWithText("$departure 출발", substring = true).fetchSemanticsNodes().isNotEmpty() }
        assertEquals(1, graph.server.transitRequests)
    }

    private companion object {
        const val ORIGIN = "예시역 1호선"
        const val DESTINATION = "견본 카페"

        /** 직접 만든 지상 2호선 한 구간(성수 → 뚝섬, 북쪽으로 10분). 실제 응답이 아니다. */
        const val ABOVE_GROUND_SUBWAY = """{"status":"OK","properties":{"total":1,"bus":0,"subway":1,"busAndSubway":0},
            "routes":[{"properties":{"type":"SUBWAY","totalDistance":2000,"totalTime":600,"transfers":0,"fare":{"value":1500}},
            "steps":[{"properties":{"guidance":"2호선 성수 승차 → 뚝섬 하차","type":"SUBWAY","distance":2000,"time":600,
            "stops":[{"name":"성수"},{"name":"뚝섬"}],"vehicles":[{"name":"2호선","type":"일반"}]},
            "path":{"points":[[127.05,37.54],[127.05,37.55],[127.05,37.56]]}}]}]}"""
    }
}
