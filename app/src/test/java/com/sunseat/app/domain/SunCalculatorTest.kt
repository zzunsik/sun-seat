package com.sunseat.app.domain

import java.time.LocalDateTime
import java.time.ZoneId
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SunCalculatorTest {
    private val lat = 37.5665
    private val lon = 126.9780
    private val kst = ZoneId.of("Asia/Seoul")
    private val tilt = 23.44

    private fun at(y: Int, m: Int, d: Int, h: Int, min: Int = 0) =
        SunCalculator.positionAt(lat, lon, LocalDateTime.of(y, m, d, h, min).atZone(kst).toInstant())

    // 서울의 남중 시각은 약 12시 30분 무렵(경도 127°, 균시차 반영). 남중 고도 = 90 − 위도 ± 23.44
    @Test
    fun summerSolsticeNoonAltitude() {
        assertEquals(90 - lat + tilt, at(2025, 6, 21, 12, 34).altitude, 1.0)
    }

    @Test
    fun winterSolsticeNoonAltitude() {
        assertEquals(90 - lat - tilt, at(2025, 12, 21, 12, 30).altitude, 1.0)
    }

    @Test
    fun springEquinoxNoonAltitude() {
        assertEquals(90 - lat, at(2025, 3, 20, 12, 38).altitude, 1.0)
    }

    @Test
    fun noonAzimuthIsSouth() {
        assertEquals(180.0, at(2025, 3, 20, 12, 38).azimuth, 3.0)
    }

    // 하지 서울 일출 약 05:11, 일몰 약 19:57
    @Test
    fun altitudeSignAroundSunriseAndSunset() {
        assertTrue(at(2025, 6, 21, 4, 40).altitude < 0)
        assertTrue(at(2025, 6, 21, 5, 45).altitude > 0)
        assertTrue(at(2025, 6, 21, 19, 25).altitude > 0)
        assertTrue(at(2025, 6, 21, 20, 30).altitude < 0)
    }

    @Test
    fun morningSunIsEastAndAfternoonSunIsWest() {
        val morning = at(2025, 3, 20, 9, 0).azimuth
        val afternoon = at(2025, 3, 20, 16, 0).azimuth
        assertTrue("오전 방위각 $morning", morning in 90.0..180.0)
        assertTrue("오후 방위각 $afternoon", afternoon in 180.0..270.0)
    }

    // 춘분 무렵 해는 거의 정동에서 떠서 정서로 진다
    @Test
    fun equinoxSunriseAzimuthNearEast() {
        assertEquals(90.0, at(2025, 3, 20, 6, 40).azimuth, 5.0)
        assertEquals(270.0, at(2025, 3, 20, 18, 40).azimuth, 5.0)
    }

    @Test
    fun azimuthIsNormalized() {
        for (h in 0..23) {
            val az = at(2025, 6, 21, h).azimuth
            assertTrue(az >= 0.0 && az < 360.0)
        }
    }
}
