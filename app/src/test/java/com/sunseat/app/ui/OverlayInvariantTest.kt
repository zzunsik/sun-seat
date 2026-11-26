package com.sunseat.app.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.click
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.swipe
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.test.swipeUp
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.unit.Density
import com.sunseat.app.ui.theme.Spacing
import androidx.compose.ui.unit.dp
import com.sunseat.app.domain.LatLng
import com.sunseat.app.domain.NamedPlace
import com.sunseat.app.domain.TravelMode
import com.sunseat.app.ui.candidates.CandidatesActions
import com.sunseat.app.ui.candidates.CandidatesFixtures
import com.sunseat.app.ui.candidates.CandidatesScreen
import com.sunseat.app.ui.candidates.CandidatesState
import com.sunseat.app.ui.candidates.toUi
import com.sunseat.app.ui.input.Endpoint
import com.sunseat.app.ui.input.EndpointField
import com.sunseat.app.ui.input.InputActions
import com.sunseat.app.ui.input.InputScreen
import com.sunseat.app.ui.input.InputUiState
import com.sunseat.app.ui.input.MapPickScreen
import com.sunseat.app.ui.input.MapPickState
import com.sunseat.app.ui.input.PickAddressStatus
import com.sunseat.app.ui.map.LocalMapRenderer
import com.sunseat.app.ui.map.MapFocus
import com.sunseat.app.ui.map.MapRenderer
import com.sunseat.app.ui.result.ResultActions
import com.sunseat.app.ui.result.ResultFixtures
import com.sunseat.app.ui.result.ResultScreen
import com.sunseat.app.ui.result.ResultUiState
import com.sunseat.app.ui.theme.SunSeatTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * #172: 화면 불변식 공통 검사 `assertNoTextUnderOverlays`를 입력·지도 선택·후보(목록·미리보기)·결과 화면에 붙인다.
 * 햇빛 따라가기는 `SimulationOverlayTest`가 같은 검사를 한다. 환경 행렬: 세로 390dp, 320dp·1.5배, 가로 832×384dp + 시스템 바 인셋 24dp.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(application = android.app.Application::class, sdk = [36], qualifiers = "w390dp-h844dp-xxhdpi")
@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
class OverlayInvariantTest {
    @get:Rule val compose = createComposeRule()

    private fun settle() { compose.mainClock.advanceTimeBy(5000); compose.waitForIdle() }

    private fun render(fontScale: Float = 1f, bars: Boolean = false, content: @Composable () -> Unit) {
        com.airbnb.lottie.LottieCompositionFactory.fromRawResSync(org.robolectric.RuntimeEnvironment.getApplication(), com.sunseat.app.R.raw.sun_loading)
        compose.setContent {
            CompositionLocalProvider(LocalMapRenderer provides MapRenderer.PREVIEW, LocalDensity provides Density(LocalDensity.current.density, fontScale)) {
                SunSeatTheme { Box(if (bars) Modifier.windowInsetsPadding(WindowInsets(top = 24.dp, bottom = 24.dp)) else Modifier) { content() } }
            }
        }
        settle()
    }

    private val input = InputUiState(
        origin = Endpoint(NamedPlace("서울역", LatLng(37.55, 126.97))),
        destination = Endpoint(NamedPlace("강남역", LatLng(37.50, 127.03))),
        mode = TravelMode.TRANSIT,
        departure = java.time.LocalDateTime.of(2026, 11, 16, 9, 0),
    )
    private val pin = LatLng(37.56, 126.98)

    private fun inputScreen(fontScale: Float = 1f, bars: Boolean = false) {
        render(fontScale, bars) { InputScreen(input, InputActions()) }
        compose.assertNoTextUnderOverlays()
    }

    private fun mapPick(fontScale: Float = 1f, bars: Boolean = false) {
        render(fontScale, bars) {
            MapPickScreen(MapPickState(EndpointField.DESTINATION, pin, MapFocus(pin, 1), status = PickAddressStatus.FOUND, address = "서울 가상구 예시로 200"),
                InputActions(), NamedPlace("서울역", LatLng(37.55, 126.97)), null)
        }
        compose.assertNoTextUnderOverlays()
    }

    private fun candidates(preview: Boolean, fontScale: Float = 1f, bars: Boolean = false) {
        val items = CandidatesFixtures.options.toUi(java.time.LocalDateTime.of(2026, 11, 15, 9, 0))
        var state by mutableStateOf(CandidatesState.Options(items))
        render(fontScale, bars) {
            CandidatesScreen(CandidatesFixtures.transitRequest, state, CandidatesActions(onHighlight = { state = state.copy(selectedId = it.id) }), animateLoader = false)
        }
        if (preview) {
            compose.onNodeWithTag("candidate_0").performTouchInput { click() }
            settle()
        }
        compose.assertNoTextUnderOverlays()
    }

    private fun result(fontScale: Float = 1f, bars: Boolean = false) {
        val advice = ResultFixtures.advisor(90.0, 30.0).advise(ResultFixtures.transitRoute, ResultFixtures.departure, com.sunseat.app.domain.SunGoal.AVOID)
        render(fontScale, bars) {
            ResultScreen(ResultFixtures.transitRequest, ResultUiState(goal = com.sunseat.app.domain.SunGoal.AVOID, departure = ResultFixtures.departure, result = advice),
                ResultActions(), animateIn = false, zone = ResultFixtures.zone)
        }
        // 쉬는 상태: 위 캡슐은 본문을 덮지 않는다. 아래 행동 바 밑으로는 본문이 지나간다.
        compose.assertNoTextUnderOverlays(floatingOverScroll = setOf("result_actions_capsule"))
        // 끝까지 내리면 마지막 내용이 아래 행동 바 위에서 끝난다(본문 아래 여백 = 측정한 바 높이).
        // 지도나 하단 행동이 끌기를 소비하지 않도록 본문 바깥 여백에서 실제 터치로 내린다(#208).
        repeat(8) { compose.onRoot().performTouchInput {
            swipe(Offset(Spacing.s.toPx(), height * 0.7f), Offset(Spacing.s.toPx(), height * 0.3f))
        } }
        settle()
        compose.assertNoTextUnderOverlays(floatingOverScroll = setOf("result_journey_capsule"))
    }

    @Test fun inputPortrait() = inputScreen()
    @Config(qualifiers = "w320dp-h844dp-xxhdpi") @Test fun inputSmallLargeText() = inputScreen(1.5f)
    @Config(qualifiers = "w832dp-h384dp-xxhdpi") @Test fun inputLandscapeBars() = inputScreen(bars = true)

    @Test fun mapPickPortrait() = mapPick()
    @Config(qualifiers = "w320dp-h844dp-xxhdpi") @Test fun mapPickSmallLargeText() = mapPick(1.5f)
    @Config(qualifiers = "w832dp-h384dp-xxhdpi") @Test fun mapPickLandscapeBars() = mapPick(bars = true)

    @Test fun candidatesListPortrait() = candidates(false)
    @Config(qualifiers = "w320dp-h844dp-xxhdpi") @Test fun candidatesListSmallLargeText() = candidates(false, 1.5f)
    @Config(qualifiers = "w832dp-h384dp-xxhdpi") @Test fun candidatesListLandscapeBars() = candidates(false, bars = true)
    @Test fun candidatesPreviewPortrait() = candidates(true)
    @Config(qualifiers = "w320dp-h844dp-xxhdpi") @Test fun candidatesPreviewSmallLargeText() = candidates(true, 1.5f)
    @Config(qualifiers = "w832dp-h384dp-xxhdpi") @Test fun candidatesPreviewLandscapeBars() = candidates(true, bars = true)

    @Test fun resultPortrait() = result()
    @Config(qualifiers = "w320dp-h844dp-xxhdpi") @Test fun resultSmallLargeText() = result(1.5f)
    @Config(qualifiers = "w832dp-h384dp-xxhdpi") @Test fun resultLandscapeBars() = result(bars = true)
}
