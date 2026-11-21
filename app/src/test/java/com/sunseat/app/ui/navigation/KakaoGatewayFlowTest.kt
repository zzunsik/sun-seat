package com.sunseat.app.ui.navigation

import android.app.Application
import androidx.activity.ComponentActivity
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.performImeAction
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.test.performTextInput
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
 * 앱 API 게이트웨이(#189)의 거부·호출 제한 응답이 화면에서 "잠시 뒤 다시"와 다시 시도로 이어지는지 본다.
 * 응답 본문은 앱 API 게이트웨이가 만드는 형태(workers/api/src/index.ts)를 흉내 낸 직접 만든 값이다.
 */
@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class, sdk = [36], qualifiers = "w390dp-h844dp-xxhdpi")
class KakaoGatewayFlowTest {
    @get:Rule val temporary = TemporaryFolder()
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()

    private var graph: FlowTestGraph? = null

    @After fun close() { graph?.close() }

    private fun text(id: Int): String = compose.activity.getString(id)

    private fun launch(server: FakeKakaoServer) {
        val created = FlowTestGraph(ApplicationProvider.getApplicationContext(), temporary.newFolder(), server)
        graph = created
        compose.setContent {
            CompositionLocalProvider(LocalMapRenderer provides MapRenderer.PREVIEW) {
                SunSeatContent(created, compose.activity.window)
            }
        }
        compose.waitForIdle()
    }

    private fun waitForText(value: String) {
        compose.waitUntil(10_000) { compose.onAllNodesWithText(value).fetchSemanticsNodes().isNotEmpty() }
    }

    private fun clickText(value: String) {
        compose.onNode(hasClickAction() and hasText(value)).performSemanticsAction(SemanticsActions.OnClick)
        compose.waitForIdle()
    }

    private fun search(placeholder: String, query: String) {
        clickText(placeholder)
        compose.onNode(hasSetTextAction()).performTextInput(query)
        compose.onNode(hasSetTextAction()).performImeAction()
    }

    @Test fun rateLimitedSearchShowsRetryLaterAndRetryRecovers() {
        val server = FakeKakaoServer().apply { places = { FakeKakaoServer.json("""{"error":"rate_limited"}""", 429) } }
        launch(server)
        search(text(R.string.input_origin_placeholder), "예시")
        waitForText(text(R.string.search_error_quota))

        server.places = { FakeKakaoServer.json(com.sunseat.app.data.network.fixture("kakao/place_keyword.json")) }
        clickText(text(R.string.search_retry))
        waitForText(ORIGIN)
    }

    @Test fun refusedRouteShowsRetryLaterAndRetryRecovers() {
        val server = FakeKakaoServer().apply { transit = { FakeKakaoServer.json("""{"error":"forbidden"}""", 403) } }
        launch(server)
        search(text(R.string.input_origin_placeholder), "예시")
        waitForText(ORIGIN)
        clickText(ORIGIN)
        search(text(R.string.input_destination_placeholder), "견본")
        waitForText(DESTINATION)
        clickText(DESTINATION)
        waitForText(text(R.string.candidates_error_quota))
        val before = server.transitRequests

        server.transit = { FakeKakaoServer.json(com.sunseat.app.data.network.fixture("kakao/transit_routes.json")) }
        clickText(text(R.string.search_retry))
        compose.waitUntil(10_000) { compose.onAllNodesWithText(text(R.string.candidates_error_quota)).fetchSemanticsNodes().isEmpty() }
        assertEquals(before + 1, server.transitRequests)
    }

    private companion object {
        const val ORIGIN = "예시역 1호선"
        const val DESTINATION = "견본 카페"
    }
}
