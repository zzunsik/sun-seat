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
import androidx.compose.ui.test.click
import androidx.compose.ui.test.performTouchInput
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
    private var notice = ""
    private fun notice(id: Int) { notice = text(id) }

    private fun render(content: @Composable () -> Unit) {
        compose.setContent {
            CompositionLocalProvider(LocalMapRenderer provides MapRenderer.PREVIEW, LocalMapKeyAvailable provides false) { SunSeatTheme { content() } }
        }
        compose.waitForIdle()
    }

    /** 병합 트리에서 안내 글이 몇 번 읽히는지(글자·설명 모두). */
    private fun noticeCount() = compose.onAllNodes(hasText(notice, substring = true) or hasContentDescription(notice, substring = true)).fetchSemanticsNodes().size
    private fun routeDescriptions() = compose.onAllNodes(hasContentDescription("경로 지도", substring = true) or hasContentDescription("선택 경로:", substring = true)).fetchSemanticsNodes().size

    private fun assertSingleNotice(id: Int = R.string.map_missing_key) {
        notice(id)
        assertEquals("the unavailable notice is read once", 1, noticeCount())
        // 화면이 열린 뒤 지도가 실패해도 이유가 들리게 안내는 polite 라이브 영역이다.
        val node = compose.onNode(hasText(notice, substring = true)).fetchSemanticsNode()
        assertEquals(androidx.compose.ui.semantics.LiveRegionMode.Polite, node.config[androidx.compose.ui.semantics.SemanticsProperties.LiveRegion])
        assertEquals("no route map description or summary for a map that is not there", 0, routeDescriptions())
        assertEquals(MapAvailability.MISSING_KEY, compose.onNode(SemanticsMatcher.keyIsDefined(MapAvailabilityKey)).fetchSemanticsNode().config[MapAvailabilityKey])
    }

    /** #147 재현: 지도 선택 화면의 지도 상자가 의미를 모두 지워 지도 위 안내가 트리에 없었다. */
    @Test fun mapPickReadsTheUnavailableNoticeOnce() {
        val pin = LatLng(37.56, 126.98)
        val pick = MapPickState(EndpointField.DESTINATION, pin, MapFocus(pin, 1), status = PickAddressStatus.NONE).copy(mapUnavailable = true)
        render { MapPickScreen(pick, InputActions(), NamedPlace("서울역", LatLng(37.5547, 126.9707)), null) }
        // 지도 선택은 상태만 말하고(행동은 아래 패널) 다른 화면의 "앱 업데이트…"·"자리 추천…" 줄을 쓰지 않는다.
        assertSingleNotice(R.string.map_unavailable_short)
        assertEquals(0, compose.onAllNodes(hasText(text(R.string.map_missing_key), substring = true)).fetchSemanticsNodes().size)
        // #180: 아래에는 같은 안내를 되풀이하는 줄 없이 다음 행동 버튼 하나만 있다.
        assertEquals(1, compose.onAllNodes(hasText(text(R.string.map_pick_close)) and androidx.compose.ui.test.hasClickAction()).fetchSemanticsNodes().size)
        assertEquals(0, compose.onAllNodes(hasContentDescription("가운데 핀 위치", substring = true)).fetchSemanticsNodes().size)
    }

    /** #180: 지도 선택에서 지도를 쓸 수 없으면 꺼진 선택 버튼 대신 검색으로 돌아가는 켜진 버튼을 준다(실제 터치로 누른다). */
    @Test fun mapPickOffersBackToSearchWhenUnavailable() {
        val pin = LatLng(37.56, 126.98)
        var cancelled = 0
        val pick = MapPickState(EndpointField.DESTINATION, pin, MapFocus(pin, 1), status = PickAddressStatus.NONE).copy(mapUnavailable = true)
        render { MapPickScreen(pick, InputActions(cancelMapPick = { cancelled++ }), NamedPlace("서울역", LatLng(37.5547, 126.9707)), null) }
        assertEquals(0, compose.onAllNodes(hasText(text(R.string.map_pick_confirm_destination))).fetchSemanticsNodes().size)
        compose.onNode(hasText(text(R.string.map_pick_close))).performTouchInput { click() }
        assertEquals(1, cancelled)
    }

    /** #180: 입력 화면은 지도를 쓸 수 없으면 카메라만 옮기는 현재 위치 버튼을 숨긴다. */
    @Test fun inputHidesTheMapLocationButtonWhenUnavailable() {
        render { com.sunseat.app.ui.input.InputScreen(com.sunseat.app.ui.input.InputUiState(), InputActions()) }
        compose.mainClock.advanceTimeBy(1000); compose.waitForIdle()
        assertEquals(0, compose.onAllNodes(hasContentDescription(text(R.string.location_use_current)) and androidx.compose.ui.test.hasClickAction()).fetchSemanticsNodes().size)
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
        // 안내는 시트 밑이 아니라 보이는 지도 띠 안에 있다(실기기 확인에서 시트에 가려 접근성 트리에서 빠졌다).
        compose.mainClock.advanceTimeBy(5000); compose.waitForIdle()
        val notice = compose.onNode(hasText(text(R.string.map_missing_key), substring = true)).fetchSemanticsNode().boundsInRoot
        val sheet = compose.onNode(androidx.compose.ui.test.hasTestTag("candidates_sheet_handle"), useUnmergedTree = true).fetchSemanticsNode().boundsInRoot
        org.junit.Assert.assertTrue("notice $notice must sit above the sheet $sheet", notice.bottom <= sheet.top + 0.5f)
    }
}
