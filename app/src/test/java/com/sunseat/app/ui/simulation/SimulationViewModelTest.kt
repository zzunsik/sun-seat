package com.sunseat.app.ui.simulation

import androidx.lifecycle.SavedStateHandle
import com.sunseat.app.domain.*
import java.time.Duration
import java.time.Instant
import org.junit.Assert.*
import org.junit.Test

class SimulationViewModelTest {
    private fun timeline(): TripTimeline {
        val route = Route(listOf(Leg(Mode.BUS, listOf(LatLng(37.5, 127.0), LatLng(37.6, 127.0)), Duration.ofMinutes(5), "A", "B")))
        val source = SunPositionSource { _, _, _ -> SunPosition(90.0, 30.0) }
        return TripTimeline(route, TripAdvisor(ExposureAnalyzer(source)).advise(route, Instant.EPOCH, SunGoal.AVOID), source)
    }
    @Test fun playSpeedEndRestartAndScrub() {
        val vm = SimulationViewModel(timeline())
        vm.advance(1000); assertEquals(0L, vm.state.value.elapsedMillis)
        vm.toggle(); vm.setSpeed(2); vm.advance(1000)
        assertEquals(120_000L, vm.state.value.elapsedMillis)
        vm.pause(); vm.advance(1000); assertEquals(120_000L, vm.state.value.elapsedMillis)
        vm.toggle(); vm.setSpeed(4); vm.advance(1000)
        assertEquals(300_000L, vm.state.value.elapsedMillis); assertFalse(vm.state.value.playing)
        vm.toggle(); assertEquals(0L, vm.state.value.elapsedMillis)
        vm.seek(0.5f); assertEquals(150_000L, vm.state.value.elapsedMillis); assertFalse(vm.state.value.playing)
    }
    @Test fun savedStateRestoresPositionSpeedAndCameraMode() {
        val saved = SavedStateHandle()
        val timeline = timeline()
        val first = SimulationViewModel(timeline, saved)
        first.seek(0.5f); first.setSpeed(4); first.follow(false)
        first.seat(0,"LEFT-2-WINDOW"); first.seat(1,"rear-RIGHT"); first.camera(true)
        val copied = SavedStateHandle(saved.keys().associateWith { saved.get<Any>(it) })
        assertEquals(first.state.value, SimulationViewModel(timeline, copied).state.value)
        assertEquals("LEFT-2-WINDOW", first.state.value.seats[0])
        assertEquals("rear-RIGHT", first.state.value.seats[1])
    }
}
