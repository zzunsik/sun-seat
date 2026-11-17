package com.sunseat.app.domain

import org.junit.Assert.*
import org.junit.Test
import java.time.Duration

class CabinSimulationTest {
    @Test fun subwayBackrestsStayInsideTheSolidSideWalls() {
        val p=CabinProfile.forLeg(leg(Mode.SUBWAY),TravelMode.TRANSIT)!!
        for(seat in p.seats) {
            assertTrue("bench backrest must not disappear into left wall",seat.backBox.min.x> -p.width/2+p.wallHalfThickness)
            assertTrue("bench backrest must not disappear into right wall",seat.backBox.max.x< p.width/2-p.wallHalfThickness)
        }
    }
    private fun leg(mode: Mode)=Leg(mode,listOf(LatLng(37.0,127.0),LatLng(37.01,127.0)),Duration.ofMinutes(30),"A","B",GroundLevel.ABOVE)
    private val profile=CabinProfile.forLeg(leg(Mode.CAR),TravelMode.CAR)!!
    @Test fun `world and vehicle bearings use right up and forward correctly`() {
        val right=CabinSimulation.towardSun(SunPosition(90.0,0.0),0.0)
        assertEquals(1.0,right.x,1e-9); assertEquals(0.0,right.z,1e-9)
        val front=CabinSimulation.towardSun(SunPosition(90.0,30.0),90.0)
        assertEquals(-kotlin.math.cos(Math.PI/6),front.z,1e-9)
        assertEquals(.5,front.y,1e-9)
        val a=CabinSimulation.towardSun(SunPosition(359.0,20.0),0.0)
        val b=CabinSimulation.towardSun(SunPosition(-1.0,20.0),0.0)
        assertEquals(a.x,b.x,1e-9)
    }
    @Test fun `low side sunlight enters windows but overhead sun is blocked by roof`() {
        val low=CabinSimulation.calculate(profile,SunPosition(90.0,20.0),0.0,true)
        assertTrue(low.cells.any { it.window==CabinWindow.RIGHT })
        val high=CabinSimulation.calculate(profile,SunPosition(90.0,89.9),0.0,true)
        assertTrue(high.cells.none { it.window!=null })
        low.cells.filter { it.window!=null }.forEach { assertTrue(it.incidence!! in 0.0..90.0) }
        low.cells.filter { it.window!=null }.forEach { cell ->
            assertEquals(cell.window,profile.windowAt(cell.windowPoint!!))
            val seat=profile.seats.first { it.id==cell.seatId }
            val normal=if(cell.surface==SeatSurface.CUSHION) V3(0.0,1.0,0.0) else seat.facing
            val delta=cell.windowPoint-(cell.center+normal*.006)
            val cross=delta.cross(low.towardSun)
            assertEquals(0.0,cross.dot(cross),1e-10)
        }
    }
    @Test fun `night underground unknown and missing geometry never invent sunlight`() {
        for ((sun,heading,available) in listOf(Triple(SunPosition(90.0,0.0),0.0,true),Triple(SunPosition(90.0,-10.0),0.0,true),Triple(SunPosition(90.0,20.0),0.0,false),Triple(null,null,true))) {
            assertTrue(CabinSimulation.calculate(profile,sun,heading,available).cells.none { it.window!=null })
        }
    }
    @Test fun `axis parallel rays and pillar intersections are stable`() {
        val b=CabinBox(V3(1.0,0.0,0.0),V3(2.0,1.0,1.0))
        assertEquals(1.0,b.hit(V3(0.0,.5,.5),V3(1.0,0.0,0.0))!!,1e-9)
        assertNull(b.hit(V3(0.0,2.0,.5),V3(1.0,0.0,0.0)))
        assertNull(b.hit(V3(0.0,.5,.5),V3(-1.0,0.0,0.0)))
    }
    @Test fun `profiles map advice and keep taxi driver unavailable`() {
        for (mode in listOf(TravelMode.TAXI,TravelMode.CAR)) {
            val p=CabinProfile.forLeg(leg(Mode.CAR),mode)!!
            for (seat in CarSeat.entries) assertEquals(seat,p.recommended(SeatSpot(if(seat==CarSeat.REAR_LEFT) Side.LEFT else Side.RIGHT,carSeat=seat))!!.spot.carSeat)
            assertEquals(3,p.seats.size)
        }
        val city=CabinProfile.forLeg(leg(Mode.BUS),TravelMode.TRANSIT)!!
        assertEquals(CabinKind.BUS,city.kind)
        assertTrue(city.seats.none { it.spot.side==Side.LEFT && it.spot.position==SeatPosition.AISLE })
        assertEquals(CabinKind.COACH,CabinProfile.forLeg(leg(Mode.BUS).copy(busType="광역"),TravelMode.TRANSIT)!!.kind)
        val bench=CabinProfile.forLeg(leg(Mode.SUBWAY),TravelMode.TRANSIT)!!
        assertTrue(bench.seats.filter { it.spot.side==Side.LEFT }.all { it.facing.x==1.0 })
        assertNull(CabinProfile.forLeg(leg(Mode.WALK),TravelMode.TRANSIT))
    }
    @Test fun `window aperture rejects sill roof and subway end walls`() {
        assertNull(profile.windowAt(V3(profile.width/2,profile.sill,0.0)))
        assertNull(profile.windowAt(V3(profile.width/2,profile.windowTop,0.0)))
        assertEquals(CabinWindow.RIGHT,profile.windowAt(V3(profile.width/2,1.0,0.0)))
        val subway=CabinProfile.forLeg(leg(Mode.SUBWAY),TravelMode.TRANSIT)!!
        assertNull(subway.windowAt(V3(0.0,1.2,-subway.length/2)))
    }
    @Test fun `window projection agrees with analytic intersection and resolves partial intrusion`() {
        val seat=CabinSeat("test",SeatSpot(Side.RIGHT),V3(0.0,.5,.5),V3(0.0,0.0,-1.0))
        val p=CabinProfile(CabinKind.BUS,2.0,4.0,2.0,.7,1.6,listOf(seat))
        val light=CabinSimulation.calculate(p,SunPosition(90.0,12.0),0.0,true)
        val cushion=light.cells.filter { it.surface==SeatSurface.CUSHION }
        val lit=cushion.count { it.window!=null }
        assertTrue(lit in 1..35)
        for(cell in cushion) {
            val yAtWindow=.506+(1.0-cell.center.x)*kotlin.math.tan(Math.toRadians(12.0))
            assertEquals(yAtWindow in .7..1.6,cell.window==CabinWindow.RIGHT)
        }
        assertTrue(CabinSimulation.calculate(p,SunPosition(90.0,20.0),0.0,true).cells.filter { it.surface==SeatSurface.CUSHION }.all { it.window==CabinWindow.RIGHT })
    }
}
