package com.sunseat.app.ui.input

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.accessibility.enableAccessibilityChecks
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.tryPerformAccessibilityChecks
import androidx.compose.ui.unit.Density
import androidx.test.core.app.ApplicationProvider
import com.github.takahirom.roborazzi.captureRoboImage
import com.sunseat.app.R
import com.sunseat.app.domain.LatLng
import com.sunseat.app.domain.NamedPlace
import com.sunseat.app.ui.map.LocalMapRenderer
import com.sunseat.app.ui.map.MapFocus
import com.sunseat.app.ui.map.MapRenderer
import com.sunseat.app.ui.theme.SunSeatTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** 지도에서 위치 선택(#85) 스크린샷: 밝은·어두운, 390·320dp, 글자 1.0·1.5배, 주소 상태별. 좌표·주소는 직접 만든 값이다. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(application = android.app.Application::class, sdk = [36], qualifiers = "w390dp-h844dp-xxhdpi")
class MapPickScreenshotTest {
    @get:Rule val compose = createComposeRule()

    private fun text(id: Int) = ApplicationProvider.getApplicationContext<android.content.Context>().getString(id)

    private val origin = NamedPlace("서울역", LatLng(37.5547, 126.9707))
    private val pin = LatLng(37.56, 126.98)
    private fun pick(field: EndpointField = EndpointField.DESTINATION, status: PickAddressStatus = PickAddressStatus.FOUND, address: String? = "서울 가상구 예시로 200") =
        MapPickState(field, pin, MapFocus(pin, 1), status = status, address = address.takeIf { status == PickAddressStatus.FOUND })

    private fun render(state: MapPickState, dark: Boolean, fontScale: Float = 1f, actions: InputActions = InputActions()) {
        compose.enableAccessibilityChecks()
        compose.setContent {
            val density = LocalDensity.current
            CompositionLocalProvider(LocalMapRenderer provides MapRenderer.PREVIEW, LocalDensity provides Density(density.density, fontScale)) {
                SunSeatTheme(darkTheme = dark) { MapPickScreen(state, actions, origin, null) }
            }
        }
        compose.waitForIdle()
        compose.onRoot().tryPerformAccessibilityChecks()
    }

    private fun shot(name: String) = compose.onRoot().captureRoboImage("src/test/screenshots/$name.png")

    @Test fun foundLight() { render(pick(), false); shot("map_pick_found_light") }
    @Test fun foundDark() { render(pick(), true); shot("map_pick_found_dark") }
    @Test fun loadingLight() { render(pick(status = PickAddressStatus.LOADING), false); shot("map_pick_loading_light") }
    @Test fun noAddressDark() { render(pick(status = PickAddressStatus.NONE), true); shot("map_pick_none_dark") }
    @Test fun originLight() { render(pick(field = EndpointField.ORIGIN), false); shot("map_pick_origin_light") }

    @Test fun failedRetryDark() {
        render(pick(status = PickAddressStatus.FAILED).copy(canRetry = true), true)
        compose.onNodeWithText(text(R.string.search_retry)).assertIsDisplayed()
        compose.onNode(hasClickAction() and hasText(text(R.string.map_pick_confirm_destination))).assertIsEnabled()
        shot("map_pick_failed_dark")
    }

    @Test fun sameEndpointLight() { render(pick().copy(messageRes = R.string.input_same_endpoints), false); shot("map_pick_same_light") }

    @Test fun mapUnavailableLight() {
        render(pick().copy(mapUnavailable = true), false)
        compose.onNode(hasClickAction() and hasText(text(R.string.map_pick_confirm_destination))).assertIsNotEnabled()
        shot("map_pick_unavailable_light")
    }

    @Test fun movingDisablesConfirm() {
        render(pick(status = PickAddressStatus.LOADING).copy(moving = true), false)
        compose.onNode(hasClickAction() and hasText(text(R.string.map_pick_confirm_destination))).assertIsNotEnabled()
        shot("map_pick_moving_light")
    }

    @Test fun backButtonAndRecenterAreSeparateActions() {
        var cancelled = 0
        var recentred = 0
        var confirmed = 0
        render(pick(), false, actions = InputActions(cancelMapPick = { cancelled++ }, showCurrentLocationOnMap = { recentred++ }, confirmMapPick = { confirmed++ }))
        compose.onNodeWithContentDescription(text(R.string.map_pick_recenter)).performClick()
        compose.onNodeWithContentDescription(text(R.string.map_pick_close)).performClick()
        compose.onNodeWithText(text(R.string.map_pick_confirm_destination)).performClick()
        assertEquals(listOf(1, 1, 1), listOf(recentred, cancelled, confirmed))
    }

    @Test @Config(qualifiers = "w320dp-h640dp-xxhdpi")
    fun smallLargeTextLight() { render(pick(status = PickAddressStatus.FAILED).copy(canRetry = true), false, 1.5f); shot("map_pick_small_large_text_light") }

    @Test @Config(qualifiers = "w320dp-h640dp-xxhdpi")
    fun smallLargeTextDark() { render(pick(field = EndpointField.ORIGIN), true, 1.5f); shot("map_pick_small_large_text_dark") }

    @Test @Config(qualifiers = "w320dp-h640dp-xxhdpi")
    fun smallLight() { render(pick(), false); shot("map_pick_small_light") }
}
