package com.sunseat.app.domain

import java.time.Duration
import java.time.Instant
import org.junit.Assert.*
import org.junit.Test

class TripTimelineTest {
    private val start = Instant.parse("2026-11-16T00:00:00Z")
    private val source = SunPositionSource { _, _, _ -> SunPosition(90.0, 15.0) }
    private fun leg(mode: Mode, minutes: Long, latitude: Double = 37.5, ground: GroundLevel = GroundLevel.ABOVE) = Leg(mode,
        listOf(LatLng(latitude, 127.0), LatLng(latitude + 0.01, 127.0), LatLng(latitude + 0.02, 127.0)), Duration.ofMinutes(minutes), "A", "B", ground)
    private val route = Route(listOf(leg(Mode.BUS, 5), leg(Mode.WALK, 2), leg(Mode.SUBWAY, 5)))
    private fun timeline(route: Route = this.route, sun: SunPositionSource = source): TripTimeline {
        val advisor = TripAdvisor(ExposureAnalyzer(sun))
        return TripTimeline(route, advisor.advise(route, start, SunGoal.AVOID), sun, advisor)
    }
    @Test fun clampsEndsAndChoosesNextLegAtBoundary() {
        val timeline = timeline()
        assertEquals(route.legs.first().path.first(), timeline.at(start.minusSeconds(1))!!.position)
        assertEquals(1, timeline.at(start.plusSeconds(300))!!.legIndex)
        assertTrue(timeline.at(start.plusSeconds(300))!!.litSides.isEmpty())
        assertEquals(2, timeline.at(start.plusSeconds(420))!!.legIndex)
        assertEquals(route.legs.last().path.last(), timeline.at(start.plusSeconds(9999))!!.position)
        assertEquals(1f, timeline.at(start.plusSeconds(9999))!!.routeProgress, 0.001f)
    }
    @Test fun interpolatesWithoutRecalculatingSeatRules() {
        val timeline = timeline()
        assertEquals(37.505, timeline.at(start.plusSeconds(75))!!.position!!.latitude, 0.000001)
        for (seconds in 0 until 720 step 10) {
            val point = timeline.at(start.plusSeconds(seconds.toLong()))!!
            val fraction = seconds / 720f
            val expected = mutableSetOf<Side>()
            if (timeline.result.leftSpans.any { fraction >= it.start && fraction < it.end }) expected += Side.LEFT
            if (timeline.result.rightSpans.any { fraction >= it.start && fraction < it.end }) expected += Side.RIGHT
            assertEquals("second=$seconds", expected, point.litSides)
        }
    }
    @Test fun undergroundMovesButNeverLightsSeats() {
        val timeline = timeline(Route(listOf(leg(Mode.SUBWAY, 5, ground = GroundLevel.BELOW))))
        val point = timeline.at(start.plusSeconds(150))!!
        assertEquals(37.51, point.position!!.latitude, 0.00001)
        assertTrue(point.undergroundOrUnknown)
        assertTrue(point.litSides.isEmpty())
        assertTrue(timeline.result.leftSpans.isEmpty() && timeline.result.rightSpans.isEmpty())
    }
    @Test fun unknownAndNightCannotShowSeatSun() {
        assertTrue(timeline(Route(listOf(leg(Mode.SUBWAY, 5, ground = GroundLevel.UNKNOWN)))).at(start)!!.litSides.isEmpty())
        assertTrue(timeline(sun = SunPositionSource { _, _, _ -> SunPosition(90.0, -10.0) }).at(start)!!.litSides.isEmpty())
    }
    @Test fun missingGeometryAndZeroDurationAreSafe() {
        val missing = timeline(Route(listOf(leg(Mode.BUS, 5).copy(path = emptyList())))).at(start)!!
        assertNull(missing.position); assertNull(missing.heading)
        val zeroThenBus = timeline(Route(listOf(leg(Mode.WALK, 0), leg(Mode.BUS, 5))))
        assertEquals(1, zeroThenBus.at(start)!!.legIndex)
        assertNull(timeline(Route(emptyList())).at(start))
    }
    @Test fun indexesManySegmentsCorrectly() {
        val path = (0..10000).map { LatLng(37.0 + it * 0.00001, 127.0) }
        val route = Route(listOf(Leg(Mode.BUS, path, Duration.ofSeconds(10000), "A", "B")))
        val timeline = timeline(route)
        assertEquals(37.08765, timeline.at(start.plusMillis(8_765_000))!!.position!!.latitude, 0.000001)
    }
}
