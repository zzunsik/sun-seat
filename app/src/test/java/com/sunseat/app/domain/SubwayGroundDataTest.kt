package com.sunseat.app.domain

import java.time.Duration
import java.time.Instant
import org.junit.Assert.*
import org.junit.Test

class SubwayGroundDataTest {
    private val data = SubwayGroundData.bundled

    @Test fun knownPlatformsAreLineSpecific() {
        assertEquals(GroundLevel.ABOVE, data.level("2호선", "성수"))
        assertEquals(GroundLevel.BELOW, data.level("2호선", "시청"))
        assertEquals(GroundLevel.ABOVE, data.level("1호선", "도봉산"))
        assertEquals(GroundLevel.BELOW, data.level("7호선", "건대입구"))
        assertEquals(GroundLevel.ABOVE, data.level("2호선", "건대입구"))
        assertEquals(GroundLevel.BELOW, data.level("인천1호선", "부평"))
        assertEquals(GroundLevel.ABOVE, data.level("광주1호선", "녹동"))
    }

    @Test fun normalizationDoesNotCrossLines() {
        assertEquals("서울", SubwayGroundData.normalizeStation(" 서울역 (부역명) "))
        assertEquals(data.level("1호선", "서울"), data.level("수도권 1호선", "서울역"))
        assertEquals(GroundLevel.UNKNOWN, data.level("9호선", "건대입구"))
        assertEquals(GroundLevel.UNKNOWN, data.level("2호선", "없는역"))
    }

    @Test fun sectionRequiresEveryKnownStopToBeAboveGround() {
        assertEquals(GroundLevel.ABOVE, data.section("2호선", listOf("성수", "건대입구", "구의")))
        assertEquals(GroundLevel.BELOW, data.section("2호선", listOf("성수", "시청", "구의")))
        assertEquals(GroundLevel.UNKNOWN, data.section("2호선", listOf("성수", "없는역")))
        assertEquals(GroundLevel.UNKNOWN, data.section("2호선", listOf("성수")))
    }

    @Test fun carTimelineHasNoExcludedGaps() {
        val leg = Leg(Mode.CAR, listOf(LatLng(37.5, 127.0), LatLng(37.6, 127.0)), Duration.ofMinutes(10), "A", "B")
        val result = TripAdvisor(ExposureAnalyzer { _, _, _ -> SunPosition(90.0, 30.0) })
            .advise(Route(listOf(leg)), Instant.EPOCH, SunGoal.AVOID)
        assertTrue(result.walkSpans.isEmpty())
        assertTrue(result.rightSpans.isNotEmpty())
    }

    @Test fun undergroundAndUnknownAreExcludedWithoutLosingTravelTime() {
        val leg = Leg(Mode.SUBWAY, listOf(LatLng(37.5, 127.0), LatLng(37.6, 127.0)), Duration.ofMinutes(10), "A", "B")
        val route = Route(listOf(leg.copy(groundLevel = GroundLevel.BELOW), leg, leg.copy(mode = Mode.BUS)))
        val advisor = TripAdvisor(ExposureAnalyzer { _, _, _ -> SunPosition(90.0, 30.0) })
        val result = advisor.advise(route, Instant.EPOCH, SunGoal.AVOID)
        assertEquals(LegAdvice.Underground, result.legs[0].advice)
        assertEquals(LegAdvice.UnknownGround, result.legs[1].advice)
        assertEquals(Duration.ZERO, result.legs[0].rightSun)
        assertEquals(Duration.ofMinutes(30), Duration.between(result.departure, result.arrival))
        assertEquals(Duration.ofMinutes(10), result.sunIfFollowed + result.shadeIfFollowed)
        assertEquals(2, result.walkSpans.size)
        assertEquals(2f / 3f, result.walkSpans.last().end, 0.001f)
        assertTrue(result.rightSpans.all { it.start >= 2f / 3f })
    }
}
