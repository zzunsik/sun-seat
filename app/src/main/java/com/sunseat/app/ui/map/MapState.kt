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
    /**
     * 지도 위·아래를 덮는 부품의 측정 두께(헤더·시트·정보 카드·떠 있는 행동). 카메라 정책의 보이는 영역이 되며 SDK 지도 여백으로는 쓰지 않는다(#168).
     * 이 값만 바뀌면 카메라는 움직이지 않는다(#148).
     */
    val padding: MapPadding = MapPadding(),
    val onGesture: () -> Unit = {},
    val routeProgress: Float? = null,
    val showDecorations: Boolean = true,
    val focus: MapFocus? = null,
    /** Interactive sheets update padding without moving a user-controlled camera. */
    val automaticFit: Boolean = true,
    val cameraRevision: Long = 0,
    /** [cameraRevision]을 바꾼 이유. 사용자가 지도를 옮긴 뒤 맞출지를 정책이 이 이유로 정한다(DESIGN 12.3). */
    val fitReason: FitReason = FitReason.EXPLICIT,
    val fitEnabled: Boolean = true,
    /** 지도 가운데(지도에서 위치 선택, #85). 처음 만들 때(화면 재생성 포함) 이 위치에서 시작하고, 이후에는 순번이 바뀔 때만 카메라를 옮긴다. */
    val center: MapFocus? = null,
    /** 카메라가 멈췄을 때의 가운데 좌표(지도 선택의 핀 좌표). */
    val onCameraIdle: ((LatLng) -> Unit)? = null,
    /** 지도 키 없음·인증 실패로 지도를 쓸 수 없다. */
    val onUnavailable: () -> Unit = {},
    /** 사용 불가일 때 지도 위 안내를 화면에 맞게 바꾼다(#173: 지도 선택은 상태만 말하고 행동은 아래 패널이 말한다). null이면 기본 안내. */
    val unavailableMessage: Int? = null,
    /** 카메라 모드·맞춤 횟수가 바뀌면 알린다(#141: 사용자가 옮긴 뒤 "지도 맞춤"). */
    val onCameraStatus: ((CameraStatus) -> Unit)? = null,
)

/** A new sequence requests one camera move, even when the coordinate is unchanged. */
data class MapFocus(val location: LatLng, val sequence: Long)
data class MapCursor(val position: LatLng, val heading: Double, val sunAzimuth: Double, val sunAltitude: Double)
data class MapPadding(val top: Dp = 0.dp, val bottom: Dp = 0.dp)

/**
 * 좌표 묶음을 종횡비를 유지해 화면에 꽉 채우는 등비 투영(#168 이전 테스트 지도). 지금 테스트 지도는 카메라 정책과 같은 가운데 좌표·정수 줌
 * 투영([CameraPosition.toScreen])을 쓴다. 이 투영은 기존 투영 테스트(`MapProjectionTest`, `MapPickProjectionTest`)를 위해 남긴다.
 */
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
 * #170부터 렌더러의 숨김·비키기는 [markerLayout]이 정한다(차량은 고정 표식, 글자 표식은 비킨 뒤에도 겹칠 때만 정보 카드가 있으면 숨김). 이 거리 판정은 그 규칙의 "차량에 가림" 정의로 남긴다.
 * [distancePx]는 렌더러의 현재 투영으로 잰 두 좌표의 화면 거리, [markerPx]는 차량 표식 크기다.
 */
internal fun MapState.coveredByVehicle(at: LatLng, markerPx: Float, distancePx: (LatLng, LatLng) -> Float): Boolean {
    if (routeProgress == null || arrived) return false
    val vehicle = cursor?.position ?: return false
    return distancePx(vehicle, at) < markerPx
}

/**
 * [MapState]의 변화를 카메라 요청([CameraRequest])으로 바꾸고 모드를 들고 있는 렌더러 쪽 상태(#168). 결정은 [cameraPolicy]가 한다.
 * 두 렌더러가 같은 클래스를 쓴다. [camera]·[shouldFit]·[labelMargins]는 #146 이전부터 쓰던 판정 이름을 그대로 둔 얇은 어댑터다.
 */
internal class MapFitPolicy {
    /** 맞춤 판단에 마지막으로 쓴 상태. 맞출 수 없는 동안(fitEnabled 거짓·좌표 없음)에는 바꾸지 않아 그동안의 요청이 남는다. */
    private var previous: MapState? = null
    private var lastPadding: MapPadding? = null
    private var wasFollowing = false
    private var lastFollow: LatLng? = null
    private var lastCenter: MapFocus? = null
    private var lastFocus: MapFocus? = null

    var mode: CameraMode = CameraMode.Unset
        private set
    /** 화면·상태 변화가 요청해 정책이 실제로 맞춘 횟수(첫 화면 맞춤 제외). 화면 테스트가 보는 카메라 revision이다. */
    var fits = 0L
        private set

    /**
     * 마지막 카메라 이동이 경로 맞춤이었는지(#143 이름표 발자국을 쓴 맞춤). 따라가기 맞춤(#146)은 차량 둘레만 본다.
     * 따라가기를 멈춰도 다음 경로 맞춤 전까지 그대로다.
     */
    var labelMargins = true
        private set

    /** 지도를 사용자가 직접 움직였다(SDK 제스처, 테스트 지도 끌기). */
    fun gesture() { mode = cameraAction(mode, CameraRequest.Gesture).second }

    /** [state]의 변화로 생긴 요청. 따라가기·가운데 요청을 맞춤 요청보다 먼저 둔다(따라가는 동안 맞춤은 하지 않는다). */
    fun requests(state: MapState): List<CameraRequest> {
        val out = mutableListOf<CameraRequest>()
        val follow = state.followTarget
        when {
            follow != null && !wasFollowing -> out += CameraRequest.FollowStart(follow)
            follow != null && follow != lastFollow -> out += CameraRequest.FollowTo(follow)
            follow == null && wasFollowing -> out += CameraRequest.FollowStop
        }
        wasFollowing = follow != null
        lastFollow = follow
        // 지도 선택(#85)은 처음 열 때 거리 단위 줌(17)으로 열고, 이후 요청(현재 위치로 이동)은 지금 줌을 유지한다.
        state.center?.let { if (it.sequence != lastCenter?.sequence) { out += CameraRequest.CenterOn(it.location, if (lastCenter == null) FIT_MAX_ZOOM else null); lastCenter = it } }
        state.focus?.let { if (it != lastFocus) { out += CameraRequest.CenterOn(it.location); lastFocus = it } }
        if (lastPadding != null && lastPadding != state.padding) out += CameraRequest.ViewportChanged
        lastPadding = state.padding
        if (state.fitEnabled && state.points().isNotEmpty()) {
            val old = previous
            previous = state
            val geometryChanged = old != null && (old.routes != state.routes || old.selectedRoute != state.selectedRoute ||
                old.origin != state.origin || old.destination != state.destination)
            when {
                old == null -> out += CameraRequest.Fit(FitReason.INITIAL)
                old.cameraRevision != state.cameraRevision -> out += CameraRequest.Fit(state.fitReason)
                state.automaticFit && geometryChanged -> out += CameraRequest.GeometryChanged
            }
        }
        return out
    }

    /**
     * 렌더러용: [state]의 요청을 차례로 정책에 넣어 마지막으로 옮길 카메라를 돌려준다(없으면 null). 뷰 크기를 모르면 요청을 꺼내지 않고 다음으로 미룬다.
     * [input]의 `current`는 지금 카메라이며, 앞 요청이 옮긴 카메라로 이어 받는다.
     */
    fun update(state: MapState, input: CameraInput): CameraPosition? {
        if (!input.viewport.ready) return null
        var current = input.current
        var moved: CameraPosition? = null
        for (request in requests(state)) {
            val (action, _) = cameraAction(mode, request)
            val decision = cameraPolicy(mode, request, input.copy(current = current, followTarget = state.followTarget))
            mode = decision.mode
            decision.move?.let { move ->
                current = move; moved = move
                if (action == CameraAction.FIT) { if (request != CameraRequest.Fit(FitReason.INITIAL)) fits++; labelMargins = true }
                if (action == CameraAction.FRAME_FOLLOW) labelMargins = false
            }
        }
        return moved
    }

    /** 맞춤 종류만 본다(좌표 계산 없이). #146 이전 판정 테스트용 어댑터. */
    fun camera(state: MapState): MapCamera {
        var result = MapCamera.NONE
        for (request in requests(state)) {
            val (action, next) = cameraAction(mode, request)
            mode = if (action == CameraAction.FIT) CameraMode.Fitted(state.fitTarget) else next
            when (action) {
                CameraAction.FIT -> { result = MapCamera.FIT_ROUTE; labelMargins = true }
                CameraAction.FRAME_FOLLOW -> { result = MapCamera.FRAME_FOLLOW; labelMargins = false }
                else -> Unit
            }
        }
        return result
    }

    /** 전체 경로 맞춤. 따라가기 중에는 하지 않는다([camera]). */
    fun shouldFit(state: MapState): Boolean = camera(state) == MapCamera.FIT_ROUTE
}

/** 화면이 보는 카메라 상태: 모드(#141 "지도 맞춤" 표시)와 정책이 맞춘 횟수. */
data class CameraStatus(val mode: CameraMode, val fits: Long)

/** 맞춘 대상의 종류. */
internal val MapState.fitTarget: FitTarget get() = when {
    routes.isEmpty() -> FitTarget.ENDPOINTS
    routes.getOrNull(selectedRoute) == null -> FitTarget.ALL_ROUTES
    else -> FitTarget.SELECTED_ROUTE
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
/** 테스트 지도가 지금 카메라로 그린 발자국 상자(px, 지도 뷰 기준, 테스트 관측용, #168). */
internal val MapFootprintBoxesKey = androidx.compose.ui.semantics.SemanticsPropertyKey<List<ScreenBox>>("MapFootprintBoxes")
/** 테스트 지도의 보이는 표식(종류 이름, 화면 상자 px, 테스트 관측용, #170). */
internal val MapMarkBoxesKey = androidx.compose.ui.semantics.SemanticsPropertyKey<List<Pair<String, ScreenBox>>>("MapMarkBoxes")
/** 테스트 지도가 숨긴 표식(종류 이름, 정보가 남는 곳, 테스트 관측용, #170). */
internal val MapHiddenMarksKey = androidx.compose.ui.semantics.SemanticsPropertyKey<List<Pair<String, Fallback>>>("MapHiddenMarks")
/** 지도 사용 가능 상태(#173, 테스트 관측용). */
internal val MapAvailabilityKey = androidx.compose.ui.semantics.SemanticsPropertyKey<MapAvailability>("MapAvailability")
/** 카메라 정책의 보이는 영역(px, 지도 뷰 기준, 테스트 관측용, #168). */
internal val MapVisibleAreaKey = androidx.compose.ui.semantics.SemanticsPropertyKey<ScreenBox>("MapVisibleArea")

/**
 * 카메라 맞춤 틀(px). 지도 여백 [top]·[bottom]·[left]·[right]와 네 변에 같이 들어가는 맞춤 [inset]. [side]는 이름표가 없을 때의 좌우 여백이다.
 * #168부터 지도 여백(setPadding)은 쓰지 않고, 카메라 정책은 이 틀의 [inset] 규칙만 맞춤 틈([fitGap])으로 쓴다. 표식·이름표 몫은 발자국([footprints])이 맡는다.
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
 *
 * #151: 정류장 노선 이름표는 정류장 좌표를 세로 가운데로 두므로 [labelHeight](이름표 상자 높이 + 위아래 가장자리 틈)의 절반이
 * 줄인 inset보다 크면 아래쪽에 그만큼 지도 여백을 더한다(맨 아래 정류장 이름표가 시트 경계에 걸리지 않게). 위쪽은 표식 높이
 * 여백([markerHeight])이 이미 더 크다. 이 몫은 [FIT_SHARE] 계산에 넣지 않는다(최대 몇 dp라 줌 단계에 거의 영향이 없다).
 */
internal fun fitFrame(top: Float, bottom: Float, width: Float, height: Float, inset: Float, edge: Float, markerHeight: Float, labelLeft: Float = 0f, labelRight: Float = 0f, labelHeight: Float = 0f): FitFrame {
    val visible = (height - top - bottom).coerceAtLeast(0f)
    val spare = visible * (1 - FIT_SHARE) // 맞춤 inset 두 변 + 위쪽 표식 여백에 쓸 수 있는 높이
    // inset이 표식 높이 이상이면 위 여백을 더하지 않으므로 spare를 위아래 inset이 나눠 쓰고, 아니면 표식 높이를 먼저 뺀다.
    val needed = if (spare / 2 >= markerHeight) spare / 2 else spare - markerHeight
    val fitted = minOf(inset, maxOf(edge, needed))
    // #143: 정류장 옆 노선 이름표가 inset보다 넓으면 그쪽 지도 여백을 이름표 폭만큼 둔다(그 밖의 좌우는 기본 inset 폭).
    return FitFrame(top + (markerHeight - fitted).coerceAtLeast(0f), bottom + (labelHeight / 2 - fitted).coerceAtLeast(0f), inset - fitted, fitted,
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
    fun side(onLeft: Boolean) = labeled.filter { it.labelOnLeft == onLeft }.maxOfOrNull { labelReach(it, stopRadius, boardClearance, labelWidth(it)) } ?: 0f
    return side(true) to side(false)
}

/** 정류장 노선 이름표가 정류장 좌표에서 이름표 쪽으로 뻗는 거리(px): 정류장 반지름(첫 승차 정류장은 출발 표식을 비킨 거리, #153) + 이름표 폭. */
internal fun labelReach(stop: StopMarker, stopRadius: Float, boardClearance: Float, labelWidth: Float): Float =
    (if (stop.kind == StopKind.BOARD) maxOf(stopRadius, boardClearance) else stopRadius) + labelWidth

/**
 * 발자국을 만들 표식 크기(px). 두 렌더러가 그리기와 같은 측정으로 채운다.
 * [markerHeight]는 출발·도착 표식 높이 + 위 틈, [labelHeight]는 이름표 상자 높이 + 위아래 틈, [labelWidth]는 이름표 상자 폭 + 가장자리 틈이다.
 */
internal data class MarkerSizes(
    val markerHeight: Float,
    val originWidth: Float,
    val destinationWidth: Float,
    val stopRadius: Float,
    val labelHeight: Float,
    val boardClearance: Float,
    val labelWidth: (StopMarker) -> Float,
)

/**
 * 맞춤 대상의 발자국(#168): 경로 좌표(점), 출발·도착 표식(꼬리 끝이 좌표, 몸통은 위로), 선택 경로의 정류장 원과 노선 이름표.
 * 핀 높이(#138), 이름표 폭(#143)·높이(#151), 출발 표식을 비킨 첫 승차 이름표(#153)가 인자별 여백이 아니라 이 상자로 들어간다.
 */
internal fun MapState.footprints(sizes: MarkerSizes): List<Footprint> {
    val pins = listOfNotNull(
        origin?.let { Footprint(it.location, ScreenBox(-sizes.originWidth / 2, -sizes.markerHeight, sizes.originWidth / 2, 0f)) },
        destination?.let { Footprint(it.location, ScreenBox(-sizes.destinationWidth / 2, -sizes.markerHeight, sizes.destinationWidth / 2, 0f)) },
    )
    val r = sizes.stopRadius
    val stops = routes.getOrNull(selectedRoute)?.let { RouteLinePolicy.stops(it) }.orEmpty().map { stop ->
        if (!stop.labeled) Footprint(stop.location, ScreenBox(-r, -r, r, r))
        else {
            val reach = labelReach(stop, r, sizes.boardClearance, sizes.labelWidth(stop))
            val half = maxOf(r, sizes.labelHeight / 2)
            Footprint(stop.location, if (stop.labelOnLeft) ScreenBox(-reach, -half, r, half) else ScreenBox(-r, -half, reach, half))
        }
    }
    return points().map { Footprint(it) } + pins + stops
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
    // #170: 표식 배치 규칙(markerLayout) 위의 얇은 함수다. 표식은 고정, 이름표는 반지름에서 바깥으로 gap씩 maxPush까지 비킨다. 비킬 자리가 없으면 제자리.
    val origin = LatLng(0.0, 0.0)
    val marks = pins.mapIndexed { i, pin -> Mark("pin$i", MarkKind.ORIGIN, origin, 0f, 0f, listOf(ScreenBox(pin.left, pin.top, pin.right, pin.bottom)), MarkPriority.ORIGIN, fixed = true) } +
        Mark("label", MarkKind.STOP_LABEL, origin, 0f, 0f, labelVariants(onLeft, labelWidth, labelHeight, stopRadius, gap, maxPush), MarkPriority.STOP_LABEL, Fallback.TALKBACK_SUMMARY)
    val placement = markerLayout(marks).last()
    return if (placement.hidden) stopRadius else stopRadius + placement.variant * gap

}
