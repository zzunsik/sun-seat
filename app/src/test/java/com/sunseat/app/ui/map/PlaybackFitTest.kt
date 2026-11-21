package com.sunseat.app.ui.map

import androidx.compose.ui.unit.dp
import com.sunseat.app.domain.ExposureAnalyzer
import com.sunseat.app.domain.LatLng
import com.sunseat.app.domain.Leg
import com.sunseat.app.domain.Mode
import com.sunseat.app.domain.NamedPlace
import com.sunseat.app.domain.Route
import java.time.Duration
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * #183: 햇빛 따라가기에서 따라가기를 끈 첫 화면(재생성 등)의 맞춤이 경로 전체(약 11km)를 낮은 지도 띠(약 117dp)에 넣어
 * 경로가 약 70px로 줄고 차량·도착 표식만 보였다. 재생 중 맞춤은 따라가기와 같은 차량 둘레(2×250m)를 맞춘다. 도착 뒤에는 도착지(#152).
 * 값은 px(밀도 3).
 */
class PlaybackFitTest {
    private val d = 3f
    private val scale = MapScale(PREVIEW_WORLD_DP * d)
    // 세로 390dp 시뮬레이션 지도 202dp 중 정보 카드가 위 84dp를 덮는다(보이는 높이 약 117dp).
    private val viewport = Viewport(390f * d, 202f * d, top = 84f * d, bottom = 1f * d)
    private val a = LatLng(37.50, 127.0)
    private val b = LatLng(37.55, 127.0)
    private val c = LatLng(37.55, 127.001)
    private val e = LatLng(37.60, 127.001)
    private val route = Route(listOf(
        Leg(Mode.BUS, listOf(a, b), Duration.ofMinutes(10), "a", "b", lineName = "402"),
        Leg(Mode.WALK, listOf(b, c), Duration.ofMinutes(2), "b", "c"),
        Leg(Mode.SUBWAY, listOf(c, e), Duration.ofMinutes(10), "c", "e", lineName = "2호선"),
    ))
    private val vehicle = LatLng(37.53, 127.0)
    private val paused = MapState(routes = listOf(route), origin = NamedPlace("숲 입구", a), destination = NamedPlace("공원", e),
        cursor = MapCursor(vehicle, 0.0, 90.0, 20.0), followCursor = false, routeProgress = 0.29f, padding = MapPadding(top = 84.dp, bottom = 1.dp))

    private fun sizes() = MarkerSizes(markerHeight = 36f * d, originWidth = 40f * d, destinationWidth = 40f * d, stopRadius = 7f * d,
        labelHeight = 28f * d, boardClearance = 24f * d, labelWidth = { 40f * d })

    private fun fit(state: MapState): CameraPosition {
        val policy = MapFitPolicy()
        val input = CameraInput(viewport, scale, state.footprints(sizes()), state.fitTarget, fitGap(viewport, 40f * d, 8f * d, 36f * d))
        return policy.update(state, input)!!
    }

    /** 화면에서 [meters]m가 차지하는 px. */
    private fun pxOf(camera: CameraPosition, meters: Double): Float {
        val north = LatLng(camera.center.latitude + meters / 111_320.0, camera.center.longitude)
        return camera.toScreen(camera.center, viewport.width, viewport.height, scale).second - camera.toScreen(north, viewport.width, viewport.height, scale).second
    }

    @Test fun pausedPlaybackFitsTheVehicleSurroundingsNotTheWholeRoute() {
        val camera = fit(paused)
        // 차량 둘레 2×250m가 보이는 높이의 절반 이상을 쓴다(따라가기와 같은 거리 단위 줌). 경로 전체 맞춤은 11km를 약 70px에 넣었다.
        assertTrue("500 m spans ${pxOf(camera, 500.0)} px of ${viewport.visibleHeight}", pxOf(camera, 500.0) >= viewport.visibleHeight / 2)
        // 차량은 보이는 영역 가운데.
        val (x, y) = camera.toScreen(vehicle, viewport.width, viewport.height, scale)
        assertEquals(viewport.width / 2, x, 1f)
        assertEquals((viewport.visibleTop + viewport.visibleBottom) / 2, y, 1f)
    }

    @Test fun arrivalFitsTheDestination() {
        val arrived = paused.copy(cursor = MapCursor(e, 0.0, 90.0, 20.0), routeProgress = 1f)
        val camera = fit(arrived)
        val (x, y) = camera.toScreen(e, viewport.width, viewport.height, scale)
        assertEquals(viewport.width / 2, x, 1f)
        assertEquals((viewport.visibleTop + viewport.visibleBottom) / 2, y, 1f)
        assertEquals(CameraMode.Fitted(FitTarget.VEHICLE), MapFitPolicy().also { it.update(arrived, CameraInput(viewport, scale, arrived.footprints(sizes()), arrived.fitTarget)) }.mode)
    }

    @Test fun resultMapWithoutPlaybackStillFitsTheWholeRoute() {
        // 결과 화면 지도(재생 아님)는 그대로 경로 전체를 맞춘다.
        val result = paused.copy(cursor = null, routeProgress = null)
        val camera = fit(result)
        val span = ExposureAnalyzer.distanceMeters(a, e)
        assertTrue(pxOf(camera, span) <= viewport.visibleHeight)
        assertEquals(FitTarget.SELECTED_ROUTE, result.fitTarget)
    }
}
