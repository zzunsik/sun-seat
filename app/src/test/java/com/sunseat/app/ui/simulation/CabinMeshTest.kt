package com.sunseat.app.ui.simulation

import com.sunseat.app.domain.*
import org.junit.Assert.*
import org.junit.Test
import java.time.Duration

class CabinMeshTest {
    @Test fun allProfilesProduceFiniteTrianglesAndExposureGeometry() {
        val neutral=floatArrayOf(.7f,.7f,.7f,1f)
        val lat=LatLng(37.0,127.0)
        for(mode in listOf(Mode.CAR,Mode.BUS,Mode.SUBWAY)) {
            val p=CabinProfile.forLeg(Leg(mode,listOf(lat,lat.copy(latitude=38.0)),Duration.ofMinutes(30),"A","B",GroundLevel.ABOVE),TravelMode.CAR)!!
            val start=System.nanoTime()
            repeat(5) {
                val light=CabinSimulation.calculate(p,SunPosition(90.0,20.0+it),0.0,true)
                for(mesh in listOf(CabinMesh.body(p,neutral,neutral),CabinMesh.body(p,neutral,neutral,cutaway=false),CabinMesh.overlays(p,light,p.seats.first().id,neutral,neutral,neutral))) {
                    assertTrue(mesh.count>0); assertEquals(0,mesh.count%3)
                    val floats=mesh.buffer().asFloatBuffer()
                    while(floats.hasRemaining()) assertTrue(floats.get().isFinite())
                }
                val beams=CabinMesh.beams(p,light,p.seats.first().id,neutral)
                assertEquals(0,beams.count%3)
                val beamFloats=beams.buffer().asFloatBuffer()
                while(beamFloats.hasRemaining()) assertTrue(beamFloats.get().isFinite())
            }
            println("$mode JVM geometry+mesh five iterations: ${(System.nanoTime()-start)/1_000_000.0} ms (not a device FPS measurement)")
        }
    }
}
