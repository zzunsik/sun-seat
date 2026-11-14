package com.sunseat.app.ui.candidates

import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.sunseat.app.R
import com.sunseat.app.data.network.ApiError
import com.sunseat.app.data.network.ApiResult
import com.sunseat.app.data.route.CarNoRouteReason
import com.sunseat.app.data.route.CarRouteApi
import com.sunseat.app.data.route.NoRouteReason
import com.sunseat.app.data.route.TransitRouteApi
import com.sunseat.app.data.route.TransitRouteOption
import com.sunseat.app.domain.Mode
import com.sunseat.app.domain.Route
import com.sunseat.app.domain.TravelMode
import com.sunseat.app.domain.TripRequest
import com.sunseat.app.ui.input.messageRes
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.launch
import java.time.Duration
import java.time.LocalDateTime

/** 결과 화면으로 넘기는 선택한 경로. */
data class ChosenRoute(val route: Route, val totalDuration: Duration)

/** 후보 카드의 구간 표시 하나. [lineName]은 지하철 노선 색을 고를 때 쓴다. */
data class LegChip(val mode: Mode, val label: String, val lineName: String = "")

/** 경로 후보 카드 하나. */
data class CandidateUi(
    val id: Int,
    val totalDuration: Duration,
    /** 지금 출발 기준 도착 예상 시각. 출발 시각을 정했으면 null(경로가 지금 기준이라 맞지 않음) */
    val arrival: LocalDateTime?,
    val transfers: Int,
    /** 환승 도보 합(분). 출발지→첫 정류장, 마지막 정류장→도착지 도보는 응답에 없어 빠진다 */
    val walkMinutes: Long,
    val fare: Int?,
    val fastest: Boolean,
    val chips: List<LegChip>,
    val boardAt: String,
    val alightAt: String,
    val chosen: ChosenRoute,
)

sealed interface CandidatesState {
    data class Loading(val mode: TravelMode) : CandidatesState
    data class Options(val items: List<CandidateUi>) : CandidatesState

    /** 경로 없음. [suggestCar]면 "자가용 기준으로 좌석 보기"를 제안한다. */
    data class NoRoute(
        @param:StringRes val titleRes: Int,
        @param:StringRes val detailRes: Int?,
        val suggestCar: Boolean,
    ) : CandidatesState

    data class Error(
        @param:StringRes val messageRes: Int,
        @param:DrawableRes val iconRes: Int,
        val canRetry: Boolean,
    ) : CandidatesState
}

internal fun TransitRouteOption.toUi(id: Int, now: LocalDateTime?, fastest: Boolean): CandidateUi {
    val chips = route.legs.mapIndexed { i, leg ->
        val name = legDetails.getOrNull(i)?.vehicles?.firstOrNull()?.name.orEmpty()
        when (leg.mode) {
            Mode.SUBWAY -> LegChip(leg.mode, name.ifBlank { "지하철" }, lineName = name)
            Mode.BUS -> LegChip(leg.mode, if (name.isBlank()) "버스" else "버스 $name")
            Mode.WALK -> LegChip(leg.mode, "도보 ${leg.duration.toMinutes().coerceAtLeast(1)}분")
            Mode.CAR -> LegChip(leg.mode, "자가용")
        }
    }
    val rides = route.legs.filter { it.mode != Mode.WALK }
    return CandidateUi(
        id = id,
        totalDuration = totalDuration,
        arrival = now?.plus(totalDuration),
        transfers = transfers,
        walkMinutes = route.legs.filter { it.mode == Mode.WALK }.sumOf { it.duration.toMinutes() },
        fare = fare,
        fastest = fastest,
        chips = chips,
        boardAt = rides.firstOrNull()?.startName.orEmpty(),
        alightAt = rides.lastOrNull()?.endName.orEmpty(),
        chosen = ChosenRoute(route, totalDuration),
    )
}

internal fun List<TransitRouteOption>.toUi(now: LocalDateTime?): List<CandidateUi> {
    val best = minOfOrNull { it.totalDuration }
    return mapIndexed { i, o -> o.toUi(i, now, fastest = size > 1 && o.totalDuration == best) }
}

/**
 * 경로 후보를 찾는다. 대중교통은 목록을, 자가용은 경로 하나를 찾자마자 [autoSelected]로 결과 화면에 넘긴다.
 */
class CandidatesViewModel(
    private val request: TripRequest,
    private val transit: TransitRouteApi,
    private val car: CarRouteApi,
    private val now: () -> LocalDateTime = { LocalDateTime.now() },
) : ViewModel() {

    private val _state = MutableStateFlow<CandidatesState>(CandidatesState.Loading(request.mode))
    val state: StateFlow<CandidatesState> = _state.asStateFlow()

    private val _autoSelected = Channel<ChosenRoute>(Channel.BUFFERED)
    val autoSelected = _autoSelected.receiveAsFlow()

    init { load() }

    fun load() {
        _state.value = CandidatesState.Loading(request.mode)
        viewModelScope.launch {
            val origin = request.origin.location
            val destination = request.destination.location
            _state.value = when (request.mode) {
                TravelMode.TRANSIT -> when (val r = transit.search(origin, destination)) {
                    is ApiResult.Success -> when {
                        r.value.options.isNotEmpty() ->
                            CandidatesState.Options(r.value.options.toUi(if (request.departure == null) now() else null))
                        else -> r.value.noRouteReason.toNoRoute()
                    }
                    is ApiResult.Failure -> r.error.toState()
                }
                TravelMode.CAR -> when (val r = car.search(origin, destination)) {
                    is ApiResult.Success -> {
                        val option = r.value.option
                        if (option != null) {
                            _autoSelected.send(ChosenRoute(option.route, option.duration))
                            CandidatesState.Loading(TravelMode.CAR)
                        } else {
                            CandidatesState.NoRoute(r.value.noRouteReason.messageRes(), null, suggestCar = false)
                        }
                    }
                    is ApiResult.Failure -> r.error.toState()
                }
            }
        }
    }

    private fun ApiError.toState() = CandidatesState.Error(
        messageRes = messageRes(),
        iconRes = if (this == ApiError.NETWORK) R.drawable.ic_ms_wifi_off else R.drawable.ic_ms_error,
        canRetry = this != ApiError.MISSING_KEY && this != ApiError.UNAUTHORIZED,
    )
}

internal fun NoRouteReason?.toNoRoute(): CandidatesState.NoRoute = when (this) {
    NoRouteReason.NO_START_STOP -> CandidatesState.NoRoute(R.string.candidates_no_start_stop, R.string.candidates_try_car_detail, suggestCar = true)
    NoRouteReason.NO_END_STOP -> CandidatesState.NoRoute(R.string.candidates_no_end_stop, R.string.candidates_try_car_detail, suggestCar = true)
    NoRouteReason.SAME_POINTS -> CandidatesState.NoRoute(R.string.candidates_same_points, null, suggestCar = false)
    NoRouteReason.NO_RESULTS, null -> CandidatesState.NoRoute(R.string.candidates_no_transit, R.string.candidates_no_transit_detail, suggestCar = true)
}

@StringRes
internal fun CarNoRouteReason?.messageRes(): Int = when (this) {
    CarNoRouteReason.NO_START_ROAD -> R.string.candidates_car_no_start_road
    CarNoRouteReason.NO_END_ROAD -> R.string.candidates_car_no_end_road
    CarNoRouteReason.SAME_POINTS -> R.string.candidates_same_points
    CarNoRouteReason.NO_RESULTS, null -> R.string.candidates_car_no_route
}
