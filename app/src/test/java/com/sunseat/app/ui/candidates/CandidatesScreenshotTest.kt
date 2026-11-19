package com.sunseat.app.ui.candidates

import androidx.compose.material3.*
import androidx.compose.runtime.*
import kotlinx.coroutines.launch
import androidx.compose.ui.test.junit4.accessibility.enableAccessibilityChecks
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.tryPerformAccessibilityChecks
import com.github.takahirom.roborazzi.captureRoboImage
import com.sunseat.app.R
import com.sunseat.app.ui.candidates.CandidatesFixtures.options
import com.sunseat.app.ui.candidates.CandidatesFixtures.transitRequest
import com.sunseat.app.ui.theme.SunSeatTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** 경로 후보 화면 스크린샷(조회 중·목록·경로 없음·오류)과 접근성 검사. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(application = android.app.Application::class, sdk = [36], qualifiers = "w390dp-h844dp-xxhdpi")
class CandidatesScreenshotTest {
    @get:Rule
    val compose = createComposeRule(effectContext = object : androidx.compose.ui.MotionDurationScale { override val scaleFactor = 0f })

    private val list = CandidatesState.Options(options.toUi(java.time.LocalDateTime.of(2026, 11, 15, 9, 0)).map { item ->
        val route = item.chosen.route.copy(legs = item.chosen.route.legs.map { it.copy(groundLevel = com.sunseat.app.domain.GroundLevel.ABOVE) })
        val result = com.sunseat.app.domain.TripAdvisor(com.sunseat.app.domain.ExposureAnalyzer { _, _, _ -> com.sunseat.app.domain.SunPosition(90.0, 15.0) })
            .advise(route, java.time.Instant.parse("2026-11-15T00:00:00Z"), transitRequest.goal, totalDuration = item.totalDuration)
        item.copy(chosen = item.chosen.copy(route = route, cachedResult = result))
    })
    private val noRoute = R.string.candidates_no_transit.let { CandidatesState.NoRoute(it, R.string.candidates_no_transit_detail, suggestCar = true) }

    @OptIn(ExperimentalMaterial3Api::class)
    private fun render(state: CandidatesState, dark: Boolean, request: com.sunseat.app.domain.TripRequest = transitRequest, fontScale: Float = 1f) {
        // 로딩 애니메이션(Lottie)을 미리 읽어 둔다. 비동기 로딩이 캡처보다 늦으면 해가 빠진 화면이 찍힌다(#59).
        com.airbnb.lottie.LottieCompositionFactory.fromRawResSync(org.robolectric.RuntimeEnvironment.getApplication(), R.raw.sun_loading)
        android.provider.Settings.Global.putFloat(org.robolectric.RuntimeEnvironment.getApplication().contentResolver, android.provider.Settings.Global.ANIMATOR_DURATION_SCALE, 0f)
        compose.mainClock.autoAdvance = true
        lateinit var sheet: BottomSheetScaffoldState
        lateinit var scope: kotlinx.coroutines.CoroutineScope
        compose.setContent {
            sheet = rememberBottomSheetScaffoldState()
            scope = rememberCoroutineScope()
            androidx.compose.runtime.CompositionLocalProvider(com.sunseat.app.ui.map.LocalMapRenderer provides com.sunseat.app.ui.map.MapRenderer.PREVIEW) {
                val density = androidx.compose.ui.platform.LocalDensity.current
                androidx.compose.runtime.CompositionLocalProvider(androidx.compose.ui.platform.LocalDensity provides androidx.compose.ui.unit.Density(density.density, fontScale)) {
                    SunSeatTheme(darkTheme = dark) { CandidatesScreen(request, state, CandidatesActions(), animateLoader = false, scaffoldState = sheet) }
                }
            }
        }
        compose.waitForIdle()
        compose.runOnIdle { scope.launch { sheet.bottomSheetState.partialExpand() } }
        compose.mainClock.advanceTimeBy(5000)
        compose.waitForIdle()
        if (state is CandidatesState.Options) org.junit.Assert.assertEquals(SheetValue.PartiallyExpanded, sheet.bottomSheetState.currentValue)
    }

    @Test
    @Config(qualifiers = "w320dp-h640dp-xxhdpi")
    fun longEndpointsLargeTextSmallScreen() {
        compose.enableAccessibilityChecks()
        render(list, false, transitRequest.copy(origin = transitRequest.origin.copy(name = "아주 긴 견본 출발지 이름과 부가 설명"), destination = transitRequest.destination.copy(name = "아주 긴 견본 목적지 이름과 부가 설명")), fontScale = 1.5f)
        compose.onRoot().tryPerformAccessibilityChecks()
        shot("candidates_endpoints_small_large_text")
    }

    @Test fun loadingLight() { render(CandidatesState.Loading(com.sunseat.app.domain.TravelMode.TRANSIT), false); shot("candidates_loading_light") }
    private fun taxi(dark: Boolean) {
        val route = com.sunseat.app.ui.result.ResultFixtures.carRoute
        val result = com.sunseat.app.ui.result.ResultFixtures.advisor(90.0, 20.0).advise(route, com.sunseat.app.ui.result.ResultFixtures.departure, transitRequest.goal, travelMode = com.sunseat.app.domain.TravelMode.TAXI)
        val item = list.items.first().copy(totalDuration = route.duration, arrival = (result.departure + route.duration).atZone(java.time.ZoneId.of("Asia/Seoul")).toLocalDateTime(), transfers = 0, walkMinutes = 0, fare = 14500, chips = listOf(LegChip(com.sunseat.app.domain.Mode.CAR)), chosen = ChosenRoute(route, route.duration, result))
        compose.enableAccessibilityChecks()
        render(CandidatesState.Options(listOf(item), selectedId = item.id), dark, transitRequest.copy(mode = com.sunseat.app.domain.TravelMode.TAXI))
        compose.onRoot().tryPerformAccessibilityChecks()
        shot("candidates_taxi_${if (dark) "dark" else "light"}")
    }
    @Test fun taxiLight() = taxi(false)
    @Test fun taxiDark() = taxi(true)
    @Test fun loadingDark() { render(CandidatesState.Loading(com.sunseat.app.domain.TravelMode.TRANSIT), true); shot("candidates_loading_dark") }
    @Test fun loadingCarLight() { render(CandidatesState.Loading(com.sunseat.app.domain.TravelMode.CAR), false); shot("candidates_loading_car_light") }
    @Test fun listLight() { render(list, false); shot("candidates_list_light") }
    @Test fun listDark() { render(list, true); shot("candidates_list_dark") }
    @Test fun noRouteLight() { render(noRoute, false); shot("candidates_no_route_light") }
    @Test fun errorDark() { render(CandidatesState.Error(R.string.search_error_network, R.drawable.ic_ms_wifi_off, canRetry = true), true); shot("candidates_error_dark") }

    @Test fun errorLight() { render(CandidatesState.Error(R.string.search_error_network, R.drawable.ic_ms_wifi_off, canRetry = true), false); shot("candidates_error_light") }
    @Test fun unavailableLight() { render(CandidatesState.Error(R.string.candidates_error_unavailable, R.drawable.ic_ms_error, canRetry = false), false); shot("candidates_unavailable_light") }
    @Test fun unavailableDark() { render(CandidatesState.Error(R.string.candidates_error_unavailable, R.drawable.ic_ms_error, canRetry = false), true); shot("candidates_unavailable_dark") }
    @Test fun sunLight() { render(list.copy(sort = CandidateSort.SUN), false); shot("candidates_sun_light") }
    @Test fun sunDark() { render(list.copy(sort = CandidateSort.SUN), true); shot("candidates_sun_dark") }
    @Test fun filterLight() { render(list.copy(filters = setOf(com.sunseat.app.data.route.TransitRouteType.BUS), selectedId = 1), false); shot("candidates_filter_light") }
    @Test fun filterDark() { render(list.copy(filters = setOf(com.sunseat.app.data.route.TransitRouteType.BUS), selectedId = 1), true); shot("candidates_filter_dark") }
    @Test fun emptyFilterLight() { render(list.copy(filters = setOf(com.sunseat.app.data.route.TransitRouteType.BUS_AND_SUBWAY)), false); shot("candidates_empty_filter_light") }
    @Test fun emptyFilterDark() { render(list.copy(filters = setOf(com.sunseat.app.data.route.TransitRouteType.BUS_AND_SUBWAY)), true); shot("candidates_empty_filter_dark") }
    @Test fun noRouteDark() { render(noRoute, true); shot("candidates_no_route_dark") }

    @Test
    fun accessibilityList() {
        compose.enableAccessibilityChecks()
        render(list, false)
        compose.onRoot().tryPerformAccessibilityChecks()
    }

    @Test
    fun accessibilityNoRoute() {
        compose.enableAccessibilityChecks()
        render(noRoute, true)
        compose.onRoot().tryPerformAccessibilityChecks()
    }

    private fun shot(name: String) = compose.onRoot().captureRoboImage("src/test/screenshots/$name.png")
}
