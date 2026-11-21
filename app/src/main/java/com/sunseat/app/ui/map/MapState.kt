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
/**
 * 따라가기 중 카메라가 둘 위치. 평소에는 차량 위치이고, 도착하면 차량 대신 남긴 도착 표식이 화면 안에 들도록 도착지다(#152).
 * 따라가기가 아니거나 차량이 없으면 null.
 */
internal val MapState.followTarget: LatLng? get() = cursor?.position?.takeIf { followCursor }?.let { at -> destination?.location?.takeIf { arrived } ?: at }

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

/** 재생이 끝까지 왔다(이동 100%). */
internal val MapState.arrived: Boolean get() = (routeProgress ?: 0f) >= 1f

/**
 * 지도에 그릴 차량(재생 커서). 도착하면 도착 표식이 그 순간의 핵심이라 차량을 거두고 도착 표식을 남긴다(#152).
 * 카메라는 [followTarget](도착지)을 따른다.
 */
internal val MapState.visibleCursor: MapCursor? get() = cursor?.takeUnless { arrived }

/**
 * 재생 중 차량 표식과 화면에서 겹치는 글자 표식(출발·도착·노선 배지)은 숨긴다(#146). 차량이 지금 보여 줄 정보이고,
 * 출발지가 곧 승차 정류장이면 줌과 관계없이 세 표식이 한 점에 겹친다. 정류장 원은 남긴다.
 * 도착한 뒤에는 차량을 그리지 않으므로([visibleCursor]) 아무것도 숨기지 않는다(#152).
 * [distancePx]는 렌더러의 현재 투영으로 잰 두 좌표의 화면 거리, [markerPx]는 차량 표식 크기다.
 */
internal fun MapState.coveredByVehicle(at: LatLng, markerPx: Float, distancePx: (LatLng, LatLng) -> Float): Boolean {
    if (routeProgress == null || arrived) return false
    val vehicle = cursor?.position ?: return false
    return distancePx(vehicle, at) < markerPx
}

/** Shared camera decision for SDK and test renderer. Padding updates alone need not fit. */
internal class MapFitPolicy {
    private var previous: MapState? = null
    private var framed = false

    /**
     * 맞춤 틀 좌우에 정류장 노선 이름표 폭을 둘지(#143). 경로 맞춤에서만 둔다. 따라가기 맞춤(#146)은 차량을 가운데에 두므로
     * 두지 않고, 따라가기를 멈춰도(드래그) 다음 경로 맞춤 전까지는 그대로 두어 여백이 바뀌며 지도가 밀리지 않게 한다.
     */
    var labelMargins = true
        private set

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
        val move = when {
            frame -> MapCamera.FRAME_FOLLOW
            following -> MapCamera.NONE
            requested -> MapCamera.FIT_ROUTE
            else -> MapCamera.NONE
        }
        if (move != MapCamera.NONE) labelMargins = move == MapCamera.FIT_ROUTE
        return move
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

/**
 * 카메라 맞춤 틀(px). 지도 여백 [top]·[bottom]·[left]·[right]와 네 변에 같이 들어가는 맞춤 [inset]. [side]는 이름표가 없을 때의 좌우 여백이다.
 * 실제 SDK 지도(setPadding + fitMapPoints)와 테스트 지도가 같은 값을 쓴다.
 */
internal data class FitFrame(val top: Float, val bottom: Float, val side: Float, val inset: Float, val left: Float = side, val right: Float = side)

/**
 * [top]·[bottom]은 지도 위·아래 여백(헤더·시트 겹침), [inset]은 기본 맞춤 inset, [edge]는 가장 작은 맞춤 inset,
 * [markerHeight]는 좌표 위로 솟는 출발·도착 표식 높이(틈 포함)다. [labelLeft]·[labelRight]는 정류장 노선 이름표가 좌표에서
 * 왼쪽·오른쪽으로 뻗는 최대 폭([labelExtents])이다. 어느 정류장이 가장자리에 올지 맞춤 전에는 모르므로 가장 넓은 이름표 기준이다.
 *
 * #138: 맞춤 inset은 네 변에 같이 들어가므로 낮은 지도 띠(후보 목록 기본 상태 약 110dp)에서는 기본 inset 두 배가
 * 높이 대부분을 차지해 지도가 크게 축소됐다. Kakao 줌은 정수 단계라 경로 높이가 두 배 들어갈 자리가 있어야 한 단계 가까워진다.
 * 그래서 경로·표식 좌표가 보이는 높이의 [FIT_SHARE] 이상을 쓰도록 필요한 만큼만 inset을 줄이고(최소 [edge]),
 * 위쪽에는 솟는 표식 높이만큼 지도 여백을 더하고(아래는 표식 꼬리 끝이 좌표라 더하지 않는다), 좌우는 줄인 inset만큼 지도 여백으로
 * 돌려 기본 inset과 같은 폭을 남긴다(높이가 맞춤을 제한하므로 손해가 없고, 가장자리 정류장 노선 이름표가 잘리지 않는다).
 * 보이는 높이가 200dp 이상인 지도(미리보기·결과·세로 입력·지도 선택)는 기본 inset 그대로다.
 */
internal fun fitFrame(top: Float, bottom: Float, width: Float, height: Float, inset: Float, edge: Float, markerHeight: Float, labelLeft: Float = 0f, labelRight: Float = 0f): FitFrame {
    val visible = (height - top - bottom).coerceAtLeast(0f)
    val spare = visible * (1 - FIT_SHARE) // 맞춤 inset 두 변 + 위쪽 표식 여백에 쓸 수 있는 높이
    // inset이 표식 높이 이상이면 위 여백을 더하지 않으므로 spare를 위아래 inset이 나눠 쓰고, 아니면 표식 높이를 먼저 뺀다.
    val needed = if (spare / 2 >= markerHeight) spare / 2 else spare - markerHeight
    val fitted = minOf(inset, maxOf(edge, needed))
    // #143: 정류장 옆 노선 이름표가 inset보다 넓으면 그쪽 지도 여백을 이름표 폭만큼 둔다(그 밖의 좌우는 기본 inset 폭).
    return FitFrame(top + (markerHeight - fitted).coerceAtLeast(0f), bottom, inset - fitted, fitted,
        left = maxOf(inset, labelLeft) - fitted, right = maxOf(inset, labelRight) - fitted)
}

/** 경로·표식 좌표가 보이는 지도 높이에서 최소한 차지할 비율. */
internal const val FIT_SHARE = 0.6f

/**
 * 정류장 옆 노선 이름표가 정류장 좌표에서 왼쪽·오른쪽으로 뻗는 최대 폭(px). [labelWidth]는 렌더러가 그리는 이름표 상자 폭이다.
 * [boardClearance]는 첫 승차 정류장 이름표가 출발 표식을 비켜 밀려나는 거리다(#153, [stopLabelOffset]). 축소된 맞춤 화면에서는
 * 출발지와 첫 승차 정류장이 거의 한 점이라 그만큼 밀린다.
 */
internal fun labelExtents(stops: List<StopMarker>, stopRadius: Float, boardClearance: Float = 0f, labelWidth: (StopMarker) -> Float): Pair<Float, Float> {
    val labeled = stops.filter { it.labeled }
    fun reach(stop: StopMarker) = (if (stop.kind == StopKind.BOARD) maxOf(stopRadius, boardClearance) else stopRadius) + labelWidth(stop)
    fun side(onLeft: Boolean) = labeled.filter { it.labelOnLeft == onLeft }.maxOfOrNull { reach(it) } ?: 0f
    return side(true) to side(false)
}

/** 출발·도착 표식 몸통의 화면 상자(px). 정류장 좌표 기준이며 아래가 +다. 꼬리는 좁아 넣지 않는다. */
internal data class PinBox(val left: Float, val top: Float, val right: Float, val bottom: Float)

/** 출발·도착 글자 표식의 몸통 폭(px). 두 렌더러가 그리기와 [PinBox]에 같이 쓴다. */
internal fun placeMarkerWidth(textWidth: Float, marker: Float, tail: Float, padding: Float): Float = maxOf(marker - tail, textWidth + padding * 4)

/** 꼬리 끝이 정류장 기준 ([dx], [dy])에 있는 폭 [width] 표식의 몸통 상자. */
internal fun placeMarkerBox(dx: Float, dy: Float, width: Float, marker: Float, tail: Float): PinBox =
    PinBox(dx - width / 2, dy - marker, dx + width / 2, dy - tail)

/**
 * #153: 정류장 노선 이름표가 정류장 좌표에서 떨어지는 거리(px). 기본은 정류장 반지름 [stopRadius]다.
 * 출발·도착 표식([pins])이 이름표 자리를 덮으면(예: 출발지 바로 옆 승차 정류장의 "402"가 출발 표식 밑에 가려 "40"으로 보임)
 * 이름표를 표식 바깥으로 [gap]만큼 띄워 민다. [maxPush]보다 더 밀어야 하면 그 표식은 무시한다:
 * 이름표가 정류장에서 너무 떨어지면 어느 정류장 것인지 읽히지 않는다.
 */
internal fun stopLabelOffset(onLeft: Boolean, labelWidth: Float, labelHeight: Float, stopRadius: Float, pins: List<PinBox>, gap: Float, maxPush: Float): Float {
    var offset = stopRadius
    // 한 표식을 비키면 다른 표식에 걸릴 수 있어 바뀌지 않을 때까지 다시 본다(표식은 둘뿐이다).
    repeat(pins.size + 1) {
        var moved = false
        for (pin in pins) {
            val left = if (onLeft) -offset - labelWidth else offset
            val covered = left < pin.right && left + labelWidth > pin.left && -labelHeight / 2 < pin.bottom && labelHeight / 2 > pin.top
            if (!covered) continue
            val needed = (if (onLeft) -pin.left else pin.right) + gap
            if (needed > offset && needed - stopRadius <= maxPush) { offset = needed; moved = true }
        }
        if (!moved) return offset
    }
    return offset
}
