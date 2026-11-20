package com.sunseat.app.ui.candidates

import androidx.compose.material3.*
import androidx.compose.foundation.verticalScroll
import androidx.compose.ui.test.performScrollTo
import androidx.compose.runtime.*
import kotlinx.coroutines.launch
import androidx.compose.ui.test.junit4.accessibility.enableAccessibilityChecks
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.assertIsDisplayed
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
@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
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
    private fun render(state: CandidatesState, dark: Boolean, request: com.sunseat.app.domain.TripRequest = transitRequest, fontScale: Float = 1f): Pair<BottomSheetScaffoldState, kotlinx.coroutines.CoroutineScope> {
        // 로딩 애니메이션(Lottie)을 미리 읽어 둔다. 비동기 로딩이 캡처보다 늦으면 해가 빠진 화면이 찍힌다(#59).
        com.airbnb.lottie.LottieCompositionFactory.fromRawResSync(org.robolectric.RuntimeEnvironment.getApplication(), R.raw.sun_loading)
        android.provider.Settings.Global.putFloat(org.robolectric.RuntimeEnvironment.getApplication().contentResolver, android.provider.Settings.Global.ANIMATOR_DURATION_SCALE, 0f)
        compose.mainClock.autoAdvance = true
        lateinit var sheet: BottomSheetScaffoldState
        lateinit var scope: kotlinx.coroutines.CoroutineScope
        var current by mutableStateOf(state)
        compose.setContent {
            sheet = rememberBottomSheetScaffoldState()
            scope = rememberCoroutineScope()
            androidx.compose.runtime.CompositionLocalProvider(com.sunseat.app.ui.map.LocalMapRenderer provides com.sunseat.app.ui.map.MapRenderer.PREVIEW) {
                val density = androidx.compose.ui.platform.LocalDensity.current
                androidx.compose.runtime.CompositionLocalProvider(androidx.compose.ui.platform.LocalDensity provides androidx.compose.ui.unit.Density(density.density, fontScale)) {
                    SunSeatTheme(darkTheme = dark) { CandidatesScreen(request, current, CandidatesActions(onHighlight = { current = (current as CandidatesState.Options).copy(selectedId = it.id) }), animateLoader = false, scaffoldState = sheet) }
                }
            }
        }
        compose.waitForIdle()
        compose.runOnIdle { scope.launch { sheet.bottomSheetState.partialExpand() } }
        compose.mainClock.advanceTimeBy(5000)
        compose.waitForIdle()
        if (state is CandidatesState.Options) org.junit.Assert.assertEquals(SheetValue.PartiallyExpanded, sheet.bottomSheetState.currentValue)
        return sheet to scope
    }

    private fun preview(dark: Boolean, name: String, fontScale: Float = 1f, state: CandidatesState.Options = list) {
        compose.enableAccessibilityChecks()
        render(state, dark, fontScale = fontScale)
        compose.onNodeWithTag("candidate_${state.items.first().id}").performSemanticsAction(SemanticsActions.OnClick)
        compose.mainClock.advanceTimeBy(5000)
        compose.waitUntil(10_000) { compose.onNodeWithTag("candidate_map").fetchSemanticsNode().config[com.sunseat.app.ui.map.MapCameraRevisionKey] > 0 }
        compose.onRoot().tryPerformAccessibilityChecks()
        compose.onNodeWithText("자리 보기").assertIsDisplayed()
        shot(name)
    }
    /** #99: 같은 출발·도착에서 멀리 갈라지는 후보. 미리보기는 선택 경로만, 개요는 비교용 전체를 그린다. */
    private val branching = list.copy(items = list.items.mapIndexed { index, item ->
        val origin = transitRequest.origin.location
        val destination = transitRequest.destination.location
        val via = com.sunseat.app.domain.LatLng(origin.latitude - 0.12 * index, origin.longitude + 0.1 - 0.06 * index)
        item.copy(chosen = item.chosen.copy(route = item.chosen.route.copy(legs = item.chosen.route.legs.mapIndexed { legIndex, leg ->
            leg.copy(path = if (legIndex == 0) listOf(origin, via) else listOf(via, destination))
        })))
    }, selectedId = list.items.first().id)
    @Test fun previewBranchingLight() = preview(false, "candidates_preview_branching_light", state = branching)
    @Test fun previewBranchingDark() = preview(true, "candidates_preview_branching_dark", state = branching)
    @Test fun listBranchingLight() { render(branching, false); shot("candidates_list_branching_light") }

    @Test fun previewLight() = preview(false, "candidates_preview_light")
    @Test fun previewDark() = preview(true, "candidates_preview_dark")
    @Test @Config(qualifiers = "w320dp-h640dp-xxhdpi")
    fun previewSmallLargeText() = preview(false, "candidates_preview_small_large_text", 1.5f)
    @Test @Config(qualifiers = "w844dp-h390dp-xxhdpi")
    fun previewLandscape() = preview(false, "candidates_preview_landscape")

    @OptIn(ExperimentalMaterial3Api::class)
    @Test fun expandedList() = expanded(false, "candidates_expanded_light")
    @Test fun expandedDark() = expanded(true, "candidates_expanded_dark")
    @Test @Config(qualifiers = "w320dp-h640dp-xxhdpi")
    fun expandedSmallLargeText() = expanded(false, "candidates_expanded_small_large_text", transitRequest.copy(origin = transitRequest.origin.copy(name = "아주 긴 견본 출발지 이름과 부가 설명"), destination = transitRequest.destination.copy(name = "아주 긴 견본 목적지 이름과 부가 설명")), 1.5f)
    @Test @Config(qualifiers = "w844dp-h390dp-xxhdpi")
    fun expandedLandscape() = expanded(false, "candidates_expanded_landscape")

    /** 완전 확장: 펼친 헤더 조각 없이 축약 여정 바 + 시트 전체(#101). */
    @OptIn(ExperimentalMaterial3Api::class)
    private fun expanded(dark: Boolean, name: String, request: com.sunseat.app.domain.TripRequest = transitRequest, fontScale: Float = 1f) {
        compose.enableAccessibilityChecks()
        val (sheet, scope) = render(list, dark, request, fontScale)
        compose.runOnIdle { scope.launch { sheet.bottomSheetState.expand() } }
        compose.mainClock.advanceTimeBy(5000)
        compose.waitForIdle()
        org.junit.Assert.assertEquals(SheetValue.Expanded, sheet.bottomSheetState.currentValue)
        compose.onNodeWithTag("candidates_journey_bar").assertIsDisplayed()
        compose.onRoot().tryPerformAccessibilityChecks()
        if (name != "candidates_expanded_landscape") {
            val listBottom = compose.onNodeWithTag("candidate_list").getUnclippedBoundsInRoot().bottom
            val secondTime = compose.onNodeWithTag("candidate_time_1", useUnmergedTree = true).getUnclippedBoundsInRoot()
            val firstCard = compose.onNodeWithTag("candidate_0").getUnclippedBoundsInRoot()
            println("#98 density $name: first row=${firstCard.bottom - firstCard.top}, first top=${firstCard.top}, second time=${secondTime.top}..${secondTime.bottom}, list bottom=$listBottom")
            if (name != "candidates_expanded_landscape") org.junit.Assert.assertTrue("second candidate total must be fully visible", secondTime.bottom <= listBottom)
            if (fontScale == 1f && name != "candidates_expanded_landscape") {
                compose.onNodeWithTag("route_bar_1", useUnmergedTree = true).assertIsDisplayed()
                compose.onNodeWithTag("candidate_boarding_1", useUnmergedTree = true).assertIsDisplayed()
            }
            if (fontScale > 1f) org.junit.Assert.assertTrue("one whole candidate must fit at 320dp / 1.5x", firstCard.bottom <= listBottom)
        }
        shot(name)
        compose.runOnIdle { scope.launch { sheet.bottomSheetState.partialExpand() } }
        compose.mainClock.advanceTimeBy(5000)
        compose.waitForIdle()
        org.junit.Assert.assertEquals(SheetValue.PartiallyExpanded, sheet.bottomSheetState.currentValue)
    }

    @Test
    @Config(qualifiers = "w320dp-h640dp-xxhdpi")
    fun longEndpointsLargeTextSmallScreen() {
        compose.enableAccessibilityChecks()
        render(list, false, transitRequest.copy(origin = transitRequest.origin.copy(name = "아주 긴 견본 출발지 이름과 부가 설명"), destination = transitRequest.destination.copy(name = "아주 긴 견본 목적지 이름과 부가 설명")), fontScale = 1.5f)
        compose.onRoot().tryPerformAccessibilityChecks()
        compose.onNodeWithText("지금 출발").assertIsDisplayed()
        shot("candidates_endpoints_small_large_text")
    }

    @Test fun loadingLight() { render(CandidatesState.Loading(com.sunseat.app.domain.TravelMode.TRANSIT), false); shot("candidates_loading_light") }
    private fun taxi(dark: Boolean, car: Boolean = false) {
        val route = com.sunseat.app.ui.result.ResultFixtures.carRoute
        val result = com.sunseat.app.ui.result.ResultFixtures.advisor(90.0, 20.0).advise(route, com.sunseat.app.ui.result.ResultFixtures.departure, transitRequest.goal, travelMode = com.sunseat.app.domain.TravelMode.TAXI)
        val item = list.items.first().copy(totalDuration = route.duration, arrival = (result.departure + route.duration).atZone(java.time.ZoneId.of("Asia/Seoul")).toLocalDateTime(), transfers = 0, walkMinutes = 0, fare = 14500, chips = listOf(LegChip(com.sunseat.app.domain.Mode.CAR)), chosen = ChosenRoute(route, route.duration, result))
        compose.enableAccessibilityChecks()
        render(CandidatesState.Options(listOf(item), selectedId = item.id), dark, transitRequest.copy(mode = if (car) com.sunseat.app.domain.TravelMode.CAR else com.sunseat.app.domain.TravelMode.TAXI))
        compose.onRoot().tryPerformAccessibilityChecks()
        shot("candidates_${if (car) "car" else "taxi"}_${if (dark) "dark" else "light"}")
    }
    @Test fun carLight() = taxi(false, car = true)
    @Test fun carDark() = taxi(true, car = true)
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

    /** #105: 최소 도보 정렬은 응답의 환승 도보만 비교한다는 목록 공통 안내. */
    private fun walkSort(dark: Boolean, name: String, fontScale: Float = 1f) {
        compose.enableAccessibilityChecks()
        render(list.copy(sort = CandidateSort.WALK), dark, fontScale = fontScale)
        compose.onRoot().tryPerformAccessibilityChecks()
        compose.onNodeWithTag("walk_scope_notice").assertIsDisplayed()
        shot(name)
    }
    @Test fun walkSortLight() = walkSort(false, "candidates_walk_sort_light")
    @Test fun walkSortDark() = walkSort(true, "candidates_walk_sort_dark")
    @Test @Config(qualifiers = "w320dp-h640dp-xxhdpi")
    fun walkSortSmallLargeText() = walkSort(false, "candidates_walk_sort_small_large_text", 1.5f)

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

    private fun readableCard(dark: Boolean, large: Boolean = false, missing: Boolean = false) {
        compose.enableAccessibilityChecks()
        val base = CandidatesFixtures.readable.toUi(0, java.time.LocalDateTime.of(2026, 11, 15, 23, 50), false)
        val night = com.sunseat.app.ui.result.ResultFixtures.advisor(90.0, -20.0).advise(base.chosen.route, java.time.Instant.parse("2026-11-15T14:50:00Z"), transitRequest.goal, totalDuration = base.totalDuration)
        val computed = base.copy(chosen = base.chosen.copy(cachedResult = night))
        val item = if (missing) computed.copy(chips = emptyList(), boardAt = "", alightAt = "", chosen = computed.chosen.copy(route = computed.chosen.route.copy(legs = computed.chosen.route.legs.map { it.copy(startName = "", endName = "") }))) else computed
        compose.setContent {
            val density = androidx.compose.ui.platform.LocalDensity.current
            CompositionLocalProvider(androidx.compose.ui.platform.LocalDensity provides androidx.compose.ui.unit.Density(density.density, if (large) 1.5f else 1f)) {
                SunSeatTheme(darkTheme = dark) {
                    androidx.compose.foundation.layout.Column(androidx.compose.ui.Modifier.then(androidx.compose.ui.Modifier.verticalScroll(androidx.compose.foundation.rememberScrollState()))) {
                        CandidateCard(item, true, transitRequest, {}, {})
                    }
                }
            }
        }
        compose.onRoot().tryPerformAccessibilityChecks()
        if (!missing) {
            compose.onNodeWithText("버스 견본 광역급행 아주 긴 노선 이름 12345 / 대체노선 67890").assertExists()
            compose.onNodeWithText("견본 출발 정류장 이름이 아주 긴 곳 승차").assertExists()
            compose.onNodeWithText("견본 환승센터 하차").assertExists()
        }
        compose.onNodeWithText("23:50 → 다음 날 00:30").assertExists()
        shot("candidates_readable_${if (dark) "dark" else "light"}${if (large) "_large" else ""}${if (missing) "_missing" else ""}")
        compose.onNodeWithText("자리 보기").performScrollTo().assertIsDisplayed()
        compose.onRoot().tryPerformAccessibilityChecks()
        if (large) shot("candidates_readable_${if (dark) "dark" else "light"}_large_bottom")
    }
    @Test fun readableLight() = readableCard(false)
    @Test fun readableDark() = readableCard(true)
    @Test @Config(qualifiers = "w320dp-h640dp-xxhdpi")
    fun readableSmallLarge() = readableCard(false, large = true)
    @Test @Config(qualifiers = "w320dp-h640dp-xxhdpi")
    fun readableDarkSmallLarge() = readableCard(true, large = true)
    @Test fun readableMissing() = readableCard(false, missing = true)

    @Test fun sameLineReboardingWithShortWalkLight() {
        val base = list.items.first()
        val ride = base.chosen.route.legs.first().copy(estimatedStationTiming = true, transitStepIndex = 0)
        val second = ride.copy(startName = ride.endName, endName = "견본 중간역")
        val walk = base.chosen.route.legs[1].copy(duration = java.time.Duration.ofSeconds(20), startName = "견본 중간역", endName = "견본 환승역")
        val again = ride.copy(startName = "견본 환승역", endName = "견본 종점", transitStepIndex = 1)
        val route = com.sunseat.app.domain.Route(listOf(ride, second, walk, again))
        val result = com.sunseat.app.ui.result.ResultFixtures.advisor(90.0, 15.0)
            .advise(route, java.time.Instant.parse("2026-11-15T00:00:00Z"), transitRequest.goal, totalDuration = base.totalDuration)
        val item = base.copy(chips = listOf(base.chips[0], base.chips[0], base.chips[1], base.chips[0]),
            chosen = base.chosen.copy(route = route, cachedResult = result))
        compose.enableAccessibilityChecks()
        render(CandidatesState.Options(listOf(item), selectedId = item.id), false)
        compose.onRoot().tryPerformAccessibilityChecks()
        shot("candidates_same_line_reboarding_light")
    }

    /** #102: 접힌 요약(총시간·환승·막대·노선 조합·자리 보기)과 펼친 상세(시각·요금·전체 장소명·타임라인). */
    private val longRequest = transitRequest.copy(origin = transitRequest.origin.copy(name = "아주 긴 견본 출발지 이름과 부가 설명"),
        destination = transitRequest.destination.copy(name = "아주 긴 견본 목적지 이름과 부가 설명"))
    private val readableList = list.copy(items = listOf(CandidatesFixtures.readable.toUi(0, java.time.LocalDateTime.of(2026, 11, 15, 9, 0), false)) + list.items.map { it.copy(id = it.id + 1) })

    private fun previewSummary(dark: Boolean, name: String, fontScale: Float = 1f, state: CandidatesState.Options = list,
                               request: com.sunseat.app.domain.TripRequest = transitRequest, expand: Boolean = false) {
        compose.enableAccessibilityChecks()
        val (sheet, scope) = render(state, dark, request, fontScale)
        compose.onNodeWithTag("candidate_${state.items.first().id}").performSemanticsAction(SemanticsActions.OnClick)
        compose.mainClock.advanceTimeBy(5000)
        compose.waitUntil(10_000) { compose.onNodeWithTag("candidate_map").fetchSemanticsNode().config[com.sunseat.app.ui.map.MapCameraRevisionKey] > 0 }
        if (expand) {
            compose.runOnIdle { scope.launch { sheet.bottomSheetState.expand() } }
            compose.mainClock.advanceTimeBy(5000)
            compose.waitForIdle()
            org.junit.Assert.assertEquals(SheetValue.Expanded, sheet.bottomSheetState.currentValue)
        } else compose.onNodeWithText("자리 보기").assertIsDisplayed()
        compose.onRoot().tryPerformAccessibilityChecks()
        shot(name)
    }

    @Test @Config(qualifiers = "w320dp-h640dp-xxhdpi")
    fun previewSmallLargeTextDark() = previewSummary(true, "candidates_preview_small_large_text_dark", 1.5f)
    @Test @Config(qualifiers = "w320dp-h640dp-xxhdpi")
    fun previewLongNamesTransfersSmallLargeText() = previewSummary(false, "candidates_preview_long_small_large_text", 1.5f, readableList, longRequest)
    @Test fun previewTransfersLight() = previewSummary(false, "candidates_preview_transfers_light", state = readableList)
    @Test fun previewExpandedLight() = previewSummary(false, "candidates_preview_expanded_light", expand = true)
    @Test fun previewExpandedDark() = previewSummary(true, "candidates_preview_expanded_dark", expand = true)
    @Test @Config(qualifiers = "w320dp-h640dp-xxhdpi")
    fun previewExpandedSmallLargeTextLongNames() = previewSummary(false, "candidates_preview_expanded_small_large_text", 1.5f, readableList, longRequest, expand = true)

    @Test fun previewTaxiLight() {
        val route = com.sunseat.app.ui.result.ResultFixtures.carRoute
        val item = list.items.first().copy(totalDuration = route.duration, arrival = null, transfers = 0, walkMinutes = 0, fare = 14500, chips = listOf(LegChip(com.sunseat.app.domain.Mode.CAR)), boardAt = "", alightAt = "", chosen = ChosenRoute(route, route.duration))
        previewSummary(false, "candidates_preview_taxi_light", state = CandidatesState.Options(listOf(item)), request = transitRequest.copy(mode = com.sunseat.app.domain.TravelMode.TAXI))
    }
    @Test fun previewWalkOnlyLight() {
        val base = list.items.first()
        val walk = com.sunseat.app.domain.Leg(com.sunseat.app.domain.Mode.WALK, base.chosen.route.legs.first().path, java.time.Duration.ofMinutes(25), "", "")
        val item = base.copy(totalDuration = java.time.Duration.ofMinutes(25), transfers = 0, walkMinutes = 25, chips = listOf(LegChip(com.sunseat.app.domain.Mode.WALK)), boardAt = "", alightAt = "", chosen = base.chosen.copy(route = com.sunseat.app.domain.Route(listOf(walk)), cachedResult = null))
        previewSummary(false, "candidates_preview_walk_light", state = CandidatesState.Options(listOf(item)))
    }

    private fun shot(name: String) = compose.onRoot().captureRoboImage("src/test/screenshots/$name.png")
}
