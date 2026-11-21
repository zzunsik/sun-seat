package com.sunseat.app.ui.input

import androidx.annotation.StringRes
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.sunseat.app.R
import com.sunseat.app.data.network.ApiError
import com.sunseat.app.data.network.ApiResult
import com.sunseat.app.data.network.retryable
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

/** 지도 가운데 좌표의 주소 조회 상태(#85). */
enum class PickAddressStatus { LOADING, FOUND, NONE, FAILED }

/**
 * 지도에서 위치 선택(#85). 장소 검색 위에 열리고 [field]를 바꾼다. 확정 전에는 여정을 바꾸지 않는다.
 * [center]는 마지막으로 멈춘 지도 가운데(핀) 좌표, [camera]는 지도를 옮기라는 요청(진입·현재 위치)이다.
 */
data class MapPickState(
    val field: EndpointField,
    val center: com.sunseat.app.domain.LatLng,
    val camera: com.sunseat.app.ui.map.MapFocus,
    /** 지도를 움직이는 중. 멈춰야 핀과 확정 좌표가 같으므로 그동안 확정하지 않는다. */
    val moving: Boolean = false,
    val status: PickAddressStatus = PickAddressStatus.LOADING,
    val address: String? = null,
    val canRetry: Boolean = false,
    @param:StringRes val messageRes: Int? = null,
    /** 지도 인증·불러오기 실패. 핀이 의미 없으므로 확정하지 않고 검색으로 돌아가게 한다. */
    val mapUnavailable: Boolean = false,
) {
    val canConfirm: Boolean get() = !moving && !mapUnavailable
}

/** 지도 선택 확정 결과. [Chosen.named]가 false면 주소를 몰라 정직한 이름("지도에서 선택한 위치")을 쓴 것이다. */
sealed interface PickResult {
    data object Rejected : PickResult
    data object Unchanged : PickResult
    data class Chosen(val place: Place, val named: Boolean) : PickResult
}

data class InputUiState(
    val origin: Endpoint? = null,
    val destination: Endpoint? = null,
    val mode: TravelMode = TravelMode.TRANSIT,
    /** null이면 "지금" */
    val departure: LocalDateTime? = null,
    val goal: SunGoal = SunGoal.AVOID,
    val search: SearchState? = null,
    /** 검색 위에 열린 지도 선택. 검색 상태([search])는 그대로 두어 취소하면 검색으로 돌아간다. */
    val pick: MapPickState? = null,
    val locating: Boolean = false,
    val originLocationRevision: Long = 0,
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
    // 앱 API 게이트웨이·카카오의 거부는 서버 쪽에서 고칠 수 있으므로 "잠시 뒤 다시"로 안내한다(#189).
    ApiError.UNAUTHORIZED -> R.string.search_error_quota
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
    private val reverseGeocode: com.sunseat.app.data.place.ReverseGeocodeApi? = null,
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
    /** Downstream confirmation is already handled; returning to input must not submit it again. */
    fun acceptRequest(request: TripRequest) {
        lastAutomaticRequest = request
        modeChosen = true
        goalChosen = true
        _state.update { state -> state.copy(
            origin = state.origin?.takeIf { it.place == request.origin } ?: Endpoint(request.origin),
            destination = state.destination?.takeIf { it.place == request.destination } ?: Endpoint(request.destination),
            mode = request.mode, departure = request.departure, goal = request.goal,
        ) }
    }
    fun automaticRequest(): TripRequest? {
        if (_state.value.search != null) return null
        val request = buildRequest() ?: return null
        if (request == lastAutomaticRequest) return null
        lastAutomaticRequest = request
        return request
    }

    fun setSchedule(value: LocalDateTime?) {
        _state.update { it.copy(departure = value) }
    }

    private var searchJob: Job? = null
    private var searchGeneration = 0L
    private var runningQuery: String? = null
    private var locationJob: Job? = null
    private var locationGeneration = 0L
    private var locationPurpose = LocationPurpose.ORIGIN
    private var pendingLocationPermission = false
    private var mapFocusSequence = 0L

    fun prepareLocation(purpose: LocationPurpose) { locationPurpose = purpose; pendingLocationPermission = true }

    fun onLocationPermissionAvailable() {
        if (pendingLocationPermission) retryLocation() else locateInitialOrigin()
    }

    fun locateInitialOrigin() {
        if (locationPurpose == LocationPurpose.ORIGIN && _state.value.origin == null && !_state.value.locating) fetchCurrentLocation()
    }

    fun retryLocation() = fetchCurrentLocation(locationPurpose)

    fun openSearch(field: EndpointField) {
        searchGeneration++
        searchJob?.cancel()
        runningQuery = null
        val endpoint = if (field == EndpointField.ORIGIN) _state.value.origin else _state.value.destination
        dropPick()
        _state.update { it.copy(search = SearchState(field, query = endpoint?.takeUnless { it.isCurrentLocation }?.place?.name.orEmpty()), pick = null, favoriteMessageRes = null) }
    }

    fun closeSearch() {
        searchGeneration++
        runningQuery = null
        searchJob?.cancel()
        dropPick()
        _state.update { it.copy(search = null, pick = null) }
    }

    fun onQueryChange(query: String) {
        val search = _state.value.search ?: return
        if (search.query == query) return
        _state.update { it.copy(search = search.copy(results = emptyList(), query = query, messageRes = null, canRetry = false, loading = query.isNotBlank())) }
        runSearch(query, debounce = true)
    }

    /** 키보드 검색 키·다시 시도: 기다리지 않고 바로 검색한다. */
    fun searchNow() {
        val query = _state.value.search?.query ?: return
        if (runningQuery == query && searchJob?.isActive == true) return
        _state.update { s -> s.copy(search = s.search?.copy(messageRes = null, canRetry = false, loading = query.isNotBlank())) }
        runSearch(query, debounce = false)
    }

    private fun runSearch(query: String, debounce: Boolean) {
        val generation = ++searchGeneration
        runningQuery = null
        searchJob?.cancel()
        if (query.isBlank()) {
            _state.update { s -> s.copy(search = s.search?.copy(results = emptyList(), loading = false)) }
            return
        }
        searchJob = viewModelScope.launch {
            if (debounce) delay(SEARCH_DEBOUNCE_MILLIS)
            runningQuery = query
            val near = _state.value.origin?.place?.location
            val result = placeSearch.search(query.trim(), near = near)
            _state.update { s ->
                val current = s.search ?: return@update s
                if (generation != searchGeneration || current.query != query) return@update s
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
                        canRetry = result.error.retryable,
                    )
                }.let { s.copy(search = it) }
            }
        }
    }

    fun cancelEndpointEdit(request: TripRequest) {
        locationGeneration++
        locationJob?.cancel()
        pendingLocationPermission = false
        closeSearch()
        _state.update { it.copy(locating = false, mapLocating = false, locationMessageRes = null, locationAction = null) }
        acceptRequest(request)
    }

    fun rejectSameEndpoint() {
        _state.update { it.copy(search = it.search?.copy(messageRes = R.string.input_same_endpoints, canRetry = false)) }
    }

    /** Candidate editing commits only a valid endpoint and keeps every other trip condition. */
    fun chooseEditedPlace(place: Place, remember: Boolean = true): TripRequest? {
        val field = _state.value.search?.field ?: return null
        val other = if (field == EndpointField.ORIGIN) _state.value.destination else _state.value.origin
        if (other?.place?.location == place.location) {
            _state.update { it.copy(search = it.search?.copy(messageRes = R.string.input_same_endpoints, canRetry = false)) }
            return null
        }
        choosePlace(place, remember)
        return buildRequest()?.also(::acceptRequest)
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

    fun choosePlace(place: Place, remember: Boolean = true) {
        val field = _state.value.search?.field ?: return
        if (field == EndpointField.ORIGIN) { locationGeneration++; locationJob?.cancel(); pendingLocationPermission = false }
        val endpoint = Endpoint(NamedPlace(place.name, place.location))
        _state.update {
            when (field) {
                EndpointField.ORIGIN -> it.copy(origin = endpoint, locating = false, mapLocating = false, locationMessageRes = null, locationAction = null)
                EndpointField.DESTINATION -> it.copy(destination = endpoint)
            }.copy(search = null, pick = null)
        }
        searchGeneration++
        runningQuery = null
        searchJob?.cancel()
        val mode = _state.value.mode
        if (remember) mutateRecent { it.remember(place, mode) }
    }

    /** 위치 권한 요청 결과. 허용이면 바로 현재 위치를 찾는다. */
    fun onLocationPermission(status: LocationPermissionStatus, keepSearch: Boolean = false) {
        if (status.isGranted) {
            if (status == LocationPermissionStatus.ApproximateOnly) {
                _state.update { it.copy(locationMessageRes = status.messageRes(), locationAction = null) }
            }
            fetchCurrentLocation(locationPurpose, keepSearch)
        } else {
            pendingLocationPermission = true
            _state.update { it.copy(locationMessageRes = status.messageRes(), locationAction = status.action(), search = if (locationPurpose == LocationPurpose.ORIGIN && !keepSearch) null else it.search) }
        }
    }

    /** 권한이 이미 있을 때 현재 위치를 출발지로 정한다. */
    fun fetchCurrentLocation(purpose: LocationPurpose = LocationPurpose.ORIGIN, keepSearch: Boolean = false) {
        val generation = ++locationGeneration
        locationPurpose = purpose
        pendingLocationPermission = false
        locationJob?.cancel()
        _state.update { it.copy(locating = purpose == LocationPurpose.ORIGIN, mapLocating = purpose == LocationPurpose.MAP,
            search = if (purpose == LocationPurpose.ORIGIN && !keepSearch) null else it.search) }
        locationJob = viewModelScope.launch {
            val result = locationProvider.currentLocation()
            val focus = if (purpose == LocationPurpose.MAP && result is LocationResult.Success)
                com.sunseat.app.ui.map.MapFocus(com.sunseat.app.domain.LatLng(result.latitude, result.longitude), ++mapFocusSequence) else null
            _state.update {
                if (generation != locationGeneration) return@update it
                when (result) {
                    is LocationResult.Success -> it.copy(
                        originLocationRevision = if (purpose == LocationPurpose.ORIGIN) it.originLocationRevision + 1 else it.originLocationRevision,
                        origin = if (purpose == LocationPurpose.ORIGIN) Endpoint(NamedPlace(currentLocationName, com.sunseat.app.domain.LatLng(result.latitude, result.longitude)), isCurrentLocation = true) else it.origin,
                        mapFocus = focus ?: it.mapFocus,
                        // In the map picker the current location only recentres the pin (#68, #85).
                        pick = if (focus != null) it.pick?.copy(center = focus.location, camera = focus, moving = false, status = PickAddressStatus.LOADING, address = null, canRetry = false, messageRes = null) else it.pick,
                        locating = false, mapLocating = false,
                        locationMessageRes = if (result.isApproximate) R.string.location_approximate_only else null,
                        locationAction = null,
                    )
                    else -> it.copy(locating = false, mapLocating = false, locationMessageRes = result.messageRes(), locationAction = result.action())
                }
            }
            if (focus != null && _state.value.pick?.camera == focus) resolveAddress(focus.location, debounce = false)
        }
    }

    fun dismissLocationMessage() {
        _state.update { it.copy(locationMessageRes = null, locationAction = null) }
    }

    fun swap() {
        locationGeneration++
        locationJob?.cancel()
        pendingLocationPermission = false
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
        return TripRequest(s.origin!!.place, s.destination!!.place, s.mode, departure, s.goal)
    }

    // --- 지도에서 위치 선택(#85) ---
    private var addressJob: Job? = null
    private var addressGeneration = 0L

    private fun endpointOf(field: EndpointField, s: InputUiState = _state.value) = if (field == EndpointField.ORIGIN) s.origin else s.destination
    private fun otherOf(field: EndpointField, s: InputUiState = _state.value) = if (field == EndpointField.ORIGIN) s.destination else s.origin

    /** 검색 중인 칸의 지도 선택을 연다. 위치 권한을 요구하지 않고 기존 끝점 → 반대 끝점 → 지도 초점 → 기본 위치에서 시작한다. */
    fun openMapPick() {
        val s = _state.value
        val field = s.search?.field ?: return
        val start = endpointOf(field, s)?.place?.location ?: otherOf(field, s)?.place?.location ?: s.mapFocus?.location ?: DEFAULT_PICK_CENTER
        _state.update { it.copy(pick = MapPickState(field, start, com.sunseat.app.ui.map.MapFocus(start, ++mapFocusSequence)), favoriteMessageRes = null) }
        resolveAddress(start, debounce = false)
    }

    /** 사용자가 지도를 움직이기 시작했다. 멈출 때까지 확정하지 않고 이전 주소를 보여 주지 않는다. */
    fun onPickCameraMoving() {
        val pick = _state.value.pick ?: return
        if (pick.moving) return
        addressGeneration++
        addressJob?.cancel()
        _state.update { it.copy(pick = it.pick?.copy(moving = true, status = PickAddressStatus.LOADING, address = null, canRetry = false, messageRes = null)) }
    }

    /** 지도가 멈췄다. 같은 좌표(SDK 반올림 차이 포함)면 다시 조회하지 않고, 아니면 잠시 기다려 마지막 좌표만 조회한다. */
    fun onPickCameraIdle(center: com.sunseat.app.domain.LatLng) {
        val pick = _state.value.pick ?: return
        if (sameSpot(pick.center, center)) {
            if (pick.moving) {
                _state.update { it.copy(pick = it.pick?.copy(moving = false)) }
                resolveAddress(pick.center, debounce = false)
            }
            return
        }
        _state.update { it.copy(pick = it.pick?.copy(center = center, moving = false, status = PickAddressStatus.LOADING, address = null, canRetry = false, messageRes = null)) }
        resolveAddress(center, debounce = true)
    }

    fun retryPickAddress() {
        val pick = _state.value.pick ?: return
        _state.update { it.copy(pick = it.pick?.copy(status = PickAddressStatus.LOADING, canRetry = false)) }
        resolveAddress(pick.center, debounce = false)
    }

    fun onPickMapUnavailable() {
        _state.update { it.copy(pick = it.pick?.copy(mapUnavailable = true, moving = false)) }
    }

    /** 임시 좌표만 버리고 아래의 검색 상태로 돌아간다. */
    fun cancelMapPick() {
        dropPick()
        _state.update { it.copy(pick = null) }
    }

    /**
     * '선택': 반대 끝점과 같으면 거절하고, 지금 끝점과 같으면 바꾸지 않는다.
     * 그 밖에는 핀 좌표로 장소를 만든다. 주소를 모르면 [unnamedName]을 쓰고 근처 상호를 이름으로 붙이지 않는다.
     * 반환된 장소는 호출한 쪽이 `choosePlace`/`chooseEditedPlace`로 확정한다.
     */
    fun confirmPick(unnamedName: String): PickResult {
        val s = _state.value
        val pick = s.pick ?: return PickResult.Rejected
        if (!pick.canConfirm) return PickResult.Rejected
        if (otherOf(pick.field, s)?.place?.location?.let { sameSpot(it, pick.center) } == true) {
            _state.update { it.copy(pick = it.pick?.copy(messageRes = R.string.input_same_endpoints)) }
            return PickResult.Rejected
        }
        if (endpointOf(pick.field, s)?.place?.location?.let { sameSpot(it, pick.center) } == true) {
            cancelMapPick()
            return PickResult.Unchanged
        }
        val address = pick.address?.takeIf { pick.status == PickAddressStatus.FOUND }
        cancelMapPick()
        val place = Place(
            id = "$MAP_PICK_ID_PREFIX${pick.center.latitude},${pick.center.longitude}",
            name = address ?: unnamedName,
            address = address.orEmpty(),
            roadAddress = "",
            location = pick.center,
            category = "",
        )
        return PickResult.Chosen(place, named = address != null)
    }

    private fun dropPick() {
        addressGeneration++
        addressJob?.cancel()
    }

    private fun resolveAddress(center: com.sunseat.app.domain.LatLng, debounce: Boolean) {
        val generation = ++addressGeneration
        addressJob?.cancel()
        val api = reverseGeocode
        if (api == null) {
            _state.update { it.copy(pick = it.pick?.copy(status = PickAddressStatus.NONE, address = null)) }
            return
        }
        addressJob = viewModelScope.launch {
            if (debounce) delay(SEARCH_DEBOUNCE_MILLIS)
            val result = api.address(center)
            _state.update { s ->
                val pick = s.pick ?: return@update s
                // An answer for an older centre never attaches to the pin's newer coordinate.
                if (generation != addressGeneration || pick.center != center || pick.moving) return@update s
                s.copy(pick = when (result) {
                    is ApiResult.Success -> if (result.value != null) pick.copy(status = PickAddressStatus.FOUND, address = result.value, canRetry = false)
                        else pick.copy(status = PickAddressStatus.NONE, address = null, canRetry = false)
                    is ApiResult.Failure -> pick.copy(status = PickAddressStatus.FAILED, address = null,
                        canRetry = result.error.retryable)
                })
            }
        }
    }

    private fun sameSpot(a: com.sunseat.app.domain.LatLng, b: com.sunseat.app.domain.LatLng) =
        kotlin.math.abs(a.latitude - b.latitude) < SAME_SPOT_DEGREES && kotlin.math.abs(a.longitude - b.longitude) < SAME_SPOT_DEGREES

    companion object {
        const val SEARCH_DEBOUNCE_MILLIS = 300L
        /** 끝점·지도 초점이 모두 없을 때 지도 선택의 시작점(서울시청, 공개 장소). */
        val DEFAULT_PICK_CENTER = com.sunseat.app.domain.LatLng(37.5665, 126.9780)
        /** 지도 SDK가 돌려준 가운데 좌표의 반올림 차이(약 0.1m)는 같은 위치로 본다. */
        private const val SAME_SPOT_DEGREES = 1e-6
        const val MAP_PICK_ID_PREFIX = "map:"
    }
}
