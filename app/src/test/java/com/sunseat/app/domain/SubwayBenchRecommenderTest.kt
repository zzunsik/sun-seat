package com.sunseat.app.domain

import java.time.Duration
import java.time.Instant
import kotlin.math.cos
import kotlin.math.sin
import org.junit.Assert.assertEquals
import org.junit.Test

class SubwayBenchRecommenderTest {
    private val recommender = SubwayBenchRecommender()
    private val p = LatLng(37.5, 127.0)

    /** 60초짜리 선분. 가중치는 ExposureAnalyzer 공식대로 계산한다. */
    private fun seg(index: Int, relativeAngle: Double, altitude: Double, seconds: Long = 60): SegmentExposure {
        val weight = if (altitude <= 0) 0.0
        else seconds * cos(Math.toRadians(altitude)) * kotlin.math.abs(sin(Math.toRadians(relativeAngle)))
        val start = Instant.EPOCH.plusSeconds(index * 60L)
        return SegmentExposure(
            legIndex = 0,
            segmentIndex = index,
            from = p,
            to = p,
            startTime = start,
            endTime = start.plusSeconds(seconds),
            heading = 0.0,
            sun = SunPosition(relativeAngle, altitude),
            relativeAngle = relativeAngle,
            side = if (relativeAngle < 180) Side.RIGHT else Side.LEFT,
            weight = weight,
        )
    }

    private fun subway(vararg segments: SegmentExposure, index: Int = 0, mode: Mode = Mode.SUBWAY) =
        LegExposure(index, Leg(mode, listOf(p, p), Duration.ofMinutes(5), "A역", "B역"), Instant.EPOCH, segments.toList())

    @Test
    fun effectiveAltitudeFormula() {
        assertEquals(20.0, SubwayBenchRecommender.effectiveAltitude(20.0, 90.0), 1e-9)
        // r = 30°이면 |sin r| = 0.5 → h' = atan(tan 20° / 0.5) ≈ 36.05°
        assertEquals(36.05, SubwayBenchRecommender.effectiveAltitude(20.0, 30.0), 0.01)
        assertEquals(90.0, SubwayBenchRecommender.effectiveAltitude(20.0, 0.0), 1e-9)
    }

    @Test
    fun highSunGivesNoFrontExposureToOppositeBench() {
        val result = recommender.recommendLeg(subway(seg(0, 90.0, 60.0)))
        assertEquals(60 * cos(Math.toRadians(60.0)), result.right.back, 1e-6)
        assertEquals(0.0, result.left.front, 0.0)
        assertEquals(0.0, result.left.back, 0.0)
        assertEquals(Duration.ZERO, result.left.frontTime)
    }

    @Test
    fun lowSunGivesFrontExposureToOppositeBench() {
        val result = recommender.recommendLeg(subway(seg(0, 90.0, 20.0)))
        val w = 60 * cos(Math.toRadians(20.0))
        assertEquals(w, result.right.back, 1e-6)
        assertEquals(w, result.left.front, 1e-6)
        assertEquals(Duration.ofSeconds(60), result.left.frontTime)
        assertEquals(Duration.ofSeconds(60), result.right.backTime)
    }

    @Test
    fun lowSunNearlyAheadHasHighEffectiveAltitude() {
        // 고도 20°라도 상대각 30°면 h' ≈ 36° > 30° → 정면 직사 없음
        val result = recommender.recommendLeg(subway(seg(0, 30.0, 20.0)))
        assertEquals(0.0, result.left.front, 0.0)
    }

    @Test
    fun thresholdIsAdjustable() {
        val result = SubwayBenchRecommender(frontMaxEffectiveAltitude = 40.0).recommendLeg(subway(seg(0, 30.0, 20.0)))
        assertEquals(60 * cos(Math.toRadians(20.0)) * 0.5, result.left.front, 1e-6)
    }

    // 왼쪽에 높은 해(짧게), 오른쪽에 낮은 해(길게):
    // 왼쪽 벤치: 등 = 높은 해, 정면 = 낮은 해 / 오른쪽 벤치: 등 = 낮은 해, 정면 = 0
    private val mixed = subway(seg(0, 270.0, 60.0, seconds = 240), seg(1, 90.0, 10.0, seconds = 240))

    @Test
    fun avoidBackPrefersBenchWithLessBackSun() {
        val result = recommender.recommendLeg(mixed, BenchPreference.AVOID_BACK)
        assertEquals(BenchAdvice.Recommend(Side.LEFT, "진행 방향 왼쪽 좌석"), result.advice)
    }

    @Test
    fun avoidFrontPrefersBenchWithLessFrontSun() {
        val result = recommender.recommendLeg(mixed, BenchPreference.AVOID_FRONT)
        assertEquals(Side.RIGHT, (result.advice as BenchAdvice.Recommend).side)
    }

    @Test
    fun minTotalPrefersBenchWithLessTotal() {
        val result = recommender.recommendLeg(mixed, BenchPreference.MIN_TOTAL)
        assertEquals(Side.RIGHT, (result.advice as BenchAdvice.Recommend).side)
        assertEquals(result.left.back + result.left.front, result.left.total, 1e-9)
    }

    @Test
    fun defaultPreferenceIsAvoidBack() {
        assertEquals(BenchPreference.AVOID_BACK, recommender.recommendLeg(mixed).preference)
    }

    @Test
    fun seekModeReversesRecommendation() {
        val back = recommender.recommendLeg(mixed, BenchPreference.AVOID_BACK, SunGoal.SEEK)
        val total = recommender.recommendLeg(mixed, BenchPreference.MIN_TOTAL, SunGoal.SEEK)
        assertEquals(Side.RIGHT, (back.advice as BenchAdvice.Recommend).side)
        assertEquals(Side.LEFT, (total.advice as BenchAdvice.Recommend).side)
    }

    @Test
    fun equalExposureIsNoDifference() {
        // 낮은 해가 한쪽에만 있으면 두 벤치 총 노출이 같다(한쪽 등 = 다른 쪽 정면)
        val result = recommender.recommendLeg(subway(seg(0, 90.0, 20.0)), BenchPreference.MIN_TOTAL)
        assertEquals(BenchAdvice.NoDifference, result.advice)
    }

    @Test
    fun nightHasNoRecommendation() {
        val result = recommender.recommendLeg(subway(seg(0, 90.0, -5.0)))
        assertEquals(BenchAdvice.NoSun, result.advice)
        assertEquals(0.0, result.left.total + result.right.total, 0.0)
    }

    @Test
    fun onlySubwayLegsAreRecommended() {
        val route = RouteExposure(
            listOf(
                subway(seg(0, 90.0, 20.0), index = 0, mode = Mode.WALK),
                subway(seg(0, 90.0, 20.0), index = 1),
                subway(seg(0, 90.0, 20.0), index = 2, mode = Mode.BUS),
            ),
        )
        assertEquals(listOf(1), recommender.recommend(route).map { it.legIndex })
    }
}
