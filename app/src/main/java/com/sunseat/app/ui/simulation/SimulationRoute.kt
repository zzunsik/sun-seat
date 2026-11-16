package com.sunseat.app.ui.simulation

import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.createSavedStateHandle
import androidx.lifecycle.repeatOnLifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.sunseat.app.domain.*
import com.sunseat.app.ui.theme.Motion
import com.sunseat.app.ui.theme.SunSeatTheme
import kotlinx.coroutines.isActive

@Composable
fun SimulationRoute(
    request: TripRequest, route: Route, result: TripResult, onBack: () -> Unit,
    modifier: Modifier = Modifier,
    timeline: TripTimeline = remember(route, result) { TripTimeline(route, result) },
    viewModel: SimulationViewModel = viewModel(key = result.departure.toString() + route.hashCode() + result.goal,
        factory = viewModelFactory { initializer { SimulationViewModel(timeline, createSavedStateHandle()) } }),
) {
    val vm = viewModel
    val state by vm.state.collectAsStateWithLifecycle()
    val owner = LocalLifecycleOwner.current
    val reduce = SunSeatTheme.reduceMotion
    LaunchedEffect(state.playing, state.speed, reduce, owner) {
        if (reduce) vm.pause()
        else if (state.playing) owner.lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
            var previous = withFrameMillis { it }
            while (isActive) {
                val frame = withFrameMillis { it }
                val elapsed = frame - previous
                if (elapsed >= Motion.SIMULATION_FRAME_MS) { vm.advance(elapsed); previous = frame }
            }
        }
    }
    val point = remember(state.elapsedMillis, timeline) { timeline.at(result.departure.plusMillis(state.elapsedMillis)) }
    SimulationScreen(request, route, result, state, point, timeline.boundaries,
        SimulationActions(onBack, vm::seek, vm::toggle, vm::setSpeed, { vm.follow(false) }, { vm.follow(true) }), modifier)
}
