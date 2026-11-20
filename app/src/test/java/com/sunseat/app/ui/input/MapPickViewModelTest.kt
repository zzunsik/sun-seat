package com.sunseat.app.ui.input

import com.sunseat.app.R
import com.sunseat.app.data.network.ApiError
import com.sunseat.app.data.network.ApiResult
import com.sunseat.app.data.place.Place
import com.sunseat.app.data.place.PlaceSearchApi
import com.sunseat.app.data.place.ReverseGeocodeApi
import com.sunseat.app.domain.LatLng
import com.sunseat.app.domain.SunGoal
import com.sunseat.app.domain.TravelMode
import com.sunseat.app.location.LocationProvider
import com.sunseat.app.location.LocationResult
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.time.LocalDateTime

/** 지도에서 위치 선택(#85)의 상태 기계. 좌표·주소는 직접 만든 값이다. */
@OptIn(ExperimentalCoroutinesApi::class)
class MapPickViewModelTest {
    private val dispatcher = StandardTestDispatcher()

    /** 좌표마다 응답을 정하고, [gates]에 걸린 좌표는 풀어 줄 때까지 응답을 미룬다. */
    private class FakeGeocoder : ReverseGeocodeApi {
        val calls = mutableListOf<LatLng>()
        val gates = mutableMapOf<LatLng, CompletableDeferred<Unit>>()
        var answer: (LatLng) -> ApiResult<String?> = { ApiResult.Success("가상구 예시로 ${it.latitude}") }
        override suspend fun address(location: LatLng): ApiResult<String?> {
            calls += location
            gates[location]?.await()
            return answer(location)
        }
    }

    private class NoSearch : PlaceSearchApi {
        override suspend fun search(query: String, near: LatLng?, size: Int): ApiResult<List<Place>> = ApiResult.Success(emptyList())
    }

    private fun place(name: String, lat: Double, lng: Double) =
        Place(id = name, name = name, address = "주소 $name", roadAddress = "", location = LatLng(lat, lng), category = "")

    private val seoul = place("서울역", 37.5547, 126.9707)
    private val gangnam = place("강남역", 37.4979, 127.0276)
    private val moved = LatLng(37.51, 127.01)
    private val movedAgain = LatLng(37.52, 127.02)

    private var locationCalls = 0
    private fun vm(geocoder: ReverseGeocodeApi? = FakeGeocoder(), location: LocationResult = LocationResult.Unavailable) =
        InputViewModel(NoSearch(), LocationProvider { locationCalls++; location }, currentLocationName = "현재 위치", reverseGeocode = geocoder)

    /** 서울역 → 강남역 여정을 확정한 상태. */
    private fun InputViewModel.trip(): com.sunseat.app.domain.TripRequest {
        openSearch(EndpointField.ORIGIN); choosePlace(seoul)
        openSearch(EndpointField.DESTINATION); choosePlace(gangnam)
        setMode(TravelMode.CAR); setGoal(SunGoal.SEEK); setDeparture(LocalDateTime.of(2026, 11, 20, 9, 0))
        return buildRequest()!!.also(::acceptRequest)
    }

    @Before fun setUp() = Dispatchers.setMain(dispatcher)
    @After fun tearDown() = Dispatchers.resetMain()

    @Test fun startsAtTheEditedEndpointAndResolvesItsAddressWithoutAskingForLocation() = runTest(dispatcher) {
        val geocoder = FakeGeocoder()
        val vm = vm(geocoder)
        vm.trip()
        vm.openSearch(EndpointField.DESTINATION)
        vm.onQueryChange("강남")
        vm.openMapPick()
        val pick = vm.state.value.pick!!
        assertEquals(EndpointField.DESTINATION, pick.field)
        assertEquals(gangnam.location, pick.center)
        assertEquals(gangnam.location, pick.camera.location)
        assertEquals(PickAddressStatus.LOADING, pick.status)
        advanceUntilIdle()
        assertEquals(PickAddressStatus.FOUND, vm.state.value.pick!!.status)
        assertEquals("가상구 예시로 37.4979", vm.state.value.pick!!.address)
        assertEquals(listOf(gangnam.location), geocoder.calls)
        assertEquals(0, locationCalls)
        // The search underneath is kept, so cancelling returns to it, and no trip is produced while picking.
        assertEquals("강남", vm.state.value.search!!.query)
        assertNull(vm.automaticRequest())
    }

    @Test fun emptyFieldStartsAtTheOtherEndpointThenTheMapFocusThenCityHall() = runTest(dispatcher) {
        val vm = vm()
        vm.openSearch(EndpointField.DESTINATION)
        vm.openMapPick()
        assertEquals(InputViewModel.DEFAULT_PICK_CENTER, vm.state.value.pick!!.center)
        vm.cancelMapPick()
        vm.closeSearch()
        vm.openSearch(EndpointField.ORIGIN); vm.choosePlace(seoul)
        vm.openSearch(EndpointField.DESTINATION)
        vm.openMapPick()
        assertEquals(seoul.location, vm.state.value.pick!!.center)
        assertEquals(0, locationCalls)
    }

    @Test fun movingFastAppliesOnlyTheLatestAddressAndDebouncesLookups() = runTest(dispatcher) {
        val geocoder = FakeGeocoder()
        val vm = vm(geocoder)
        vm.trip()
        vm.openSearch(EndpointField.ORIGIN)
        vm.openMapPick()
        advanceUntilIdle()
        geocoder.calls.clear()

        // Several idles in quick succession collapse into one lookup for the final centre.
        vm.onPickCameraMoving()
        assertFalse(vm.state.value.pick!!.canConfirm)
        vm.onPickCameraIdle(LatLng(37.50, 127.00))
        advanceTimeBy(InputViewModel.SEARCH_DEBOUNCE_MILLIS / 2)
        vm.onPickCameraMoving()
        vm.onPickCameraIdle(moved)
        advanceUntilIdle()
        assertEquals(listOf(moved), geocoder.calls)
        assertEquals(moved, vm.state.value.pick!!.center)
        assertEquals("가상구 예시로 37.51", vm.state.value.pick!!.address)
        assertTrue(vm.state.value.pick!!.canConfirm)

        // A slow answer for an older centre never attaches to the newer centre.
        val slow = CompletableDeferred<Unit>()
        geocoder.gates[movedAgain] = slow
        vm.onPickCameraIdle(movedAgain)
        advanceTimeBy(InputViewModel.SEARCH_DEBOUNCE_MILLIS + 1); runCurrent()
        vm.onPickCameraIdle(moved)
        slow.complete(Unit)
        advanceUntilIdle()
        assertEquals(moved, vm.state.value.pick!!.center)
        assertEquals("가상구 예시로 37.51", vm.state.value.pick!!.address)
    }

    @Test fun sameCentreWithinSdkRoundingDoesNotLookUpAgain() = runTest(dispatcher) {
        val geocoder = FakeGeocoder()
        val vm = vm(geocoder)
        vm.trip()
        vm.openSearch(EndpointField.ORIGIN)
        vm.openMapPick()
        advanceUntilIdle()
        vm.onPickCameraIdle(LatLng(seoul.location.latitude + 1e-9, seoul.location.longitude - 1e-9))
        advanceUntilIdle()
        assertEquals(1, geocoder.calls.size)
        assertEquals(seoul.location, vm.state.value.pick!!.center)
        assertEquals(PickResult.Unchanged, vm.confirmPick("지도에서 선택한 위치"))
    }

    @Test fun failureAndNoAddressKeepTheCoordinateConfirmableWithAnHonestName() = runTest(dispatcher) {
        val geocoder = FakeGeocoder().apply { answer = { ApiResult.Failure(ApiError.NETWORK) } }
        val vm = vm(geocoder)
        val request = vm.trip()
        vm.openSearch(EndpointField.DESTINATION)
        vm.openMapPick()
        vm.onPickCameraIdle(moved)
        advanceUntilIdle()
        assertEquals(PickAddressStatus.FAILED, vm.state.value.pick!!.status)
        assertTrue(vm.state.value.pick!!.canRetry)

        geocoder.answer = { ApiResult.Success(null) }
        vm.retryPickAddress()
        advanceUntilIdle()
        assertEquals(PickAddressStatus.NONE, vm.state.value.pick!!.status)
        assertNull(vm.state.value.pick!!.address)

        val result = vm.confirmPick("지도에서 선택한 위치") as PickResult.Chosen
        assertFalse(result.named)
        assertEquals("지도에서 선택한 위치", result.place.name)
        assertEquals(moved, result.place.location)
        assertNull(vm.state.value.pick)
        val edited = vm.chooseEditedPlace(result.place, remember = result.named)!!
        assertEquals(request.copy(destination = com.sunseat.app.domain.NamedPlace("지도에서 선택한 위치", moved)), edited)
    }

    @Test fun missingKeyIsNotRetryable() = runTest(dispatcher) {
        val vm = vm(FakeGeocoder().apply { answer = { ApiResult.Failure(ApiError.MISSING_KEY) } })
        vm.openSearch(EndpointField.DESTINATION)
        vm.openMapPick()
        advanceUntilIdle()
        assertEquals(PickAddressStatus.FAILED, vm.state.value.pick!!.status)
        assertFalse(vm.state.value.pick!!.canRetry)
        assertTrue(vm.state.value.pick!!.canConfirm)
    }

    @Test fun confirmChangesOnlyThePickedEndpointAndKeepsEveryOtherCondition() = runTest(dispatcher) {
        val vm = vm()
        val request = vm.trip()
        vm.openSearch(EndpointField.ORIGIN)
        vm.openMapPick()
        vm.onPickCameraIdle(moved)
        advanceUntilIdle()
        val result = vm.confirmPick("지도에서 선택한 위치") as PickResult.Chosen
        assertTrue(result.named)
        assertEquals("가상구 예시로 37.51", result.place.name)
        val edited = vm.chooseEditedPlace(result.place, remember = result.named)!!
        assertEquals(request.copy(origin = com.sunseat.app.domain.NamedPlace("가상구 예시로 37.51", moved)), edited)
        assertEquals(TravelMode.CAR, edited.mode)
        assertEquals(SunGoal.SEEK, edited.goal)
        assertNull(vm.state.value.search)
        assertNull(vm.state.value.pick)
    }

    @Test fun pickingTheOtherEndpointIsRejectedInsideThePicker() = runTest(dispatcher) {
        val vm = vm()
        val request = vm.trip()
        vm.openSearch(EndpointField.DESTINATION)
        vm.openMapPick()
        vm.onPickCameraIdle(seoul.location)
        advanceUntilIdle()
        assertEquals(PickResult.Rejected, vm.confirmPick("지도에서 선택한 위치"))
        assertEquals(R.string.input_same_endpoints, vm.state.value.pick!!.messageRes)
        assertEquals(request, vm.buildRequest())
        // Moving again clears the warning.
        vm.onPickCameraIdle(moved)
        assertNull(vm.state.value.pick!!.messageRes)
    }

    @Test fun cannotConfirmWhileMovingOrWhenTheMapIsUnavailable() = runTest(dispatcher) {
        val vm = vm()
        val request = vm.trip()
        vm.openSearch(EndpointField.DESTINATION)
        vm.openMapPick()
        vm.onPickCameraMoving()
        assertEquals(PickResult.Rejected, vm.confirmPick("지도에서 선택한 위치"))
        vm.onPickCameraIdle(moved)
        vm.onPickMapUnavailable()
        assertTrue(vm.state.value.pick!!.mapUnavailable)
        assertEquals(PickResult.Rejected, vm.confirmPick("지도에서 선택한 위치"))
        assertEquals(request, vm.buildRequest())
    }

    @Test fun cancelDiscardsOnlyTheDraftAndIgnoresALateAddress() = runTest(dispatcher) {
        val geocoder = FakeGeocoder()
        val vm = vm(geocoder)
        val request = vm.trip()
        vm.openSearch(EndpointField.DESTINATION)
        vm.onQueryChange("강남")
        advanceUntilIdle()
        val gate = CompletableDeferred<Unit>()
        geocoder.gates[moved] = gate
        vm.openMapPick()
        vm.onPickCameraIdle(moved)
        advanceTimeBy(InputViewModel.SEARCH_DEBOUNCE_MILLIS + 1); runCurrent()
        vm.cancelMapPick()
        gate.complete(Unit)
        advanceUntilIdle()
        assertNull(vm.state.value.pick)
        assertNotNull(vm.state.value.search)
        assertEquals("강남", vm.state.value.search!!.query)
        vm.cancelEndpointEdit(request)
        assertEquals(request, vm.buildRequest())
        assertNull(vm.automaticRequest())
    }

    @Test fun currentLocationOnlyMovesThePickerCentre() = runTest(dispatcher) {
        val geocoder = FakeGeocoder()
        val vm = vm(geocoder, LocationResult.Success(37.53, 127.03, 10f, false))
        val request = vm.trip()
        vm.openSearch(EndpointField.ORIGIN)
        vm.openMapPick()
        advanceUntilIdle()
        val before = vm.state.value.pick!!.camera
        vm.fetchCurrentLocation(LocationPurpose.MAP, keepSearch = true)
        advanceUntilIdle()
        val pick = vm.state.value.pick!!
        assertEquals(LatLng(37.53, 127.03), pick.center)
        assertEquals(LatLng(37.53, 127.03), pick.camera.location)
        assertTrue(pick.camera.sequence > before.sequence)
        assertEquals(LatLng(37.53, 127.03), geocoder.calls.last())
        // The origin is still the confirmed place (#68): only the camera moved.
        assertEquals(request, vm.buildRequest())
        assertNotNull(vm.state.value.search)
    }

    @Test fun inputFlowConfirmProducesOneAutomaticRequest() = runTest(dispatcher) {
        val vm = vm()
        vm.openSearch(EndpointField.ORIGIN); vm.choosePlace(seoul)
        vm.openSearch(EndpointField.DESTINATION)
        vm.openMapPick()
        vm.onPickCameraIdle(moved)
        advanceUntilIdle()
        assertNull(vm.automaticRequest())
        val result = vm.confirmPick("지도에서 선택한 위치") as PickResult.Chosen
        vm.choosePlace(result.place, remember = result.named)
        val request = vm.automaticRequest()!!
        assertEquals(moved, request.destination.location)
        assertNull(vm.automaticRequest())
    }
}
