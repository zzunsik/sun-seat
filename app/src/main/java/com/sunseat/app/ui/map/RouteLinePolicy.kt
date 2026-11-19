package com.sunseat.app.ui.map

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.sunseat.app.domain.LatLng
import com.sunseat.app.domain.Mode
import com.sunseat.app.domain.Route
import com.sunseat.app.ui.theme.MapRouteColors
import com.sunseat.app.ui.theme.Sizes
import com.sunseat.app.ui.theme.TransitLineColors

/** 지도에서 경로선이 맡는 역할. docs/DESIGN.md 11.1 */
internal enum class RouteRole { SELECTED, ALTERNATE, PASSED }
internal enum class LinePattern { SOLID, DOTTED }

/**
 * 한 구간 선의 모양. [core]는 속 선, [edge]는 양쪽 테두리 하나의 두께([edgeColor]가 null이면 0).
 * [layer]는 아래(1)→위 순서이며 네이티브 SDK와 테스트 렌더러가 같은 순서로 그린다.
 */
internal data class RouteLineSpec(
    val color: Color,
    val core: Dp,
    val edgeColor: Color?,
    val edge: Dp,
    val pattern: LinePattern,
    val layer: Int,
    val arrows: Boolean,
) {
    val total: Dp get() = core + edge * 2
}

internal enum class StopKind { BOARD, TRANSFER, ALIGHT }

/** 탑승 구간 끝점에서만 만든 정류장 표식. [label]은 환승 후 노선 이름(모르면 null). */
internal data class StopMarker(val location: LatLng, val kind: StopKind, val color: Color, val label: String?)

/** TalkBack 요약의 한 단계. [line]이 null이면 수단 이름으로 읽는다. */
internal data class RideStep(val kind: StopKind, val mode: Mode, val line: String?, val stop: String)

/** 수단·노선·역할 → 선 모양. 네이티브와 테스트 렌더러가 함께 쓰는 단일 계약(#100). */
internal object RouteLinePolicy {
    private const val MIN_LINE_CONTRAST = 3.0

    fun forLeg(mode: Mode, lineName: String?, role: RouteRole): RouteLineSpec = when {
        role == RouteRole.ALTERNATE ->
            RouteLineSpec(MapRouteColors.alternate, Sizes.mapRouteAlternate, null, 0.dp, LinePattern.SOLID, layer = 1, arrows = false)
        mode == Mode.WALK ->
            // Passed overlay is a continuous progress line, so it is solid grey on top of the dotted walk.
            if (role == RouteRole.PASSED) RouteLineSpec(MapRouteColors.passed, Sizes.mapRouteWalk, null, 0.dp, LinePattern.SOLID, layer = 2, arrows = false)
            else RouteLineSpec(MapRouteColors.walk, Sizes.mapRouteWalk, null, 0.dp, LinePattern.DOTTED, layer = 2, arrows = false)
        else -> {
            val color = lineColor(mode, lineName)
            val edge = if (contrastOnWhite(color) < MIN_LINE_CONTRAST) MapRouteColors.outlineLowContrast else MapRouteColors.casing
            if (role == RouteRole.PASSED) RouteLineSpec(MapRouteColors.passed, Sizes.mapRouteCore, MapRouteColors.casing, Sizes.mapRouteCasing, LinePattern.SOLID, layer = 3, arrows = false)
            else RouteLineSpec(color, Sizes.mapRouteCore, edge, Sizes.mapRouteCasing, LinePattern.SOLID, layer = 3, arrows = true)
        }
    }

    /** 지하철은 노선 공식 색(한 노선으로 확정될 때만), 그 밖은 routeTransit. 모르는 노선은 추측하지 않는다. */
    fun lineColor(mode: Mode, lineName: String?): Color {
        if (mode != Mode.SUBWAY || lineName.isNullOrBlank()) return MapRouteColors.transit
        val colors = lineName.split(" / ").map { TransitLineColors.of(it) }
        return colors.distinct().singleOrNull() ?: MapRouteColors.transit
    }

    /** WCAG 대비(흰 바탕). */
    fun contrastOnWhite(color: Color): Double = 1.05 / (color.luminance() + 0.05)

    /**
     * Kakao RouteLineStyle의 lineWidth/strokeWidth는 SDK가 내부에서 KakaoMap.getMapDpScale()을 다시 곱한다
     * (SM-S936N density 3.75에서 mapDpScale 1.6977, 실측 선폭 = 인자 × 1.70). 화면 픽셀이 논리 dp × density가
     * 되도록 인자를 mapDpScale로 나눈다. 값을 아직 모르면(-1) 픽셀 그대로 넘긴다.
     */
    fun sdkWidth(width: Dp, density: Float, mapDpScale: Float): Float =
        width.value * density / (if (mapDpScale > 0f) mapDpScale else 1f)

    /** 같은 탑승(연속된 같은 transitStepIndex의 역 구간)을 하나로 묶은 탑승 구간들. */
    private fun rides(route: Route): List<List<com.sunseat.app.domain.Leg>> {
        val rides = mutableListOf<List<com.sunseat.app.domain.Leg>>()
        var previousIndex = -2
        route.legs.forEachIndexed { index, leg ->
            if (leg.mode != Mode.BUS && leg.mode != Mode.SUBWAY) return@forEachIndexed
            val last = rides.lastOrNull()?.last()
            if (last != null && previousIndex == index - 1 && leg.transitStepIndex != null && last.transitStepIndex == leg.transitStepIndex) rides[rides.size - 1] = rides.last() + leg
            else rides += listOf(leg)
            previousIndex = index
        }
        return rides
    }

    fun stops(route: Route): List<StopMarker> {
        val rides = rides(route).filter { ride -> ride.first().path.isNotEmpty() && ride.last().path.isNotEmpty() }
        return rides.flatMapIndexed { index, ride ->
            val first = ride.first()
            val color = lineColor(first.mode, first.lineName)
            val board = StopMarker(first.path.first(), if (index == 0) StopKind.BOARD else StopKind.TRANSFER, color, if (index == 0) null else first.lineName)
            // A transfer marker already marks where the next ride starts; only the final alight gets its own marker
            // so two markers do not pile up at short walking transfers (device check, #100).
            if (index < rides.lastIndex) listOf(board) else listOf(board, StopMarker(ride.last().path.last(), StopKind.ALIGHT, color, null))
        }
    }

    fun rideSteps(route: Route): List<RideStep> {
        val rides = rides(route)
        if (rides.isEmpty()) return emptyList()
        return rides.mapIndexed { index, ride ->
            RideStep(if (index == 0) StopKind.BOARD else StopKind.TRANSFER, ride.first().mode, ride.first().lineName, ride.first().startName)
        } + rides.last().last().let { RideStep(StopKind.ALIGHT, it.mode, it.lineName, it.endName) }
    }
}

/**
 * Kakao SDK 2.15.2 RouteLinePattern 이미지가 실기기(SM-S936N)에서 그려지지 않아(공식 예제 그대로도 흰 선/무표시)
 * 도보 점선과 진행 방향 표식을 현재 줌의 미터/픽셀로 좌표에서 직접 만든다. 테스트 렌더러는 같은 dp 간격을 쓴다.
 */
internal object RouteGeometry {
    private fun lerp(a: LatLng, b: LatLng, t: Double) = LatLng(a.latitude + (b.latitude - a.latitude) * t, a.longitude + (b.longitude - a.longitude) * t)
    private fun distance(a: LatLng, b: LatLng) = com.sunseat.app.domain.ExposureAnalyzer.distanceMeters(a, b)

    fun length(path: List<LatLng>): Double = path.zipWithNext { a, b -> distance(a, b) }.sum()

    /** 경로를 따라 [at] 미터 지점과 그 구간의 방위(도). */
    private fun pointAt(path: List<LatLng>, at: Double): Pair<LatLng, Double>? {
        var left = at
        path.zipWithNext().forEach { (a, b) ->
            val d = distance(a, b)
            if (d > 0 && left <= d) return lerp(a, b, left / d) to com.sunseat.app.domain.ExposureAnalyzer.bearing(a, b)
            left -= d
        }
        return null
    }

    /** [dash] 길이 조각을 [gap] 간격으로. 각 조각은 시작·끝 두 점(경로가 꺾이면 근사). */
    fun dashes(path: List<LatLng>, dash: Double, gap: Double): List<List<LatLng>> {
        if (path.size < 2 || dash <= 0 || gap < 0) return emptyList()
        val total = length(path)
        val out = mutableListOf<List<LatLng>>()
        var at = 0.0
        while (at < total && out.size < MAX_MARKS) {
            val start = pointAt(path, at)?.first ?: break
            val end = pointAt(path, minOf(at + dash, total))?.first ?: path.last()
            if (start != end) out += listOf(start, end)
            at += dash + gap
        }
        return out
    }

    /** 진행 방향 표식 위치: 간격 [spacing], 첫 표식은 절반 지점. 구간이 간격보다 짧으면 없음. */
    fun marks(path: List<LatLng>, spacing: Double): List<Pair<LatLng, Double>> {
        if (path.size < 2 || spacing <= 0) return emptyList()
        val total = length(path)
        if (total < spacing) return emptyList()
        return generateSequence(spacing / 2) { it + spacing }.takeWhile { it < total - spacing / 4 }.take(MAX_MARKS).mapNotNull { pointAt(path, it) }.toList()
    }

    private const val MAX_MARKS = 400
}
