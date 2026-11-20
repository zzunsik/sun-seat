package com.sunseat.app.ui.candidates

import com.sunseat.app.R
import com.sunseat.app.data.network.ApiError
import com.sunseat.app.data.network.ApiResult
import com.sunseat.app.data.route.CarNoRouteReason
import com.sunseat.app.data.route.CarRouteApi
import com.sunseat.app.data.route.CarRouteOption
import com.sunseat.app.data.route.CarRouteResult
import com.sunseat.app.data.route.NoRouteReason
import com.sunseat.app.data.route.TransitRouteApi
import com.sunseat.app.data.route.TransitRoutes
import com.sunseat.app.data.route.TransitRouteType
import com.sunseat.app.domain.LatLng
import com.sunseat.app.domain.Leg
import com.sunseat.app.domain.Mode
import com.sunseat.app.domain.Route
import com.sunseat.app.ui.candidates.CandidatesFixtures.carRequest
import com.sunseat.app.ui.candidates.CandidatesFixtures.options
import com.sunseat.app.ui.candidates.CandidatesFixtures.transitRequest
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.time.Duration

@OptIn(ExperimentalCoroutinesApi::class)
class CandidatesViewModelTest {
    private val dispatcher = StandardTestDispatcher()

    private class FakeTransit(var result: ApiResult<TransitRoutes>) : TransitRouteApi {
        var calls = 0
        override suspend fun search(origin: LatLng, destination: LatLng): ApiResult<TransitRoutes> { calls++; return result }
    }

    private class FakeCar(var result: ApiResult<CarRouteResult>) : CarRouteApi {
        var calls = 0
        override suspend fun search(origin: LatLng, destination: LatLng): ApiResult<CarRouteResult> { calls++; return result }
    }

    private val carRoute = Route(listOf(Leg(Mode.CAR, listOf(LatLng(0.0, 0.0), LatLng(0.1, 0.1)), Duration.ofMinutes(23), "", "")))
    private val noCar = FakeCar(ApiResult.Failure(ApiError.UNKNOWN))
    private val noTransit = FakeTransit(ApiResult.Failure(ApiError.UNKNOWN))

    @Before fun setUp() = Dispatchers.setMain(dispatcher)
    @After fun tearDown() = Dispatchers.resetMain()

    @Test fun taxiShowsFareAndPassengerCalculationBeforeSelection() = runTest(dispatcher) {
        val car = FakeCar(ApiResult.Success(CarRouteResult(CarRouteOption(carRoute, 10000, 0, 14500))))
        val vm = CandidatesViewModel(carRequest.copy(mode = com.sunseat.app.domain.TravelMode.TAXI), noTransit, car, compute = dispatcher)
        advanceUntilIdle()
        val item = (vm.state.value as CandidatesState.Options).items.single()
        assertEquals(14500, item.fare)
        assertEquals(1, car.calls)
        assertEquals(0, noTransit.calls)
        assertTrue(item.chosen.cachedResult!!.glare.isEmpty())
        val sit = item.chosen.cachedResult!!.legs.single().advice as? com.sunseat.app.domain.LegAdvice.Sit
        assertTrue(sit == null || com.sunseat.app.domain.CarSeat.PASSENGER !in sit.carSeats)
    }

    @Test fun scheduleUpdateReusesRoutesAndKeepsSelectionSortAndFilter() = runTest(dispatcher) {
        val transit = FakeTransit(ApiResult.Success(TransitRoutes(options)))
        var clock = java.time.LocalDateTime.of(2026, 11, 18, 9, 0)
        val vm = CandidatesViewModel(transitRequest, transit, noCar, now = { clock }, compute = dispatcher)
        advanceUntilIdle()
        vm.setSort(CandidateSort.WALK)
        vm.selectCategory(RouteCategory.SUBWAY)
        vm.highlight(0)
        val arrival = clock.withHour(23).withMinute(37)
        vm.updateRequest(transitRequest.copy(departure = arrival), 1)
        assertTrue((vm.state.value as CandidatesState.Options).items.all { it.chosen.cachedResult == null })
        advanceUntilIdle()
        val state = vm.state.value as CandidatesState.Options
        assertEquals(1, transit.calls)
        assertEquals(CandidateSort.WALK, state.sort)
        assertEquals(RouteCategory.SUBWAY, state.category)
        assertEquals(0, state.selectedId)
        assertTrue(state.items.all { it.arrival == arrival.plus(it.totalDuration) })
        clock = clock.plusHours(1)
        vm.updateRequest(transitRequest, 2)
        advanceUntilIdle()
        assertTrue((vm.state.value as CandidatesState.Options).items.all {
            it.chosen.cachedResult!!.departure == clock.atZone(java.time.ZoneId.systemDefault()).toInstant()
        })
        // A fresh 'now' confirmation must not reuse the original calculation just because fields match.
        clock = clock.plusHours(1)
        vm.updateRequest(transitRequest, 3)
        advanceUntilIdle()
        assertEquals(1, transit.calls)
        assertTrue((vm.state.value as CandidatesState.Options).items.all {
            it.chosen.cachedResult!!.departure == clock.atZone(java.time.ZoneId.systemDefault()).toInstant()
        })
    }

    @Test fun timeUpdateDuringNetworkLoadUsesLatestScheduleWithoutAnotherCall() = runTest(dispatcher) {
        val response = kotlinx.coroutines.CompletableDeferred<ApiResult<TransitRoutes>>()
        var calls = 0
        val transit = object : TransitRouteApi {
            override suspend fun search(origin: LatLng, destination: LatLng): ApiResult<TransitRoutes> { calls++; return response.await() }
        }
        val vm = CandidatesViewModel(transitRequest, transit, noCar, compute = dispatcher)
        runCurrent()
        val arrival = java.time.LocalDateTime.of(2026, 11, 19, 12, 37)
        vm.updateRequest(transitRequest.copy(departure = arrival), 1)
        response.complete(ApiResult.Success(TransitRoutes(options)))
        advanceUntilIdle()
        assertEquals(1, calls)
        assertTrue((vm.state.value as CandidatesState.Options).items.all { it.arrival == arrival.plus(it.totalDuration) })
    }

    @Test
    fun readableItineraryKeepsAlternativesAndMissingData() {
        val item = CandidatesFixtures.readable.toUi(0, null, false)
        assertEquals("견본 광역급행 아주 긴 노선 이름 12345 / 대체노선 67890", item.chips.first().lineName)
        assertEquals("", item.chips.last().lineName)
        assertEquals(null, item.fare)
        assertEquals("견본 출발 정류장 이름이 아주 긴 곳", item.boardAt)
        assertEquals("견본 도착 정류장", item.alightAt)
        assertEquals(java.time.Duration.ofSeconds(20), item.chosen.route.legs[1].duration)
    }

    @Test
    fun transitOptionsMapToCards() = runTest(dispatcher) {
        val vm = CandidatesViewModel(transitRequest, FakeTransit(ApiResult.Success(TransitRoutes(options))), noCar, now = { java.time.LocalDateTime.of(2026, 11, 15, 9, 0) }, compute = dispatcher)
        advanceUntilIdle()
        val items = (vm.state.value as CandidatesState.Options).items
        assertEquals(2, items.size)
        val first = items[0]
        assertEquals(listOf(Mode.SUBWAY, Mode.WALK, Mode.SUBWAY), first.chips.map { it.mode })
        assertEquals(3L, first.chips[1].walkMinutes)
        assertEquals("1호선", first.chips[0].lineName)
        assertEquals("서울역", first.boardAt)
        assertEquals("강남", first.alightAt)
        assertEquals(1, first.transfers)
        assertEquals(1550, first.fare)
        assertEquals(listOf("140"), items[1].chips.map { it.lineName })
        assertEquals(3L, first.walkMinutes)
        assertTrue(first.fastest)
        assertFalse(items[1].fastest)
        assertEquals(java.time.LocalDateTime.of(2026, 11, 15, 9, 38), first.arrival)
        assertEquals("1호선", first.chips[0].lineName)
        assertEquals(0, noCar.calls)
    }

    @Test
    fun showsListBeforeParallelCalculationsFinish() = runTest(dispatcher) {
        var active = 0
        var peak = 0
        val vm = CandidatesViewModel(transitRequest, FakeTransit(ApiResult.Success(TransitRoutes(options))), noCar, compute = dispatcher,
            calculate = { route, departure, goal, bench, total ->
                active++; peak = maxOf(peak, active)
                kotlinx.coroutines.delay(100)
                active--
                com.sunseat.app.domain.TripAdvisor().advise(route, departure, goal, bench, total)
            })
        runCurrent()
        assertEquals(2, (vm.state.value as CandidatesState.Options).items.size)
        assertTrue((vm.state.value as CandidatesState.Options).items.all { it.chosen.cachedResult == null })
        assertEquals(2, peak)
        advanceUntilIdle()
        assertTrue((vm.state.value as CandidatesState.Options).items.all { it.chosen.cachedResult != null })
    }

    @Test
    fun reloadCancelsOldCalculationsAndKeepsNewRoutes() = runTest(dispatcher) {
        val gate = kotlinx.coroutines.CompletableDeferred<Unit>()
        var cancelled = 0
        val transit = FakeTransit(ApiResult.Success(TransitRoutes(options)))
        val vm = CandidatesViewModel(transitRequest, transit, noCar, compute = dispatcher,
            calculate = { route, departure, goal, bench, total ->
                try { gate.await() } catch (e: kotlinx.coroutines.CancellationException) { cancelled++; throw e }
                com.sunseat.app.domain.TripAdvisor().advise(route, departure, goal, bench, total)
            })
        runCurrent()
        transit.result = ApiResult.Success(TransitRoutes(listOf(CandidatesFixtures.busOnly)))
        vm.load(); runCurrent()
        gate.complete(Unit); advanceUntilIdle()
        assertEquals(2, cancelled)
        val item = (vm.state.value as CandidatesState.Options).items.single()
        assertEquals(com.sunseat.app.data.route.TransitRouteType.BUS, item.type)
        assertTrue(item.chosen.cachedResult!!.legs.all { it.leg.mode == Mode.BUS })
    }

    @Test
    fun cancelledNonCooperativeApiCannotOverwriteLatestList() = runTest(dispatcher) {
        val gate = kotlinx.coroutines.CompletableDeferred<Unit>()
        var calls = 0
        val api = object : TransitRouteApi {
            override suspend fun search(origin: LatLng, destination: LatLng): ApiResult<TransitRoutes> {
                val call = ++calls
                if (call == 1) kotlinx.coroutines.withContext(kotlinx.coroutines.NonCancellable) { gate.await() }
                return ApiResult.Success(TransitRoutes(listOf(if (call == 1) CandidatesFixtures.subwayOnly else CandidatesFixtures.busOnly)))
            }
        }
        val vm = CandidatesViewModel(transitRequest, api, noCar, compute = dispatcher)
        runCurrent(); vm.load(); runCurrent()
        assertEquals(com.sunseat.app.data.route.TransitRouteType.BUS, (vm.state.value as CandidatesState.Options).items.single().type)
        gate.complete(Unit); advanceUntilIdle()
        assertEquals(com.sunseat.app.data.route.TransitRouteType.BUS, (vm.state.value as CandidatesState.Options).items.single().type)
    }

    @Test
    fun noTransitRouteSuggestsCar() = runTest(dispatcher) {
        val vm = CandidatesViewModel(transitRequest, FakeTransit(ApiResult.Success(TransitRoutes(emptyList(), NoRouteReason.NO_RESULTS))), noCar, compute = dispatcher)
        advanceUntilIdle()
        val s = vm.state.value as CandidatesState.NoRoute
        assertEquals(R.string.candidates_no_transit, s.titleRes)
        assertEquals(R.string.candidates_no_transit_detail, s.detailRes)
        assertTrue(s.suggestCar)
    }

    @Test
    fun samePointsDoesNotSuggestCar() = runTest(dispatcher) {
        val vm = CandidatesViewModel(transitRequest, FakeTransit(ApiResult.Success(TransitRoutes(emptyList(), NoRouteReason.SAME_POINTS))), noCar, compute = dispatcher)
        advanceUntilIdle()
        assertFalse((vm.state.value as CandidatesState.NoRoute).suggestCar)
    }

    @Test
    fun networkErrorCanRetryAndRetryReloads() = runTest(dispatcher) {
        val transit = FakeTransit(ApiResult.Failure(ApiError.NETWORK))
        val vm = CandidatesViewModel(transitRequest, transit, noCar, compute = dispatcher)
        advanceUntilIdle()
        val err = vm.state.value as CandidatesState.Error
        assertTrue(err.canRetry)
        assertEquals(R.string.search_error_network, err.messageRes)
        assertEquals(R.drawable.ic_ms_wifi_off, err.iconRes)
        transit.result = ApiResult.Success(TransitRoutes(options))
        vm.load()
        assertEquals(CandidatesState.Loading(com.sunseat.app.domain.TravelMode.TRANSIT), vm.state.value)
        advanceUntilIdle()
        assertTrue(vm.state.value is CandidatesState.Options)
        assertEquals(2, transit.calls)
    }

    @Test
    fun departureSetShowsScheduledArrival() = runTest(dispatcher) {
        val req = transitRequest.copy(departure = java.time.LocalDateTime.of(2026, 11, 16, 9, 0))
        val vm = CandidatesViewModel(req, FakeTransit(ApiResult.Success(TransitRoutes(options))), noCar, compute = dispatcher)
        advanceUntilIdle()
        assertEquals(req.departure!!.plusMinutes(38), (vm.state.value as CandidatesState.Options).items[0].arrival)
    }

    @Test
    fun missingKeyCannotRetry() = runTest(dispatcher) {
        val vm = CandidatesViewModel(transitRequest, FakeTransit(ApiResult.Failure(ApiError.MISSING_KEY)), noCar, compute = dispatcher)
        advanceUntilIdle()
        assertFalse((vm.state.value as CandidatesState.Error).canRetry)
    }

    @Test
    fun carRouteIsAutoSelected() = runTest(dispatcher) {
        val car = FakeCar(ApiResult.Success(CarRouteResult(CarRouteOption(carRoute, 9000, 0, 12000))))
        val vm = CandidatesViewModel(carRequest, noTransit, car, compute = dispatcher)
        advanceUntilIdle()
        val chosen = vm.autoSelected.first()
        assertEquals(carRoute, chosen.route)
        assertEquals(Duration.ofMinutes(23), chosen.totalDuration)
        assertEquals(0, noTransit.calls)
    }

    @Test
    fun carNoRouteShowsReasonWithoutCarSuggestion() = runTest(dispatcher) {
        val vm = CandidatesViewModel(carRequest, noTransit, FakeCar(ApiResult.Success(CarRouteResult(null, CarNoRouteReason.NO_START_ROAD))), compute = dispatcher)
        advanceUntilIdle()
        val s = vm.state.value as CandidatesState.NoRoute
        assertEquals(R.string.candidates_car_no_start_road, s.titleRes)
        assertFalse(s.suggestCar)
    }

    @Test
    fun sortFilterAndSelectionCombine() = runTest(dispatcher) {
        // 0: 지하철 38분·환승 1·도보 3, 1: 버스 52분·환승 0·도보 0, 2: 버스+지하철 45분·환승 2·도보 0
        val mixed = CandidatesFixtures.busOnly.copy(type = TransitRouteType.BUS_AND_SUBWAY, totalDuration = Duration.ofMinutes(45), transfers = 2)
        val vm = CandidatesViewModel(transitRequest, FakeTransit(ApiResult.Success(TransitRoutes(options + mixed))), noCar, compute = dispatcher)
        vm.setSort(CandidateSort.TIME)
        vm.selectCategory(RouteCategory.BUS)
        assertTrue(vm.state.value is CandidatesState.Loading)
        advanceUntilIdle()
        fun options() = vm.state.value as CandidatesState.Options
        fun ids() = options().visible.map { it.id }
        assertEquals(CandidateSort.RECOMMENDED, options().sort)
        assertEquals(RouteCategory.ALL, options().category)

        // #82: 범주는 하나만 고른다. 고른 경로가 새 범주에도 있으면 선택을 유지한다.
        vm.setSort(CandidateSort.TIME)
        assertEquals(listOf(0, 2, 1), ids())
        vm.highlight(2)
        vm.selectCategory(RouteCategory.MIXED)
        assertEquals(listOf(2), ids())
        assertEquals(CandidateSort.TIME, options().sort)
        assertEquals(2, options().selectedId)

        // 고른 경로가 숨는 범주로 바꾸면 선택을 해제한다(목록에 없는데 지도만 선택된 상태 방지).
        vm.selectCategory(RouteCategory.BUS)
        assertEquals(listOf(1), ids())
        assertNull(options().selectedId)
        // 같은 범주를 다시 골라도 전체로 숨은 토글되지 않는다.
        vm.selectCategory(RouteCategory.BUS)
        assertEquals(RouteCategory.BUS, options().category)
        assertEquals(listOf(1), ids())

        vm.setSort(CandidateSort.TRANSFERS)
        vm.highlight(1)
        vm.setSort(CandidateSort.WALK)
        assertEquals(1, options().selectedId)

        vm.selectCategory(RouteCategory.ALL)
        assertEquals(CandidateSort.WALK, options().sort)
        assertEquals(listOf(1, 2, 0), ids())
        assertEquals(1, options().selectedId)
    }

    /** #82: 범주별 개수는 받은 후보 기준이며 햇빛 계산·정렬·범주 선택과 무관하게 그대로다. */
    @Test fun categoryCountsStayFixedAcrossSunCalculationSortAndCategory() = runTest(dispatcher) {
        val mixed = CandidatesFixtures.busOnly.copy(type = TransitRouteType.BUS_AND_SUBWAY)
        val transit = FakeTransit(ApiResult.Success(TransitRoutes(options + mixed + mixed)))
        val vm = CandidatesViewModel(transitRequest, transit, noCar, compute = dispatcher)
        assertTrue(vm.state.value is CandidatesState.Loading)
        runCurrent()
        fun options() = vm.state.value as CandidatesState.Options
        val expected = mapOf(RouteCategory.ALL to 4, RouteCategory.BUS to 1, RouteCategory.SUBWAY to 1, RouteCategory.MIXED to 2)
        assertEquals(expected, options().counts)
        advanceUntilIdle()
        assertTrue(options().items.all { it.chosen.cachedResult != null })
        assertEquals(expected, options().counts)
        vm.setSort(CandidateSort.SUN)
        vm.selectCategory(RouteCategory.SUBWAY)
        assertEquals(expected, options().counts)
        assertEquals(1, transit.calls)
    }
}
