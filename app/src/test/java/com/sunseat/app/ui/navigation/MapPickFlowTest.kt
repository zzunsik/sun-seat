package com.sunseat.app.ui.navigation

import android.app.Application
import androidx.activity.ComponentActivity
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performImeAction
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipe
import androidx.compose.ui.geometry.Offset
import androidx.test.core.app.ApplicationProvider
import com.sunseat.app.R
import com.sunseat.app.SunSeatContent
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
 * 지도에서 위치 선택(#85) 화면 흐름. 지도는 테스트 렌더러(PREVIEW)이고 끌면 가운데 좌표가 바뀐다.
 * 카카오 응답(장소·주소·경로)은 모두 직접 만든 값이다.
 */
@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class, sdk = [36], qualifiers = "w390dp-h844dp-xxhdpi")
class MapPickFlowTest {
    @get:Rule val temporary = TemporaryFolder()
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()

    private var graph: FlowTestGraph? = null

    @After fun close() { graph?.close() }

    private fun text(id: Int, vararg args: Any): String = compose.activity.getString(id, *args)

    private fun launch(server: FakeKakaoServer = FakeKakaoServer()): FlowTestGraph {
        val created = FlowTestGraph(ApplicationProvider.getApplicationContext(), temporary.newFolder(), server)
        graph = created
        compose.setContent {
            CompositionLocalProvider(LocalMapRenderer provides MapRenderer.PREVIEW) {
                SunSeatContent(created, compose.activity.window)
            }
        }
        compose.waitForIdle()
        return created
    }

    private fun waitForText(value: String) =
        compose.waitUntil(10_000) { compose.onAllNodesWithText(value).fetchSemanticsNodes().isNotEmpty() }

    private fun SemanticsNodeInteraction.tap(): SemanticsNodeInteraction = performSemanticsAction(SemanticsActions.OnClick).also { compose.waitForIdle() }

    private fun clickText(value: String) = compose.onNode(hasClickAction() and hasText(value)).tap()

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

    /** 지도 선택 화면의 지도(설명에 제목이 들어 있다)를 끌어 핀 아래 좌표를 바꾼다. */
    private fun dragMap(title: String) {
        compose.onNode(hasContentDescription(title, substring = true) and !hasClickAction()).performTouchInput {
            swipe(center, center + Offset(width / 4f, height / 6f), durationMillis = 200)
        }
        compose.waitForIdle()
        // The address lookup waits for the map to settle (debounce); advance the paused main looper past it.
        org.robolectric.shadows.ShadowLooper.idleMainLooper(com.sunseat.app.ui.input.InputViewModel.SEARCH_DEBOUNCE_MILLIS + 50, java.util.concurrent.TimeUnit.MILLISECONDS)
        compose.waitForIdle()
    }

    @Test fun inputDestinationFromMapConfirmsOnceWithTheAddress() {
        val graph = launch()
        search(text(R.string.input_origin_placeholder), "예시", ORIGIN)
        clickText(text(R.string.input_destination_placeholder))
        clickText(text(R.string.map_pick_open))
        val title = text(R.string.map_pick_title_destination)
        compose.onNodeWithText(title).assertExists()
        // The pin starts at the other endpoint and its address is looked up once.
        waitForText(ADDRESS)
        assertEquals(1, graph.server.addressRequests)
        dragMap(title)
        compose.waitUntil(10_000) { graph.server.addressRequests == 2 }
        waitForText(ADDRESS)
        assertEquals(0, graph.server.transitRequests)
        clickText(text(R.string.map_pick_confirm_destination))
        compose.waitUntil(10_000) { graph.server.transitRequests == 1 }
        waitForText("30분")
        compose.onNodeWithText(ADDRESS).assertExists()
        compose.onNodeWithText(ORIGIN).assertExists()
        compose.waitForIdle()
        assertEquals(1, graph.server.transitRequests)
        // A named pick is remembered like a searched place.
        compose.waitUntil(10_000) { graph.recent.places.value.any { it.name == ADDRESS } }
    }

    @Test fun candidateEditBackReturnsToSearchAndUnmovedConfirmKeepsTheTrip() {
        val graph = launch()
        search(text(R.string.input_origin_placeholder), "예시", ORIGIN)
        search(text(R.string.input_destination_placeholder), "견본", DESTINATION)
        waitForText("30분")
        clickText(DESTINATION)
        compose.onNode(hasSetTextAction() and hasText(DESTINATION)).assertExists()
        clickText(text(R.string.map_pick_open))
        waitForText(ADDRESS)
        val title = text(R.string.map_pick_title_destination)
        dragMap(title)
        // Back discards only the draft and returns to the prefilled search.
        back()
        compose.onNodeWithText(title).assertDoesNotExist()
        compose.onNode(hasSetTextAction() and hasText(DESTINATION)).assertExists()
        assertEquals(1, graph.server.transitRequests)
        // Re-opening starts at the confirmed destination again; confirming it unmoved changes nothing.
        clickText(text(R.string.map_pick_open))
        waitForText(ADDRESS)
        clickText(text(R.string.map_pick_confirm_destination))
        compose.onNodeWithText(title).assertDoesNotExist()
        compose.onNode(hasSetTextAction()).assertDoesNotExist()
        compose.onNodeWithText(DESTINATION).assertExists()
        compose.waitForIdle()
        assertEquals(1, graph.server.transitRequests)
    }

    @Test fun candidateOriginFromMapChangesOnlyTheOriginAndQueriesOnce() {
        val graph = launch()
        search(text(R.string.input_origin_placeholder), "예시", ORIGIN)
        search(text(R.string.input_destination_placeholder), "견본", DESTINATION)
        waitForText("30분")
        clickText(ORIGIN)
        clickText(text(R.string.map_pick_open))
        waitForText(ADDRESS)
        dragMap(text(R.string.map_pick_title_origin))
        compose.waitUntil(10_000) { graph.server.addressRequests == 2 }
        waitForText(ADDRESS)
        clickText(text(R.string.map_pick_confirm_origin))
        compose.waitUntil(10_000) { graph.server.transitRequests == 2 }
        waitForText("30분")
        compose.onNodeWithText(ADDRESS).assertExists()
        compose.onNodeWithText(DESTINATION).assertExists()
        // Back from candidates keeps the new origin on the input screen without another query.
        back()
        compose.onNodeWithText(ADDRESS).assertExists()
        compose.onNodeWithText(DESTINATION).assertExists()
        assertEquals(2, graph.server.transitRequests)
    }

    @Test fun addressFailureStillConfirmsAnHonestlyNamedLocation() {
        val server = FakeKakaoServer().apply { address = { FakeKakaoServer.disconnect() } }
        val graph = launch(server)
        search(text(R.string.input_origin_placeholder), "예시", ORIGIN)
        clickText(text(R.string.input_destination_placeholder))
        clickText(text(R.string.map_pick_open))
        waitForText(text(R.string.map_pick_address_error))
        compose.onNodeWithText(text(R.string.search_retry)).assertExists()
        dragMap(text(R.string.map_pick_title_destination))
        waitForText(text(R.string.map_pick_address_error))
        compose.onNode(hasClickAction() and hasText(text(R.string.map_pick_confirm_destination))).assertIsEnabled()
        clickText(text(R.string.map_pick_confirm_destination))
        compose.waitUntil(10_000) { graph.server.transitRequests == 1 }
        waitForText(text(R.string.map_pick_unnamed))
        // An unnamed pick is not added to the recent places (the searched origin is).
        compose.waitUntil(10_000) { graph.recent.places.value.any { it.name == ORIGIN } }
        compose.waitForIdle()
        assertEquals(listOf(ORIGIN), graph.recent.places.value.map { it.name })
    }

    @Test fun samePlaceAsTheOtherEndpointIsRejectedInThePicker() {
        val graph = launch()
        search(text(R.string.input_origin_placeholder), "예시", ORIGIN)
        clickText(text(R.string.input_destination_placeholder))
        clickText(text(R.string.map_pick_open))
        waitForText(ADDRESS)
        // Unmoved, the pin is still on the origin.
        clickText(text(R.string.map_pick_confirm_destination))
        waitForText(text(R.string.input_same_endpoints))
        compose.onNodeWithText(text(R.string.map_pick_title_destination)).assertExists()
        assertEquals(0, graph.server.transitRequests)
        back()
        back()
        compose.onNodeWithText(ORIGIN).assertExists()
        compose.onNodeWithText(text(R.string.input_destination_placeholder)).assertExists()
        assertEquals(0, graph.server.transitRequests)
    }

    @Test fun pickerSurvivesRecreationAtTheMovedPinAndStillConfirmsOnce() {
        val graph = FlowTestGraph(ApplicationProvider.getApplicationContext(), temporary.newFolder(), FakeKakaoServer())
        this.graph = graph
        val restoration = androidx.compose.ui.test.junit4.StateRestorationTester(compose)
        restoration.setContent {
            CompositionLocalProvider(LocalMapRenderer provides MapRenderer.PREVIEW) { SunSeatContent(graph, compose.activity.window) }
        }
        compose.waitForIdle()
        search(text(R.string.input_origin_placeholder), "예시", ORIGIN)
        search(text(R.string.input_destination_placeholder), "견본", DESTINATION)
        waitForText("30분")
        clickText(DESTINATION)
        clickText(text(R.string.map_pick_open))
        waitForText(ADDRESS)
        val title = text(R.string.map_pick_title_destination)
        dragMap(title)
        compose.waitUntil(10_000) { graph.server.addressRequests == 2 }
        waitForText(ADDRESS)
        restoration.emulateSavedInstanceStateRestore()
        compose.waitForIdle()
        // Still picking the destination; the moved pin is not looked up again and the trip is unchanged.
        compose.onNodeWithText(title).assertExists()
        compose.onNodeWithText(ADDRESS).assertExists()
        assertEquals(2, graph.server.addressRequests)
        // StateRestorationTester also disposes the nav entry ViewModel stores, so the candidates list may reload
        // once on its own (a real configuration change keeps them); count only what confirming adds.
        compose.waitForIdle()
        val before = graph.server.transitRequests
        clickText(text(R.string.map_pick_confirm_destination))
        compose.waitUntil(10_000) { graph.server.transitRequests == before + 1 }
        waitForText("30분")
        compose.onNodeWithText(ORIGIN).assertExists()
        compose.onNodeWithText(ADDRESS).assertExists()
        compose.waitForIdle()
        assertEquals(before + 1, graph.server.transitRequests)
    }

    private companion object {
        const val ORIGIN = "예시역 1호선"
        const val DESTINATION = "견본 카페"
        /** coord2address.json의 도로명 주소(직접 만든 값). */
        const val ADDRESS = "서울 가상구 예시로 200"
    }
}
