package com.sunseat.app.ui.map

import android.app.Application
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.test.core.app.ApplicationProvider
import com.sunseat.app.R
import com.sunseat.app.domain.LatLng
import com.sunseat.app.domain.NamedPlace
import com.sunseat.app.ui.candidates.CandidatesActions
import com.sunseat.app.ui.candidates.CandidatesFixtures
import com.sunseat.app.ui.candidates.CandidatesScreen
import com.sunseat.app.ui.candidates.CandidatesState
import com.sunseat.app.ui.candidates.toUi
import com.sunseat.app.ui.input.EndpointField
import com.sunseat.app.ui.input.InputActions
import com.sunseat.app.ui.input.MapPickScreen
import com.sunseat.app.ui.input.MapPickState
import com.sunseat.app.ui.input.PickAddressStatus
import com.sunseat.app.ui.result.ResultFixtures
import com.sunseat.app.ui.theme.Sizes
import com.sunseat.app.ui.theme.SunSeatTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * #173/#147: 지도 키가 없으면(사용 불가) 지도 노드가 지도 위 안내를 접근성 트리에 한 번 두고, 경로 지도 설명·경로 요약은 읽지 않는다.
 * 테스트 지도도 렌더러 경계(LocalMapKeyAvailable)에서 사용 불가를 주입하면 실제 안내를 그린다.
 */
@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class, sdk = [36], qualifiers = "w390dp-h844dp-xxhdpi")
@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
class MapAvailabilityTest {
    @get:Rule val compose = createComposeRule()
    private fun text(id: Int) = ApplicationProvider.getApplicationContext<android.content.Context>().getString(id)
    private val notice get() = text(R.string.map_missing_key)

    private fun render(content: @Composable () -> Unit) {
        compose.setContent {
            CompositionLocalProvider(LocalMapRenderer provides MapRenderer.PREVIEW, LocalMapKeyAvailable provides false) { SunSeatTheme { content() } }
        }
        compose.waitForIdle()
    }

    /** 병합 트리에서 안내 글이 몇 번 읽히는지(글자·설명 모두). */
    private fun noticeCount() = compose.onAllNodes(hasText(notice, substring = true) or hasContentDescription(notice, substring = true)).fetchSemanticsNodes().size
    private fun routeDescriptions() = compose.onAllNodes(hasContentDescription("경로 지도", substring = true) or hasContentDescription("선택 경로:", substring = true)).fetchSemanticsNodes().size

    private fun assertSingleNotice() {
        assertEquals("the unavailable notice is read once", 1, noticeCount())
        assertEquals("no route map description or summary for a map that is not there", 0, routeDescriptions())
        assertEquals(MapAvailability.MISSING_KEY, compose.onNode(SemanticsMatcher.keyIsDefined(MapAvailabilityKey)).fetchSemanticsNode().config[MapAvailabilityKey])
    }

    /** #147 재현: 지도 선택 화면의 지도 상자가 의미를 모두 지워 지도 위 안내가 트리에 없었다. */
    @Test fun mapPickReadsTheUnavailableNoticeOnce() {
        val pin = LatLng(37.56, 126.98)
        val pick = MapPickState(EndpointField.DESTINATION, pin, MapFocus(pin, 1), status = PickAddressStatus.NONE).copy(mapUnavailable = true)
        render { MapPickScreen(pick, InputActions(), NamedPlace("서울역", LatLng(37.5547, 126.9707)), null) }
        assertSingleNotice()
        // 아래 패널은 다음 행동만 말한다(같은 안내를 두 번 읽지 않는다).
        assertEquals(1, compose.onAllNodes(hasText(text(R.string.map_pick_unavailable))).fetchSemanticsNodes().size)
        assertEquals(0, compose.onAllNodes(hasContentDescription("가운데 핀 위치", substring = true)).fetchSemanticsNodes().size)
    }

    @Test fun routeMapReadsOnlyTheNotice() {
        render {
            SunSeatMap(MapState(routes = listOf(ResultFixtures.transitRoute), origin = ResultFixtures.transitRequest.origin, destination = ResultFixtures.transitRequest.destination),
                Modifier.fillMaxWidth().height(Sizes.mapHeight))
        }
        assertSingleNotice()
    }

    @Test fun candidatesMapReadsOnlyTheNotice() {
        val items = CandidatesFixtures.options.toUi(java.time.LocalDateTime.of(2026, 11, 15, 9, 0))
        render { CandidatesScreen(CandidatesFixtures.transitRequest, CandidatesState.Options(items), CandidatesActions()) }
        assertSingleNotice()
    }
}
