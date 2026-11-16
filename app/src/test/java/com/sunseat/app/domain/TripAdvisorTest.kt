package com.sunseat.app.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Duration
import java.time.Instant

class TripAdvisorTest {
    private val t0 = Instant.parse("2026-11-16T00:00:00Z")

    private fun advisor(azimuth: Double, altitude: Double) =
        TripAdvisor(analyzer = ExposureAnalyzer { _, _, _ -> SunPosition(azimuth, altitude) })

    /** 북쪽으로 가는 직선 구간 */
    private fun north(mode: Mode, min: Long, lat0: Double = 37.0) =
        Leg(mode, listOf(LatLng(lat0, 127.0), LatLng(lat0 + 0.05, 127.0), LatLng(lat0 + 0.1, 127.0)), Duration.ofMinutes(min), "A", "B", com.sunseat.app.domain.GroundLevel.ABOVE)

    private fun south(mode: Mode, min: Long, lat0: Double = 37.1) =
        Leg(mode, listOf(LatLng(lat0, 127.0), LatLng(lat0 - 0.1, 127.0)), Duration.ofMinutes(min), "B", "C", com.sunseat.app.domain.GroundLevel.ABOVE)

    @Test
    fun sunOnRightRecommendsLeftWhenAvoiding() {
        val r = advisor(90.0, 30.0).advise(Route(listOf(north(Mode.BUS, 30))), t0, SunGoal.AVOID)
        val a = r.advice as TripAdvice.Sit
        assertEquals(Side.LEFT, a.side)
        assertEquals(SunPlacement.RIGHT, r.sunPlacement)
        assertEquals(Duration.ofMinutes(30), r.shadeIfFollowed)
        assertEquals(Duration.ZERO, r.sunIfFollowed)
        assertTrue(r.leftSpans.isEmpty())
        assertEquals(1, r.rightSpans.size)
        assertEquals(0f, r.rightSpans[0].start, 0.01f)
        assertEquals(1f, r.rightSpans[0].end, 0.01f)
        assertEquals(t0 + Duration.ofMinutes(30), r.arrival)
    }

    @Test
    fun seekingSunFlipsSide() {
        val r = advisor(90.0, 30.0).advise(Route(listOf(north(Mode.BUS, 30))), t0, SunGoal.SEEK)
        assertEquals(Side.RIGHT, (r.advice as TripAdvice.Sit).side)
        assertEquals(Duration.ofMinutes(30), r.sunIfFollowed)
    }

    @Test
    fun nightHasNoSun() {
        val r = advisor(90.0, -10.0).advise(Route(listOf(north(Mode.BUS, 30))), t0, SunGoal.AVOID)
        assertEquals(TripAdvice.NoSun, r.advice)
        assertEquals(null, r.sunPlacement)
        assertTrue(r.leftSpans.isEmpty() && r.rightSpans.isEmpty())
    }

    @Test
    fun oppositeDirectionsAreMixed() {
        val r = advisor(90.0, 30.0).advise(Route(listOf(north(Mode.BUS, 20), south(Mode.BUS, 20))), t0, SunGoal.AVOID)
        assertEquals(TripAdvice.Mixed, r.advice)
        assertEquals(Side.LEFT, (r.legs[0].advice as LegAdvice.Sit).side)
        assertEquals(Side.RIGHT, (r.legs[1].advice as LegAdvice.Sit).side)
        // 오른쪽(해) 구간은 앞 절반, 왼쪽(해) 구간은 뒤 절반
        assertEquals(0.5f, r.rightSpans.last().end, 0.02f)
        assertEquals(0.5f, r.leftSpans.first().start, 0.02f)
    }

    @Test
    fun walkLegIsExcluded() {
        val walk = Leg(Mode.WALK, listOf(LatLng(37.1, 127.0), LatLng(37.1, 127.001)), Duration.ofMinutes(3), "B", "B")
        val r = advisor(90.0, 30.0).advise(Route(listOf(north(Mode.BUS, 20), walk)), t0, SunGoal.AVOID)
        assertEquals(LegAdvice.Walk, r.legs[1].advice)
        assertTrue(r.advice is TripAdvice.Sit)
        assertEquals(Duration.ofMinutes(20), r.shadeIfFollowed + r.sunIfFollowed)
    }

    @Test
    fun subwayNumbersTimelineAndBenchesAgree() {
        // 해가 오른쪽 낮게(20°): 오른쪽 벤치는 등 뒤, 왼쪽 벤치는 정면으로 해를 받는다
        val r = advisor(90.0, 20.0).advise(Route(listOf(north(Mode.SUBWAY, 30))), t0, SunGoal.AVOID)
        val leg = r.legs[0]
        val sit = leg.advice as LegAdvice.Sit
        assertEquals(leg.leftBench!!.total, leg.leftSun)
        assertEquals(leg.rightBench!!.total, leg.rightSun)
        assertTrue(leg.rightBench!!.back > Duration.ZERO)
        assertTrue(leg.leftBench!!.front > Duration.ZERO)
        // 숫자 = 추천 쪽의 해 받는 시간, 타임라인도 그쪽 줄에 같은 구간이 칠해진다
        assertEquals(leg.sun(sit.side), r.sunIfFollowed)
        assertTrue(r.leftSpans.isNotEmpty() && r.rightSpans.isNotEmpty())
    }

    @Test
    fun highSunOnSubwayLeavesOppositeBenchInShade() {
        val r = advisor(90.0, 60.0).advise(Route(listOf(north(Mode.SUBWAY, 30))), t0, SunGoal.AVOID)
        assertEquals(Side.LEFT, (r.advice as TripAdvice.Sit).side)
        assertEquals(Duration.ZERO, r.sunIfFollowed)
        assertTrue(r.leftSpans.isEmpty())
    }

    @Test
    fun sunNearlyAheadIsNotSideSun() {
        val r = advisor(10.0, 10.0).advise(Route(listOf(north(Mode.CAR, 30))), t0, SunGoal.AVOID)
        assertEquals(SunPlacement.AHEAD, r.sunPlacement)
        assertTrue(r.leftSpans.isEmpty() && r.rightSpans.isEmpty())
        assertEquals(Duration.ofMinutes(30), r.legs[0].aheadSun)
        assertEquals(TripAdvice.NoDifference, r.advice)
    }

    @Test
    fun walkSpansAreReported() {
        val walk = Leg(Mode.WALK, listOf(LatLng(37.1, 127.0), LatLng(37.1, 127.001)), Duration.ofMinutes(10), "B", "B")
        val r = advisor(90.0, 30.0).advise(Route(listOf(north(Mode.BUS, 30), walk)), t0, SunGoal.AVOID)
        assertEquals(1, r.walkSpans.size)
        assertEquals(0.75f, r.walkSpans[0].start, 0.01f)
        assertEquals(1f, r.walkSpans[0].end, 0.01f)
    }

    @Test
    fun carLabelsSeatsAndDetectsGlare() {
        val avoid = advisor(90.0, 30.0).advise(Route(listOf(north(Mode.CAR, 30))), t0, SunGoal.AVOID)
        assertEquals(listOf(CarSeat.REAR_LEFT), (avoid.advice as TripAdvice.Sit).carSeats)
        val glare = advisor(0.0, 10.0).advise(Route(listOf(north(Mode.CAR, 30))), t0, SunGoal.AVOID)
        assertTrue(SeatReason.LOW_FRONT in (glare.legs[0].advice as LegAdvice.Sit).reasons)
        assertTrue(glare.glare.isNotEmpty())
        assertEquals(0L, glare.glare[0].fromMinute)
        val noGlareBus = advisor(0.0, 10.0).advise(Route(listOf(north(Mode.BUS, 30))), t0, SunGoal.AVOID)
        assertTrue(noGlareBus.glare.isEmpty())
    }
}
