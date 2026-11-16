package com.sunseat.app.domain

import org.junit.Assert.*
import org.junit.Test
import java.time.Duration
import java.time.Instant

class SeatDetailsTest {
    private val start = Instant.parse("2026-11-16T00:00:00Z")
    private val path = listOf(LatLng(37.0, 127.0), LatLng(37.1, 127.0), LatLng(37.2, 127.0))
    private fun leg(mode: Mode = Mode.BUS, minutes: Long = 20) = Leg(mode, path, Duration.ofMinutes(minutes), "A", "B", GroundLevel.ABOVE)
    private fun advisor(angle: Double, altitude: Double) = TripAdvisor(ExposureAnalyzer { _, _, _ -> SunPosition(angle, altitude) })
    private fun sit(angle: Double, altitude: Double = 20.0, goal: SunGoal = SunGoal.AVOID, mode: Mode = Mode.BUS, travel: TravelMode = TravelMode.TRANSIT) =
        advisor(angle, altitude).advise(Route(listOf(leg(mode))), start, goal, travelMode = travel).legs.single().advice as LegAdvice.Sit

    @Test fun lowFrontAndRearChooseOppositeZones() {
        assertEquals(SeatZone.MIDDLE_REAR, sit(0.0).spot.zone)
        assertEquals(SeatZone.FRONT_MIDDLE, sit(180.0).spot.zone)
        assertEquals(SeatZone.MIDDLE_REAR, sit(45.0).spot.zone)
        assertEquals(SeatZone.FRONT_MIDDLE, sit(135.0).spot.zone)
        assertEquals(SeatZone.FRONT_MIDDLE, sit(0.0, goal = SunGoal.SEEK).spot.zone)
        assertNull(sit(0.0, altitude = 25.01).spot.zone)
        assertEquals(SeatZone.MIDDLE_REAR, sit(0.0, altitude = 25.0).spot.zone)
    }
    @Test fun busEffectiveAltitudeBoundaryAndAlternative() {
        assertTrue(SeatReason.CROSS_CABIN in sit(90.0, 30.0).reasons)
        assertFalse(SeatReason.CROSS_CABIN in sit(90.0, 30.01).reasons)
        assertEquals(SeatPosition.AISLE, sit(90.0).alternative!!.position)
        val seek = sit(90.0, goal = SunGoal.SEEK)
        assertEquals(Side.RIGHT, seek.side)
        assertEquals(SeatPosition.WINDOW, seek.spot.position)
    }
    @Test fun taxiRearSeatsDoorAndNoDriverGlare() {
        val left = sit(90.0, mode = Mode.CAR, travel = TravelMode.TAXI)
        assertEquals(listOf(CarSeat.REAR_LEFT), left.carSeats)
        assertTrue(SeatReason.TAXI_RIGHT_DOOR in left.reasons)
        val right = sit(270.0, mode = Mode.CAR, travel = TravelMode.TAXI)
        assertEquals(listOf(CarSeat.REAR_RIGHT), right.carSeats)
        assertFalse(SeatReason.TAXI_RIGHT_DOOR in right.reasons)
        val front = sit(0.0, mode = Mode.CAR, travel = TravelMode.TAXI)
        assertFalse(CarSeat.PASSENGER in front.carSeats)
        assertFalse(CarSeat.PASSENGER in sit(0.0, mode = Mode.CAR).carSeats)
        assertTrue(advisor(0.0, 10.0).advise(Route(listOf(leg(Mode.CAR))), start, SunGoal.AVOID, travelMode = TravelMode.TAXI).glare.isEmpty())
    }
    private fun changing(minutes: Long, mode: Mode = Mode.BUS): Pair<TripAdvisor, TripResult> {
        val advisor = TripAdvisor(ExposureAnalyzer { _, _, time -> SunPosition(if (time < start.plusSeconds(minutes * 30)) 90.0 else 270.0, 50.0) })
        return advisor to advisor.advise(Route(listOf(leg(mode, minutes))), start, SunGoal.AVOID)
    }
    @Test fun switchesRequireFiveContinuousMinutesAndMatchTimelineAndTotals() {
        val (advisor, result) = changing(10)
        val advice = result.legs.single().advice as LegAdvice.Sit
        assertEquals(1, advice.switches.size)
        val change = advice.switches.single()
        assertEquals(start.plusSeconds(300), change.at)
        assertNull(change.stopName)
        assertEquals(Side.RIGHT, change.to.side)
        assertEquals(Duration.ZERO, result.sunIfFollowed)
        assertEquals(Duration.ofMinutes(10), result.shadeIfFollowed)
        val timeline = TripTimeline(Route(listOf(leg(minutes = 10))), result, SunPositionSource { _, _, t -> SunPosition(if (t < start.plusSeconds(300)) 90.0 else 270.0, 50.0) }, advisor)
        assertEquals(Side.LEFT, timeline.at(change.at.minusSeconds(1))!!.recommended)
        assertEquals(Side.RIGHT, timeline.at(change.at)!!.recommended)
        assertTrue((changing(8).second.legs.single().advice as LegAdvice.Sit).switches.isEmpty())
    }
    @Test fun subwayFrontWindowsMergeAndUndergroundIsExcluded() {
        val result = advisor(90.0, 20.0).advise(Route(listOf(leg(Mode.SUBWAY), leg(Mode.SUBWAY).copy(groundLevel = GroundLevel.BELOW))), start, SunGoal.AVOID)
        assertEquals(1, result.legs[0].frontWindows.size)
        assertEquals(Duration.ofMinutes(20), Duration.between(result.legs[0].frontWindows[0].start, result.legs[0].frontWindows[0].end))
        assertTrue(result.legs[1].frontWindows.isEmpty())
        assertEquals(LegAdvice.Underground, result.legs[1].advice)
    }
    @Test fun subwayBalancedWholeLegStillSwitchesWhenSunSideChanges() {
        val result = changing(10, Mode.SUBWAY).second
        val advice = result.legs.single().advice as LegAdvice.Sit
        assertEquals(1, advice.switches.size)
        assertEquals(Side.LEFT, advice.side)
        assertEquals(Side.RIGHT, advice.switches.single().to.side)
        assertEquals(Duration.ZERO, result.sunIfFollowed)
    }
}
