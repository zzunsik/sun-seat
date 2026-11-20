package com.sunseat.app.ui.map

import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.sunseat.app.domain.LatLng
import com.sunseat.app.domain.Route
import com.sunseat.app.domain.NamedPlace
import kotlin.math.cos

/** 지도에 필요한 값만 전달한다. 진행 위치 변경은 경로선을 다시 만들지 않는다. */
data class MapState(
    val routes: List<Route> = emptyList(),
    val selectedRoute: Int = 0,
    val origin: NamedPlace? = null,
    val destination: NamedPlace? = null,
    val cursor: MapCursor? = null,
    val followCursor: Boolean = false,
    val padding: MapPadding = MapPadding(),
    val onGesture: () -> Unit = {},
    val routeProgress: Float? = null,
    val showDecorations: Boolean = true,
    val focus: MapFocus? = null,
    /** Interactive sheets update padding without moving a user-controlled camera. */
    val automaticFit: Boolean = true,
    val cameraRevision: Long = 0,
    val fitEnabled: Boolean = true,
    /** 지도 가운데(지도에서 위치 선택, #85). 처음 만들 때(화면 재생성 포함) 이 위치에서 시작하고, 이후에는 순번이 바뀔 때만 카메라를 옮긴다. */
    val center: MapFocus? = null,
    /** 카메라가 멈췄을 때의 가운데 좌표(지도 선택의 핀 좌표). */
    val onCameraIdle: ((LatLng) -> Unit)? = null,
    /** 지도 키 없음·인증 실패로 지도를 쓸 수 없다. */
    val onUnavailable: () -> Unit = {},
)

/** A new sequence requests one camera move, even when the coordinate is unchanged. */
data class MapFocus(val location: LatLng, val sequence: Long)
data class MapCursor(val position: LatLng, val heading: Double, val sunAzimuth: Double, val sunAltitude: Double)
data class MapPadding(val top: Dp = 0.dp, val bottom: Dp = 0.dp)

/** 테스트 지도는 실제 좌표를 종횡비를 유지해 투영한다. */
internal class MapProjection(points: List<LatLng>, private val width: Float, private val height: Float, private val inset: Float) {
    private val centerLat = points.map { it.latitude }.average().takeIf { it.isFinite() } ?: 0.0
    private val longitudeScale = cos(Math.toRadians(centerLat)).coerceAtLeast(0.01)
    private val minX = points.minOfOrNull { it.longitude * longitudeScale } ?: 0.0
    private val maxX = points.maxOfOrNull { it.longitude * longitudeScale } ?: 0.0
    private val minY = points.minOfOrNull { it.latitude } ?: 0.0
    private val maxY = points.maxOfOrNull { it.latitude } ?: 0.0
    private val scale = minOf((width - 2 * inset).coerceAtLeast(1f) / (maxX - minX).coerceAtLeast(0.0001), (height - 2 * inset).coerceAtLeast(1f) / (maxY - minY).coerceAtLeast(0.0001))
    fun project(point: LatLng): Pair<Float, Float> =
        ((width / 2 + (point.longitude * longitudeScale - (minX + maxX) / 2) * scale).toFloat()) to
        ((height / 2 - (point.latitude - (minY + maxY) / 2) * scale).toFloat())
    /** [project]의 역. 테스트 지도에서 화면 가운데의 좌표(지도 선택 핀)를 구한다. */
    fun unproject(x: Float, y: Float): LatLng =
        LatLng((height / 2 - y) / scale + (minY + maxY) / 2, ((x - width / 2) / scale + (minX + maxX) / 2) / longitudeScale)
}
internal fun MapState.points(): List<LatLng> =
    (routes.getOrNull(selectedRoute)?.let { listOf(it) } ?: routes).flatMap { it.legs }.flatMap { it.path } +
        listOfNotNull(origin?.location, destination?.location)

/** 카메라 이동 종류. [FRAME_FOLLOW]는 차량 둘레 [followBounds]를 맞춘다(#146). */
internal enum class MapCamera { NONE, FIT_ROUTE, FRAME_FOLLOW }

/** 따라가기 중인 차량 위치. 따라가기가 아니거나 차량이 없으면 null. */
internal val MapState.followTarget: LatLng? get() = cursor?.position?.takeIf { followCursor }

/**
 * 따라가기 중 차량 둘레로 보여 줄 반경(m). 전체 경로를 맞추면 긴 노선(예: 402번)에서 지도가 구 단위까지 축소되어
 * 출발 표식·노선 배지·차량 표식이 한 점에 겹쳤다(#146). 이 범위를 맞춰 거리 수준의 줌을 유지한다.
 */
internal const val FOLLOW_RADIUS_METERS = 250.0

/** [center]를 가운데에 둔, 한 변이 2×[radius]m인 범위의 남서·북동 꼭짓점. */
internal fun followBounds(center: LatLng, radius: Double = FOLLOW_RADIUS_METERS): List<LatLng> {
    val metersPerLat = com.sunseat.app.domain.ExposureAnalyzer.distanceMeters(center, LatLng(center.latitude + 1, center.longitude))
    val metersPerLon = com.sunseat.app.domain.ExposureAnalyzer.distanceMeters(center, LatLng(center.latitude, center.longitude + 1)).coerceAtLeast(1.0)
    val dLat = radius / metersPerLat
    val dLon = radius / metersPerLon
    return listOf(LatLng(center.latitude - dLat, center.longitude - dLon), LatLng(center.latitude + dLat, center.longitude + dLon))
}

/** Shared camera decision for SDK and test renderer. Padding updates alone need not fit. */
internal class MapFitPolicy {
    private var previous: MapState? = null
    private var framed = false
    /** 전체 경로 맞춤. 따라가기 중에는 하지 않는다([camera]). */
    fun shouldFit(state: MapState): Boolean = camera(state) == MapCamera.FIT_ROUTE

    /**
     * 따라가기 중에는 맞춤 요청(첫 화면·여백 변경·순번)을 전체 경로 대신 차량 둘레 맞춤으로 바꾸고,
     * 따라가기를 다시 시작할 때도 한 번 맞춘다. 그 사이 차량 이동은 렌더러가 가운데만 옮긴다(줌 유지).
     */
    fun camera(state: MapState): MapCamera {
        val requested = requested(state)
        val following = state.followTarget != null
        val frame = following && (requested || !framed)
        framed = following
        return when {
            frame -> MapCamera.FRAME_FOLLOW
            following -> MapCamera.NONE
            requested -> MapCamera.FIT_ROUTE
            else -> MapCamera.NONE
        }
    }

    private fun requested(state: MapState): Boolean {
        if (!state.fitEnabled || state.points().isEmpty()) return false
        val old = previous
        previous = state
        val geometryChanged = old == null || old.routes != state.routes || old.selectedRoute != state.selectedRoute ||
            old.origin != state.origin || old.destination != state.destination
        return old == null || old.cameraRevision != state.cameraRevision ||
            (state.automaticFit && (geometryChanged || old.padding != state.padding))
    }
}

/** Coordinates share the root space; the map already starts below the top panel. */
internal fun sheetMapPadding(mapTop: Dp, mapHeight: Dp, sheetTop: Dp): MapPadding =
    MapPadding(bottom = (mapTop + mapHeight - sheetTop).coerceIn(0.dp, mapHeight.coerceAtLeast(0.dp)))

/** Low sun is visually stronger; all rays retain the semantic sun color. */
internal fun sunRayOpacity(altitude: Double): Float = (1.0 - 0.30 * altitude.coerceIn(0.0, 90.0) / 90.0).toFloat()

internal val MapCameraRevisionKey = androidx.compose.ui.semantics.SemanticsPropertyKey<Long>("MapCameraRevision")
internal val MapVisibleHeightKey = androidx.compose.ui.semantics.SemanticsPropertyKey<Float>("MapVisibleHeight")
/** 지도 위쪽 여백(dp, 테스트 관측용). 입력 화면은 헤더의 실제 측정 높이다(#103). */
internal val MapTopPaddingKey = androidx.compose.ui.semantics.SemanticsPropertyKey<Float>("MapTopPadding")
