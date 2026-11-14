package com.sunseat.app.domain

import java.time.Duration
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.sin
import kotlin.math.tan

/** 지하철 벤치 선택 기준. */
enum class BenchPreference {
    /** 등 뒤 햇빛 피하기(기본). */
    AVOID_BACK,

    /** 정면 햇빛 피하기. */
    AVOID_FRONT,

    /** 총 노출 최소. */
    MIN_TOTAL,
}

/**
 * 한쪽 벤치(진행 방향 [side])의 노출.
 * 노출 값은 [ExposureAnalyzer]의 가중치(가중 초), 시간은 해당 선분 소요시간 합이다.
 */
data class BenchExposure(
    val side: Side,
    val back: Double,
    val front: Double,
    val backTime: Duration,
    val frontTime: Duration,
) {
    val total: Double get() = back + front
    val totalTime: Duration get() = backTime + frontTime

    fun value(preference: BenchPreference): Double = when (preference) {
        BenchPreference.AVOID_BACK -> back
        BenchPreference.AVOID_FRONT -> front
        BenchPreference.MIN_TOTAL -> total
    }
}

sealed interface BenchAdvice {
    data class Recommend(val side: Side, val label: String) : BenchAdvice

    /** 선호 기준으로 두 벤치의 차이가 기준 미만이다. */
    data object NoDifference : BenchAdvice

    /** 해가 없어(야간 등) 추천할 것이 없다. */
    data object NoSun : BenchAdvice
}

data class SubwayBenchRecommendation(
    val legIndex: Int,
    val left: BenchExposure,
    val right: BenchExposure,
    val preference: BenchPreference,
    val advice: BenchAdvice,
) {
    fun bench(side: Side): BenchExposure = if (side == Side.LEFT) left else right
}

/**
 * 지하철 벤치별 노출 계산과 추천.
 * 해 쪽 벤치는 등에 직사를 받고, 반대쪽 벤치는 객차 단면 유효 고도가 낮을 때만 창을 통해 정면 직사를 받는다.
 */
class SubwayBenchRecommender(
    /** 반대쪽 벤치 정면 직사 판정의 유효 고도 h' 임계값(도). */
    val frontMaxEffectiveAltitude: Double = FRONT_MAX_EFFECTIVE_ALTITUDE_DEG,
    /** 선호 기준 값의 차이 비율이 이 값 미만이면 "차이 없음". [SeatRecommender]와 같은 기준이다. */
    val noDifferenceThreshold: Double = SeatRecommender.NO_DIFFERENCE_THRESHOLD,
) {

    /** SUBWAY 구간마다 추천한다. */
    fun recommend(
        exposure: RouteExposure,
        preference: BenchPreference = BenchPreference.AVOID_BACK,
        goal: SunGoal = SunGoal.AVOID,
    ): List<SubwayBenchRecommendation> =
        exposure.legs.filter { it.leg.mode == Mode.SUBWAY }.map { recommendLeg(it, preference, goal) }

    fun recommendLeg(
        leg: LegExposure,
        preference: BenchPreference = BenchPreference.AVOID_BACK,
        goal: SunGoal = SunGoal.AVOID,
    ): SubwayBenchRecommendation {
        val left = benchExposure(leg, Side.LEFT)
        val right = benchExposure(leg, Side.RIGHT)
        return SubwayBenchRecommendation(leg.legIndex, left, right, preference, advise(left, right, preference, goal))
    }

    fun benchExposure(leg: LegExposure, side: Side): BenchExposure {
        var back = 0.0
        var front = 0.0
        var backTime = Duration.ZERO
        var frontTime = Duration.ZERO
        for (segment in leg.segments) {
            if (segment.weight <= 0.0) continue
            if (segment.side == side) {
                back += segment.weight
                backTime += segment.duration
            } else if (effectiveAltitude(segment) <= frontMaxEffectiveAltitude) {
                front += segment.weight
                frontTime += segment.duration
            }
        }
        return BenchExposure(side, back, front, backTime, frontTime)
    }

    private fun advise(
        left: BenchExposure,
        right: BenchExposure,
        preference: BenchPreference,
        goal: SunGoal,
    ): BenchAdvice {
        if (left.total + right.total <= SeatRecommender.EPSILON) return BenchAdvice.NoSun
        val l = left.value(preference)
        val r = right.value(preference)
        if (differenceRatio(l, r) < noDifferenceThreshold) return BenchAdvice.NoDifference
        val less = if (l < r) Side.LEFT else Side.RIGHT
        val side = if (goal == SunGoal.AVOID) less else less.opposite
        return BenchAdvice.Recommend(side, benchLabel(side))
    }

    companion object {
        const val FRONT_MAX_EFFECTIVE_ALTITUDE_DEG = 30.0

        fun benchLabel(side: Side): String = if (side == Side.LEFT) "진행 방향 왼쪽 좌석" else "진행 방향 오른쪽 좌석"

        /** 객차 단면(진행 방향에 수직인 면)에 투영한 태양 고도 h' = atan(tan h / |sin r|)(도). */
        fun effectiveAltitude(altitude: Double, relativeAngle: Double): Double {
            val t = tan(Math.toRadians(altitude))
            val s = abs(sin(Math.toRadians(relativeAngle)))
            return Math.toDegrees(atan2(t, s))
        }

        fun effectiveAltitude(segment: SegmentExposure): Double =
            effectiveAltitude(segment.sun.altitude, segment.relativeAngle)
    }
}
