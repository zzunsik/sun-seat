package com.sunseat.app.ui.input

import androidx.annotation.StringRes
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.sunseat.app.R
import com.sunseat.app.data.network.ApiError
import com.sunseat.app.data.network.ApiResult
import com.sunseat.app.data.place.Place
import com.sunseat.app.data.place.PlaceSearchApi
import com.sunseat.app.domain.NamedPlace
import com.sunseat.app.domain.SunGoal
import com.sunseat.app.domain.TravelMode
import com.sunseat.app.domain.TripRequest
import com.sunseat.app.location.LocationAction
import com.sunseat.app.location.LocationPermissionStatus
import com.sunseat.app.location.LocationProvider
import com.sunseat.app.location.LocationResult
import com.sunseat.app.location.action
import com.sunseat.app.location.messageRes
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.LocalDateTime

enum class EndpointField { ORIGIN, DESTINATION }

/** 출발지·도착지 하나. [isCurrentLocation]이면 이름 대신 "현재 위치"로 보여 준다. */
data class Endpoint(val place: NamedPlace, val isCurrentLocation: Boolean = false)

/** 장소 검색 중 상태. 화면 위를 덮는 검색 영역에 쓴다. */
data class SearchState(
    val field: EndpointField,
    val query: String = "",
    val results: List<Place> = emptyList(),
    val loading: Boolean = false,
    @param:StringRes val messageRes: Int? = null,
    /** 오류일 때 다시 시도 버튼을 보여 준다 */
    val canRetry: Boolean = false,
)

data class InputUiState(
    val origin: Endpoint? = null,
    val destination: Endpoint? = null,
    val mode: TravelMode = TravelMode.TRANSIT,
    /** null이면 "지금" */
    val departure: LocalDateTime? = null,
    val goal: SunGoal = SunGoal.AVOID,
    val search: SearchState? = null,
    val locating: Boolean = false,
    @param:StringRes val locationMessageRes: Int? = null,
    val locationAction: LocationAction? = null,
) {
    val sameEndpoints: Boolean get() = origin != null && destination != null && origin.place.location == destination.place.location
    val canSubmit: Boolean get() = origin != null && destination != null && !sameEndpoints && !locating

    /** 버튼은 늘 누를 수 있다. 비어 있으면 그 칸의 검색을 연다. 같은 장소·위치 찾는 중일 때만 막는다. */
    val submitEnabled: Boolean get() = !sameEndpoints && !locating
}

@StringRes
internal fun ApiError.messageRes(): Int = when (this) {
    ApiError.MISSING_KEY -> R.string.search_error_missing_key
    ApiError.UNAUTHORIZED -> R.string.search_error_unauthorized
    ApiError.QUOTA_EXCEEDED -> R.string.search_error_quota
    ApiError.NETWORK -> R.string.search_error_network
    ApiError.UNKNOWN -> R.string.search_error_unknown
}

/**
 * 입력 화면 상태. 검색어는 [SEARCH_DEBOUNCE_MILLIS] 동안 입력이 멈추면 검색한다.
 * 장소 검색 결과는 저장하지 않는다(docs/requirements.md 4.1).
 */
@OptIn(FlowPreview::class)
class InputViewModel(
    private val placeSearch: PlaceSearchApi,
    private val locationProvider: LocationProvider,
    private val currentLocationName: String,
    private val now: () -> LocalDateTime = { LocalDateTime.now() },
    settings: kotlinx.coroutines.flow.Flow<com.sunseat.app.data.settings.Settings> = kotlinx.coroutines.flow.flowOf(com.sunseat.app.data.settings.Settings.DEFAULT),
) : ViewModel() {

    private val _state = MutableStateFlow(InputUiState())
    val state: StateFlow<InputUiState> = _state.asStateFlow()

    private var goalChosen = false
    private var modeChosen = false
    init {
        viewModelScope.launch {
            settings.collect { defaults ->
                _state.update { it.copy(goal = if (goalChosen) it.goal else defaults.goal, mode = if (modeChosen) it.mode else defaults.mode) }
            }
        }
    }
    private var searchJob: Job? = null
    private var locationJob: Job? = null

    fun openSearch(field: EndpointField) {
        _state.update { it.copy(search = SearchState(field)) }
    }

    fun closeSearch() {
        searchJob?.cancel()
        _state.update { it.copy(search = null) }
    }

    fun onQueryChange(query: String) {
        val search = _state.value.search ?: return
        _state.update { it.copy(search = search.copy(query = query, messageRes = null, canRetry = false, loading = query.isNotBlank())) }
        runSearch(query, debounce = true)
    }

    /** 키보드 검색 키·다시 시도: 기다리지 않고 바로 검색한다. */
    fun searchNow() {
        val query = _state.value.search?.query ?: return
        _state.update { s -> s.copy(search = s.search?.copy(messageRes = null, canRetry = false, loading = query.isNotBlank())) }
        runSearch(query, debounce = false)
    }

    private fun runSearch(query: String, debounce: Boolean) {
        searchJob?.cancel()
        if (query.isBlank()) {
            _state.update { s -> s.copy(search = s.search?.copy(results = emptyList(), loading = false)) }
            return
        }
        searchJob = viewModelScope.launch {
            if (debounce) delay(SEARCH_DEBOUNCE_MILLIS)
            val near = _state.value.origin?.place?.location
            val result = placeSearch.search(query.trim(), near = near)
            _state.update { s ->
                val current = s.search ?: return@update s
                if (current.query != query) return@update s
                when (result) {
                    is ApiResult.Success -> current.copy(
                        results = result.value,
                        loading = false,
                        messageRes = if (result.value.isEmpty()) R.string.search_no_results else null,
                    )
                    is ApiResult.Failure -> current.copy(
                        results = emptyList(),
                        loading = false,
                        messageRes = result.error.messageRes(),
                        canRetry = result.error != ApiError.MISSING_KEY && result.error != ApiError.UNAUTHORIZED,
                    )
                }.let { s.copy(search = it) }
            }
        }
    }

    fun choosePlace(place: Place) {
        val field = _state.value.search?.field ?: return
        val endpoint = Endpoint(NamedPlace(place.name, place.location))
        _state.update {
            when (field) {
                EndpointField.ORIGIN -> it.copy(origin = endpoint, locationMessageRes = null, locationAction = null)
                EndpointField.DESTINATION -> it.copy(destination = endpoint)
            }.copy(search = null)
        }
        searchJob?.cancel()
    }

    /** 위치 권한 요청 결과. 허용이면 바로 현재 위치를 찾는다. */
    fun onLocationPermission(status: LocationPermissionStatus) {
        if (status.isGranted) {
            if (status == LocationPermissionStatus.ApproximateOnly) {
                _state.update { it.copy(locationMessageRes = status.messageRes(), locationAction = null) }
            }
            fetchCurrentLocation()
        } else {
            _state.update { it.copy(locationMessageRes = status.messageRes(), locationAction = status.action(), search = null) }
        }
    }

    /** 권한이 이미 있을 때 현재 위치를 출발지로 정한다. */
    fun fetchCurrentLocation() {
        locationJob?.cancel()
        _state.update { it.copy(locating = true, search = null) }
        locationJob = viewModelScope.launch {
            val result = locationProvider.currentLocation()
            _state.update {
                when (result) {
                    is LocationResult.Success -> it.copy(
                        origin = Endpoint(NamedPlace(currentLocationName, com.sunseat.app.domain.LatLng(result.latitude, result.longitude)), isCurrentLocation = true),
                        locating = false,
                        locationMessageRes = if (result.isApproximate) R.string.location_approximate_only else null,
                        locationAction = null,
                    )
                    else -> it.copy(locating = false, locationMessageRes = result.messageRes(), locationAction = result.action())
                }
            }
        }
    }

    fun dismissLocationMessage() {
        _state.update { it.copy(locationMessageRes = null, locationAction = null) }
    }

    fun swap() {
        _state.update { it.copy(origin = it.destination, destination = it.origin) }
    }

    fun setMode(mode: TravelMode) { modeChosen = true; _state.update { it.copy(mode = mode) } }

    fun setDeparture(departure: LocalDateTime?) = _state.update { it.copy(departure = departure) }

    fun setGoal(goal: SunGoal) { goalChosen = true; _state.update { it.copy(goal = goal) } }

    /** 좌석 찾기: 입력이 다 되면 요청을, 빈 칸이 있으면 그 칸의 검색을 연다. */
    fun submit(): TripRequest? {
        val s = _state.value
        when {
            s.origin == null -> openSearch(EndpointField.ORIGIN)
            s.destination == null -> openSearch(EndpointField.DESTINATION)
            else -> return buildRequest()
        }
        return null
    }

    /** 입력이 다 되면 요청을 만든다. 지난 시각은 "지금"으로 바꾼다. */
    fun buildRequest(): TripRequest? {
        val s = _state.value
        if (!s.canSubmit) return null
        val departure = s.departure?.takeIf { it.isAfter(now()) }
        return TripRequest(s.origin!!.place, s.destination!!.place, s.mode, departure, s.goal)
    }

    companion object {
        const val SEARCH_DEBOUNCE_MILLIS = 300L
    }
}
