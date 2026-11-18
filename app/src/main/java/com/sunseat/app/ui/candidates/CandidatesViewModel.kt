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
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.flow.update
import com.sunseat.app.domain.TripResult
import com.sunseat.app.domain.TripAdvisor
import com.sunseat.app.domain.SunGoal
import com.sunseat.app.domain.BenchPreference
import com.sunseat.app.domain.departureInstant
import com.sunseat.app.data.route.TransitRouteType
import java.time.Instant
import java.time.ZoneId
import java.time.Duration
import java.time.LocalDateTime

/** 결과 화면으로 넘기는 선택한 경로. */
data class ChosenRoute(val route: Route, val totalDuration: Duration, val cachedResult: TripResult? = null, val preference: BenchPreference = BenchPreference.AVOID_BACK)

/** 후보 카드의 구간 표시 하나. [lineName]은 지하철 노선 색을 고를 때 쓴다. */
data class LegChip(val mode: Mode, val lineName: String = "", val walkMinutes: Long = 0)

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
    val type: TransitRouteType = TransitRouteType.OTHER,
)

sealed interface CandidatesState {
    data class Loading(val mode: TravelMode) : CandidatesState
    data class Options(
        val items: List<CandidateUi>, val sort: CandidateSort = CandidateSort.RECOMMENDED,
        val filters: Set<TransitRouteType> = emptySet(), val selectedId: Int? = null,
        val goal: SunGoal = SunGoal.AVOID,
    ) : CandidatesState {
        val visible: List<CandidateUi> get() = orderCandidates(items, sort, filters, goal)
    }

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
        LegChip(leg.mode, lineName = name, walkMinutes = leg.duration.toMinutes().coerceAtLeast(1))
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
        type = type,
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
    private var request: TripRequest,
    private val transit: TransitRouteApi,
    private val car: CarRouteApi,
    private val now: () -> LocalDateTime = { LocalDateTime.now() },
    private val compute: CoroutineDispatcher = Dispatchers.Default,
    private val zone: ZoneId = ZoneId.systemDefault(),
    private val preference: BenchPreference = BenchPreference.AVOID_BACK,
    private val calculate: suspend (Route, Instant, SunGoal, BenchPreference, Duration) -> TripResult = { route, departure, goal, bench, total ->
        TripAdvisor().advise(route, departure, goal, bench, total, request.mode)
    },
) : ViewModel() {

    private val _state = MutableStateFlow<CandidatesState>(CandidatesState.Loading(request.mode))
    val state: StateFlow<CandidatesState> = _state.asStateFlow()

    private val _autoSelected = Channel<ChosenRoute>(Channel.BUFFERED)
    val autoSelected = _autoSelected.receiveAsFlow()

    private var loadJob: Job? = null
    private var sunJob: Job? = null
    private var generation = 0
    private var revision = 0L
    init { load() }

    /** Schedule changes reuse network routes, but discard calculations for the old departure. */
    fun updateRequest(value: TripRequest, revision: Long = 0) {
        if (value == request && revision == this.revision) return
        val routeChanged = value.origin != request.origin || value.destination != request.destination || value.mode != request.mode
        request = value
        this.revision = revision
        if (routeChanged) { load(); return }
        val options = _state.value as? CandidatesState.Options ?: return
        sunJob?.cancel()
        val version = ++generation
        val base = now().atZone(zone).toInstant()
        val refreshed = options.copy(goal = value.goal, items = options.items.map { item ->
            item.copy(arrival = (value.departureInstant(item.totalDuration, base, zone) + item.totalDuration).atZone(zone).toLocalDateTime(),
                chosen = item.chosen.copy(cachedResult = null))
        })
        _state.value = refreshed
        calculateSun(refreshed, base, version)
    }
    fun setSort(sort: CandidateSort) { _state.update { if (it is CandidatesState.Options) it.copy(sort = sort) else it } }
    fun toggleFilter(type: TransitRouteType) { _state.update {
        if (it is CandidatesState.Options) it.copy(filters = if (type in it.filters) it.filters - type else it.filters + type, selectedId = null) else it
    } }
    fun clearFilters() { _state.update { if (it is CandidatesState.Options) it.copy(filters = emptySet()) else it } }
    fun highlight(id: Int) { _state.update { if (it is CandidatesState.Options) it.copy(selectedId = id) else it } }

    fun load() {
        loadJob?.cancel()
        sunJob?.cancel()
        val version = ++generation
        _state.value = CandidatesState.Loading(request.mode)
        loadJob = viewModelScope.launch {
            val sampledNow = now()
            val base = sampledNow.atZone(zone).toInstant()
            val origin = request.origin.location
            val destination = request.destination.location
            val loaded = when (request.mode) {
                TravelMode.TRANSIT -> when (val r = transit.search(origin, destination)) {
                    is ApiResult.Success -> when {
                        r.value.options.isNotEmpty() ->
                            CandidatesState.Options(r.value.options.toUi(sampledNow).map { item ->
                                item.copy(arrival = (request.departureInstant(item.totalDuration, base, zone) + item.totalDuration).atZone(zone).toLocalDateTime())
                            }, goal = request.goal)
                        else -> r.value.noRouteReason.toNoRoute()
                    }
                    is ApiResult.Failure -> r.error.toState()
                }
                TravelMode.CAR, TravelMode.TAXI -> when (val r = car.search(origin, destination)) {
                    is ApiResult.Success -> {
                        val option = r.value.option
                        if (option != null) {
                            currentCoroutineContext().ensureActive()
                            if (version != generation) return@launch
                            if (request.mode == TravelMode.TAXI) CandidatesState.Options(listOf(CandidateUi(
                                id = 0, totalDuration = option.duration, arrival = (request.departureInstant(option.duration, base, zone) + option.duration).atZone(zone).toLocalDateTime(),
                                transfers = 0, walkMinutes = 0, fare = option.taxiFare.takeIf { it > 0 }, fastest = false,
                                chips = listOf(LegChip(Mode.CAR)), boardAt = request.origin.name, alightAt = request.destination.name,
                                chosen = ChosenRoute(option.route, option.duration),
                            )), goal = request.goal) else {
                                _autoSelected.send(ChosenRoute(option.route, option.duration))
                                CandidatesState.Loading(TravelMode.CAR)
                            }
                        } else {
                            CandidatesState.NoRoute(r.value.noRouteReason.messageRes(), null, suggestCar = false)
                        }
                    }
                    is ApiResult.Failure -> r.error.toState()
                }
            }
            currentCoroutineContext().ensureActive()
            if (version != generation) return@launch
            _state.value = loaded
            val options = loaded as? CandidatesState.Options ?: return@launch
            calculateSun(options, base, version)
        }
    }

    private fun calculateSun(options: CandidatesState.Options, base: Instant, version: Int) {
        val activeRequest = request
        sunJob = viewModelScope.launch {
            coroutineScope {
                options.items.map { item -> async(compute) {
                    val result = calculate(item.chosen.route, activeRequest.departureInstant(item.totalDuration, base, zone), activeRequest.goal, preference, item.totalDuration)
                    currentCoroutineContext().ensureActive()
                    if (version == generation) _state.update { current ->
                        if (current is CandidatesState.Options) current.copy(items = current.items.map {
                            if (it.id == item.id) it.copy(chosen = it.chosen.copy(cachedResult = result, preference = preference)) else it
                        }) else current
                    }
                } }.awaitAll()
            }
        }
    }

    private fun ApiError.toState() = CandidatesState.Error(
        messageRes = when (this) {
            ApiError.MISSING_KEY, ApiError.UNAUTHORIZED -> R.string.candidates_error_unavailable
            ApiError.QUOTA_EXCEEDED -> R.string.candidates_error_quota
            ApiError.NETWORK -> R.string.search_error_network
            ApiError.UNKNOWN -> R.string.candidates_error_unknown
        },
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


enum class CandidateSort { RECOMMENDED, TIME, TRANSFERS, WALK, SUN }

/** Filters combine with OR; pending sun calculations sort last in both goals. */
fun orderCandidates(items: List<CandidateUi>, sort: CandidateSort, filters: Set<TransitRouteType>, goal: SunGoal): List<CandidateUi> {
    val visible = items.filter { filters.isEmpty() || it.type in filters }
    return when (sort) {
        CandidateSort.RECOMMENDED -> visible
        CandidateSort.TIME -> visible.sortedBy { it.totalDuration }
        CandidateSort.TRANSFERS -> visible.sortedBy { it.transfers }
        CandidateSort.WALK -> visible.sortedBy { it.walkMinutes }
        CandidateSort.SUN -> visible.sortedWith(compareBy<CandidateUi> { !it.comparableSun() }.thenComparator { a, b ->
            val av = a.chosen.cachedResult?.sunIfFollowed ?: Duration.ZERO
            val bv = b.chosen.cachedResult?.sunIfFollowed ?: Duration.ZERO
            if (goal == SunGoal.AVOID) av.compareTo(bv) else bv.compareTo(av)
        })
    }
}

private fun CandidateUi.comparableSun(): Boolean {
    val result = chosen.cachedResult ?: return false
    return !(result.sunIfFollowed + result.shadeIfFollowed).isZero && result.legs.none { it.advice == com.sunseat.app.domain.LegAdvice.UnknownGround }
}
