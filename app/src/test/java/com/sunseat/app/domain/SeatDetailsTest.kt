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

    // ---- 경계값(#60): 해가 진 시각, 정남·정북 진행, 0분 구간 ----

    @Test fun sunAtOrBelowHorizonGivesNoSeatDetail() {
        listOf(0.0, -0.01, -10.0).forEach { altitude ->
            listOf(Mode.BUS, Mode.CAR, Mode.SUBWAY).forEach { mode ->
                val result = advisor(90.0, altitude).advise(Route(listOf(leg(mode))), start, SunGoal.AVOID)
                val legResult = result.legs.single()
                assertEquals("$mode $altitude", LegAdvice.NoSun, legResult.advice)
                // 타임라인·그림에도 햇빛이 칠해지지 않는다
                assertEquals(Duration.ZERO, legResult.leftSun + legResult.rightSun + legResult.aheadSun)
                assertNull(result.sunPlacement)
                assertTrue(result.leftSpans.isEmpty() && result.rightSpans.isEmpty())
                assertEquals(TripAdvice.NoSun, result.advice)
                assertEquals(Duration.ZERO, result.sunIfFollowed)
                assertEquals(Duration.ofMinutes(20), result.shadeIfFollowed)
                assertTrue(legResult.frontWindows.isEmpty())
                assertTrue(result.glare.isEmpty())
            }
        }
        // 해가 막 뜬 경우(고도 0.01°)는 자리 추천이 나온다
        assertTrue(advisor(90.0, 0.01).advise(Route(listOf(leg())), start, SunGoal.AVOID).legs.single().advice is LegAdvice.Sit)
    }

    @Test fun sunSettingMidLegSplitsLitAndDarkTime() {
        val sunset = TripAdvisor(ExposureAnalyzer { _, _, time -> SunPosition(270.0, if (time < start.plusSeconds(600)) 5.0 else -1.0) })
        val result = sunset.advise(Route(listOf(leg())), start, SunGoal.AVOID)
        val advice = result.legs.single().advice as LegAdvice.Sit
        assertEquals(Side.RIGHT, advice.side)
        assertTrue(advice.switches.isEmpty())
        assertEquals(Duration.ofMinutes(10), result.legs.single().leftSun)
        assertEquals(Duration.ZERO, result.sunIfFollowed)
    }

    private val southPath = path.reversed()
    private fun heading(path: List<LatLng>, azimuth: Double, altitude: Double = 20.0, mode: Mode = Mode.BUS) =
        advisor(azimuth, altitude).advise(Route(listOf(Leg(mode, path, Duration.ofMinutes(20), "A", "B", GroundLevel.ABOVE))), start, SunGoal.AVOID)

    @Test fun dueNorthAndDueSouthMirrorSides() {
        // 동쪽 해: 북행이면 오른쪽, 남행이면 왼쪽에 해가 있다
        assertEquals(Side.LEFT, (heading(path, 90.0).legs.single().advice as LegAdvice.Sit).side)
        assertEquals(Side.RIGHT, (heading(southPath, 90.0).legs.single().advice as LegAdvice.Sit).side)
        assertEquals(Side.RIGHT, (heading(path, 270.0).legs.single().advice as LegAdvice.Sit).side)
        assertEquals(Side.LEFT, (heading(southPath, 270.0).legs.single().advice as LegAdvice.Sit).side)
        // 낮은 남쪽 해: 남행이면 앞, 북행이면 뒤
        assertEquals(SeatZone.MIDDLE_REAR, (heading(southPath, 180.0).legs.single().advice as LegAdvice.Sit).spot.zone)
        assertEquals(SeatZone.FRONT_MIDDLE, (heading(path, 180.0).legs.single().advice as LegAdvice.Sit).spot.zone)
    }

    @Test fun sunExactlyAheadOrBehindIsNotASideRecommendation() {
        listOf(path to 0.0, path to 180.0, southPath to 180.0, southPath to 0.0).forEach { (p, azimuth) ->
            val result = heading(p, azimuth)
            val leg = result.legs.single()
            assertEquals(Duration.ZERO, leg.leftSun)
            assertEquals(Duration.ZERO, leg.rightSun)
            assertEquals(Duration.ofMinutes(20), leg.aheadSun)
            assertEquals(TripAdvice.NoDifference, result.advice)
            assertEquals(SunPlacement.AHEAD, result.sunPlacement)
        }
        // 앞쪽 원뿔(±25°) 경계: 25°는 아직 앞, 25° 밖은 옆
        assertEquals(Duration.ofMinutes(20), heading(path, 24.99).legs.single().aheadSun)
        assertTrue(heading(path, 25.01).legs.single().advice is LegAdvice.Sit)
    }

    @Test fun zeroMinuteLegsDoNotAddTimeOrSwitches() {
        listOf(Mode.BUS, Mode.CAR, Mode.SUBWAY).forEach { mode ->
            val zero = Leg(mode, path, Duration.ZERO, "A", "B", GroundLevel.ABOVE)
            val only = advisor(90.0, 20.0).advise(Route(listOf(zero)), start, SunGoal.AVOID, travelMode = if (mode == Mode.CAR) TravelMode.CAR else TravelMode.TRANSIT)
            assertEquals("$mode", Duration.ZERO, only.sunIfFollowed + only.shadeIfFollowed)
            assertEquals(start, only.arrival)
            (only.legs.single().advice as? LegAdvice.Sit)?.let { assertTrue(it.switches.isEmpty()) }

            val mixed = advisor(90.0, 20.0).advise(Route(listOf(zero, leg(mode, 10))), start, SunGoal.AVOID)
            val alone = advisor(90.0, 20.0).advise(Route(listOf(leg(mode, 10))), start, SunGoal.AVOID)
            assertEquals("$mode", alone.sunIfFollowed, mixed.sunIfFollowed)
            assertEquals("$mode", alone.shadeIfFollowed, mixed.shadeIfFollowed)
            assertEquals("$mode", alone.advice, mixed.advice)
        }
    }
}
