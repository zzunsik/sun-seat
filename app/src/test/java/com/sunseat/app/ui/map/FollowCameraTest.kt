package com.sunseat.app.ui.map

import androidx.compose.ui.unit.dp
import com.sunseat.app.domain.LatLng
import com.sunseat.app.domain.Leg
import com.sunseat.app.domain.Mode
import com.sunseat.app.domain.NamedPlace
import com.sunseat.app.domain.Route
import com.sunseat.app.domain.ExposureAnalyzer
import com.sunseat.app.ui.theme.Sizes
import java.time.Duration
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** 햇빛 따라가기(#146): 따라가기 중에는 전체 경로 맞춤으로 지도를 멀리 축소하지 않는다. */
class FollowCameraTest {
    // 서울역 → 버스 402(가상 정류장 좌표) → 남산 방향, 약 7km. 공개 POI 근처의 지어낸 좌표다.
    private val origin = LatLng(37.555, 126.972)
    private val stop = LatLng(37.556, 126.973)
    private val end = LatLng(37.500, 127.000)
    private val route = Route(listOf(
        Leg(Mode.WALK, listOf(origin, stop), Duration.ofMinutes(2), "서울역", "서울역 정류장"),
        Leg(Mode.BUS, listOf(stop, end), Duration.ofMinutes(25), "서울역 정류장", "종점", lineName = "402"),
    ))
    private val playback = MapState(
        routes = listOf(route), origin = NamedPlace("서울역", origin), destination = NamedPlace("종점", end),
        cursor = MapCursor(origin, 30.0, 120.0, 30.0), followCursor = true, routeProgress = 0f,
        padding = MapPadding(top = 84.dp, bottom = 8.dp),
    )

    @Test fun followingPlaybackNeverFitsTheWholeRoute() {
        val policy = MapFitPolicy()
        assertFalse("first frame while following must not zoom out to the whole route", policy.shouldFit(playback))
        assertFalse(policy.shouldFit(playback.copy(padding = MapPadding(top = 84.dp, bottom = 16.dp))))
        assertFalse(policy.shouldFit(playback.copy(cameraRevision = 1)))
    }

    @Test fun followingFramesStreetLevelAroundTheVehicleThenOnlyRecentres() {
        val policy = MapFitPolicy()
        assertEquals(MapCamera.FRAME_FOLLOW, policy.camera(playback))
        // Moving vehicle: renderer re-centres and keeps the zoom.
        assertEquals(MapCamera.NONE, policy.camera(playback.copy(cursor = MapCursor(stop, 30.0, 120.0, 30.0), routeProgress = .01f)))
        // Padding change while following re-frames the vehicle instead of the whole route.
        assertEquals(MapCamera.FRAME_FOLLOW, policy.camera(playback.copy(padding = MapPadding(top = 84.dp, bottom = 16.dp))))
        // Stopping (drag) keeps the user's camera; following again frames the vehicle once more.
        val stopped = playback.copy(followCursor = false, padding = MapPadding(top = 84.dp, bottom = 16.dp))
        assertEquals(MapCamera.NONE, policy.camera(stopped))
        assertEquals(MapCamera.FRAME_FOLLOW, policy.camera(stopped.copy(followCursor = true)))
    }

    @Test fun followFrameSeparatesStartMarkerBoardingBadgeAndVehicle() {
        val box = followBounds(origin)
        val south = box[0]; val north = box[1]
        val height = ExposureAnalyzer.distanceMeters(south, LatLng(north.latitude, south.longitude))
        val width = ExposureAnalyzer.distanceMeters(south, LatLng(south.latitude, north.longitude))
        assertEquals(2 * FOLLOW_RADIUS_METERS, height, 1.0)
        assertEquals(2 * FOLLOW_RADIUS_METERS, width, 1.0)
        assertEquals(origin.latitude, (south.latitude + north.latitude) / 2, 1e-9)
        assertEquals(origin.longitude, (south.longitude + north.longitude) / 2, 1e-9)
        // The whole-route frame packs the ~140 m walk to the stop into a few dp; the follow frame spreads it beyond a marker.
        val walk = ExposureAnalyzer.distanceMeters(origin, stop)
        val mapDp = 190.0 // portrait simulation map height minus the info card (Sizes.simulationMapInfo)
        val routeSpan = ExposureAnalyzer.distanceMeters(LatLng(end.latitude, origin.longitude), LatLng(stop.latitude, origin.longitude))
        assertTrue(walk / routeSpan * mapDp < Sizes.mapMarker.value)
        assertTrue(walk / (2 * FOLLOW_RADIUS_METERS) * mapDp > Sizes.mapMarker.value)
    }

    @Test fun textMarkersUnderThePlaybackVehicleAreHidden() {
        val marker = 32f
        // Screen distance from a fixed scale: 1 px = 1 m.
        val metres = { a: LatLng, b: LatLng -> ExposureAnalyzer.distanceMeters(a, b).toFloat() }
        // Origin is the boarding stop: 출발 marker and the 402 badge sit under the vehicle at the start.
        assertTrue(playback.coveredByVehicle(origin, marker, metres))
        // ~140 m away at this scale: visible.
        assertFalse(playback.coveredByVehicle(stop, marker, metres))
        // Not playback (result map with a cursor) or no vehicle: never hidden.
        assertFalse(playback.copy(routeProgress = null).coveredByVehicle(origin, marker, metres))
        assertFalse(playback.copy(cursor = null).coveredByVehicle(origin, marker, metres))
    }

    @Test fun followingIgnoresMissingCursor() {
        assertNull(playback.copy(cursor = null).followTarget)
        assertNull(playback.copy(followCursor = false).followTarget)
        assertEquals(origin, playback.followTarget)
    }
}
