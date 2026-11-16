package com.sunseat.app.ui.result

import com.sunseat.app.domain.Side
import com.sunseat.app.domain.SunGoal
import com.sunseat.app.domain.TripAdvice
import com.sunseat.app.ui.result.ResultFixtures.advisor
import com.sunseat.app.ui.result.ResultFixtures.chosen
import com.sunseat.app.ui.result.ResultFixtures.departure
import com.sunseat.app.ui.result.ResultFixtures.transitRequest
import com.sunseat.app.ui.result.ResultFixtures.transitRoute
import com.sunseat.app.ui.result.ResultFixtures.zone
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import java.time.LocalDateTime

@OptIn(ExperimentalCoroutinesApi::class)
class ResultViewModelTest {
    private val dispatcher = StandardTestDispatcher()

    @Before fun setUp() = Dispatchers.setMain(dispatcher)
    @After fun tearDown() = Dispatchers.resetMain()

    private fun vm(request: com.sunseat.app.domain.TripRequest = transitRequest) =
        ResultViewModel(request, chosen(transitRoute), advisor(90.0, 30.0), now = { departure }, zone = zone, compute = dispatcher)

    @Test
    fun preferenceChangesRecomputeTheExistingResult() = runTest(dispatcher) {
        val settings = kotlinx.coroutines.flow.MutableStateFlow(com.sunseat.app.data.settings.Settings.DEFAULT)
        val route = com.sunseat.app.domain.Route(listOf(transitRoute.legs.first()))
        val vm = ResultViewModel(transitRequest, chosen(route), advisor(90.0, 20.0), now = { departure }, zone = zone, compute = dispatcher, settings = settings)
        advanceUntilIdle()
        assertEquals(Side.LEFT, (vm.state.value.result!!.advice as TripAdvice.Sit).side)
        settings.value = settings.value.copy(benchPreference = com.sunseat.app.domain.BenchPreference.AVOID_FRONT)
        advanceUntilIdle()
        assertEquals(Side.RIGHT, (vm.state.value.result!!.advice as TripAdvice.Sit).side)
    }

    @Test
    fun computesWithNowAndPreselectsRecommendedSide() = runTest(dispatcher) {
        val vm = vm()
        assertNull(vm.state.value.result)
        advanceUntilIdle()
        val r = vm.state.value.result!!
        assertEquals(departure, r.departure)
        assertEquals(Side.LEFT, (r.advice as TripAdvice.Sit).side)
        assertEquals(departure, vm.state.value.departure)
    }

    @Test
    fun goalChangeRecomputesImmediately() = runTest(dispatcher) {
        val vm = vm()
        advanceUntilIdle()
        vm.setGoal(SunGoal.SEEK)
        advanceUntilIdle()
        val r = vm.state.value.result!!
        assertEquals(SunGoal.SEEK, r.goal)
        assertEquals(Side.RIGHT, (r.advice as TripAdvice.Sit).side)
    }

    @Test
    fun usesRequestedDepartureTime() = runTest(dispatcher) {
        val at = LocalDateTime.of(2026, 11, 16, 15, 30)
        val vm = vm(transitRequest.copy(departure = at))
        advanceUntilIdle()
        assertEquals(at.atZone(zone).toInstant(), vm.state.value.result!!.departure)
    }

    @Test
    fun arrivalModeUsesTotalRouteDuration() = runTest(dispatcher) {
        val at = LocalDateTime.of(2026, 11, 17, 9, 0)
        val duration = java.time.Duration.ofMinutes(90)
        val vm = ResultViewModel(transitRequest.copy(departure = at, timeMode = com.sunseat.app.domain.TimeMode.ARRIVE),
            com.sunseat.app.ui.candidates.ChosenRoute(transitRoute, duration), advisor(90.0, 30.0), now = { departure }, zone = zone, compute = dispatcher)
        advanceUntilIdle()
        assertEquals(at.atZone(zone).toInstant() - duration, vm.state.value.result!!.departure)
        assertEquals(at.atZone(zone).toInstant(), vm.state.value.result!!.arrival)
        assertEquals(vm.state.value.result!!.departure + transitRoute.duration, vm.state.value.result!!.modeledArrival)
    }

    @Test
    fun sameGoalDoesNotRecompute() = runTest(dispatcher) {
        val vm = vm()
        advanceUntilIdle()
        val before = vm.state.value.result
        vm.setGoal(SunGoal.AVOID)
        advanceUntilIdle()
        assertEquals(before, vm.state.value.result)
    }
}
