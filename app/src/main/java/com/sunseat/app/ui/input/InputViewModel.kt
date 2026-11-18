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
enum class LocationPurpose { ORIGIN, MAP }

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
    @param:StringRes val hintRes: Int? = null,
)

data class InputUiState(
    val origin: Endpoint? = null,
    val destination: Endpoint? = null,
    val mode: TravelMode = TravelMode.TRANSIT,
    /** null이면 "지금" */
    val departure: LocalDateTime? = null,
    val timeMode: com.sunseat.app.domain.TimeMode = com.sunseat.app.domain.TimeMode.DEPART,
    val goal: SunGoal = SunGoal.AVOID,
    val search: SearchState? = null,
    val locating: Boolean = false,
    val mapLocating: Boolean = false,
    val mapFocus: com.sunseat.app.ui.map.MapFocus? = null,
    @param:StringRes val locationMessageRes: Int? = null,
    val locationAction: LocationAction? = null,
    val recent: List<com.sunseat.app.data.recent.RecentPlace> = emptyList(),
    @param:StringRes val recentMessageRes: Int? = null,
    val favorites: List<com.sunseat.app.data.favorites.Favorite> = emptyList(),
    @param:StringRes val favoriteMessageRes: Int? = null,
) {
    val sameEndpoints: Boolean get() = origin != null && destination != null && origin.place.location == destination.place.location
    val canSubmit: Boolean get() = origin != null && destination != null && !sameEndpoints && !locating

    /** 버튼은 늘 누를 수 있다. 비어 있으면 그 칸의 검색을 연다. 같은 장소·위치 찾는 중일 때만 막는다. */
    val submitEnabled: Boolean get() = !sameEndpoints && !locating
}

@StringRes
internal fun ApiError.messageRes(): Int = when (this) {
    ApiError.MISSING_KEY -> R.string.search_error_unavailable
    ApiError.UNAUTHORIZED -> R.string.search_error_unavailable
    ApiError.QUOTA_EXCEEDED -> R.string.search_error_quota
    ApiError.NETWORK -> R.string.search_error_network
    ApiError.UNKNOWN -> R.string.search_error_unknown
}

/**
 * 입력 화면 상태. 검색어는 [SEARCH_DEBOUNCE_MILLIS] 동안 입력이 멈추면 검색한다.
 * 검색 결과 목록은 저장하지 않는다. 선택 장소 저장은 즐겨찾기 저장소가 맡는다(docs/requirements.md 4.1).
 */
@OptIn(FlowPreview::class)
class InputViewModel(
    private val placeSearch: PlaceSearchApi,
    private val locationProvider: LocationProvider,
    private val currentLocationName: String,
    settings: kotlinx.coroutines.flow.Flow<com.sunseat.app.data.settings.Settings> = kotlinx.coroutines.flow.flowOf(com.sunseat.app.data.settings.Settings.DEFAULT),
    private val recentRepository: com.sunseat.app.data.recent.RecentPlacesRepository? = null,
    private val favoriteRepository: com.sunseat.app.data.favorites.FavoritesRepository? = null,
) : ViewModel() {

    private val _state = MutableStateFlow(InputUiState())
    val state: StateFlow<InputUiState> = _state.asStateFlow()

    private var goalChosen = false
    private var modeChosen = false
    init {
        recentRepository?.let { repository ->
            viewModelScope.launch { repository.places.collect { saved -> _state.update { it.copy(recent = saved) } } }
        }
        favoriteRepository?.let { repository ->
            viewModelScope.launch { repository.favorites.collect { saved -> _state.update { it.copy(favorites = saved) } } }
        }
        viewModelScope.launch {
            settings.collect { defaults ->
                _state.update { it.copy(goal = if (goalChosen) it.goal else defaults.goal, mode = if (modeChosen) it.mode else defaults.mode) }
            }
        }
    }
    private var lastAutomaticRequest: TripRequest? = null
    fun automaticRequest(): TripRequest? {
        if (_state.value.search != null) return null
        val request = buildRequest() ?: return null
        if (request == lastAutomaticRequest) return null
        lastAutomaticRequest = request
        return request
    }

    fun setSchedule(value: LocalDateTime?, mode: com.sunseat.app.domain.TimeMode) {
        _state.update { it.copy(departure = value, timeMode = if (value == null) com.sunseat.app.domain.TimeMode.DEPART else mode) }
    }

    private var searchJob: Job? = null
    private var locationJob: Job? = null
    private var locationPurpose = LocationPurpose.ORIGIN
    private var mapFocusSequence = 0L

    fun prepareLocation(purpose: LocationPurpose) { locationPurpose = purpose }

    fun locateInitialOrigin() {
        if (locationPurpose == LocationPurpose.ORIGIN && _state.value.origin == null && !_state.value.locating) fetchCurrentLocation()
    }

    fun retryLocation() = fetchCurrentLocation(locationPurpose)

    fun openSearch(field: EndpointField) {
        _state.update { it.copy(search = SearchState(field), favoriteMessageRes = null) }
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

    fun chooseRecent(recent: com.sunseat.app.data.recent.RecentPlace) {
        if (_state.value.search == null) return
        setMode(recent.mode)
        choosePlace(recent.asPlace())
    }
    fun removeRecent(id: String) = mutateRecent { it.remove(id) }
    fun clearRecent() = mutateRecent { it.clear() }
    private fun mutateRecent(operation: suspend (com.sunseat.app.data.recent.RecentPlacesRepository) -> Unit) {
        val repository = recentRepository ?: return
        viewModelScope.launch {
            try { operation(repository); _state.update { it.copy(recentMessageRes = null) } }
            catch (_: java.io.IOException) { _state.update { it.copy(recentMessageRes = R.string.recent_save_error) } }
        }
    }

    fun chooseFavorite(favorite: com.sunseat.app.data.favorites.Favorite) = choosePlace(favorite.asPlace())
    fun toggleFavorite(place: Place) {
        val repository = favoriteRepository ?: return
        viewModelScope.launch {
            try {
                repository.toggle(place)
                _state.update { it.copy(favoriteMessageRes = null) }
            } catch (_: com.sunseat.app.data.favorites.FavoriteLimitException) {
                _state.update { it.copy(favoriteMessageRes = R.string.favorites_limit) }
            } catch (_: java.io.IOException) {
                _state.update { it.copy(favoriteMessageRes = R.string.favorites_save_error) }
            }
        }
    }

    fun choosePlace(place: Place) {
        val field = _state.value.search?.field ?: return
        if (field == EndpointField.ORIGIN) locationJob?.cancel()
        val endpoint = Endpoint(NamedPlace(place.name, place.location))
        _state.update {
            when (field) {
                EndpointField.ORIGIN -> it.copy(origin = endpoint, locating = false, mapLocating = false, locationMessageRes = null, locationAction = null)
                EndpointField.DESTINATION -> it.copy(destination = endpoint)
            }.copy(search = null)
        }
        searchJob?.cancel()
        val mode = _state.value.mode
        mutateRecent { it.remember(place, mode) }
    }

    /** 위치 권한 요청 결과. 허용이면 바로 현재 위치를 찾는다. */
    fun onLocationPermission(status: LocationPermissionStatus) {
        if (status.isGranted) {
            if (status == LocationPermissionStatus.ApproximateOnly) {
                _state.update { it.copy(locationMessageRes = status.messageRes(), locationAction = null) }
            }
            fetchCurrentLocation(locationPurpose)
        } else {
            _state.update { it.copy(locationMessageRes = status.messageRes(), locationAction = status.action(), search = if (locationPurpose == LocationPurpose.ORIGIN) null else it.search) }
        }
    }

    /** 권한이 이미 있을 때 현재 위치를 출발지로 정한다. */
    fun fetchCurrentLocation(purpose: LocationPurpose = LocationPurpose.ORIGIN) {
        locationPurpose = purpose
        locationJob?.cancel()
        _state.update { it.copy(locating = purpose == LocationPurpose.ORIGIN, mapLocating = purpose == LocationPurpose.MAP,
            search = if (purpose == LocationPurpose.ORIGIN) null else it.search) }
        locationJob = viewModelScope.launch {
            val result = locationProvider.currentLocation()
            val focus = if (purpose == LocationPurpose.MAP && result is LocationResult.Success)
                com.sunseat.app.ui.map.MapFocus(com.sunseat.app.domain.LatLng(result.latitude, result.longitude), ++mapFocusSequence) else null
            _state.update {
                when (result) {
                    is LocationResult.Success -> it.copy(
                        origin = if (purpose == LocationPurpose.ORIGIN) Endpoint(NamedPlace(currentLocationName, com.sunseat.app.domain.LatLng(result.latitude, result.longitude)), isCurrentLocation = true) else it.origin,
                        mapFocus = focus ?: it.mapFocus,
                        locating = false, mapLocating = false,
                        locationMessageRes = if (result.isApproximate) R.string.location_approximate_only else null,
                        locationAction = null,
                    )
                    else -> it.copy(locating = false, mapLocating = false, locationMessageRes = result.messageRes(), locationAction = result.action())
                }
            }
        }
    }

    fun dismissLocationMessage() {
        _state.update { it.copy(locationMessageRes = null, locationAction = null) }
    }

    fun swap() {
        locationJob?.cancel()
        _state.update { it.copy(origin = it.destination, destination = it.origin, locating = false, mapLocating = false) }
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

    /** 입력이 다 되면 요청을 만든다. 직접 고른 시각은 과거여도 그대로 계산한다. */
    fun buildRequest(): TripRequest? {
        val s = _state.value
        if (!s.canSubmit) return null
        val departure = s.departure
        return TripRequest(s.origin!!.place, s.destination!!.place, s.mode, departure, s.goal, s.timeMode)
    }

    companion object {
        const val SEARCH_DEBOUNCE_MILLIS = 300L
    }
}
