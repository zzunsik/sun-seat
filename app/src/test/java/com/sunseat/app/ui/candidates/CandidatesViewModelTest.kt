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

    @Test
    fun transitOptionsMapToCards() = runTest(dispatcher) {
        val vm = CandidatesViewModel(transitRequest, FakeTransit(ApiResult.Success(TransitRoutes(options))), noCar, now = { java.time.LocalDateTime.of(2026, 11, 15, 9, 0) })
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
    fun noTransitRouteSuggestsCar() = runTest(dispatcher) {
        val vm = CandidatesViewModel(transitRequest, FakeTransit(ApiResult.Success(TransitRoutes(emptyList(), NoRouteReason.NO_RESULTS))), noCar)
        advanceUntilIdle()
        val s = vm.state.value as CandidatesState.NoRoute
        assertEquals(R.string.candidates_no_transit, s.titleRes)
        assertEquals(R.string.candidates_no_transit_detail, s.detailRes)
        assertTrue(s.suggestCar)
    }

    @Test
    fun samePointsDoesNotSuggestCar() = runTest(dispatcher) {
        val vm = CandidatesViewModel(transitRequest, FakeTransit(ApiResult.Success(TransitRoutes(emptyList(), NoRouteReason.SAME_POINTS))), noCar)
        advanceUntilIdle()
        assertFalse((vm.state.value as CandidatesState.NoRoute).suggestCar)
    }

    @Test
    fun networkErrorCanRetryAndRetryReloads() = runTest(dispatcher) {
        val transit = FakeTransit(ApiResult.Failure(ApiError.NETWORK))
        val vm = CandidatesViewModel(transitRequest, transit, noCar)
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
    fun departureSetHidesArrival() = runTest(dispatcher) {
        val req = transitRequest.copy(departure = java.time.LocalDateTime.of(2026, 11, 16, 9, 0))
        val vm = CandidatesViewModel(req, FakeTransit(ApiResult.Success(TransitRoutes(options))), noCar)
        advanceUntilIdle()
        assertEquals(null, (vm.state.value as CandidatesState.Options).items[0].arrival)
    }

    @Test
    fun missingKeyCannotRetry() = runTest(dispatcher) {
        val vm = CandidatesViewModel(transitRequest, FakeTransit(ApiResult.Failure(ApiError.MISSING_KEY)), noCar)
        advanceUntilIdle()
        assertFalse((vm.state.value as CandidatesState.Error).canRetry)
    }

    @Test
    fun carRouteIsAutoSelected() = runTest(dispatcher) {
        val car = FakeCar(ApiResult.Success(CarRouteResult(CarRouteOption(carRoute, 9000, 0, 12000))))
        val vm = CandidatesViewModel(carRequest, noTransit, car)
        advanceUntilIdle()
        val chosen = vm.autoSelected.first()
        assertEquals(carRoute, chosen.route)
        assertEquals(Duration.ofMinutes(23), chosen.totalDuration)
        assertEquals(0, noTransit.calls)
    }

    @Test
    fun carNoRouteShowsReasonWithoutCarSuggestion() = runTest(dispatcher) {
        val vm = CandidatesViewModel(carRequest, noTransit, FakeCar(ApiResult.Success(CarRouteResult(null, CarNoRouteReason.NO_START_ROAD))))
        advanceUntilIdle()
        val s = vm.state.value as CandidatesState.NoRoute
        assertEquals(R.string.candidates_car_no_start_road, s.titleRes)
        assertFalse(s.suggestCar)
    }
}
