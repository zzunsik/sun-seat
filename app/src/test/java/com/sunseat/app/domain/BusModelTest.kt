package com.sunseat.app.domain

import org.junit.Assert.*
import org.junit.Test
import java.time.Duration
import kotlin.math.abs

/** #76: standard city bus vs 40-seat express coach models, how the default is estimated and how seats map between them. */
class BusModelTest {
    private fun bus(type: String? = null) = Leg(Mode.BUS, listOf(LatLng(37.0, 127.0), LatLng(37.01, 127.0)), Duration.ofMinutes(30), "A", "B", GroundLevel.ABOVE, busType = type)
    private val city = CabinProfile.forLeg(bus(), TravelMode.TRANSIT, CabinKind.BUS)!!
    private val coach = CabinProfile.forLeg(bus(), TravelMode.TRANSIT, CabinKind.COACH)!!

    @Test fun coachHasFortyForwardSeatsInTenTwoPlusTwoRowsExcludingTheDriver() {
        assertEquals(CabinKind.COACH, coach.kind)
        assertEquals(40, coach.seats.size)
        assertEquals((1..40).toList(), coach.seats.map { it.number })
        assertEquals(40, coach.seats.map { it.id }.toSet().size)
        assertTrue(coach.seats.all { it.facing == V3(0.0, 0.0, -1.0) })
        for (side in Side.entries) for (pos in SeatPosition.entries)
            assertEquals("$side $pos", 10, coach.seats.count { it.spot.side == side && it.spot.position == pos })
        // 10 rows of 2+2 with one aisle in the middle.
        assertEquals(10, coach.seats.map { it.center.z }.distinct().size)
        coach.seats.groupBy { it.center.z }.values.forEach { row -> assertEquals(listOf(Side.LEFT, Side.LEFT, Side.RIGHT, Side.RIGHT), row.sortedBy { it.center.x }.map { it.spot.side }) }
        // Numbering: front to back, left window → left aisle → right aisle → right window. Model numbers only, not real ticket numbers.
        assertEquals(listOf(SeatSpot(Side.LEFT, SeatZone.FRONT, SeatPosition.WINDOW), SeatSpot(Side.LEFT, SeatZone.FRONT, SeatPosition.AISLE),
            SeatSpot(Side.RIGHT, SeatZone.FRONT, SeatPosition.AISLE), SeatSpot(Side.RIGHT, SeatZone.FRONT, SeatPosition.WINDOW)), coach.seats.take(4).map { it.spot })
        assertEquals(SeatZone.REAR, coach.seats.last().spot.zone)
        assertNotNull("driver exists as a blocker", coach.driver)
        assertTrue(coach.seats.none { it.id == coach.driver!!.id })
    }

    @Test fun cityBusHasFrontAndRearDoorsDriverAisleAndMixedSeatDirections() {
        assertEquals(CabinKind.BUS, city.kind)
        assertEquals(2, city.doors.size)
        assertTrue("doors are on the kerb (right) side", city.doors.all { it.min.x > 0 })
        assertTrue("front door is ahead of every seat", city.doors.minBy { it.min.z }.max.z < city.seats.minOf { it.center.z } - .23)
        assertTrue("some seats face the aisle", city.seats.any { abs(it.facing.x) > .5 })
        assertTrue("most seats face forward", city.seats.count { it.facing.z < -.5 } > city.seats.size / 2)
        assertTrue(city.seats.none { it.spot.side == Side.LEFT && it.spot.position == SeatPosition.AISLE })
        assertEquals((1..city.seats.size).toList(), city.seats.map { it.number })
        assertNotNull(city.driver)
        for (zone in listOf(SeatZone.FRONT, SeatZone.MIDDLE, SeatZone.REAR)) for (side in Side.entries)
            assertTrue("$side $zone window", city.seats.any { it.spot == SeatSpot(side, zone, SeatPosition.WINDOW) && it.facing.z < -.5 })
    }

    @Test fun seatsNeverOverlapEachOtherDoorsDriverWallsOrTheAisle() {
        for (p in listOf(city, coach)) {
            val inner = p.width / 2 - p.wallHalfThickness
            fun footprint(s: CabinSeat): CabinBox { // cushion plus its own backrest, in plan
                val cushion = SeatSurface.entries.flatMap { surface -> listOf(0.0, 1.0).flatMap { u -> listOf(0.0, 1.0).map { v -> s.sample(surface, u, v) } } } +
                    listOf(s.backBox.min, s.backBox.max)
                return CabinBox(V3(cushion.minOf { it.x }, 0.0, cushion.minOf { it.z }), V3(cushion.maxOf { it.x }, 1.0, cushion.maxOf { it.z }))
            }
            fun overlap(a: CabinBox, b: CabinBox) = a.min.x < b.max.x && b.min.x < a.max.x && a.min.z < b.max.z && b.min.z < a.max.z
            val prints = p.seats.associateWith { footprint(it) }
            for ((s, f) in prints) {
                assertTrue("${p.kind} ${s.id} inside walls", f.min.x > -inner && f.max.x < inner && f.min.z > -p.length / 2 && f.max.z < p.length / 2)
                p.doors.forEach { assertFalse("${p.kind} ${s.id} blocks a door", overlap(f, it)) }
                assertFalse("${p.kind} ${s.id} overlaps driver", overlap(f, footprint(p.driver!!)))
                // A walkable aisle along the centre line.
                assertTrue("${p.kind} ${s.id} intrudes into aisle", f.max.x < -.2 || f.min.x > .2)
                for ((o, g) in prints) if (o !== s) assertFalse("${p.kind} ${s.id} overlaps ${o.id}", overlap(f, g))
            }
            // Furniture the renderer draws is also what blocks light and the seat camera.
            val driver = p.driver!!
            assertTrue(p.blockers.contains(driver.backBox))
        }
    }

    @Test fun routeTypeOnlyEverGivesAnEstimate() {
        // The transit API reports a route category, never capacity or body: nothing confirms a 40-seat coach.
        assertEquals(BusModelEstimate(CabinKind.BUS, BusModelBasis.NO_TYPE, null), BusModels.estimate(null))
        assertEquals(BusModelEstimate(CabinKind.BUS, BusModelBasis.NO_TYPE, null), BusModels.estimate(" "))
        assertEquals(BusModelEstimate(CabinKind.BUS, BusModelBasis.CITY_TYPE, "간선"), BusModels.estimate("간선"))
        assertEquals(BusModelBasis.CITY_TYPE, BusModels.estimate("마을").basis)
        for (type in listOf("광역", "직행좌석", "시외"))
            assertEquals(type, BusModelEstimate(CabinKind.COACH, BusModelBasis.COACH_TYPE, type), BusModels.estimate(type))
        assertEquals(BusModelEstimate(CabinKind.BUS, BusModelBasis.UNKNOWN_TYPE, "공항"), BusModels.estimate("공항"))
        assertEquals(CabinKind.COACH, CabinProfile.forLeg(bus("광역"), TravelMode.TRANSIT)!!.kind)
        assertEquals(CabinKind.BUS, CabinProfile.forLeg(bus("공항"), TravelMode.TRANSIT)!!.kind)
    }

    @Test fun manualModelAppliesOnlyToBusLegs() {
        assertEquals(CabinKind.BUS, CabinProfile.forLeg(bus("광역"), TravelMode.TRANSIT, CabinKind.BUS)!!.kind)
        assertEquals(CabinKind.COACH, CabinProfile.forLeg(bus("간선"), TravelMode.TRANSIT, CabinKind.COACH)!!.kind)
        val subway = bus().copy(mode = Mode.SUBWAY)
        assertEquals(CabinKind.SUBWAY, CabinProfile.forLeg(subway, TravelMode.TRANSIT, CabinKind.COACH)!!.kind)
        assertNull(CabinProfile.forLeg(bus().copy(mode = Mode.WALK), TravelMode.TRANSIT, CabinKind.COACH))
    }

    @Test fun recommendedSpotKeepsItsMeaningInEitherModel() {
        val zones = listOf(SeatZone.FRONT, SeatZone.MIDDLE, SeatZone.REAR, SeatZone.FRONT_MIDDLE, SeatZone.MIDDLE_REAR, null)
        for (p in listOf(city, coach)) for (side in Side.entries) for (zone in zones) {
            val window = p.recommended(SeatSpot(side, zone, SeatPosition.WINDOW))!!
            assertEquals(side, window.spot.side); assertEquals(SeatPosition.WINDOW, window.spot.position)
            assertTrue("${p.kind} $side $zone prefers a forward seat", window.facing.z < -.5)
            if (zone in listOf(SeatZone.FRONT, SeatZone.MIDDLE, SeatZone.REAR)) assertEquals(zone, window.spot.zone)
            val aisle = p.recommended(SeatSpot(side, zone, SeatPosition.AISLE))!!
            assertEquals(side, aisle.spot.side)
        }
        assertEquals(SeatPosition.AISLE, coach.recommended(SeatSpot(Side.LEFT, SeatZone.FRONT, SeatPosition.AISLE))!!.spot.position)
    }

    @Test fun switchingModelCarriesOnlyAnEquivalentSeat() {
        val leftFront = city.seats.first { it.spot == SeatSpot(Side.LEFT, SeatZone.FRONT, SeatPosition.WINDOW) }
        val carried = city.carry(coach, leftFront.id)
        assertEquals(leftFront.spot, carried.from)
        assertTrue(carried.carried)
        assertEquals(leftFront.spot, coach.seats.first { it.id == carried.seatId }.spot)
        val leftAisle = coach.seats.first { it.spot.side == Side.LEFT && it.spot.position == SeatPosition.AISLE }
        val lost = coach.carry(city, leftAisle.id)
        assertNull("no silent move to an arbitrary seat", lost.seatId)
        assertFalse(lost.carried); assertEquals(leftAisle.spot, lost.from)
        assertEquals(SeatCarry(null, null, false), coach.carry(city, null))
        assertEquals(SeatCarry(null, null, false), coach.carry(city, "unknown"))
        assertTrue("ids never collide between models", city.seats.none { c -> coach.seats.any { it.id == c.id } })
    }

    @Test fun lightUsesTheSameModelThatIsDrawn() {
        val sun = SunPosition(90.0, 20.0)
        for (p in listOf(city, coach)) {
            val light = CabinSimulation.calculate(p, sun, 0.0, true)
            assertEquals(p.seats.map { it.id }.toSet(), light.cells.map { it.seatId }.toSet())
            assertTrue(light.cells.any { it.window == CabinWindow.RIGHT })
        }
        assertNotEquals(CabinSimulation.calculate(city, sun, 0.0, true).cells.size, CabinSimulation.calculate(coach, sun, 0.0, true).cells.size)
    }
}
