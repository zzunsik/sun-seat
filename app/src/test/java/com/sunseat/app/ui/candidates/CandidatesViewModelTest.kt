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
}
