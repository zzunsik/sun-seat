package com.sunseat.app.domain

import java.time.Duration
import java.time.Instant
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SeatRecommenderTest {
    private val recommender = SeatRecommender()
    private val p = LatLng(37.5, 127.0)

    private fun seg(
        index: Int,
        side: Side,
        weight: Double,
        relativeAngle: Double = if (side == Side.RIGHT) 90.0 else 270.0,
        altitude: Double = 30.0,
        startSec: Long = index * 60L,
        endSec: Long = startSec + 60,
    ) = SegmentExposure(
        legIndex = 0,
        segmentIndex = index,
        from = p,
        to = p,
        startTime = Instant.EPOCH.plusSeconds(startSec),
        endTime = Instant.EPOCH.plusSeconds(endSec),
        heading = 0.0,
        sun = SunPosition(relativeAngle, altitude),
        relativeAngle = relativeAngle,
        side = side,
        weight = weight,
    )

    private fun legExposure(mode: Mode, vararg segments: SegmentExposure, index: Int = 0) =
        LegExposure(index, Leg(mode, listOf(p, p), Duration.ofMinutes(10), "A", "B"), Instant.EPOCH, segments.toList())

    private fun lr(mode: Mode, left: Double, right: Double, index: Int = 0) =
        legExposure(mode, seg(0, Side.LEFT, left), seg(1, Side.RIGHT, right), index = index)

    @Test
    fun busRecommendsLessExposedSide() {
        val advice = recommender.advise(lr(Mode.BUS, left = 300.0, right = 50.0))
        assertEquals(SeatAdvice.Recommend(Side.RIGHT, emptyList()), advice)

        val other = recommender.advise(lr(Mode.BUS, left = 10.0, right = 400.0)) as SeatAdvice.Recommend
        assertEquals(Side.LEFT, other.side)
        assertEquals(Side.LEFT, other.side)
    }

    @Test
    fun carRecommendsPassengerSeats() {
        val right = recommender.advise(lr(Mode.CAR, left = 300.0, right = 50.0)) as SeatAdvice.Recommend
        assertEquals(listOf(CarSeat.PASSENGER, CarSeat.REAR_RIGHT), right.carSeats)
        assertEquals(listOf(CarSeat.PASSENGER, CarSeat.REAR_RIGHT), right.carSeats)

        val left = recommender.advise(lr(Mode.CAR, left = 50.0, right = 300.0)) as SeatAdvice.Recommend
        assertEquals(listOf(CarSeat.REAR_LEFT), left.carSeats)
    }

    @Test
    fun noDifferenceBoundary() {
        // 차이 비율 0.14 → 차이 없음, 0.15 → 추천
        assertEquals(SeatAdvice.NoDifference, recommender.advise(lr(Mode.BUS, 57.0, 43.0)))
        assertTrue(recommender.advise(lr(Mode.BUS, 57.5, 42.5)) is SeatAdvice.Recommend)
        assertEquals(SeatAdvice.NoDifference, recommender.advise(lr(Mode.BUS, 100.0, 100.0)))
    }

    @Test
    fun thresholdIsAdjustable() {
        val loose = SeatRecommender(noDifferenceThreshold = 0.5)
        assertEquals(SeatAdvice.NoDifference, loose.advise(lr(Mode.BUS, 70.0, 30.0)))
    }

    @Test
    fun seekModeReversesSide() {
        val avoid = recommender.advise(lr(Mode.BUS, 300.0, 50.0), SunGoal.AVOID) as SeatAdvice.Recommend
        val seek = recommender.advise(lr(Mode.BUS, 300.0, 50.0), SunGoal.SEEK) as SeatAdvice.Recommend
        assertEquals(Side.RIGHT, avoid.side)
        assertEquals(Side.LEFT, seek.side)
        assertEquals(Side.LEFT, seek.side)
    }

    @Test
    fun nightHasNoRecommendation() {
        assertEquals(SeatAdvice.NoSun, recommender.advise(lr(Mode.BUS, 0.0, 0.0)))
        assertEquals(SeatAdvice.NoSun, recommender.advise(legExposure(Mode.CAR), SunGoal.SEEK))
    }

    @Test
    fun walkAndSubwayLegsAreExcluded() {
        val route = RouteExposure(
            listOf(
                lr(Mode.WALK, 300.0, 0.0, index = 0),
                lr(Mode.BUS, 300.0, 0.0, index = 1),
                lr(Mode.SUBWAY, 300.0, 0.0, index = 2),
                lr(Mode.CAR, 300.0, 0.0, index = 3),
            ),
        )
        val result = recommender.recommend(route)
        assertEquals(listOf(1, 3), result.map { it.legIndex })
        assertEquals(listOf(Mode.BUS, Mode.CAR), result.map { it.mode })
    }

    @Test
    fun glareDetectedWhenSunLowAndAhead() {
        assertTrue(recommender.isGlare(seg(0, Side.RIGHT, 0.0, relativeAngle = 10.0, altitude = 10.0)))
        assertTrue(recommender.isGlare(seg(0, Side.LEFT, 0.0, relativeAngle = 345.0, altitude = 15.0)))
        // 각도 밖, 고도 초과, 해 진 뒤, 후면
        assertFalse(recommender.isGlare(seg(0, Side.RIGHT, 0.0, relativeAngle = 25.0, altitude = 10.0)))
        assertFalse(recommender.isGlare(seg(0, Side.RIGHT, 0.0, relativeAngle = 5.0, altitude = 16.0)))
        assertFalse(recommender.isGlare(seg(0, Side.RIGHT, 0.0, relativeAngle = 5.0, altitude = -1.0)))
        assertFalse(recommender.isGlare(seg(0, Side.RIGHT, 0.0, relativeAngle = 180.0, altitude = 5.0)))
    }

    @Test
    fun glareConstantsAreAdjustable() {
        val wide = SeatRecommender(glareHalfAngle = 30.0, glareMaxAltitude = 20.0)
        assertTrue(wide.isGlare(seg(0, Side.RIGHT, 0.0, relativeAngle = 25.0, altitude = 18.0)))
    }

    @Test
    fun adjacentGlareSegmentsAreMerged() {
        val leg = legExposure(
            Mode.CAR,
            seg(0, Side.RIGHT, 0.0, relativeAngle = 5.0, altitude = 8.0),
            seg(1, Side.LEFT, 0.0, relativeAngle = 350.0, altitude = 8.0),
            seg(2, Side.RIGHT, 0.0, relativeAngle = 90.0, altitude = 8.0),
            seg(3, Side.RIGHT, 0.0, relativeAngle = 15.0, altitude = 8.0),
        )
        val intervals = recommender.glareIntervals(RouteExposure(listOf(leg)))
        assertEquals(2, intervals.size)
        assertEquals(listOf(0, 1), intervals[0].segments.map { it.segmentIndex })
        assertEquals(Instant.EPOCH, intervals[0].startTime)
        assertEquals(Duration.ofMinutes(2), intervals[0].duration)
        assertEquals(listOf(3), intervals[1].segments.map { it.segmentIndex })
    }

    @Test
    fun glareOnlyForCarLegs() {
        val glare = seg(0, Side.RIGHT, 0.0, relativeAngle = 0.0, altitude = 5.0)
        val route = RouteExposure(listOf(legExposure(Mode.BUS, glare), legExposure(Mode.CAR, glare, index = 1)))
        assertEquals(listOf(1), recommender.glareIntervals(route).map { it.legIndex })
    }

    @Test
    fun glareFromAnalyzerWithLowSunAhead() {
        // 해가 정동 고도 5°일 때 동→동→북 경로: 동쪽 선분 두 개만 한 구간으로 묶인다
        val analyzer = ExposureAnalyzer { _, _, _ -> SunPosition(90.0, 5.0) }
        val leg = Leg(
            Mode.CAR,
            listOf(LatLng(0.0, 0.0), LatLng(0.0, 0.01), LatLng(0.0, 0.02), LatLng(0.01, 0.02)),
            Duration.ofMinutes(9),
            "A",
            "B",
        )
        val exposure = analyzer.analyze(Route(listOf(leg)), Instant.EPOCH)
        val intervals = recommender.glareIntervals(exposure)
        assertEquals(1, intervals.size)
        assertEquals(listOf(0, 1), intervals.single().segments.map { it.segmentIndex })
        assertEquals(360.0, intervals.single().duration.seconds.toDouble(), 2.0)
    }
}
