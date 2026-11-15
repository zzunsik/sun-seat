package com.sunseat.app.domain

import java.time.Duration
import java.time.Instant
import kotlin.math.abs

/** 햇빛을 피할지 받을지. */
enum class SunGoal { AVOID, SEEK }

/** 자가용 동승자 좌석. 운전석은 왼쪽이며 고를 수 없다. */
enum class CarSeat { PASSENGER, REAR_LEFT, REAR_RIGHT }

/** 구간 하나에 대한 좌석 추천. */
sealed interface SeatAdvice {
    /** [side] 쪽 좌석을 추천한다. [carSeats]는 자가용일 때만 채운다. */
    data class Recommend(val side: Side, val carSeats: List<CarSeat>) : SeatAdvice

    /** 좌·우 차이가 기준 미만이다. */
    data object NoDifference : SeatAdvice

    /** 해가 없어(야간 등) 추천할 것이 없다. */
    data object NoSun : SeatAdvice
}

data class LegSeatRecommendation(
    val legIndex: Int,
    val mode: Mode,
    val left: Double,
    val right: Double,
    val advice: SeatAdvice,
)

/** 운전자 정면 눈부심 구간. 인접한 선분을 묶은 것이다. */
data class GlareInterval(
    val legIndex: Int,
    val segments: List<SegmentExposure>,
) {
    val startTime: Instant get() = segments.first().startTime
    val endTime: Instant get() = segments.last().endTime
    val duration: Duration get() = Duration.between(startTime, endTime)
    val from: LatLng get() = segments.first().from
    val to: LatLng get() = segments.last().to
}

/** 좌·우 차이 비율 |L − R| / (L + R). 합이 0이면 0. */
internal fun differenceRatio(a: Double, b: Double): Double {
    val total = a + b
    return if (total <= 0.0) 0.0 else abs(a - b) / total
}

/** 버스·자가용 좌석 추천과 운전자 눈부심 구간. 지하철·도보 구간은 다루지 않는다. */
class SeatRecommender(
    /** 좌·우 차이 비율(|L − R| / (L + R))이 이 값 미만이면 "차이 없음". */
    val noDifferenceThreshold: Double = NO_DIFFERENCE_THRESHOLD,
    /** 태양이 진행 방향 ± 이 각도(도) 안이면 정면. */
    val glareHalfAngle: Double = GLARE_HALF_ANGLE_DEG,
    /** 태양 고도가 이 값(도) 이하이면 눈부심. */
    val glareMaxAltitude: Double = GLARE_MAX_ALTITUDE_DEG,
) {

    /** BUS·CAR 구간마다 추천한다. */
    fun recommend(exposure: RouteExposure, goal: SunGoal = SunGoal.AVOID): List<LegSeatRecommendation> =
        exposure.legs
            .filter { it.leg.mode == Mode.BUS || it.leg.mode == Mode.CAR }
            .map { LegSeatRecommendation(it.legIndex, it.leg.mode, it.left, it.right, advise(it, goal)) }

    fun advise(leg: LegExposure, goal: SunGoal = SunGoal.AVOID): SeatAdvice {
        val left = leg.left
        val right = leg.right
        if (left + right <= EPSILON) return SeatAdvice.NoSun
        if (differenceRatio(left, right) < noDifferenceThreshold) return SeatAdvice.NoDifference
        val shade = if (left < right) Side.LEFT else Side.RIGHT
        val side = if (goal == SunGoal.AVOID) shade else shade.opposite
        return when (leg.leg.mode) {
            Mode.CAR -> {
                val seats = carSeats(side)
                SeatAdvice.Recommend(side, seats)
            }
            else -> SeatAdvice.Recommend(side, emptyList())
        }
    }

    /** CAR 구간에서 운전자 정면 눈부심 구간을 찾는다. */
    fun glareIntervals(exposure: RouteExposure): List<GlareInterval> =
        exposure.legs.filter { it.leg.mode == Mode.CAR }.flatMap { glareIntervals(it) }

    fun glareIntervals(leg: LegExposure): List<GlareInterval> {
        val intervals = mutableListOf<GlareInterval>()
        var current = mutableListOf<SegmentExposure>()
        fun flush() {
            if (current.isNotEmpty()) intervals += GlareInterval(leg.legIndex, current)
            current = mutableListOf()
        }
        for (segment in leg.segments) {
            if (!isGlare(segment)) {
                flush()
                continue
            }
            if (current.isNotEmpty() && current.last().endTime != segment.startTime) flush()
            current += segment
        }
        flush()
        return intervals
    }

    fun isGlare(segment: SegmentExposure): Boolean {
        val altitude = segment.sun.altitude
        val r = segment.relativeAngle
        val ahead = r <= glareHalfAngle || r >= 360.0 - glareHalfAngle
        return altitude > 0.0 && altitude <= glareMaxAltitude && ahead
    }

    companion object {
        const val NO_DIFFERENCE_THRESHOLD = 0.15
        const val GLARE_HALF_ANGLE_DEG = 20.0
        const val GLARE_MAX_ALTITUDE_DEG = 15.0
        internal const val EPSILON = 1e-9


        fun carSeats(side: Side): List<CarSeat> =
            if (side == Side.LEFT) listOf(CarSeat.REAR_LEFT) else listOf(CarSeat.PASSENGER, CarSeat.REAR_RIGHT)
    }
}
