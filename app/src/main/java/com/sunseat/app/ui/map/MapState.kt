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

/** Shared camera decision for SDK and test renderer. Padding updates alone need not fit. */
internal class MapFitPolicy {
    private var previous: MapState? = null
    fun shouldFit(state: MapState): Boolean {
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

/**
 * 카메라 맞춤 틀(px). 지도 여백 [top]·[bottom]·[side](좌우 같음)과 네 변에 같이 들어가는 맞춤 [inset].
 * 실제 SDK 지도(setPadding + fitMapPoints)와 테스트 지도가 같은 값을 쓴다.
 */
internal data class FitFrame(val top: Float, val bottom: Float, val side: Float, val inset: Float)

/**
 * [top]·[bottom]은 지도 위·아래 여백(헤더·시트 겹침), [inset]은 기본 맞춤 inset, [edge]는 가장 작은 맞춤 inset,
 * [markerHeight]는 좌표 위로 솟는 출발·도착 표식 높이(틈 포함), [markerHalfWidth]는 표식 절반 폭이다.
 *
 * #138: 맞춤 inset은 네 변에 같이 들어가므로 낮은 지도 띠(후보 목록 기본 상태 약 110dp)에서는 기본 inset 두 배가
 * 높이 대부분을 차지해 지도가 크게 축소됐다. Kakao 줌은 정수 단계라 경로 높이가 두 배 들어갈 자리가 있어야 한 단계 가까워진다.
 * 그래서 경로·표식 좌표가 보이는 높이의 [FIT_SHARE] 이상을 쓰도록 필요한 만큼만 inset을 줄이고(최소 [edge]),
 * 표식이 솟는 위쪽과 표식 폭이 걸리는 좌우에만 모자라는 만큼 지도 여백을 더한다(아래는 표식 꼬리 끝이 좌표라 더하지 않는다).
 * 보이는 높이가 200dp 이상인 지도(미리보기·결과·세로 입력·지도 선택)는 기본 inset 그대로다.
 */
internal fun fitFrame(top: Float, bottom: Float, width: Float, height: Float, inset: Float, edge: Float, markerHeight: Float, markerHalfWidth: Float): FitFrame {
    val visible = (height - top - bottom).coerceAtLeast(0f)
    val spare = visible * (1 - FIT_SHARE) // 맞춤 inset 두 변 + 위쪽 표식 여백에 쓸 수 있는 높이
    // inset이 표식 높이 이상이면 위 여백을 더하지 않으므로 spare를 위아래 inset이 나눠 쓰고, 아니면 표식 높이를 먼저 뺀다.
    val needed = if (spare / 2 >= markerHeight) spare / 2 else spare - markerHeight
    val fitted = minOf(inset, maxOf(edge, needed))
    return FitFrame(top + (markerHeight - fitted).coerceAtLeast(0f), bottom, (markerHalfWidth - fitted).coerceAtLeast(0f), fitted)
}

/** 경로·표식 좌표가 보이는 지도 높이에서 최소한 차지할 비율. */
internal const val FIT_SHARE = 0.6f
