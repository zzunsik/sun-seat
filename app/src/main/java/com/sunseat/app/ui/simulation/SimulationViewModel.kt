package com.sunseat.app.ui.simulation

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import com.sunseat.app.domain.TripTimeline
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow


data class SimulationUiState(val elapsedMillis: Long = 0, val playing: Boolean = false, val speed: Int = 1, val following: Boolean = true)

class SimulationViewModel(val timeline: TripTimeline, private val saved: SavedStateHandle = SavedStateHandle()) : ViewModel() {
    private val duration = timeline.duration.toMillis().coerceAtLeast(0)
    private val current = MutableStateFlow(SimulationUiState(
        elapsedMillis = (saved.get<Long>("elapsed") ?: 0).coerceIn(0, duration),
        playing = saved.get<Boolean>("playing") ?: false,
        speed = (saved.get<Int>("speed") ?: 1).takeIf { it in listOf(1, 2, 4) } ?: 1,
        following = saved.get<Boolean>("following") ?: true,
    ))
    val state = current.asStateFlow()
    fun seek(fraction: Float) { publish(current.value.copy(elapsedMillis = (duration * fraction.coerceIn(0f, 1f)).toLong(), playing = false)) }
    fun toggle() {
        if (duration == 0L) return
        val state = current.value
        publish(state.copy(playing = !state.playing, elapsedMillis = if (state.elapsedMillis >= duration) 0 else state.elapsedMillis))
    }
    fun pause() { publish(current.value.copy(playing = false)) }
    fun setSpeed(speed: Int) { require(speed in listOf(1, 2, 4)); publish(current.value.copy(speed = speed)) }
    fun follow(value: Boolean) { publish(current.value.copy(following = value)) }
    /** One real second represents one modeled minute at 1x. */
    fun advance(realMillis: Long) {
        val state = current.value
        if (!state.playing) return
        val elapsed = (state.elapsedMillis + realMillis.coerceAtLeast(0) * 60 * state.speed).coerceAtMost(duration)
        publish(state.copy(elapsedMillis = elapsed, playing = elapsed < duration))
    }
    private fun publish(state: SimulationUiState) {
        current.value = state
        saved["elapsed"] = state.elapsedMillis; saved["playing"] = state.playing
        saved["speed"] = state.speed; saved["following"] = state.following
    }
}
