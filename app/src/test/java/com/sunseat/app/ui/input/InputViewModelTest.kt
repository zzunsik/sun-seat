package com.sunseat.app.ui.input

import com.sunseat.app.R
import com.sunseat.app.data.network.ApiError
import com.sunseat.app.data.network.ApiResult
import com.sunseat.app.data.place.Place
import com.sunseat.app.data.place.PlaceSearchApi
import com.sunseat.app.domain.LatLng
import com.sunseat.app.domain.SunGoal
import com.sunseat.app.domain.TravelMode
import com.sunseat.app.location.LocationAction
import com.sunseat.app.location.LocationPermissionStatus
import com.sunseat.app.location.LocationProvider
import com.sunseat.app.location.LocationResult
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.time.LocalDateTime

@OptIn(ExperimentalCoroutinesApi::class)
class InputViewModelTest {
    private val dispatcher = StandardTestDispatcher()

    private class FakePlaces(var result: ApiResult<List<Place>> = ApiResult.Success(emptyList())) : PlaceSearchApi {
        val queries = mutableListOf<String>()
        var lastNear: LatLng? = null
        override suspend fun search(query: String, near: LatLng?, size: Int): ApiResult<List<Place>> {
            queries += query
            lastNear = near
            return result
        }
    }

    private fun place(name: String, lat: Double, lng: Double) =
        Place(id = name, name = name, address = "주소 $name", roadAddress = "", location = LatLng(lat, lng), category = "")

    private val seoul = place("서울역", 37.5547, 126.9707)
    private val busan = place("부산역", 35.1151, 129.0422)
    private val fixedNow = LocalDateTime.of(2026, 11, 15, 8, 0)

    private fun vm(places: PlaceSearchApi = FakePlaces(), location: LocationProvider = LocationProvider { LocationResult.Unavailable }) =
        InputViewModel(places, location, currentLocationName = "현재 위치")

    @Before fun setUp() = Dispatchers.setMain(dispatcher)
    @After fun tearDown() = Dispatchers.resetMain()

    @Test fun editPrefillsWithoutSearchAndCancelPreservesRequest() = runTest(dispatcher) {
        val api = FakePlaces()
        val vm = vm(api)
        vm.openSearch(EndpointField.ORIGIN); vm.choosePlace(seoul)
        vm.openSearch(EndpointField.DESTINATION); vm.choosePlace(busan)
        val request = vm.buildRequest()!!
        vm.acceptRequest(request)
        vm.openSearch(EndpointField.ORIGIN)
        advanceUntilIdle()
        assertEquals(seoul.name, vm.state.value.search!!.query)
        assertTrue(api.queries.isEmpty())
        vm.onQueryChange("")
        vm.cancelEndpointEdit(request)
        assertEquals(request, vm.buildRequest())
        assertNull(vm.automaticRequest())
        vm.openSearch(EndpointField.ORIGIN)
        assertEquals(seoul.name, vm.state.value.search!!.query)
        assertNull(vm.chooseEditedPlace(busan))
        assertEquals(R.string.input_same_endpoints, vm.state.value.search!!.messageRes)
        assertEquals(request, vm.buildRequest())
    }

    @Test fun currentLocationLabelIsNeverAnAutomaticKeyword() = runTest(dispatcher) {
        val api = FakePlaces()
        val vm = vm(api, LocationProvider { LocationResult.Success(37.5, 127.0, 20f, false) })
        vm.fetchCurrentLocation(); advanceUntilIdle()
        vm.openSearch(EndpointField.ORIGIN); advanceUntilIdle()
        assertEquals("", vm.state.value.search!!.query)
        assertTrue(api.queries.isEmpty())
    }

    @Test
    fun immediateSearchDeduplicatesInFlightAndClearInvalidatesResponse() = runTest(dispatcher) {
        var calls = 0
        val api = object : PlaceSearchApi {
            override suspend fun search(query: String, near: LatLng?, size: Int): ApiResult<List<Place>> {
                calls++
                kotlinx.coroutines.withContext(kotlinx.coroutines.NonCancellable) { kotlinx.coroutines.delay(500) }
                return ApiResult.Success(listOf(seoul))
            }
        }
        val vm = vm(api)
        vm.openSearch(EndpointField.ORIGIN)
        vm.onQueryChange("A"); vm.searchNow(); dispatcher.scheduler.runCurrent()
        vm.searchNow(); dispatcher.scheduler.runCurrent()
        assertEquals(1, calls)
        vm.onQueryChange(" ")
        advanceUntilIdle()
        assertTrue(vm.state.value.search!!.results.isEmpty())
        assertFalse(vm.state.value.search!!.loading)
        assertNull(vm.state.value.search!!.messageRes)
    }

    @Test
    fun lateResponseCannotOverwriteSameQueryInReopenedField() = runTest(dispatcher) {
        var calls = 0
        val api = object : PlaceSearchApi {
            override suspend fun search(query: String, near: LatLng?, size: Int): ApiResult<List<Place>> {
                val old = ++calls == 1
                try {
                    kotlinx.coroutines.withContext(kotlinx.coroutines.NonCancellable) {
                        kotlinx.coroutines.delay(if (old) 1000 else 10)
                    }
                } catch (_: kotlinx.coroutines.CancellationException) {
                    // Deliberately return even when the caller has cancelled.
                }
                return ApiResult.Success(listOf(if (old) seoul else busan))
            }
        }
        val vm = vm(api)
        vm.openSearch(EndpointField.ORIGIN)
        vm.onQueryChange("same")
        vm.searchNow()
        dispatcher.scheduler.runCurrent()
        vm.closeSearch()
        vm.openSearch(EndpointField.DESTINATION)
        vm.onQueryChange("same")
        vm.searchNow()
        advanceUntilIdle()
        assertEquals(EndpointField.DESTINATION, vm.state.value.search!!.field)
        assertEquals(listOf(busan), vm.state.value.search!!.results)
    }

    @Test
    fun changedQueryImmediatelyRemovesOldSelectableResults() = runTest(dispatcher) {
        val api = FakePlaces(ApiResult.Success(listOf(seoul)))
        val vm = vm(api)
        vm.openSearch(EndpointField.ORIGIN)
        vm.onQueryChange("A")
        advanceUntilIdle()
        assertEquals(listOf(seoul), vm.state.value.search!!.results)
        vm.onQueryChange("B")
        assertTrue(vm.state.value.search!!.results.isEmpty())
        assertTrue(vm.state.value.search!!.loading)
        api.result = ApiResult.Success(listOf(busan))
        advanceUntilIdle()
        assertEquals(listOf(busan), vm.state.value.search!!.results)
    }

    @Test
    fun defaultsArriveWithoutOverwritingUserChoices() = runTest(dispatcher) {
        val settings = kotlinx.coroutines.flow.MutableStateFlow(com.sunseat.app.data.settings.Settings.DEFAULT)
        val vm = InputViewModel(FakePlaces(), LocationProvider { LocationResult.Unavailable }, currentLocationName = "현재 위치", settings = settings)
        settings.value = settings.value.copy(goal = SunGoal.SEEK, mode = TravelMode.CAR)
        advanceUntilIdle()
        assertEquals(SunGoal.SEEK, vm.state.value.goal)
        assertEquals(TravelMode.CAR, vm.state.value.mode)
        vm.setGoal(SunGoal.AVOID)
        vm.setMode(TravelMode.TRANSIT)
        settings.value = settings.value.copy(theme = com.sunseat.app.data.settings.ThemeMode.DARK)
        advanceUntilIdle()
        assertEquals(SunGoal.AVOID, vm.state.value.goal)
        assertEquals(TravelMode.TRANSIT, vm.state.value.mode)
    }

    @Test
    fun searchIsDebouncedAndOnlyLatestQueryRuns() = runTest(dispatcher) {
        val places = FakePlaces(ApiResult.Success(listOf(seoul)))
        val vm = vm(places)
        vm.openSearch(EndpointField.ORIGIN)
        vm.onQueryChange("서")
        advanceTimeBy(100)
        vm.onQueryChange("서울")
        advanceTimeBy(100)
        vm.onQueryChange("서울역")
        advanceUntilIdle()
        assertEquals(listOf("서울역"), places.queries)
        assertEquals(listOf(seoul), vm.state.value.search!!.results)
        assertFalse(vm.state.value.search!!.loading)
    }

    @Test
    fun recentPlaceAppliesCoordinatesAndModeWithoutSearch() = runTest(dispatcher) {
        val api = FakePlaces()
        val vm = vm(api)
        vm.openSearch(EndpointField.ORIGIN); vm.choosePlace(seoul)
        vm.openSearch(EndpointField.DESTINATION)
        vm.chooseRecent(com.sunseat.app.data.recent.RecentPlace(busan.id, busan.name, busan.address, busan.location.latitude, busan.location.longitude, TravelMode.CAR, 0))
        assertEquals(busan.location, vm.state.value.destination!!.place.location)
        assertEquals(TravelMode.CAR, vm.state.value.mode)
        assertTrue(api.queries.isEmpty())
        assertEquals(busan.location, vm.automaticRequest()!!.destination.location)
    }

    @Test
    fun automaticRequestOnlyEmitsForChangedCompleteInput() = runTest(dispatcher) {
        val vm = vm()
        assertNull(vm.automaticRequest())
        vm.openSearch(EndpointField.ORIGIN); vm.choosePlace(seoul)
        assertNull(vm.automaticRequest())
        vm.openSearch(EndpointField.DESTINATION); vm.choosePlace(busan)
        assertEquals(seoul.location, vm.automaticRequest()!!.origin.location)
        assertNull(vm.automaticRequest())
        vm.openSearch(EndpointField.DESTINATION); vm.choosePlace(busan)
        assertNull(vm.automaticRequest())
        vm.setSchedule(fixedNow.plusDays(1))
        assertEquals(fixedNow.plusDays(1), vm.automaticRequest()!!.departure)
        assertNull(vm.automaticRequest())
        vm.setSchedule(null)
        assertNull(vm.state.value.departure)
    }

    @Test fun acceptedDownstreamRequestDoesNotAutomaticallySubmitOnReturn() = runTest(dispatcher) {
        val vm = vm()
        vm.openSearch(EndpointField.ORIGIN); vm.choosePlace(seoul)
        vm.openSearch(EndpointField.DESTINATION); vm.choosePlace(busan)
        val changed = vm.automaticRequest()!!.copy(mode = TravelMode.TAXI, departure = fixedNow)
        vm.acceptRequest(changed)
        assertEquals(changed, vm.buildRequest())
        assertNull(vm.automaticRequest())
        vm.setSchedule(fixedNow.plusHours(1))
        assertEquals(fixedNow.plusHours(1), vm.automaticRequest()!!.departure)
        assertNull(vm.automaticRequest())
    }

    @Test
    fun blankQueryDoesNotSearch() = runTest(dispatcher) {
        val places = FakePlaces()
        val vm = vm(places)
        vm.openSearch(EndpointField.DESTINATION)
        vm.onQueryChange("   ")
        advanceUntilIdle()
        assertTrue(places.queries.isEmpty())
        assertNull(vm.state.value.search!!.messageRes)
    }

    @Test
    fun emptyResultShowsNoResultsMessage() = runTest(dispatcher) {
        val vm = vm(FakePlaces(ApiResult.Success(emptyList())))
        vm.openSearch(EndpointField.DESTINATION)
        vm.onQueryChange("없는곳")
        advanceUntilIdle()
        assertEquals(R.string.search_no_results, vm.state.value.search!!.messageRes)
    }

    @Test
    fun apiErrorsMapToMessages() = runTest(dispatcher) {
        val cases = mapOf(
            ApiError.MISSING_KEY to R.string.search_error_unavailable,
            ApiError.UNAUTHORIZED to R.string.search_error_unavailable,
            ApiError.QUOTA_EXCEEDED to R.string.search_error_quota,
            ApiError.NETWORK to R.string.search_error_network,
            ApiError.UNKNOWN to R.string.search_error_unknown,
        )
        cases.forEach { (error, res) ->
            val vm = vm(FakePlaces(ApiResult.Failure(error)))
            vm.openSearch(EndpointField.DESTINATION)
            vm.onQueryChange("서울역")
            advanceUntilIdle()
            assertEquals(error.name, res, vm.state.value.search!!.messageRes)
        }
    }

    @Test
    fun searchUsesOriginAsBias() = runTest(dispatcher) {
        val places = FakePlaces(ApiResult.Success(listOf(seoul)))
        val vm = vm(places)
        vm.openSearch(EndpointField.ORIGIN)
        vm.onQueryChange("서울역")
        advanceUntilIdle()
        vm.choosePlace(seoul)
        vm.openSearch(EndpointField.DESTINATION)
        vm.onQueryChange("부산역")
        advanceUntilIdle()
        assertEquals(seoul.location, places.lastNear)
    }

    @Test
    fun choosingPlaceSetsFieldAndClosesSearch() = runTest(dispatcher) {
        val vm = vm()
        vm.openSearch(EndpointField.DESTINATION)
        vm.choosePlace(busan)
        assertEquals("부산역", vm.state.value.destination!!.place.name)
        assertNull(vm.state.value.search)
    }

    @Test
    fun swapExchangesEndpoints() = runTest(dispatcher) {
        val vm = vm()
        vm.openSearch(EndpointField.ORIGIN); vm.choosePlace(seoul)
        vm.openSearch(EndpointField.DESTINATION); vm.choosePlace(busan)
        vm.swap()
        assertEquals("부산역", vm.state.value.origin!!.place.name)
        assertEquals("서울역", vm.state.value.destination!!.place.name)
    }

    @Test
    fun submitRequiresBothEndpointsAndDifferentPlaces() = runTest(dispatcher) {
        val vm = vm()
        assertNull(vm.buildRequest())
        vm.openSearch(EndpointField.ORIGIN); vm.choosePlace(seoul)
        assertNull(vm.buildRequest())
        vm.openSearch(EndpointField.DESTINATION); vm.choosePlace(seoul)
        assertTrue(vm.state.value.sameEndpoints)
        assertNull(vm.buildRequest())
        vm.openSearch(EndpointField.DESTINATION); vm.choosePlace(busan)
        vm.setMode(TravelMode.CAR)
        vm.setGoal(SunGoal.SEEK)
        val req = vm.buildRequest()!!
        assertEquals(TravelMode.CAR, req.mode)
        assertEquals(SunGoal.SEEK, req.goal)
        assertNull(req.departure)
    }

    @Test
    fun explicitlySelectedPastTimeIsPreserved() = runTest(dispatcher) {
        val vm = vm()
        vm.openSearch(EndpointField.ORIGIN); vm.choosePlace(seoul)
        vm.openSearch(EndpointField.DESTINATION); vm.choosePlace(busan)
        vm.setDeparture(fixedNow.minusHours(1))
        assertEquals(fixedNow.minusHours(1), vm.buildRequest()!!.departure)
        vm.setDeparture(fixedNow.plusHours(2))
        assertEquals(fixedNow.plusHours(2), vm.buildRequest()!!.departure)
    }

    @Test
    fun grantedPermissionFetchesCurrentLocationAsOrigin() = runTest(dispatcher) {
        val vm = vm(location = LocationProvider { LocationResult.Success(37.5, 127.0, 20f, isApproximate = false) })
        vm.openSearch(EndpointField.ORIGIN)
        vm.onLocationPermission(LocationPermissionStatus.Precise)
        assertTrue(vm.state.value.locating)
        advanceUntilIdle()
        val origin = vm.state.value.origin!!
        assertTrue(origin.isCurrentLocation)
        assertEquals(LatLng(37.5, 127.0), origin.place.location)
        assertFalse(vm.state.value.locating)
        assertNull(vm.state.value.search)
    }

    @Test
    fun approximateLocationShowsNotice() = runTest(dispatcher) {
        val vm = vm(location = LocationProvider { LocationResult.Success(37.5, 127.0, 2000f, isApproximate = true) })
        vm.onLocationPermission(LocationPermissionStatus.ApproximateOnly)
        advanceUntilIdle()
        assertEquals(R.string.location_approximate_only, vm.state.value.locationMessageRes)
        assertTrue(vm.state.value.origin!!.isCurrentLocation)
    }

    @Test fun mapLocationPreservesTripAndDoesNotEmitAnotherRequest() = runTest(dispatcher) {
        val vm = vm(location = LocationProvider { LocationResult.Success(37.5, 127.0, 20f, isApproximate = false) })
        vm.openSearch(EndpointField.ORIGIN); vm.choosePlace(seoul)
        vm.openSearch(EndpointField.DESTINATION); vm.choosePlace(busan)
        vm.setSchedule(fixedNow)
        vm.setMode(TravelMode.TAXI)
        val request = vm.automaticRequest()!!
        vm.fetchCurrentLocation(LocationPurpose.MAP)
        assertFalse(vm.state.value.locating)
        assertTrue(vm.state.value.mapLocating)
        advanceUntilIdle()
        assertEquals(request, vm.buildRequest())
        assertNull(vm.automaticRequest())
        val focus = vm.state.value.mapFocus!!
        assertEquals(LatLng(37.5, 127.0), focus.location)
        vm.fetchCurrentLocation(LocationPurpose.MAP)
        advanceUntilIdle()
        assertTrue(vm.state.value.mapFocus!!.sequence > focus.sequence)
        assertFalse(vm.state.value.mapLocating)
        assertEquals(request, vm.buildRequest())
        assertNull(vm.automaticRequest())
    }

    @Test fun mapPermissionAndRetryNeverFillAnEmptyOrigin() = runTest(dispatcher) {
        var result: LocationResult = LocationResult.Timeout
        val vm = vm(location = LocationProvider { result })
        vm.prepareLocation(LocationPurpose.MAP)
        vm.onLocationPermission(LocationPermissionStatus.PermanentlyDenied)
        assertNull(vm.state.value.origin)
        vm.onLocationPermission(LocationPermissionStatus.ApproximateOnly)
        advanceUntilIdle()
        assertNull(vm.state.value.origin)
        assertFalse(vm.state.value.mapLocating)
        result = LocationResult.Success(37.5, 127.0, 2000f, isApproximate = true)
        vm.retryLocation()
        advanceUntilIdle()
        vm.locateInitialOrigin()
        advanceUntilIdle()
        assertNull(vm.state.value.origin)
        assertEquals(LatLng(37.5, 127.0), vm.state.value.mapFocus!!.location)
        assertEquals(R.string.location_approximate_only, vm.state.value.locationMessageRes)
        // Explicit selection remains independent of the map request.
        vm.prepareLocation(LocationPurpose.ORIGIN)
        vm.onLocationPermission(LocationPermissionStatus.Precise)
        advanceUntilIdle()
        assertTrue(vm.state.value.origin!!.isCurrentLocation)
    }

    @Test fun grantingPermissionInSettingsResumesMapRequestOnce() = runTest(dispatcher) {
        var calls = 0
        val vm = vm(location = LocationProvider { calls++; LocationResult.Success(37.5, 127.0, 20f, false) })
        vm.prepareLocation(LocationPurpose.MAP)
        vm.onLocationPermission(LocationPermissionStatus.PermanentlyDenied)
        // ON_RESUME refresh changes permission.status without delivering a request callback.
        vm.onLocationPermissionAvailable()
        advanceUntilIdle()
        assertEquals(1, calls)
        assertNull(vm.state.value.origin)
        assertEquals(LatLng(37.5, 127.0), vm.state.value.mapFocus!!.location)
        assertNull(vm.state.value.locationMessageRes)
        vm.onLocationPermissionAvailable()
        advanceUntilIdle()
        assertEquals(1, calls)
    }

    @Test
    fun deniedPermissionShowsGuidanceWithoutCrash() = runTest(dispatcher) {
        val vm = vm()
        vm.openSearch(EndpointField.ORIGIN)
        vm.onLocationPermission(LocationPermissionStatus.PermanentlyDenied)
        assertEquals(R.string.location_permission_permanently_denied, vm.state.value.locationMessageRes)
        assertEquals(LocationAction.OpenAppSettings, vm.state.value.locationAction)
        assertNull(vm.state.value.origin)
    }

    @Test
    fun locationFailureKeepsOriginEmptyAndOffersAction() = runTest(dispatcher) {
        val vm = vm(location = LocationProvider { LocationResult.Timeout })
        vm.fetchCurrentLocation()
        advanceUntilIdle()
        assertNull(vm.state.value.origin)
        assertEquals(R.string.location_timeout, vm.state.value.locationMessageRes)
        assertFalse(vm.state.value.canSubmit)
    }
    @Test
    fun submitOpensSearchForMissingField() = runTest(dispatcher) {
        val vm = vm()
        assertNull(vm.submit())
        assertEquals(EndpointField.ORIGIN, vm.state.value.search!!.field)
        vm.choosePlace(seoul)
        assertNull(vm.submit())
        assertEquals(EndpointField.DESTINATION, vm.state.value.search!!.field)
        vm.choosePlace(busan)
        assertEquals("부산역", vm.submit()!!.destination.name)
    }

    @Test
    fun networkErrorOffersRetryAndSearchNowSkipsDebounce() = runTest(dispatcher) {
        val places = FakePlaces(ApiResult.Failure(ApiError.NETWORK))
        val vm = vm(places)
        vm.openSearch(EndpointField.DESTINATION)
        vm.onQueryChange("부산역")
        advanceUntilIdle()
        assertTrue(vm.state.value.search!!.canRetry)
        places.result = ApiResult.Success(listOf(busan))
        vm.searchNow()
        dispatcher.scheduler.runCurrent()
        assertEquals(listOf(busan), vm.state.value.search!!.results)
        assertFalse(vm.state.value.search!!.canRetry)
    }

    @Test
    fun missingKeyErrorHasNoRetry() = runTest(dispatcher) {
        val vm = vm(FakePlaces(ApiResult.Failure(ApiError.MISSING_KEY)))
        vm.openSearch(EndpointField.DESTINATION)
        vm.onQueryChange("부산역")
        advanceUntilIdle()
        assertFalse(vm.state.value.search!!.canRetry)
    }
}
