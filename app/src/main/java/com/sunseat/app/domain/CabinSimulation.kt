package com.sunseat.app.domain

import kotlin.math.*

/** Metres. +X right, +Y up, -Z vehicle front. No renderer or Android dependency. */
data class V3(val x: Double, val y: Double, val z: Double) {
    operator fun plus(b: V3) = V3(x + b.x, y + b.y, z + b.z)
    operator fun minus(b: V3) = V3(x - b.x, y - b.y, z - b.z)
    operator fun times(s: Double) = V3(x * s, y * s, z * s)
    fun dot(b: V3) = x * b.x + y * b.y + z * b.z
    fun cross(b: V3) = V3(y*b.z-z*b.y, z*b.x-x*b.z, x*b.y-y*b.x)
    fun unit(): V3 = this * (1.0 / sqrt(dot(this)).coerceAtLeast(1e-9))
}

data class CabinBox(val min: V3, val max: V3) {
    /** First positive intersection of a ray with an axis-aligned closed box. */
    fun hit(origin: V3, direction: V3): Double? {
        var near = Double.NEGATIVE_INFINITY
        var far = Double.POSITIVE_INFINITY
        for (axis in 0..2) {
            val o=when(axis) {0->origin.x;1->origin.y;else->origin.z}
            val d=when(axis) {0->direction.x;1->direction.y;else->direction.z}
            val lo=when(axis) {0->min.x;1->min.y;else->min.z}
            val hi=when(axis) {0->max.x;1->max.y;else->max.z}
            if (abs(d) < 1e-9) { if (o < lo || o > hi) return null }
            else {
                val a = (lo-o)/d; val b = (hi-o)/d
                near = maxOf(near, minOf(a,b)); far = minOf(far, maxOf(a,b))
                if (far < near) return null
            }
        }
        return if (far > 1e-6) maxOf(near, 0.0) else null
    }
}

enum class CabinKind { BUS, COACH, SUBWAY, TAXI, CAR }
enum class CabinWindow { LEFT, RIGHT, FRONT, REAR }
enum class SeatSurface { CUSHION, BACK }
data class CabinSeat(val id: String, val spot: SeatSpot, val center: V3, val facing: V3, val width: Double = .46, val depth: Double = .46) {
    val right get() = facing.cross(V3(0.0, 1.0, 0.0))
    fun sample(surface: SeatSurface, u: Double, v: Double): V3 = when (surface) {
        SeatSurface.CUSHION -> center + right * ((u-.5)*width) + facing*((v-.5)*depth)
        SeatSurface.BACK -> center + facing*(-depth/2) + right*((u-.5)*width) + V3(0.0, v*.53, 0.0)
    }
    val backBox: CabinBox get() {
        val p = center + facing * (-depth/2-.04)
        return if (abs(facing.z) > .5) CabinBox(p+V3(-width/2, -.04, -.04), p+V3(width/2,.55,.04))
        else CabinBox(p+V3(-.04,-.04,-width/2), p+V3(.04,.55,width/2))
    }
}

/** Standard illustrative profile v1, intentionally independent of actual fleet layouts. */
data class CabinProfile(val kind: CabinKind, val width: Double, val length: Double, val height: Double, val sill: Double, val windowTop: Double, val seats: List<CabinSeat>) {
    val shell = CabinBox(V3(-width/2,0.0,-length/2), V3(width/2,height,length/2))
    // All visible pillars are also calculation blockers. Roof is implicit in the closed shell.
    val pillars: List<CabinBox> = buildList {
        for (x in listOf(-width/2, width/2)) for (z in listOf(-length/2, 0.0, length/2))
            add(CabinBox(V3(x-.045,sill,z-.06), V3(x+.045,height,z+.06)))
    }
    val furniture: List<CabinBox> = buildList {
        val w=width/2; val l=length/2
        add(CabinBox(V3(-w+.06,.35,-l+.04),V3(w-.06,sill-.06,-l+.3)))
        if(kind in listOf(CabinKind.CAR,CabinKind.TAXI,CabinKind.COACH)) seats.forEach { s ->
            val head=s.center+s.facing*(-s.depth/2)+V3(0.0,.66,0.0)
            add(CabinBox(head-V3(.15,.1,.06),head+V3(.15,.1,.06)))
        }
        if(kind in listOf(CabinKind.CAR,CabinKind.TAXI)) {
            // Driver's chair is a blocker but never a selectable passenger seat.
            val driver=CabinSeat("driver",SeatSpot(Side.LEFT),V3(-.43,.48,-.65),V3(0.0,0.0,-1.0))
            add(driver.backBox)
            add(CabinBox(driver.center+V3(-.23,-.12,-.23),driver.center+V3(.23,0.0,.23)))
        }
    }
    val blockers = pillars + seats.map { it.backBox } + furniture
    fun windowAt(p: V3): CabinWindow? {
        if (p.y <= sill || p.y >= windowTop) return null
        return when {
            abs(p.x+width/2) < 1e-5 -> CabinWindow.LEFT
            abs(p.x-width/2) < 1e-5 -> CabinWindow.RIGHT
            abs(p.z+length/2) < 1e-5 && kind != CabinKind.SUBWAY -> CabinWindow.FRONT
            abs(p.z-length/2) < 1e-5 && kind != CabinKind.SUBWAY -> CabinWindow.REAR
            else -> null
        }
    }
    fun recommended(spot: SeatSpot?): CabinSeat? = spot?.let { s ->
        seats.filter { it.spot.side == s.side }.minByOrNull {
            (if (s.carSeat != null && s.carSeat != it.spot.carSeat) 100 else 0) +
            (if (s.position != null && s.position != it.spot.position) 10 else 0) +
            (if (s.zone != null && !zoneMatches(s.zone,it.spot.zone)) 5 else 0)
        }
    }
    companion object {
        private fun zoneMatches(wanted: SeatZone, actual: SeatZone?) = wanted == actual ||
            (wanted == SeatZone.FRONT_MIDDLE && actual in listOf(SeatZone.FRONT,SeatZone.MIDDLE)) ||
            (wanted == SeatZone.MIDDLE_REAR && actual in listOf(SeatZone.MIDDLE,SeatZone.REAR))
        fun forLeg(leg: Leg, mode: TravelMode): CabinProfile? {
            if (leg.mode == Mode.WALK) return null
            val kind = when (leg.mode) {
                Mode.CAR -> if (mode == TravelMode.TAXI) CabinKind.TAXI else CabinKind.CAR
                Mode.SUBWAY -> CabinKind.SUBWAY
                Mode.BUS -> if (leg.busType?.contains("광역") == true || leg.busType?.contains("직행") == true) CabinKind.COACH else CabinKind.BUS
                Mode.WALK -> return null
            }
            val car = kind in listOf(CabinKind.CAR,CabinKind.TAXI)
            val width = if (car) 1.55 else 2.5
            val length = if (car) 2.65 else if (kind == CabinKind.SUBWAY) 8.0 else 7.0
            val seats = buildList {
                if (car) {
                    add(CabinSeat("passenger",SeatSpot(Side.RIGHT,carSeat=CarSeat.PASSENGER),V3(.43,.48,-.65),V3(0.0,0.0,-1.0)))
                    for (s in Side.entries) add(CabinSeat("rear-${s.name}",SeatSpot(s,carSeat=if(s==Side.LEFT) CarSeat.REAR_LEFT else CarSeat.REAR_RIGHT),V3(if(s==Side.LEFT) -.43 else .43,.48,.65),V3(0.0,0.0,-1.0)))
                } else if (kind == CabinKind.SUBWAY) {
                    for (s in Side.entries) for (row in 0..5) add(CabinSeat("${s.name}-$row",SeatSpot(s),V3(if(s==Side.LEFT) -1.0 else 1.0,.5,-2.0+row*.8),V3(if(s==Side.LEFT) 1.0 else -1.0,0.0,0.0)))
                } else {
                    for (row in 0..5) for (s in Side.entries) for (pos in SeatPosition.entries) {
                        // Illustrative city bus: left single seats, right pairs. Coach: pairs on both sides.
                        if (kind == CabinKind.BUS && s == Side.LEFT && pos == SeatPosition.AISLE) continue
                        val zone = if(row<2) SeatZone.FRONT else if(row<4) SeatZone.MIDDLE else SeatZone.REAR
                        add(CabinSeat("${s.name}-$row-${pos.name}",SeatSpot(s,zone,pos),V3((if(s==Side.LEFT) -1 else 1)*(if(pos==SeatPosition.WINDOW) .98 else .48),.5,-2.3+row*.85),V3(0.0,0.0,-1.0)))
                    }
                }
            }
            return CabinProfile(kind,width,length,if(car) 1.42 else 2.25,if(car) .75 else .85,if(car) 1.27 else 1.95,seats)
        }
    }
}

data class SunCell(val seatId: String, val surface: SeatSurface, val row: Int, val column: Int, val center: V3, val window: CabinWindow?, val incidence: Double?, val windowPoint: V3?)
data class CabinLight(val towardSun: V3, val cells: List<SunCell>, val available: Boolean) {
    fun lit(seatId: String, surface: SeatSurface) = cells.count { it.seatId==seatId && it.surface==surface && it.window!=null }
}

object CabinSimulation {
    const val GRID = 6
    fun towardSun(sun: SunPosition, heading: Double): V3 {
        val a = Math.toRadians(sun.azimuth-heading); val h = Math.toRadians(sun.altitude)
        return V3(sin(a)*cos(h),sin(h),-cos(a)*cos(h))
    }
    fun calculate(profile: CabinProfile, sun: SunPosition?, heading: Double?, available: Boolean): CabinLight {
        val direction = if(sun!=null && heading!=null) towardSun(sun,heading) else V3(0.0,1.0,0.0)
        val active = available && sun!=null && heading!=null
        val blockers = profile.blockers
        val cells = profile.seats.flatMap { seat -> SeatSurface.entries.flatMap { surface ->
            (0 until GRID).flatMap { row -> (0 until GRID).map { column ->
                val p = seat.sample(surface,(column+.5)/GRID,(row+.5)/GRID)
                val normal = if(surface==SeatSurface.CUSHION) V3(0.0,1.0,0.0) else seat.facing
                val origin = p+normal*.006
                val distance = exitDistance(profile.shell,origin,direction)
                val exit = distance?.let { origin+direction*it }
                var window = if(active && sun!!.altitude>0 && normal.dot(direction)>0 && exit!=null) profile.windowAt(exit) else null
                if(window!=null && blockers.any { it.hit(origin,direction)?.let { d -> d < distance!!-1e-5 } == true }) window=null
                val wallNormal = when(window) {
                    CabinWindow.LEFT -> V3(-1.0,0.0,0.0); CabinWindow.RIGHT -> V3(1.0,0.0,0.0)
                    CabinWindow.FRONT -> V3(0.0,0.0,-1.0); CabinWindow.REAR -> V3(0.0,0.0,1.0); null -> null
                }
                SunCell(seat.id,surface,row,column,p,window,wallNormal?.let { Math.toDegrees(acos(it.dot(direction).coerceIn(-1.0,1.0))) },if(window!=null) exit else null)
            } }
        } }
        return CabinLight(direction,cells,active)
    }
    private fun exitDistance(box: CabinBox, p: V3, d: V3): Double? {
        val distances = listOf(
            if(abs(d.x)>1e-9) ((if(d.x>0) box.max.x else box.min.x)-p.x)/d.x else Double.POSITIVE_INFINITY,
            if(abs(d.y)>1e-9) ((if(d.y>0) box.max.y else box.min.y)-p.y)/d.y else Double.POSITIVE_INFINITY,
            if(abs(d.z)>1e-9) ((if(d.z>0) box.max.z else box.min.z)-p.z)/d.z else Double.POSITIVE_INFINITY,
        )
        return distances.filter { it>=0 && it.isFinite() }.minOrNull()
    }
}
