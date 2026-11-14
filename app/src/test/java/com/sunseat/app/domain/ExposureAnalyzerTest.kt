package com.sunseat.app.domain

import java.time.Duration
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId
import kotlin.math.cos
import kotlin.math.sin
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ExposureAnalyzerTest {
    private val kst = ZoneId.of("Asia/Seoul")
    private val seoul = LatLng(37.5665, 126.9780)
    private val real = ExposureAnalyzer()

    private fun kst(y: Int, m: Int, d: Int, h: Int, min: Int = 0): Instant =
        LocalDateTime.of(y, m, d, h, min).atZone(kst).toInstant()

    private fun fixedSun(azimuth: Double, altitude: Double) =
        ExposureAnalyzer { _, _, _ -> SunPosition(azimuth, altitude) }

    private fun leg(vararg points: LatLng, minutes: Long = 10, mode: Mode = Mode.BUS) =
        Leg(mode, points.toList(), Duration.ofMinutes(minutes), "출발", "도착")

    // 서울에서 약 1km 정북·정동 이동
    private val northLeg = leg(seoul, LatLng(seoul.latitude + 0.01, seoul.longitude))
    private val eastLeg = leg(seoul, LatLng(seoul.latitude, seoul.longitude + 0.01))

    private fun single(leg: Leg, start: Instant, analyzer: ExposureAnalyzer = real) =
        analyzer.analyze(Route(listOf(leg)), start).legs.single()

    @Test
    fun headingNorthMorningSunOnRightAfternoonOnLeft() {
        val morning = single(northLeg, kst(2025, 3, 20, 9))
        assertEquals(0.0, morning.segments.single().heading, 0.1)
        assertTrue(morning.right > 0 && morning.left == 0.0)

        val afternoon = single(northLeg, kst(2025, 3, 20, 16))
        assertTrue(afternoon.left > 0 && afternoon.right == 0.0)
    }

    @Test
    fun headingEastEarlySummerMorningOnLeftAfternoonOnRight() {
        // 하지 이른 아침 해는 북동쪽(방위각 약 65°)에 있다
        val morning = single(eastLeg, kst(2025, 6, 21, 6))
        assertEquals(90.0, morning.segments.single().heading, 0.1)
        assertTrue(morning.left > 0 && morning.right == 0.0)

        // 춘분 오후 해는 남서쪽(방위각 약 225°)에 있다
        val afternoon = single(eastLeg, kst(2025, 3, 20, 15))
        assertTrue(afternoon.right > 0 && afternoon.left == 0.0)
    }

    @Test
    fun nightHasNoExposure() {
        val night = single(northLeg, kst(2025, 6, 21, 23))
        assertEquals(1, night.segments.size)
        assertEquals(0.0, night.left, 0.0)
        assertEquals(0.0, night.right, 0.0)
    }

    @Test
    fun belowHorizonFixedSunHasNoExposure() {
        val result = single(northLeg, Instant.EPOCH, fixedSun(90.0, -0.5))
        assertEquals(0.0, result.left + result.right, 0.0)
    }

    @Test
    fun sunAheadOrBehindIsNearZero() {
        val ahead = single(northLeg, Instant.EPOCH, fixedSun(0.0, 30.0))
        val behind = single(northLeg, Instant.EPOCH, fixedSun(180.0, 30.0))
        assertEquals(0.0, ahead.left + ahead.right, 1e-3)
        assertEquals(0.0, behind.left + behind.right, 1e-3)
    }

    @Test
    fun weightIsDurationTimesCosAltitudeTimesSinR() {
        val result = single(northLeg, Instant.EPOCH, fixedSun(90.0, 60.0))
        assertEquals(600 * cos(Math.toRadians(60.0)), result.right, 0.5)
    }

    @Test
    fun lShapedLegSumsBothSegments() {
        // 적도 부근에서는 위도·경도 0.01°가 거의 같은 거리여서 두 선분이 300초씩 나뉜다
        val l = leg(LatLng(0.0, 0.0), LatLng(0.01, 0.0), LatLng(0.01, 0.01), minutes = 10)
        val result = single(l, Instant.EPOCH, fixedSun(45.0, 30.0))
        val (north, east) = result.segments
        assertEquals(Side.RIGHT, north.side) // r = 45
        assertEquals(Side.LEFT, east.side) // r = 315
        assertEquals(Instant.EPOCH, north.startTime)
        assertEquals(300.0, north.duration.seconds.toDouble(), 1.0)
        assertEquals(north.endTime, east.startTime)
        val expected = 300 * cos(Math.toRadians(30.0)) * sin(Math.toRadians(45.0))
        assertEquals(expected, result.right, 1.0)
        assertEquals(expected, result.left, 1.0)
    }

    @Test
    fun legsStartAfterPreviousLegs() {
        val route = Route(listOf(northLeg, leg(seoul, seoul.copy(latitude = 37.6), minutes = 5)))
        val result = fixedSun(90.0, 30.0).analyze(route, Instant.EPOCH)
        assertEquals(Instant.EPOCH.plusSeconds(600), result.legs[1].startTime)
        assertEquals(Instant.EPOCH.plusSeconds(600), result.legs[1].segments.first().startTime)
        assertEquals(Duration.ofMinutes(15), route.duration)
    }

    @Test
    fun fewerThanTwoPointsHasNoSegments() {
        val analyzer = fixedSun(90.0, 30.0)
        for (l in listOf(leg(), leg(seoul))) {
            val result = single(l, Instant.EPOCH, analyzer)
            assertTrue(result.segments.isEmpty())
            assertEquals(0.0, result.left + result.right, 0.0)
        }
    }

    @Test
    fun zeroDurationHasZeroWeight() {
        val result = single(leg(seoul, LatLng(37.6, 126.978), minutes = 0), Instant.EPOCH, fixedSun(90.0, 30.0))
        assertEquals(1, result.segments.size)
        assertEquals(0.0, result.left + result.right, 0.0)
    }

    @Test
    fun repeatedPointsAreSkipped() {
        val result = single(leg(seoul, seoul, LatLng(37.6, 126.978)), Instant.EPOCH, fixedSun(90.0, 30.0))
        assertEquals(1, result.segments.size)
        assertEquals(600.0, result.segments.single().duration.seconds.toDouble(), 1.0)
    }
}
