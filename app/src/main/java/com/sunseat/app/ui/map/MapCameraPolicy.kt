package com.sunseat.app.ui.map

import com.sunseat.app.domain.LatLng
import kotlin.math.PI
import kotlin.math.atan
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.ln
import kotlin.math.pow
import kotlin.math.tan

/*
 * #168: 지도 카메라 정책. 언제·어디로·어느 줌으로 카메라를 옮길지는 이 파일의 순수 함수가 정하고, 두 렌더러(카카오 SDK·테스트 지도)는
 * 결과(가운데 좌표 + 정수 줌)를 적용만 한다. 화면을 덮는 부품(헤더·시트·정보 카드·떠 있는 행동)은 지도 여백(SDK setPadding)이 아니라
 * "보이는 영역"으로만 들어온다. 그래서 시트를 끌어 보이는 영역만 바뀌면 카메라도 화면 내용도 그대로다(#148, #71).
 * 맞춤은 표식이 화면에서 차지하는 상자(발자국: 출발·도착 핀, 정류장 이름표)가 모두 보이는 영역 안에 드는 가장 큰 정수 줌이다(#138 #143 #151 #153).
 */

/** 좌표가 화면에 놓이는 점을 기준으로 한 화면 상자(px, 아래가 +). */
internal data class ScreenBox(val left: Float, val top: Float, val right: Float, val bottom: Float) {
    companion object { val Point = ScreenBox(0f, 0f, 0f, 0f) }
}

/** 표식 발자국: 좌표와, 그 좌표를 기준으로 표식이 화면에서 차지하는 상자. */
internal data class Footprint(val at: LatLng, val box: ScreenBox = ScreenBox.Point)

/**
 * 지도 뷰 크기와 그 위를 덮는 측정 부품의 두께(px). 보이는 영역 = 뷰 − 덮는 부품.
 * 카메라 가운데는 언제나 뷰 가운데다(지도 여백을 쓰지 않는다).
 */
internal data class Viewport(val width: Float, val height: Float, val top: Float = 0f, val bottom: Float = 0f, val left: Float = 0f, val right: Float = 0f) {
    val visibleLeft: Float get() = left.coerceIn(0f, width)
    val visibleRight: Float get() = (width - right).coerceAtLeast(visibleLeft)
    val visibleTop: Float get() = top.coerceIn(0f, height)
    val visibleBottom: Float get() = (height - bottom).coerceAtLeast(visibleTop)
    val visibleWidth: Float get() = visibleRight - visibleLeft
    val visibleHeight: Float get() = visibleBottom - visibleTop
    val ready: Boolean get() = width > 0f && height > 0f
}

/** 카메라: 뷰 가운데에 오는 좌표와 정수 줌. */
internal data class CameraPosition(val center: LatLng, val zoom: Int)

/**
 * 줌 → 배율. Web Mercator로 세계 전체 폭(경도 360°)이 줌 0에서 [worldPxAtZero] px이고 줌 한 단계마다 두 배다.
 * 테스트 지도는 256dp, 카카오 지도는 실기기에서 잰 값(`fromScreenPoint`)을 쓴다.
 */
internal data class MapScale(val worldPxAtZero: Double) {
    fun worldPx(zoom: Int): Double = worldPxAtZero * 2.0.pow(zoom)

    companion object {
        private const val EARTH_CIRCUMFERENCE_M = 2 * PI * 6_378_137.0

        /** 줌 [zoom]에서 위도 [latitude]의 m/px를 잰 값으로 배율을 구한다. */
        fun fromMetersPerPx(metersPerPx: Double, zoom: Int, latitude: Double): MapScale =
            MapScale(EARTH_CIRCUMFERENCE_M * cos(Math.toRadians(latitude)) / metersPerPx / 2.0.pow(zoom))
    }
}

/** Web Mercator 정규 좌표(0~1). 북이 위(작은 y)다. */
internal object Mercator {
    private const val MAX_LAT = 85.05112878
    fun x(longitude: Double): Double = (longitude + 180.0) / 360.0
    fun y(latitude: Double): Double {
        val phi = Math.toRadians(latitude.coerceIn(-MAX_LAT, MAX_LAT))
        return (1.0 - ln(tan(PI / 4 + phi / 2)) / PI) / 2.0
    }
    fun longitude(x: Double): Double = x * 360.0 - 180.0
    fun latitude(y: Double): Double = Math.toDegrees(2 * atan(exp(PI * (1 - 2 * y))) - PI / 2)
}

/** 카메라 [this]에서 좌표 [point]의 화면 위치(px). 뷰 크기 [width]×[height]. */
internal fun CameraPosition.toScreen(point: LatLng, width: Float, height: Float, scale: MapScale): Pair<Float, Float> {
    val s = scale.worldPx(zoom)
    return (width / 2 + (Mercator.x(point.longitude) - Mercator.x(center.longitude)) * s).toFloat() to
        (height / 2 + (Mercator.y(point.latitude) - Mercator.y(center.latitude)) * s).toFloat()
}

/** [toScreen]의 역. */
internal fun CameraPosition.fromScreen(x: Float, y: Float, width: Float, height: Float, scale: MapScale): LatLng {
    val s = scale.worldPx(zoom)
    return LatLng(Mercator.latitude(Mercator.y(center.latitude) + (y - height / 2) / s), Mercator.longitude(Mercator.x(center.longitude) + (x - width / 2) / s))
}

/** 발자국의 화면 상자(px, 뷰 기준). */
internal fun CameraPosition.screenBox(footprint: Footprint, width: Float, height: Float, scale: MapScale): ScreenBox {
    val (x, y) = toScreen(footprint.at, width, height, scale)
    return ScreenBox(x + footprint.box.left, y + footprint.box.top, x + footprint.box.right, y + footprint.box.bottom)
}

/** 맞춤 줌 범위. 최대 17은 카카오 `fitMapPoints`에 쓰던 상한(거리 단위)이다. */
internal const val FIT_MAX_ZOOM = 17
internal const val FIT_MIN_ZOOM = 1

/**
 * 발자국 [footprints]가 모두 보이는 영역 안(네 변 [gap] 안쪽)에 드는 가장 큰 정수 줌과, 그 줌에서 발자국 묶음을 보이는 영역 가운데에 두는 카메라.
 * 가장 작은 줌에서도 들지 않으면 그 줌에서 가운데에 둔다. 발자국이 없거나 뷰 크기를 모르면 null.
 */
internal fun fitCamera(footprints: List<Footprint>, viewport: Viewport, scale: MapScale, gap: Float, maxZoom: Int = FIT_MAX_ZOOM, minZoom: Int = FIT_MIN_ZOOM): CameraPosition? =
    if (footprints.isEmpty()) null else fitCamera({ footprints }, viewport, scale, gap, maxZoom, minZoom)

/** [fitCamera]와 같되 발자국이 줌마다 다르다(표식 배치는 화면 거리에 따라 바뀐다, #170). */
internal fun fitCamera(footprintsAt: (Int) -> List<Footprint>, viewport: Viewport, scale: MapScale, gap: Float, maxZoom: Int = FIT_MAX_ZOOM, minZoom: Int = FIT_MIN_ZOOM): CameraPosition? {
    if (!viewport.ready) return null
    val roomX = (viewport.visibleWidth - 2 * gap).toDouble()
    val roomY = (viewport.visibleHeight - 2 * gap).toDouble()
    for (zoom in maxZoom downTo minZoom) {
        val footprints = footprintsAt(zoom)
        if (footprints.isEmpty()) return null
        val xs = footprints.map { Mercator.x(it.at.longitude) }
        val ys = footprints.map { Mercator.y(it.at.latitude) }
        val s = scale.worldPx(zoom)
        val left = footprints.indices.minOf { xs[it] * s + footprints[it].box.left }
        val right = footprints.indices.maxOf { xs[it] * s + footprints[it].box.right }
        val top = footprints.indices.minOf { ys[it] * s + footprints[it].box.top }
        val bottom = footprints.indices.maxOf { ys[it] * s + footprints[it].box.bottom }
        if ((right - left <= roomX && bottom - top <= roomY) || zoom == minZoom) {
            // 발자국 묶음의 가운데(세계 px)를 보이는 영역 가운데에 둔다. 카메라 가운데는 뷰 가운데이므로 두 가운데의 차이만큼 옮긴다.
            val centerX = (left + right) / 2 + (viewport.width / 2 - (viewport.visibleLeft + viewport.visibleRight) / 2)
            val centerY = (top + bottom) / 2 + (viewport.height / 2 - (viewport.visibleTop + viewport.visibleBottom) / 2)
            return CameraPosition(LatLng(Mercator.latitude(centerY / s), Mercator.longitude(centerX / s)), zoom)
        }
    }
    return null
}

/** 좌표 [location]을 보이는 영역 가운데에 두는 줌 [zoom]의 카메라. */
internal fun centerCamera(location: LatLng, zoom: Int, viewport: Viewport, scale: MapScale): CameraPosition {
    val s = scale.worldPx(zoom)
    val x = Mercator.x(location.longitude) * s + (viewport.width / 2 - (viewport.visibleLeft + viewport.visibleRight) / 2)
    val y = Mercator.y(location.latitude) * s + (viewport.height / 2 - (viewport.visibleTop + viewport.visibleBottom) / 2)
    return CameraPosition(LatLng(Mercator.latitude(y / s), Mercator.longitude(x / s)), zoom)
}

/**
 * 맞춤 틈(px): 보이는 영역 가장자리와 발자국 사이. 기본은 `Sizes.mapInset`([inset])이다. 낮은 영역에서는 보이는 높이의 [FIT_SHARE]를 뺀 나머지를
 * 위아래 틈과 출발·도착 표식 높이([markerHeight])가 나눠 쓰도록 줄이되 [edge](`Spacing.s`)보다 작게 하지 않는다([fitFrame]의 inset 규칙, #138).
 * 표식 높이는 발자국이 이미 품으므로 틈에서 그만큼 덜어 낸다.
 */
internal fun fitGap(viewport: Viewport, inset: Float, edge: Float, markerHeight: Float): Float =
    fitFrame(viewport.visibleTop, viewport.height - viewport.visibleBottom, viewport.width, viewport.height, inset, edge, markerHeight).inset

/** 카메라 모드. 화면이 "지도 맞춤" 행동을 보일지(#141) 같은 결정을 이 상태에서 읽는다. */
sealed interface CameraMode {
    /** 아직 카메라를 정하지 않았다. */
    data object Unset : CameraMode
    /** 정책이 [target]에 맞췄다. */
    data class Fitted(val target: FitTarget) : CameraMode
    /** 사용자가 지도를 직접 움직였다(#71). */
    data object UserMoved : CameraMode
    /** 햇빛 따라가기: 차량 둘레(#146). */
    data object Following : CameraMode
    /** 한 좌표를 가운데에 둠: 지도 선택 핀(#85)·현재 위치(#68). */
    data object Centered : CameraMode
}

/** 맞춘 대상. */
enum class FitTarget { SELECTED_ROUTE, ALL_ROUTES, ENDPOINTS }

/**
 * 맞춤 요청 이유. [overridesUserMove]가 참이면 사용자가 지도를 옮긴 뒤에도 맞춘다(DESIGN 12.3:
 * 선택·필터 변경·행 탭(미리보기 열기)·명시적 "지도 맞춤"은 맞추고, 미리보기 닫기·시트 이동은 맞추지 않는다).
 */
enum class FitReason(val overridesUserMove: Boolean) {
    /** 화면을 처음 그림. */
    INITIAL(false),
    /** 그리는 경로·끝점이 바뀜(자동 맞춤 화면, 입력의 끝점 변경). */
    GEOMETRY(true),
    /** 후보 집합·선택 변경(범주 필터 포함). */
    SELECTION(true),
    /** 미리보기 열기(행 탭). */
    PREVIEW_OPEN(true),
    /** 미리보기 닫기(#135). 수동 이동 뒤에는 맞추지 않는다. */
    PREVIEW_CLOSE(false),
    /** "지도 맞춤" 행동(#102, #141). */
    EXPLICIT(true),
}

/** 카메라 요청. */
internal sealed interface CameraRequest {
    data class Fit(val reason: FitReason) : CameraRequest
    /** 사용자가 지도를 끌거나 확대했다. */
    data object Gesture : CameraRequest
    /** 따라가기 시작(또는 재시작): 차량 둘레를 맞춘다. */
    data class FollowStart(val target: LatLng) : CameraRequest
    /** 따라가는 차량이 움직였다: 가운데만 옮기고 줌은 그대로. */
    data class FollowTo(val target: LatLng) : CameraRequest
    data object FollowStop : CameraRequest
    /** 보이는 영역만 바뀜(시트 끌기, 덮는 부품 높이 변화). */
    data object ViewportChanged : CameraRequest
    /** 그리는 경로·끝점이 바뀜. 자동 맞춤 화면만 보낸다. */
    data object GeometryChanged : CameraRequest
    /** [location]을 보이는 영역 가운데로. [zoom]이 null이면 지금 줌. */
    data class CenterOn(val location: LatLng, val zoom: Int? = null) : CameraRequest
}

/** 정책이 할 일의 종류(좌표 계산 전). */
internal enum class CameraAction { NONE, FIT, FRAME_FOLLOW, RECENTER_FOLLOW, CENTER }

/** 모드 전이표. 좌표와 무관한 부분이다. */
internal fun cameraAction(mode: CameraMode, request: CameraRequest): Pair<CameraAction, CameraMode> = when (request) {
    is CameraRequest.Fit -> when {
        mode == CameraMode.Following -> CameraAction.NONE to mode
        mode == CameraMode.UserMoved && !request.reason.overridesUserMove -> CameraAction.NONE to mode
        else -> CameraAction.FIT to mode
    }
    CameraRequest.GeometryChanged -> if (mode == CameraMode.Following) CameraAction.NONE to mode else CameraAction.FIT to mode
    CameraRequest.Gesture -> CameraAction.NONE to CameraMode.UserMoved
    is CameraRequest.FollowStart -> CameraAction.FRAME_FOLLOW to CameraMode.Following
    is CameraRequest.FollowTo -> if (mode == CameraMode.Following) CameraAction.RECENTER_FOLLOW to mode else CameraAction.NONE to mode
    CameraRequest.FollowStop -> CameraAction.NONE to (if (mode == CameraMode.Following) CameraMode.UserMoved else mode)
    // #148 #71: 보이는 영역만 바뀌면 카메라를 옮기지 않는다. 따라가기는 새 보이는 영역 가운데에 차량 둘레를 다시 맞춘다(#146).
    CameraRequest.ViewportChanged -> if (mode == CameraMode.Following) CameraAction.FRAME_FOLLOW to mode else CameraAction.NONE to mode
    is CameraRequest.CenterOn -> CameraAction.CENTER to CameraMode.Centered
}

/** 정책 입력. [footprints]는 맞춤 대상의 발자국, [followTarget]은 따라가기 중 차량(도착 뒤 도착지) 위치. */
internal data class CameraInput(
    val viewport: Viewport,
    val scale: MapScale,
    val footprints: List<Footprint> = emptyList(),
    val fitTarget: FitTarget = FitTarget.SELECTED_ROUTE,
    val gap: Float = 0f,
    val current: CameraPosition? = null,
    val followTarget: LatLng? = null,
    val followRadiusMeters: Double = FOLLOW_RADIUS_METERS,
    val defaultZoom: Int = FIT_MAX_ZOOM,
    /** 줌마다 다른 발자국(표식 배치 결과, #170). 있으면 [footprints] 대신 쓴다. */
    val footprintsAt: ((Int) -> List<Footprint>)? = null,
)

/** 정책 결과: 옮길 카메라(없으면 그대로)와 다음 모드. */
internal data class CameraDecision(val move: CameraPosition?, val mode: CameraMode)

/**
 * 카메라 정책: 지금 모드와 요청, 보이는 영역·발자국으로 카메라 이동과 다음 모드를 정한다. 맞출 수 없으면(발자국 없음·뷰 크기 모름) 옮기지 않고 모드도 그대로다.
 */
internal fun cameraPolicy(mode: CameraMode, request: CameraRequest, input: CameraInput): CameraDecision {
    val (action, next) = cameraAction(mode, request)
    val move = when (action) {
        CameraAction.NONE -> null
        CameraAction.FIT -> input.footprintsAt?.let { fitCamera(it, input.viewport, input.scale, input.gap) } ?: fitCamera(input.footprints, input.viewport, input.scale, input.gap)
        CameraAction.FRAME_FOLLOW -> (if (request is CameraRequest.FollowStart) request.target else input.followTarget)?.let { at ->
            // 차량 둘레 2×반경 범위를 보이는 영역에 넣는 줌에서 차량을 보이는 영역 가운데에 둔다(#146).
            val zoom = fitCamera(followBounds(at, input.followRadiusMeters).map { Footprint(it) }, input.viewport, input.scale, 0f)?.zoom ?: return@let null
            centerCamera(at, zoom, input.viewport, input.scale)
        }
        CameraAction.RECENTER_FOLLOW -> (request as CameraRequest.FollowTo).target.let { at ->
            input.current?.let { centerCamera(at, it.zoom, input.viewport, input.scale) }
                ?: fitCamera(followBounds(at, input.followRadiusMeters).map { Footprint(it) }, input.viewport, input.scale, 0f)?.let { centerCamera(at, it.zoom, input.viewport, input.scale) }
        }
        CameraAction.CENTER -> (request as CameraRequest.CenterOn).let { center ->
            if (!input.viewport.ready) null
            else centerCamera(center.location, center.zoom ?: input.current?.zoom ?: input.defaultZoom, input.viewport, input.scale)
        }
    }
    // 이동해야 하는데 못 하면(발자국 없음·뷰 크기 모름) 모드를 바꾸지 않는다. 사용자 이동·따라가기 멈춤은 이동 없이 바뀐다.
    val after = when {
        action == CameraAction.NONE -> next
        move == null -> mode
        action == CameraAction.FIT -> CameraMode.Fitted(input.fitTarget)
        else -> next
    }
    return CameraDecision(move, after)
}
