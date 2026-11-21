package com.sunseat.app.ui.map

import com.sunseat.app.domain.LatLng

/*
 * #170: 지도 표식 배치 규칙. 표식끼리 겹치면 우선순위가 낮은 쪽이 정해진 자리로 비키고, 비킬 자리가 없을 때만 숨긴다.
 * 숨기는 표식은 그 정보가 화면의 다른 곳(정보 카드·TalkBack 요약)에 남는 것만이다(#152). 두 렌더러가 같은 함수를 부르고,
 * 카메라 정책(#168)의 맞춤 발자국도 이 배치 결과(비킨 자리)를 쓴다.
 */

/** 출발·도착 글자 표식의 몸통 방향. 꼬리 끝은 언제나 좌표에 있다. 기본은 위(몸통이 좌표 위). */
enum class PinSide { UP, DOWN, RIGHT, LEFT }

/** 숨긴 표식의 정보가 남는 곳. [NONE]이면 숨기지 않는다. */
enum class Fallback { NONE, INFO_CARD, TALKBACK_SUMMARY }

/** 표식 종류(우선순위 표의 행, DESIGN 11.1). */
internal enum class MarkKind { ORIGIN, DESTINATION, VEHICLE, CURRENT_LOCATION, STOP_LABEL }

/**
 * 배치할 표식 하나. [x]·[y]는 좌표의 화면 위치(px, 평행 이동해도 결과가 같다), [variants]는 놓을 수 있는 상자들(좌표 기준, 앞이 먼저)이고
 * 첫째가 기본 자리다. [fixed]이면 움직이지도 숨지도 않고 먼저 놓인다(차량·현재 위치). [priority]가 높은 표식이 먼저 자리를 잡는다.
 */
internal data class Mark(
    val key: Any,
    val kind: MarkKind,
    val at: LatLng,
    val x: Float,
    val y: Float,
    val variants: List<ScreenBox>,
    val priority: Int,
    val fallback: Fallback = Fallback.NONE,
    val fixed: Boolean = false,
)

/** 배치 결과: 고른 자리([variant], 0 = 기본) 또는 숨김. */
internal data class Placement(val key: Any, val variant: Int, val hidden: Boolean = false) {
    val moved: Boolean get() = !hidden && variant != 0
}

/** 우선순위(높을수록 먼저 자리를 잡는다, DESIGN 11.1). */
internal object MarkPriority {
    /** 도착한 뒤의 도착 표식: 그 순간의 핵심 정보(#152). */
    const val ARRIVED_DESTINATION = 60
    const val ORIGIN = 51
    const val DESTINATION = 50
    const val VEHICLE = 40
    const val CURRENT_LOCATION = 35
    const val STOP_LABEL = 30
}

private fun ScreenBox.at(x: Float, y: Float) = ScreenBox(x + left, y + top, x + right, y + bottom)

/** 두 상자가 겹치는 넓이(px²). 맞닿기만 하면 0. */
private fun overlap(a: ScreenBox, b: ScreenBox): Float {
    val w = minOf(a.right, b.right) - maxOf(a.left, b.left)
    val h = minOf(a.bottom, b.bottom) - maxOf(a.top, b.top)
    return if (w > 0f && h > 0f) w * h else 0f
}

/**
 * 표식 배치. 고정 표식을 먼저 놓고, 나머지는 우선순위가 높은 것부터 기본 자리 → 비킨 자리 순서로 이미 놓인 상자와 겹치지 않는 첫 자리에 놓는다.
 * 겹치지 않는 자리가 없으면 대체 정보가 있는 표식([Mark.fallback] ≠ NONE)만 숨기고, 없는 표식은 가장 덜 겹치는 자리에 둔다.
 * 결과는 [marks]와 같은 순서다.
 */
internal fun markerLayout(marks: List<Mark>): List<Placement> {
    val placed = mutableListOf<ScreenBox>()
    val result = HashMap<Int, Placement>()
    marks.forEachIndexed { i, mark -> if (mark.fixed) { placed += mark.variants.first().at(mark.x, mark.y); result[i] = Placement(mark.key, 0) } }
    val order = marks.indices.filter { !marks[it].fixed }.sortedByDescending { marks[it].priority }
    // 출발·도착 표식은 함께 자리를 고른다: 한쪽만 비켜서는 서로 피할 수 없는 거리가 있다(#160). 기본 자리에 가까운 조합부터 본다.
    val pins = order.filter { marks[it].priority >= MarkPriority.DESTINATION }
    if (pins.size > 1) {
        val boxes = pins.map { i -> marks[i].variants.map { it.at(marks[i].x, marks[i].y) } }
        fun combos(k: Int): List<List<Int>> = if (k == pins.size) listOf(emptyList()) else combos(k + 1).flatMap { rest -> boxes[k].indices.map { listOf(it) + rest } }
        val best = combos(0).sortedWith(compareBy({ it.sum() }, { it.joinToString() })).firstOrNull { combo ->
            val chosen = combo.mapIndexed { k, v -> boxes[k][v] }
            chosen.indices.all { k -> placed.none { overlap(it, chosen[k]) > 0f } && (k + 1 until chosen.size).all { overlap(chosen[k], chosen[it]) <= 0f } }
        }
        if (best != null) pins.forEachIndexed { k, i -> placed += boxes[k][best[k]]; result[i] = Placement(marks[i].key, best[k]) }
    }
    order.filter { it !in result }.forEach { i ->
        val mark = marks[i]
        val boxes = mark.variants.map { it.at(mark.x, mark.y) }
        val free = boxes.indexOfFirst { box -> placed.none { overlap(it, box) > 0f } }
        val choice = when {
            free >= 0 -> free
            mark.fallback != Fallback.NONE -> -1
            else -> boxes.indices.minBy { v -> placed.sumOf { overlap(it, boxes[v]).toDouble() } }
        }
        if (choice < 0) result[i] = Placement(mark.key, 0, hidden = true)
        else { placed += boxes[choice]; result[i] = Placement(mark.key, choice) }
    }
    return marks.indices.map { result.getValue(it) }
}

/**
 * 표식 크기(px). [marker]는 표식 높이(꼬리 포함), [tail]은 꼬리 길이, [gap]은 표식끼리·가장자리 틈(Spacing.xs).
 * [vehicle]은 차량 표식 크기, [currentLocation]은 현재 위치 원 지름, [labelHeight]는 이름표 상자 높이, [labelWidth]는 이름표 상자 폭.
 */
internal data class MarkSizes(
    val marker: Float,
    val tail: Float,
    val gap: Float,
    val originWidth: Float,
    val destinationWidth: Float,
    val stopRadius: Float,
    val labelHeight: Float,
    val vehicle: Float,
    val currentLocation: Float,
    val labelWidth: (StopMarker) -> Float,
)

/** 꼬리 끝이 좌표에 있는 폭 [width] 표식의 몸통 상자(좌표 기준). 몸통 높이 = [marker] − [tail]. */
internal fun pinBox(side: PinSide, width: Float, marker: Float, tail: Float): ScreenBox {
    val body = marker - tail
    return when (side) {
        PinSide.UP -> ScreenBox(-width / 2, -marker, width / 2, -tail)
        PinSide.DOWN -> ScreenBox(-width / 2, tail, width / 2, marker)
        PinSide.RIGHT -> ScreenBox(tail, -body / 2, tail + width, body / 2)
        PinSide.LEFT -> ScreenBox(-tail - width, -body / 2, -tail, body / 2)
    }
}

/** 표식 키. 렌더러가 배치 결과를 자기 표식에 대응시킨다. */
internal sealed interface MarkKey {
    data object Origin : MarkKey
    data object Destination : MarkKey
    data object Vehicle : MarkKey
    data object CurrentLocation : MarkKey
    /** 선택 경로 [RouteLinePolicy.stops]의 [index]번째 정류장 이름표. */
    data class StopLabel(val index: Int) : MarkKey
}

/**
 * [MapState]의 표식. [project]는 좌표 → 화면(px). 이름표는 정류장 반지름에서 시작해 이름표 쪽 바깥으로 [MarkSizes.gap]씩
 * 출발·도착 표식 폭 + 틈까지 밀 수 있다(#153: 그보다 멀면 어느 정류장 것인지 읽히지 않는다).
 */
internal fun MapState.marks(sizes: MarkSizes, project: (LatLng) -> Pair<Float, Float>): List<Mark> {
    val playing = routeProgress != null
    // 재생 중에는 정보 카드가 출발→도착과 지금·다음 노선을 글자로 남긴다(#152). 그 밖에는 지도 설명·경로 요약(TalkBack)이 남긴다.
    val textFallback = if (playing) Fallback.INFO_CARD else Fallback.TALKBACK_SUMMARY
    val out = mutableListOf<Mark>()
    fun pin(key: MarkKey, kind: MarkKind, at: LatLng, width: Float, priority: Int, fallback: Fallback) {
        val (x, y) = project(at)
        out += Mark(key, kind, at, x, y, PinSide.entries.map { pinBox(it, width, sizes.marker, sizes.tail) }, priority, fallback)
    }
    // 출발·도착 이름은 지도 설명(TalkBack)에도 있지만, 재생 중이 아니면 숨기지 않는다(글자로 구분하는 핵심 표식, DESIGN 11.1).
    origin?.let { pin(MarkKey.Origin, MarkKind.ORIGIN, it.location, sizes.originWidth, MarkPriority.ORIGIN, if (playing) Fallback.INFO_CARD else Fallback.NONE) }
    destination?.let {
        // 도착한 뒤의 도착 표식은 그 순간의 핵심이라 숨기지 않고 가장 먼저 자리를 잡는다(#152).
        pin(MarkKey.Destination, MarkKind.DESTINATION, it.location, sizes.destinationWidth,
            if (arrived) MarkPriority.ARRIVED_DESTINATION else MarkPriority.DESTINATION, if (playing && !arrived) Fallback.INFO_CARD else Fallback.NONE)
    }
    visibleCursor?.let { cursor ->
        val (x, y) = project(cursor.position)
        val half = sizes.vehicle / 2
        out += Mark(MarkKey.Vehicle, MarkKind.VEHICLE, cursor.position, x, y, listOf(ScreenBox(-half, -half, half, half)), MarkPriority.VEHICLE, fixed = true)
    }
    focus?.let {
        val (x, y) = project(it.location)
        val half = sizes.currentLocation / 2
        out += Mark(MarkKey.CurrentLocation, MarkKind.CURRENT_LOCATION, it.location, x, y, listOf(ScreenBox(-half, -half, half, half)), MarkPriority.CURRENT_LOCATION, fixed = true)
    }
    val maxPush = maxOf(sizes.originWidth, sizes.destinationWidth) + sizes.gap
    routes.getOrNull(selectedRoute)?.let { RouteLinePolicy.stops(it) }.orEmpty().forEachIndexed { index, stop ->
        if (!stop.labeled) return@forEachIndexed
        val (x, y) = project(stop.location)
        val width = sizes.labelWidth(stop)
        out += Mark(MarkKey.StopLabel(index), MarkKind.STOP_LABEL, stop.location, x, y, labelVariants(stop.labelOnLeft, width, sizes.labelHeight, sizes.stopRadius, sizes.gap, maxPush),
            MarkPriority.STOP_LABEL, textFallback)
    }
    return out
}

/** 배치 결과에서 정류장 이름표가 정류장 좌표에서 떨어진 거리(px, [stopLabelOffset]와 같은 뜻). */
internal fun labelOffset(placement: Placement, sizes: MarkSizes): Float = sizes.stopRadius + placement.variant * sizes.gap

/** 배치 결과의 표식 몸통 방향. */
internal fun pinSide(placement: Placement): PinSide = PinSide.entries[placement.variant]

/**
 * 정류장 노선 이름표가 놓일 수 있는 상자: 정류장 반지름에서 시작해 이름표 쪽 바깥으로 [gap]씩, 최대 [maxPush]까지(#153).
 */
internal fun labelVariants(onLeft: Boolean, width: Float, height: Float, stopRadius: Float, gap: Float, maxPush: Float): List<ScreenBox> {
    val steps = (maxPush / gap.coerceAtLeast(1f)).toInt().coerceAtLeast(0)
    return (0..steps).map { k ->
        val offset = stopRadius + k * gap
        if (onLeft) ScreenBox(-offset - width, -height / 2, -offset, height / 2) else ScreenBox(offset, -height / 2, offset + width, height / 2)
    }
}

private fun ScreenBox.inflate(by: Float) = ScreenBox(left - by, top - by, right + by, bottom + by)

/**
 * 카메라 맞춤(#168)에 넘길 발자국을 줌마다 배치 결과로 만든다: 경로 좌표, 정류장 원, 보이는 출발·도착 표식과 이름표(고른 자리, 가장자리 틈 포함).
 * 화면 거리는 줌마다 다르므로 그 줌의 투영으로 배치한다. 숨긴 표식과 차량·현재 위치(따라가기·가운데 카메라가 맡음)는 넣지 않는다.
 */
internal fun MapState.layoutFootprints(sizes: MarkSizes, scale: MapScale): (Int) -> List<Footprint> {
    val base = points().firstOrNull() ?: origin?.location ?: destination?.location ?: LatLng(0.0, 0.0)
    val bx = Mercator.x(base.longitude); val by = Mercator.y(base.latitude)
    val r = sizes.stopRadius
    val stops = routes.getOrNull(selectedRoute)?.let { RouteLinePolicy.stops(it) }.orEmpty().map { Footprint(it.location, ScreenBox(-r, -r, r, r)) }
    val path = points().map { Footprint(it) }
    return { zoom ->
        val s = scale.worldPx(zoom)
        val marks = marks(sizes) { p -> ((Mercator.x(p.longitude) - bx) * s).toFloat() to ((Mercator.y(p.latitude) - by) * s).toFloat() }
        val placed = marks.zip(markerLayout(marks)).filter { (mark, placement) -> !placement.hidden && !mark.fixed }
            .map { (mark, placement) -> Footprint(mark.at, mark.variants[placement.variant].inflate(sizes.gap)) }
        path + stops + placed
    }
}
