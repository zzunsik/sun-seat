package com.sunseat.app.ui.simulation

import androidx.lifecycle.SavedStateHandle
import com.sunseat.app.domain.*
import java.time.Duration
import java.time.Instant
import org.junit.Assert.*
import org.junit.Test

class SimulationViewModelTest {
    @Test fun entryStartsNewJourneyButPreservesRestoredPauseAndReducedMotion() {
        val saved=SavedStateHandle()
        val first=SimulationViewModel(timeline(),saved)
        first.enter(false);assertTrue(first.state.value.playing)
        first.advance(1000);assertEquals(60_000L,first.state.value.elapsedMillis)
        first.pause()
        val restored=SimulationViewModel(timeline(),SavedStateHandle(saved.keys().associateWith { saved.get<Any>(it) }))
        restored.enter(false);assertFalse(restored.state.value.playing)
        assertEquals(60_000L,restored.state.value.elapsedMillis)
        val reduced=SimulationViewModel(timeline());reduced.enter(true);assertFalse(reduced.state.value.playing)
    }
    private fun timeline(): TripTimeline {
        val route = Route(listOf(Leg(Mode.BUS, listOf(LatLng(37.5, 127.0), LatLng(37.6, 127.0)), Duration.ofMinutes(5), "A", "B")))
        val source = SunPositionSource { _, _, _ -> SunPosition(90.0, 30.0) }
        return TripTimeline(route, TripAdvisor(ExposureAnalyzer(source)).advise(route, Instant.EPOCH, SunGoal.AVOID), source)
    }
    @Test fun cameraAndSeatChangesKeepPlaybackTimeAndFollowing() {
        val vm = SimulationViewModel(timeline())
        vm.enter(false); vm.setSpeed(2); vm.advance(1000); vm.follow(false)
        val before = vm.state.value
        vm.camera(true); vm.seat(0, "RIGHT-2-WINDOW"); vm.camera(false)
        val after = vm.state.value
        assertEquals(before.copy(seats = mapOf(0 to "RIGHT-2-WINDOW")), after)
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

    private fun zeroTimeline(): TripTimeline {
        val route = Route(listOf(Leg(Mode.BUS, listOf(LatLng(37.5, 127.0), LatLng(37.6, 127.0)), Duration.ZERO, "A", "B")))
        val source = SunPositionSource { _, _, _ -> SunPosition(90.0, 30.0) }
        return TripTimeline(route, TripAdvisor(ExposureAnalyzer(source)).advise(route, Instant.EPOCH, SunGoal.AVOID), source)
    }

    @Test fun reachingTheEndStopsAtTheLastFrameAndReplayRestarts() {
        val vm = SimulationViewModel(timeline())
        vm.toggle()
        vm.advance(10_000)
        assertEquals(SimulationUiState(elapsedMillis = 300_000, playing = false), vm.state.value)
        vm.advance(1000)
        assertEquals(300_000L, vm.state.value.elapsedMillis)
        vm.toggle()
        assertEquals(0L, vm.state.value.elapsedMillis)
        assertTrue(vm.state.value.playing)
    }

    @Test fun pauseResumeKeepsPositionAndSeekStopsPlayback() {
        val vm = SimulationViewModel(timeline())
        vm.toggle(); vm.advance(1000)
        vm.toggle()
        assertFalse(vm.state.value.playing)
        assertEquals(60_000L, vm.state.value.elapsedMillis)
        vm.toggle()
        assertTrue(vm.state.value.playing)
        assertEquals(60_000L, vm.state.value.elapsedMillis)
        vm.seek(-1f); assertEquals(0L, vm.state.value.elapsedMillis); assertFalse(vm.state.value.playing)
        vm.seek(2f); assertEquals(300_000L, vm.state.value.elapsedMillis)
        vm.advance(-500); assertEquals(300_000L, vm.state.value.elapsedMillis)
    }

    @Test fun speedAndFollowSurviveOtherTransitions() {
        val vm = SimulationViewModel(timeline())
        vm.setSpeed(4); vm.follow(false)
        vm.toggle(); vm.advance(500); vm.pause(); vm.seek(0.2f)
        assertEquals(4, vm.state.value.speed)
        assertFalse(vm.state.value.following)
        vm.follow(true)
        assertTrue(vm.state.value.following)
        assertThrows(IllegalArgumentException::class.java) { vm.setSpeed(3) }
        assertEquals(4, vm.state.value.speed)
    }

    @Test fun zeroLengthTimelineNeverPlays() {
        val vm = SimulationViewModel(zeroTimeline())
        vm.toggle()
        assertFalse(vm.state.value.playing)
        vm.seek(0.5f)
        assertEquals(0L, vm.state.value.elapsedMillis)
    }

    @Test fun corruptSavedStateIsClamped() {
        val saved = SavedStateHandle(mapOf("elapsed" to 9_999_999L, "speed" to 3, "playing" to true))
        val state = SimulationViewModel(timeline(), saved).state.value
        assertEquals(300_000L, state.elapsedMillis)
        assertEquals(1, state.speed)
        assertTrue(state.playing)
    }
}
