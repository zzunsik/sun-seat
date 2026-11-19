package com.sunseat.app.ui.simulation

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import com.sunseat.app.domain.CabinKind
import com.sunseat.app.domain.SeatCarry
import com.sunseat.app.domain.TripTimeline
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow


data class SimulationUiState(val elapsedMillis: Long = 0, val playing: Boolean = false, val speed: Int = 1, val following: Boolean = true,
    val seats: Map<Int,String> = emptyMap(), val interior: Boolean = false,
    /** Bus model the user picked per leg (#76). Absent = the leg's own estimate; never copied to another leg. */
    val models: Map<Int,CabinKind> = emptyMap(),
    /** Result of the last model switch, shown until the user picks a seat. Not restored after process death. */
    val notice: ModelNotice? = null)

data class ModelNotice(val leg: Int, val carry: SeatCarry)

class SimulationViewModel(val timeline: TripTimeline, private val saved: SavedStateHandle = SavedStateHandle()) : ViewModel() {
    private val duration = timeline.duration.toMillis().coerceAtLeast(0)
    private val current = MutableStateFlow(SimulationUiState(
        elapsedMillis = (saved.get<Long>("elapsed") ?: 0).coerceIn(0, duration),
        playing = saved.get<Boolean>("playing") ?: false,
        speed = (saved.get<Int>("speed") ?: 1).takeIf { it in listOf(1, 2, 4) } ?: 1,
        following = saved.get<Boolean>("following") ?: true,
        seats = (saved.get<HashMap<String,String>>("seats") ?: hashMapOf()).mapKeys { it.key.toInt() },
        interior = saved.get<Boolean>("interior") ?: false,
        models = (saved.get<HashMap<String,String>>("models") ?: hashMapOf()).mapNotNull { (k,v) ->
            val leg=k.toIntOrNull(); val kind=CabinKind.entries.firstOrNull { it.name==v }
            if(leg!=null && kind!=null) leg to kind else null }.toMap(),
    ))
    val state = current.asStateFlow()
    /** Start a new journey once. Saved pauses and completed journeys survive recreation. */
    fun enter(reduceMotion: Boolean) {
        if(reduceMotion) { saved["entered"]=true; pause(); return }
        if(saved.get<Boolean>("entered")!=true) {
            saved["entered"]=true
            publish(current.value.copy(playing=duration>0 && current.value.elapsedMillis<duration,following=true))
        }
    }
    fun seek(fraction: Float) { publish(current.value.copy(elapsedMillis = (duration * fraction.coerceIn(0f, 1f)).toLong(), playing = false)) }
    fun toggle() {
        if (duration == 0L) return
        val state = current.value
        publish(state.copy(playing = !state.playing, elapsedMillis = if (state.elapsedMillis >= duration) 0 else state.elapsedMillis))
    }
    fun pause() { publish(current.value.copy(playing = false)) }
    fun setSpeed(speed: Int) { require(speed in listOf(1, 2, 4)); publish(current.value.copy(speed = speed)) }
    fun follow(value: Boolean) { publish(current.value.copy(following = value)) }
    fun seat(leg: Int,id: String) { publish(current.value.copy(seats=current.value.seats+(leg to id),notice=current.value.notice?.takeIf { it.leg!=leg })) }
    /** Switch one leg's bus model; the seat follows only to an equivalent seat ([carry]), otherwise the choice is cleared. */
    fun busModel(leg: Int,kind: CabinKind,carry: SeatCarry) {
        val state=current.value
        publish(state.copy(models=state.models+(leg to kind),
            seats=carry.seatId?.let { state.seats+(leg to it) } ?: (state.seats-leg),
            notice=carry.from?.let { ModelNotice(leg,carry) }))
    }
    fun camera(inside: Boolean) { publish(current.value.copy(interior=inside)) }
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
        saved["seats"] = HashMap(state.seats.mapKeys { it.key.toString() }); saved["interior"] = state.interior
        saved["models"] = HashMap(state.models.entries.associate { it.key.toString() to it.value.name })
    }
}
