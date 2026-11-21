package com.sunseat.app.ui.map

import androidx.compose.ui.unit.dp
import com.sunseat.app.domain.LatLng
import com.sunseat.app.domain.Leg
import com.sunseat.app.domain.Mode
import com.sunseat.app.domain.NamedPlace
import com.sunseat.app.domain.Route
import java.time.Duration
import kotlin.random.Random
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * #168: 지도 카메라 정책(순수 함수). 값은 px이며 밀도 3(xxhdpi에 가까운 값)으로 dp를 바꾼다.
 * 발자국 상자 ⊂ 보이는 영역, 가장 큰 정수 줌, 보이는 영역만 바뀌면 카메라를 옮기지 않음(#148), 모드 전이표(DESIGN 12.3)를 본다.
 */
class MapCameraPolicyTest {
    private val density = 3f
    private val scale = MapScale(PREVIEW_WORLD_DP * density)
    private val inset = 40f * density
    private val edge = 8f * density
    private val seoul = LatLng(37.5547, 126.9707)

    private fun dp(value: Float) = value * density

    /** 시트가 아래 [bottomDp]를 덮는 [widthDp]×[heightDp] 지도. */
    private fun viewport(widthDp: Float, heightDp: Float, topDp: Float = 0f, bottomDp: Float = 0f) =
        Viewport(dp(widthDp), dp(heightDp), top = dp(topDp), bottom = dp(bottomDp))

    private fun inside(box: ScreenBox, v: Viewport, gap: Float, slack: Float = 0.5f) =
        box.left >= v.visibleLeft + gap - slack && box.right <= v.visibleRight - gap + slack &&
            box.top >= v.visibleTop + gap - slack && box.bottom <= v.visibleBottom - gap + slack

    private fun boxes(camera: CameraPosition, footprints: List<Footprint>, v: Viewport) = footprints.map { camera.screenBox(it, v.width, v.height, scale) }

    // ---------- 임의 경로 생성(고정 시드) ----------

    private enum class Shape { NORTH_SOUTH, EAST_WEST, DIAGONAL, BACK_AND_FORTH }

    /** 정류장 [stops]개, 길이 약 [km]km의 탑승 경로(버스·지하철 번갈아). 첫 승차 정류장은 출발지 옆이다. */
    private fun randomRoute(random: Random, stops: Int, shape: Shape, km: Double): Triple<Route, NamedPlace, NamedPlace> {
        val step = km / 111.0 / stops.coerceAtLeast(1)
        val start = LatLng(seoul.latitude + random.nextDouble(-0.05, 0.05), seoul.longitude + random.nextDouble(-0.05, 0.05))
        val points = (0..stops).map { i ->
            val t = i * step
            when (shape) {
                Shape.NORTH_SOUTH -> LatLng(start.latitude + t, start.longitude + random.nextDouble(-step, step) / 4)
                Shape.EAST_WEST -> LatLng(start.latitude + random.nextDouble(-step, step) / 4, start.longitude + t * 1.26)
                Shape.DIAGONAL -> LatLng(start.latitude - t, start.longitude + t * 1.26)
                Shape.BACK_AND_FORTH -> LatLng(start.latitude + (if (i % 2 == 0) 0.0 else step * stops / 2), start.longitude + t * 0.6)
            }
        }
        val lines = listOf("402", "2호선", "N16", "간선 470", "9호선 외")
        val legs = points.zipWithNext().mapIndexed { i, (a, b) ->
            val subway = i % 2 == 1
            Leg(if (subway) Mode.SUBWAY else Mode.BUS, listOf(a, b), Duration.ofMinutes(5), "정류장 $i", "정류장 ${i + 1}", transitStepIndex = i, lineName = lines[i % lines.size])
        }
        val origin = NamedPlace("출발지", LatLng(points.first().latitude - 0.0004, points.first().longitude - 0.0004))
        val destination = NamedPlace("도착지", LatLng(points.last().latitude + 0.0004, points.last().longitude + 0.0004))
        return Triple(Route(listOf(Leg(Mode.WALK, listOf(origin.location, points.first()), Duration.ofMinutes(2), "출발지", "정류장 0")) + legs), origin, destination)
    }

    /** 글자 크기 [fontScale]에서의 발자국 크기(px). 이름표 폭은 글자 수에 비례한 어림값이다. */
    private fun sizes(fontScale: Float) = MarkerSizes(
        markerHeight = dp(32f + 4f),
        originWidth = dp(40f * fontScale), destinationWidth = dp(40f * fontScale),
        stopRadius = dp(7f),
        labelHeight = dp(maxOf(20f, 16f * fontScale) + 8f),
        boardClearance = dp(20f * fontScale + 4f),
        labelWidth = { stop -> dp((stop.lines.firstOrNull()?.length ?: 2) * 8f * fontScale + 12f) },
    )

    @Test fun randomRoutesFitInsideTheVisibleAreaAtTheLargestIntegerZoom() {
        val random = Random(168)
        // 320×110dp 띠 ~ 844×390dp 가로, 위·아래 덮개.
        val areas = listOf(
            viewport(320f, 300f, bottomDp = 190f), viewport(390f, 300f, bottomDp = 190f), viewport(390f, 760f, topDp = 64f, bottomDp = 120f),
            viewport(844f, 390f), viewport(832f, 384f, topDp = 84f, bottomDp = 8f), viewport(320f, 240f), viewport(390f, 600f, topDp = 250f),
        )
        var checked = 0
        repeat(400) {
            val shape = Shape.entries[random.nextInt(Shape.entries.size)]
            val (route, origin, destination) = randomRoute(random, random.nextInt(1, 31), shape, random.nextDouble(0.3, 40.0))
            val state = MapState(routes = listOf(route), origin = origin, destination = destination)
            for (fontScale in listOf(1f, 1.5f)) {
                val footprints = state.footprints(sizes(fontScale))
                val v = areas[random.nextInt(areas.size)]
                val gap = fitGap(v, inset, edge)
                val camera = fitCamera(footprints, v, scale, gap)
                assertNotNull(camera)
                camera!!
                val fits = boxes(camera, footprints, v).all { inside(it, v, gap) }
                // 가장 작은 줌에서도 들지 않는 경우(세계 전체보다 큰 상자)는 없다.
                assertTrue("all footprints inside the visible area: $shape font=$fontScale zoom=${camera.zoom} viewport=$v", fits)
                if (camera.zoom < FIT_MAX_ZOOM) {
                    // 한 단계 더 가까우면 어느 발자국은 보이는 영역 밖이다(가장 큰 줌).
                    val closer = fitCamera(footprints, v, scale, gap, maxZoom = camera.zoom + 1, minZoom = camera.zoom + 1)!!
                    assertTrue("zoom ${closer.zoom} must not fit", !boxes(closer, footprints, v).all { inside(it, v, gap, slack = 0f) })
                }
                checked++
            }
        }
        assertEquals(800, checked)
    }

    @Test fun shortListStripFitsOneLevelCloserThanFourFixedInsets() {
        // #138: 390dp 폭 목록 기본 상태(지도 300dp 중 아래 190dp를 시트가 덮어 보이는 띠 110dp). 네 변 40dp inset이면 경로 높이가 30dp뿐이었다.
        val v = viewport(390f, 300f, bottomDp = 190f)
        val gap = fitGap(v, inset, edge)
        assertTrue("gap ${gap / density}dp must leave room in a 110dp strip", 110f * density - 2 * gap >= 2 * (110f - 2 * 40f) * density)
    }

    // ---------- #148: 보이는 영역만 바뀌면 카메라를 옮기지 않는다 ----------

    private val route = Route(listOf(Leg(Mode.BUS, listOf(seoul, LatLng(37.4979, 127.0276)), Duration.ofMinutes(30), "서울역", "강남역", lineName = "402")))
    private val listState = MapState(routes = listOf(route), origin = NamedPlace("서울역", seoul), destination = NamedPlace("강남역", LatLng(37.4979, 127.0276)), automaticFit = false)

    private fun input(state: MapState, v: Viewport, current: CameraPosition?) =
        CameraInput(v, scale, state.footprints(sizes(1f)), state.fitTarget, fitGap(v, inset, edge), current)

    @Test fun sheetDragOnlyChangesTheViewportAndNeverMovesTheCamera() {
        val policy = MapFitPolicy()
        val start = viewport(390f, 600f, bottomDp = 490f)
        val fitted = policy.update(listState.copy(padding = MapPadding(bottom = 490.dp)), input(listState, start, null))
        assertNotNull(fitted)
        fitted!!
        val markers = boxes(fitted, listState.footprints(sizes(1f)), start)
        // 손잡이를 약 60dp 올렸다 내림: 매 프레임 보이는 영역(아래 덮개)만 바뀐다.
        val frames = (0..60 step 4).map { 490f - it } + (0..60 step 4).map { 430f + it }
        for (bottom in frames) {
            val v = viewport(390f, 600f, bottomDp = bottom)
            assertNull("sheet offset $bottom dp must not move the camera", policy.update(listState.copy(padding = MapPadding(bottom = bottom.dp)), input(listState, v, fitted)))
            // 카메라가 그대로이므로 표식의 화면 위치도 그대로다(뷰 크기는 같다).
            assertEquals(markers, boxes(fitted, listState.footprints(sizes(1f)), v))
        }
        assertEquals(CameraMode.Fitted(FitTarget.SELECTED_ROUTE), policy.mode)
        for (mode in listOf(CameraMode.Fitted(FitTarget.ALL_ROUTES), CameraMode.UserMoved, CameraMode.Centered, CameraMode.Unset)) {
            val decision = cameraPolicy(mode, CameraRequest.ViewportChanged, input(listState, start, fitted))
            assertNull(decision.move)
            assertEquals(mode, decision.mode)
        }
    }

    @Test fun followingRecentresTheVehicleInTheNewVisibleArea() {
        val vehicle = LatLng(37.556, 126.973)
        val v = viewport(390f, 300f, topDp = 84f, bottomDp = 8f)
        val decision = cameraPolicy(CameraMode.Following, CameraRequest.ViewportChanged, input(listState, v, null).copy(followTarget = vehicle))
        val camera = decision.move!!
        val (x, y) = camera.toScreen(vehicle, v.width, v.height, scale)
        assertEquals((v.visibleLeft + v.visibleRight) / 2, x, 0.5f)
        assertEquals((v.visibleTop + v.visibleBottom) / 2, y, 0.5f)
        assertEquals(CameraMode.Following, decision.mode)
        // 차량 둘레 2×250m가 보이는 영역에 드는 가장 큰 줌(#146).
        val box = followBounds(vehicle).map { camera.toScreen(it, v.width, v.height, scale) }
        assertTrue(box.all { (bx, by) -> bx >= v.visibleLeft - 0.5f && bx <= v.visibleRight + 0.5f && by >= v.visibleTop - 0.5f && by <= v.visibleBottom + 0.5f })
        // 차량이 움직이면 가운데만 옮기고 줌은 그대로다.
        val moved = cameraPolicy(CameraMode.Following, CameraRequest.FollowTo(LatLng(37.557, 126.975)), input(listState, v, camera)).move!!
        assertEquals(camera.zoom, moved.zoom)
    }

    @Test fun centerOnPutsTheLocationAtTheVisibleCentre() {
        val v = viewport(390f, 700f, topDp = 200f, bottomDp = 100f)
        val decision = cameraPolicy(CameraMode.UserMoved, CameraRequest.CenterOn(seoul), input(listState, v, CameraPosition(seoul, 14)))
        val (x, y) = decision.move!!.toScreen(seoul, v.width, v.height, scale)
        assertEquals(v.width / 2, x, 0.5f)
        assertEquals((v.visibleTop + v.visibleBottom) / 2, y, 0.5f)
        assertEquals(14, decision.move!!.zoom)
        assertEquals(CameraMode.Centered, decision.mode)
        // 지도 선택(#85): 덮개가 없으면 카메라 가운데 = 뷰 가운데 = 핀.
        val pick = cameraPolicy(CameraMode.Unset, CameraRequest.CenterOn(seoul, 17), input(listState, viewport(390f, 600f), null)).move!!
        assertEquals(seoul.latitude, pick.center.latitude, 1e-9)
        assertEquals(seoul.longitude, pick.center.longitude, 1e-9)
    }

    @Test fun projectionRoundTrips() {
        val camera = CameraPosition(seoul, 13)
        listOf(LatLng(37.55, 126.98), LatLng(37.5, 127.02)).forEach { p ->
            val (x, y) = camera.toScreen(p, 1170f, 1800f, scale)
            val back = camera.fromScreen(x, y, 1170f, 1800f, scale)
            assertEquals(p.latitude, back.latitude, 1e-7)
            assertEquals(p.longitude, back.longitude, 1e-7)
        }
        // 북이 위, 동이 오른쪽.
        val north = camera.toScreen(LatLng(37.6, 126.9707), 1000f, 1000f, scale)
        val east = camera.toScreen(LatLng(37.5547, 127.0), 1000f, 1000f, scale)
        assertTrue(north.second < 500f && east.first > 500f)
        // 줌 한 단계마다 두 배.
        val one = camera.toScreen(LatLng(37.56, 126.98), 1000f, 1000f, scale).first - 500f
        val two = camera.copy(zoom = 14).toScreen(LatLng(37.56, 126.98), 1000f, 1000f, scale).first - 500f
        assertEquals(2 * one, two, 0.01f)
        // 실기기에서 잰 m/px로 만든 배율은 같은 m/px를 돌려준다.
        val measured = MapScale.fromMetersPerPx(1.67, 14, 37.6)
        assertEquals(1.67, 2 * Math.PI * 6_378_137.0 * kotlin.math.cos(Math.toRadians(37.6)) / measured.worldPx(14), 1e-9)
    }

    // ---------- 모드 전이표(DESIGN 12.3) ----------

    @Test fun transitionTable() {
        val fitted = CameraMode.Fitted(FitTarget.SELECTED_ROUTE)
        fun action(mode: CameraMode, request: CameraRequest) = cameraAction(mode, request)
        // 사용자 이동 뒤: 선택·필터 변경, 행 탭(미리보기 열기), "지도 맞춤"은 맞춘다. 미리보기 닫기·시트 이동은 맞추지 않는다.
        assertEquals(CameraAction.FIT, action(CameraMode.UserMoved, CameraRequest.Fit(FitReason.SELECTION)).first)
        assertEquals(CameraAction.FIT, action(CameraMode.UserMoved, CameraRequest.Fit(FitReason.PREVIEW_OPEN)).first)
        assertEquals(CameraAction.FIT, action(CameraMode.UserMoved, CameraRequest.Fit(FitReason.EXPLICIT)).first)
        assertEquals(CameraAction.NONE, action(CameraMode.UserMoved, CameraRequest.Fit(FitReason.PREVIEW_CLOSE)).first)
        assertEquals(CameraAction.NONE, action(CameraMode.UserMoved, CameraRequest.ViewportChanged).first)
        // 맞춘 상태에서 미리보기 닫기는 맞춘다(#135).
        assertEquals(CameraAction.FIT, action(fitted, CameraRequest.Fit(FitReason.PREVIEW_CLOSE)).first)
        assertEquals(CameraAction.NONE to fitted, action(fitted, CameraRequest.ViewportChanged))
        // 끌기 = 사용자 이동.
        assertEquals(CameraAction.NONE to CameraMode.UserMoved, action(fitted, CameraRequest.Gesture))
        // 따라가기: 맞춤 요청은 무시, 멈추면 사용자 카메라, 다시 시작하면 차량 둘레 맞춤.
        val vehicle = LatLng(37.556, 126.973)
        assertEquals(CameraAction.FRAME_FOLLOW to CameraMode.Following, action(fitted, CameraRequest.FollowStart(vehicle)))
        assertEquals(CameraAction.NONE, action(CameraMode.Following, CameraRequest.Fit(FitReason.EXPLICIT)).first)
        assertEquals(CameraAction.NONE, action(CameraMode.Following, CameraRequest.GeometryChanged).first)
        assertEquals(CameraAction.RECENTER_FOLLOW, action(CameraMode.Following, CameraRequest.FollowTo(vehicle)).first)
        assertEquals(CameraAction.NONE to CameraMode.UserMoved, action(CameraMode.Following, CameraRequest.FollowStop))
        assertEquals(CameraAction.FRAME_FOLLOW to CameraMode.Following, action(CameraMode.UserMoved, CameraRequest.FollowStart(vehicle)))
        // 지도 선택·현재 위치: 가운데.
        assertEquals(CameraAction.CENTER to CameraMode.Centered, action(CameraMode.UserMoved, CameraRequest.CenterOn(vehicle)))
    }

    @Test fun screenRequestsBecomeFitsOnlyWhenThePolicySaysSo() {
        // 후보 화면: 사용자가 옮긴 뒤 미리보기 닫기(이유 PREVIEW_CLOSE)는 맞추지 않고, "지도 맞춤"(EXPLICIT)은 맞춘다.
        val policy = MapFitPolicy()
        val v = viewport(390f, 600f, bottomDp = 300f)
        val state = listState.copy(padding = MapPadding(bottom = 300.dp))
        assertNotNull(policy.update(state, input(state, v, null)))
        policy.gesture()
        assertEquals(CameraMode.UserMoved, policy.mode)
        val fits = policy.fits
        assertNull(policy.update(state.copy(cameraRevision = 1, fitReason = FitReason.PREVIEW_CLOSE), input(state, v, null)))
        assertEquals(fits, policy.fits)
        assertNotNull(policy.update(state.copy(cameraRevision = 2, fitReason = FitReason.EXPLICIT), input(state, v, null)))
        assertEquals(fits + 1, policy.fits)
        assertEquals(CameraMode.Fitted(FitTarget.SELECTED_ROUTE), policy.mode)
        // 뷰 크기를 모르면 요청을 꺼내지 않고 남긴다.
        assertNull(policy.update(state.copy(cameraRevision = 3), input(state, Viewport(0f, 0f), null)))
        assertNotNull(policy.update(state.copy(cameraRevision = 3), input(state, v, null)))
    }
}
