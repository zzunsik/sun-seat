package com.sunseat.app.domain

import java.time.Duration
import java.time.Instant
import kotlin.math.abs
import kotlin.math.asin
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

/** 진행 방향 기준 좌·우. */
enum class Side {
    LEFT, RIGHT;

    val opposite: Side get() = if (this == LEFT) RIGHT else LEFT
}

/**
 * 선분 하나의 노출 결과(타임라인용).
 * 태양 위치는 선분 중간 지점·중간 시각에서 계산한다.
 * @param heading 진행 방위각(북쪽 기준 시계 방향, 도)
 * @param relativeAngle r = (태양 방위각 − 진행 방위각) mod 360
 * @param side 태양이 있는 쪽. 0~180°는 오른쪽, 180~360°는 왼쪽
 * @param weight 소요시간(초) × cos(고도) × |sin r|. 고도 ≤ 0이면 0
 */
data class SegmentExposure(
    val legIndex: Int,
    val segmentIndex: Int,
    val from: LatLng,
    val to: LatLng,
    val startTime: Instant,
    val endTime: Instant,
    val heading: Double,
    val sun: SunPosition,
    val relativeAngle: Double,
    val side: Side,
    val weight: Double,
) {
    val duration: Duration get() = Duration.between(startTime, endTime)
}

/** 구간별 좌·우 노출 합계(가중 초). */
data class LegExposure(
    val legIndex: Int,
    val leg: Leg,
    val startTime: Instant,
    val segments: List<SegmentExposure>,
) {
    val left: Double get() = segments.filter { it.side == Side.LEFT }.sumOf { it.weight }
    val right: Double get() = segments.filter { it.side == Side.RIGHT }.sumOf { it.weight }
    fun exposure(side: Side): Double = if (side == Side.LEFT) left else right
}

data class RouteExposure(val legs: List<LegExposure>)

/** 경로를 선분으로 나눠 좌·우 햇빛 노출을 계산한다. */
class ExposureAnalyzer(private val sun: SunPositionSource = SunCalculator) {

    fun analyze(route: Route, departure: Instant): RouteExposure {
        var legStart = departure
        val legs = route.legs.mapIndexed { index, leg ->
            analyzeLeg(index, leg, legStart).also { legStart += leg.duration }
        }
        return RouteExposure(legs)
    }

    fun analyzeLeg(legIndex: Int, leg: Leg, start: Instant): LegExposure {
        if (leg.mode == Mode.SUBWAY && leg.groundLevel in setOf(GroundLevel.BELOW, GroundLevel.UNKNOWN)) {
            return LegExposure(legIndex, leg, start, emptyList())
        }
        val path = leg.path
        if (path.size < 2) return LegExposure(legIndex, leg, start, emptyList())

        val lengths = path.zipWithNext { a, b -> distanceMeters(a, b) }
        val total = lengths.sum()
        if (total <= 0.0) return LegExposure(legIndex, leg, start, emptyList())

        val legNanos = leg.duration.toNanos().coerceAtLeast(0)
        val segments = ArrayList<SegmentExposure>(lengths.size)
        var cumulative = 0.0
        for (i in lengths.indices) {
            val length = lengths[i]
            if (length <= 0.0) continue // 같은 좌표가 반복되면 진행 방향이 없다
            val from = path[i]
            val to = path[i + 1]
            val t0 = start.plusNanos((legNanos * (cumulative / total)).toLong())
            cumulative += length
            val t1 = start.plusNanos((legNanos * (cumulative / total)).toLong())
            val mid = t0.plusNanos(Duration.between(t0, t1).toNanos() / 2)

            val heading = bearing(from, to)
            val position = sun.positionAt(
                (from.latitude + to.latitude) / 2,
                (from.longitude + to.longitude) / 2,
                mid,
            )
            val r = normalize(position.azimuth - heading)
            val seconds = Duration.between(t0, t1).toNanos() / 1e9
            val weight = if (position.altitude <= 0.0) 0.0
            else seconds * cos(Math.toRadians(position.altitude)) * abs(sin(Math.toRadians(r)))
            segments += SegmentExposure(
                legIndex = legIndex,
                segmentIndex = i,
                from = from,
                to = to,
                startTime = t0,
                endTime = t1,
                heading = heading,
                sun = position,
                relativeAngle = r,
                side = if (r < 180.0) Side.RIGHT else Side.LEFT,
                weight = weight,
            )
        }
        return LegExposure(legIndex, leg, start, segments)
    }

    companion object {
        private const val EARTH_RADIUS_M = 6_371_000.0

        fun normalize(deg: Double): Double = ((deg % 360.0) + 360.0) % 360.0

        /** 대권 초기 방위각(북쪽 기준 시계 방향, 도). */
        fun bearing(a: LatLng, b: LatLng): Double {
            val p1 = Math.toRadians(a.latitude)
            val p2 = Math.toRadians(b.latitude)
            val dl = Math.toRadians(b.longitude - a.longitude)
            val y = sin(dl) * cos(p2)
            val x = cos(p1) * sin(p2) - sin(p1) * cos(p2) * cos(dl)
            return normalize(Math.toDegrees(atan2(y, x)))
        }

        /** 하버사인 거리(m). */
        fun distanceMeters(a: LatLng, b: LatLng): Double {
            val p1 = Math.toRadians(a.latitude)
            val p2 = Math.toRadians(b.latitude)
            val dp = p2 - p1
            val dl = Math.toRadians(b.longitude - a.longitude)
            val h = sin(dp / 2) * sin(dp / 2) + cos(p1) * cos(p2) * sin(dl / 2) * sin(dl / 2)
            return 2 * EARTH_RADIUS_M * asin(sqrt(h.coerceIn(0.0, 1.0)))
        }
    }
}
