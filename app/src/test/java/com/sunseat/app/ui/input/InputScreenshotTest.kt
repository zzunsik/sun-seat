package com.sunseat.app.ui.input

import androidx.compose.ui.test.junit4.accessibility.enableAccessibilityChecks
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.isPopup
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performImeAction
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.tryPerformAccessibilityChecks
import com.github.takahirom.roborazzi.captureRoboImage
import com.sunseat.app.R
import com.sunseat.app.data.place.Place
import com.sunseat.app.domain.LatLng
import com.sunseat.app.domain.NamedPlace
import com.sunseat.app.domain.TravelMode
import com.sunseat.app.ui.theme.SunSeatTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.time.LocalDateTime

/** 입력 화면 스크린샷(밝은·어두운, 빈/채움/검색/오류)과 접근성 검사. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(application = android.app.Application::class, sdk = [36], qualifiers = "w390dp-h844dp-xxhdpi")
class InputScreenshotTest {
    @get:Rule
    val compose = createComposeRule()

    private val filled = InputUiState(
        origin = Endpoint(NamedPlace("현재 위치", LatLng(37.55, 126.97)), isCurrentLocation = true),
        destination = Endpoint(NamedPlace("부산역", LatLng(35.11, 129.04))),
        mode = TravelMode.TRANSIT,
        departure = LocalDateTime.of(2026, 11, 16, 9, 0),
    )

    private val searching = InputUiState(
        search = SearchState(
            field = EndpointField.DESTINATION,
            query = "부산역",
            results = listOf(
                Place("1", "부산역", "부산 동구 초량동 1187-1", "부산 동구 중앙대로 206", LatLng(35.11, 129.04), "교통,수송 > 기차역"),
                Place("2", "부산역 1호선", "부산 동구 초량동", "", LatLng(35.11, 129.04), "교통,수송 > 지하철역"),
            ),
        ),
    )

    private fun render(state: InputUiState, dark: Boolean) {
        compose.setContent { androidx.compose.runtime.CompositionLocalProvider(com.sunseat.app.ui.map.LocalMapRenderer provides com.sunseat.app.ui.map.MapRenderer.PREVIEW) { SunSeatTheme(darkTheme = dark) { InputScreen(state, InputActions()) } } }
        compose.waitForIdle()
    }

    @Test fun unavailableLight() { render(InputUiState(search = SearchState(EndpointField.ORIGIN, query = "서울역", messageRes = R.string.search_error_unavailable)), false); shot("input_unavailable_light") }
    @Test fun unavailableDark() { render(InputUiState(search = SearchState(EndpointField.ORIGIN, query = "서울역", messageRes = R.string.search_error_unavailable)), true); shot("input_unavailable_dark") }
    @Test fun emptyDark() { render(InputUiState(), true); shot("input_empty_dark") }
    @Test fun originLight() { render(filled.copy(destination = null), false); shot("input_origin_light") }
    @Test fun originDark() { render(filled.copy(destination = null), true); shot("input_origin_dark") }
    @Test fun searchDark() { render(searching, true); shot("input_search_dark") }
    @Test fun searchEmptyLight() { render(InputUiState(search = SearchState(EndpointField.ORIGIN)), false); shot("input_search_empty_light") }
    @Test fun searchEmptyDark() { render(InputUiState(search = SearchState(EndpointField.ORIGIN)), true); shot("input_search_empty_dark") }
    @Test fun searchErrorLight() { render(InputUiState(search = SearchState(EndpointField.ORIGIN, query = "서울역", messageRes = R.string.search_error_network, canRetry = true)), false); shot("input_search_error_light") }

    private fun timeSheet(dark: Boolean, mode: com.sunseat.app.domain.TimeMode) {
        compose.enableAccessibilityChecks()
        compose.setContent { SunSeatTheme(darkTheme = dark) { TimeSheet(filled.departure, mode, TravelMode.TRANSIT, {}, { _, _ -> }, now = filled.departure!!) } }
        compose.waitForIdle()
        compose.onRoot().tryPerformAccessibilityChecks()
        shot("input_time_${mode.name.lowercase()}_${if (dark) "dark" else "light"}")
    }
    @Test fun departLight() = timeSheet(false, com.sunseat.app.domain.TimeMode.DEPART)
    @Test fun departDark() = timeSheet(true, com.sunseat.app.domain.TimeMode.DEPART)
    @Test fun arriveLight() = timeSheet(false, com.sunseat.app.domain.TimeMode.ARRIVE)
    @Test fun arriveDark() = timeSheet(true, com.sunseat.app.domain.TimeMode.ARRIVE)

    private fun minuteMenu(dark: Boolean) {
        compose.enableAccessibilityChecks()
        compose.setContent { SunSeatTheme(darkTheme = dark) {
            TimeSheet(filled.departure!!.withMinute(37), com.sunseat.app.domain.TimeMode.DEPART, TravelMode.TRANSIT, {}, { _, _ -> }, now = filled.departure!!)
        } }
        compose.waitForIdle()
        compose.onNodeWithTag("schedule_minute").performClick()
        compose.onNode(isPopup()).tryPerformAccessibilityChecks()
        compose.onNode(isPopup()).captureRoboImage("src/test/screenshots/input_time_minutes_${if (dark) "dark" else "light"}.png")
    }
    @Test fun minuteMenuLight() = minuteMenu(false)
    @Test fun minuteMenuDark() = minuteMenu(true)

    @Test
    @Config(qualifiers = "w320dp-h640dp-xxhdpi")
    fun timeLargeTextSmallScreen() {
        compose.enableAccessibilityChecks()
        compose.setContent {
            val density = androidx.compose.ui.platform.LocalDensity.current
            androidx.compose.runtime.CompositionLocalProvider(androidx.compose.ui.platform.LocalDensity provides androidx.compose.ui.unit.Density(density.density, 1.5f)) {
                SunSeatTheme { TimeSheet(filled.departure, com.sunseat.app.domain.TimeMode.ARRIVE, TravelMode.TRANSIT, {}, { _, _ -> }, now = filled.departure!!) }
            }
        }
        compose.waitForIdle()
        compose.onNodeWithText("적용").assertIsDisplayed()
        compose.onRoot().tryPerformAccessibilityChecks()
        shot("input_time_large_text_small")
    }

    @Test fun emptyLight() { render(InputUiState(), false); shot("input_empty_light") }
    @Test fun filledLight() { render(filled, false); shot("input_filled_light") }
    @Test fun filledDark() { render(filled, true); shot("input_filled_dark") }
    @Test fun searchLight() { render(searching, false); shot("input_search_light") }

    @Test
    fun locationDeniedLight() {
        render(InputUiState(locationMessageRes = R.string.location_permission_permanently_denied, locationAction = com.sunseat.app.location.LocationAction.OpenAppSettings), false)
        shot("input_location_denied_light")
    }

    @Test
    fun searchErrorDark() {
        render(InputUiState(search = SearchState(EndpointField.ORIGIN, query = "서울역", messageRes = R.string.search_error_network, canRetry = true)), true)
        shot("input_search_error_dark")
    }

    @Test
    fun accessibilityFilled() {
        compose.enableAccessibilityChecks()
        render(filled, false)
        compose.onRoot().tryPerformAccessibilityChecks()
    }

    @Test
    fun accessibilitySearch() {
        compose.enableAccessibilityChecks()
        render(searching, true)
        compose.onRoot().tryPerformAccessibilityChecks()
    }

    @Test fun scheduleDraftAppliesAtomically() {
        var applied: Pair<LocalDateTime?, com.sunseat.app.domain.TimeMode>? = null
        compose.setContent { SunSeatTheme { TimeSheet(null, com.sunseat.app.domain.TimeMode.DEPART, TravelMode.TRANSIT, {}, { value, mode -> applied = value to mode }, now = filled.departure!!) } }
        compose.onNodeWithText("적용").assertIsDisplayed()
        compose.onNodeWithText("도착 시각").performClick()
        compose.onNodeWithTag("schedule_date").performClick()
        compose.onNodeWithText("내일").performClick()
        org.junit.Assert.assertNull(applied)
        compose.onNodeWithText("적용").performClick()
        org.junit.Assert.assertEquals(filled.departure!!.plusDays(1), applied!!.first)
        org.junit.Assert.assertEquals(com.sunseat.app.domain.TimeMode.ARRIVE, applied!!.second)
    }

    @Test fun searchKeyboardClearAndSystemBack() {
        val query = androidx.compose.runtime.mutableStateOf("서울역")
        var submitted = false
        var closed = false
        var dispatcher: androidx.activity.OnBackPressedDispatcher? = null
        compose.setContent {
            dispatcher = androidx.activity.compose.LocalOnBackPressedDispatcherOwner.current!!.onBackPressedDispatcher
            SunSeatTheme { PlaceSearchScreen(SearchState(EndpointField.ORIGIN, query = query.value), InputActions(onQueryChange = { query.value = it }, searchNow = { submitted = true }, closeSearch = { closed = true })) }
        }
        compose.onNode(hasSetTextAction()).performImeAction()
        org.junit.Assert.assertTrue(submitted)
        compose.onNodeWithContentDescription("검색어 지우기").performClick()
        org.junit.Assert.assertEquals("", query.value)
        compose.runOnIdle { dispatcher!!.onBackPressed() }
        org.junit.Assert.assertTrue(closed)
    }

    private fun recentPlaces(dark: Boolean) {
        compose.enableAccessibilityChecks()
        render(InputUiState(search = SearchState(EndpointField.DESTINATION), recent = listOf(
            com.sunseat.app.data.recent.RecentPlace("seoul", "서울역", "서울 용산구 한강대로", 37.55, 126.97, TravelMode.TRANSIT, 0),
            com.sunseat.app.data.recent.RecentPlace("park", "서울숲", "서울 성동구 뚝섬로", 37.54, 127.04, TravelMode.CAR, 1),
        )), dark)
        compose.onRoot().tryPerformAccessibilityChecks()
        shot("input_recent_${if (dark) "dark" else "light"}")
    }
    @Test fun recentLight() = recentPlaces(false)
    @Test fun recentDark() = recentPlaces(true)

    @Test fun mapLocationButtonDoesNotSelectAnOrigin() {
        var originSelections = 0
        var mapMoves = 0
        compose.setContent {
            androidx.compose.runtime.CompositionLocalProvider(com.sunseat.app.ui.map.LocalMapRenderer provides com.sunseat.app.ui.map.MapRenderer.PREVIEW) {
                SunSeatTheme { InputScreen(filled, InputActions(useCurrentLocation = { originSelections++ }, showCurrentLocationOnMap = { mapMoves++ })) }
            }
        }
        compose.onNodeWithContentDescription(androidx.test.core.app.ApplicationProvider.getApplicationContext<android.content.Context>().getString(R.string.location_use_current)).performClick()
        org.junit.Assert.assertEquals(0, originSelections)
        org.junit.Assert.assertEquals(1, mapMoves)
    }

    private fun mapFocused(dark: Boolean) {
        compose.enableAccessibilityChecks()
        render(filled.copy(mapFocus = com.sunseat.app.ui.map.MapFocus(com.sunseat.app.domain.LatLng(37.59, 127.04), 1)), dark)
        compose.onRoot().tryPerformAccessibilityChecks()
        shot("input_map_focused_${if (dark) "dark" else "light"}")
    }
    @Test fun mapFocusedLight() = mapFocused(false)
    @Test fun mapFocusedDark() = mapFocused(true)

    private fun largeModeTabs(dark: Boolean, mode: TravelMode) {
        compose.enableAccessibilityChecks()
        compose.setContent {
            val density = androidx.compose.ui.platform.LocalDensity.current
            androidx.compose.runtime.CompositionLocalProvider(
                com.sunseat.app.ui.map.LocalMapRenderer provides com.sunseat.app.ui.map.MapRenderer.PREVIEW,
                androidx.compose.ui.platform.LocalDensity provides androidx.compose.ui.unit.Density(density.density, 1.5f),
            ) {
                SunSeatTheme(darkTheme = dark) { InputScreen(filled.copy(mode = mode), InputActions()) }
            }
        }
        compose.waitForIdle()
        compose.onNodeWithText(if (mode == TravelMode.CAR) "자가용" else "대중교통").assertIsDisplayed()
        compose.onRoot().tryPerformAccessibilityChecks()
        shot("input_tabs_large_${mode.name.lowercase()}_${if (dark) "dark" else "light"}")
    }
    @Test @Config(qualifiers = "w320dp-h640dp-xxhdpi")
    fun transitTabsLargeLight() = largeModeTabs(false, TravelMode.TRANSIT)
    @Test @Config(qualifiers = "w320dp-h640dp-xxhdpi")
    fun transitTabsLargeDark() = largeModeTabs(true, TravelMode.TRANSIT)
    @Test @Config(qualifiers = "w320dp-h640dp-xxhdpi")
    fun carTabsLargeDark() = largeModeTabs(true, TravelMode.CAR)

    private fun shot(name: String) = compose.onRoot().captureRoboImage("src/test/screenshots/$name.png")
}
