package com.sunseat.app.ui.result

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.sunseat.app.domain.SunGoal
import com.sunseat.app.domain.TripAdvisor
import com.sunseat.app.domain.TripRequest
import com.sunseat.app.domain.TripResult
import com.sunseat.app.domain.departureInstant
import com.sunseat.app.ui.candidates.ChosenRoute
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.Instant
import java.time.ZoneId

data class ResultUiState(
    val goal: SunGoal,
    /** 출발 시각(계산 전에도 부제에 쓴다) */
    val departure: Instant,
    val result: TripResult? = null,
)

/**
 * 결과 계산. 출발 시각이 없으면 "지금". 피하기/받기를 바꾸면 바로 다시 계산한다.
 */
class ResultViewModel(
    request: TripRequest,
    private val chosen: ChosenRoute,
    private val advisor: TripAdvisor = TripAdvisor(),
    now: () -> Instant = { Instant.now() },
    zone: ZoneId = ZoneId.systemDefault(),
    private val compute: CoroutineDispatcher = Dispatchers.Default,
    settings: kotlinx.coroutines.flow.Flow<com.sunseat.app.data.settings.Settings> = kotlinx.coroutines.flow.flowOf(com.sunseat.app.data.settings.Settings.DEFAULT),
) : ViewModel() {

    private val departure: Instant = request.departureInstant(chosen.totalDuration, now(), zone)
    private val _state = MutableStateFlow(ResultUiState(goal = request.goal, departure = departure))
    val state: StateFlow<ResultUiState> = _state.asStateFlow()
    private var job: Job? = null

    private var benchPreference = com.sunseat.app.domain.BenchPreference.AVOID_BACK
    init {
        recompute()
        viewModelScope.launch {
            settings.collect { defaults ->
                if (benchPreference != defaults.benchPreference) {
                    benchPreference = defaults.benchPreference
                    recompute()
                }
            }
        }
    }

    fun setGoal(goal: SunGoal) {
        if (goal == _state.value.goal) return
        _state.update { it.copy(goal = goal) }
        recompute()
    }

    private fun recompute() {
        job?.cancel()
        val goal = _state.value.goal
        val preference = benchPreference
        job = viewModelScope.launch {
            val result = withContext(compute) { advisor.advise(chosen.route, departure, goal, benchPreference = preference, totalDuration = chosen.totalDuration) }
            _state.update { it.copy(result = result) }
        }
    }
}
