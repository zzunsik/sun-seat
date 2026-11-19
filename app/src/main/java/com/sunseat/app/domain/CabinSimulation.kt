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
data class CabinSeat(val id: String, val spot: SeatSpot, val center: V3, val facing: V3, val width: Double = .46, val depth: Double = .46,
    /** Model seat number (#76). 0 = not numbered. Never a real vehicle's seat number. */
    val number: Int = 0) {
    val right get() = facing.cross(V3(0.0, 1.0, 0.0))
    fun sample(surface: SeatSurface, u: Double, v: Double): V3 = when (surface) {
        SeatSurface.CUSHION -> center + right * ((u-.5)*width) + facing*((v-.5)*depth)
        SeatSurface.BACK -> center + facing*(-depth/2) + right*((u-.5)*width) + V3(0.0, v*.53, 0.0)
    }
    /** Head restraint blocker (coach/car seats). */
    val headrestBox: CabinBox get() {
        val head = center + facing * (-depth/2) + V3(0.0, .66, 0.0)
        return CabinBox(head - V3(.15, .1, .06), head + V3(.15, .1, .06))
    }
    val backBox: CabinBox get() {
        val p = center + facing * (-depth/2-.04)
        return if (abs(facing.z) > .5) CabinBox(p+V3(-width/2, -.04, -.04), p+V3(width/2,.55,.04))
        else CabinBox(p+V3(-.04,-.04,-width/2), p+V3(.04,.55,width/2))
    }
    /** Seat pan solid below the cushion surface. */
    val cushionBox: CabinBox get() = CabinBox(center+V3(-.23,-.12,-.23),center+V3(.23,0.0,.23))
}

/** How a bus leg's default model was estimated (#76). */
enum class BusModelBasis { NO_TYPE, CITY_TYPE, COACH_TYPE, UNKNOWN_TYPE }
data class BusModelEstimate(val kind: CabinKind, val basis: BusModelBasis, val type: String?)

/**
 * The transit API only reports a route category (`vehicles.type`, e.g. 간선·광역·시외), never capacity, body or seat map.
 * So a bus model is always an estimate: coach-style categories default to the 40-seat express model, everything else to the
 * city bus model, and the user can switch per leg. Nothing here confirms that a real vehicle has 40 seats.
 */
object BusModels {
    val kinds = listOf(CabinKind.BUS, CabinKind.COACH)
    private val coachTypes = listOf("광역", "직행", "시외")
    private val cityTypes = listOf("간선", "지선", "마을", "순환")
    fun estimate(busType: String?): BusModelEstimate {
        val type = busType?.trim()?.takeIf { it.isNotEmpty() } ?: return BusModelEstimate(CabinKind.BUS, BusModelBasis.NO_TYPE, null)
        return when {
            coachTypes.any { it in type } -> BusModelEstimate(CabinKind.COACH, BusModelBasis.COACH_TYPE, type)
            cityTypes.any { it in type } -> BusModelEstimate(CabinKind.BUS, BusModelBasis.CITY_TYPE, type)
            else -> BusModelEstimate(CabinKind.BUS, BusModelBasis.UNKNOWN_TYPE, type)
        }
    }
}

/** A user-chosen seat moved to another model: only an equivalent (same side/zone/window-aisle) seat is carried over. */
data class SeatCarry(val seatId: String?, val from: SeatSpot?, val carried: Boolean)

/**
 * Standard illustrative profiles, intentionally independent of actual fleet layouts.
 * [driver] and [doors] are never selectable; the driver's chair is a calculation blocker, doors are floor markings only
 * (their glass is treated like the window band, so they do not change the light calculation).
 */
data class CabinProfile(val kind: CabinKind, val width: Double, val length: Double, val height: Double, val sill: Double, val windowTop: Double, val seats: List<CabinSeat>,
    val driver: CabinSeat? = if (kind == CabinKind.CAR || kind == CabinKind.TAXI) CabinSeat("driver",SeatSpot(Side.LEFT),V3(-.43,.48,-.65),V3(0.0,0.0,-1.0)) else null,
    val doors: List<CabinBox> = emptyList()) {
    val wallHalfThickness: Double get() = .045
    val shell = CabinBox(V3(-width/2,0.0,-length/2), V3(width/2,height,length/2))
    // All visible pillars are also calculation blockers. Roof is implicit in the closed shell. No pillar stands in a doorway.
    val pillars: List<CabinBox> = buildList {
        for (x in listOf(-width/2, width/2)) for (z in listOf(-length/2, 0.0, length/2))
            if (doors.none { d -> x > 0 && z+.06 > d.min.z && z-.06 < d.max.z }) add(CabinBox(V3(x-.045,sill,z-.06), V3(x+.045,height,z+.06)))
    }
    val furniture: List<CabinBox> = buildList {
        val w=width/2; val l=length/2
        add(CabinBox(V3(-w+.06,.35,-l+.04),V3(w-.06,sill-.06,-l+.3)))
        if(kind in listOf(CabinKind.CAR,CabinKind.TAXI,CabinKind.COACH)) seats.forEach { add(it.headrestBox) }
        // Driver's chair is a blocker but never a selectable passenger seat.
        driver?.let { d ->
            add(d.backBox); add(d.cushionBox)
            if(kind==CabinKind.COACH) add(d.headrestBox)
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
            (if (s.zone != null && !zoneMatches(s.zone,it.spot.zone)) 5 else 0) +
            // Bus advice is phrased for forward-facing seats; aisle-facing seats are only a last resort.
            (if (kind in BusModels.kinds && abs(it.facing.z) < .5) 1 else 0)
        }
    }
    /** Maps the seat chosen in this model to [to]. Never substitutes a seat with a different meaning. */
    fun carry(to: CabinProfile, chosen: String?): SeatCarry {
        val from = seats.firstOrNull { it.id == chosen } ?: return SeatCarry(null, null, false)
        val match = to.seats.filter { it.spot == from.spot }
            .minByOrNull { (if (it.facing == from.facing) 0.0 else 10.0) + abs(it.center.z/to.length - from.center.z/length) }
        return SeatCarry(match?.id, from.spot, match != null)
    }
    companion object {
        private fun zoneMatches(wanted: SeatZone, actual: SeatZone?) = wanted == actual ||
            (wanted == SeatZone.FRONT_MIDDLE && actual in listOf(SeatZone.FRONT,SeatZone.MIDDLE)) ||
            (wanted == SeatZone.MIDDLE_REAR && actual in listOf(SeatZone.MIDDLE,SeatZone.REAR))
        private val FORWARD = V3(0.0,0.0,-1.0)
        /** [busModel] overrides the estimate for bus legs only (#76). */
        fun forLeg(leg: Leg, mode: TravelMode, busModel: CabinKind? = null): CabinProfile? = when (leg.mode) {
            Mode.WALK -> null
            Mode.CAR -> car(if (mode == TravelMode.TAXI) CabinKind.TAXI else CabinKind.CAR)
            Mode.SUBWAY -> subway()
            Mode.BUS -> if ((busModel?.takeIf { it in BusModels.kinds } ?: BusModels.estimate(leg.busType).kind) == CabinKind.COACH) coach() else cityBus()
        }
        private fun car(kind: CabinKind): CabinProfile {
            val seats = buildList {
                add(CabinSeat("passenger",SeatSpot(Side.RIGHT,carSeat=CarSeat.PASSENGER),V3(.43,.48,-.65),FORWARD))
                for (s in Side.entries) add(CabinSeat("rear-${s.name}",SeatSpot(s,carSeat=if(s==Side.LEFT) CarSeat.REAR_LEFT else CarSeat.REAR_RIGHT),V3(if(s==Side.LEFT) -.43 else .43,.48,.65),FORWARD))
            }
            return CabinProfile(kind,1.55,2.65,1.42,.75,1.27,seats)
        }
        private fun subway(): CabinProfile {
            val seats = buildList {
                for (s in Side.entries) for (row in 0..5) add(CabinSeat("${s.name}-$row",SeatSpot(s),V3(if(s==Side.LEFT) -.88 else .88,.5,-2.0+row*.8),V3(if(s==Side.LEFT) 1.0 else -1.0,0.0,0.0)))
            }
            return CabinProfile(CabinKind.SUBWAY,2.5,8.0,2.25,.85,1.95,seats)
        }
        // Shared bus body (self-made spec, #76): 2.5m wide, 7.8m interior (compressed illustrative proportion), driver front-left, front door front-right.
        private const val BUS_LENGTH = 7.8
        private const val BUS_WINDOW_X = .95
        private const val BUS_AISLE_X = .48
        private fun busDriver() = CabinSeat("driver",SeatSpot(Side.LEFT),V3(-.75,.5,-BUS_LENGTH/2+.55),FORWARD)
        /** Longitudinal position of forward row [i] (0.69m pitch, first row 0.15m clear of the driver's backrest). */
        private fun busRow(i: Int) = -BUS_LENGTH/2+1.24+i*.69
        /** One zone rule for both bus models: coach rows 1–3 front, 4–7 middle, 8–10 rear, city seats by the same boundaries. */
        private fun busZone(z: Double) = if(z < (busRow(2)+busRow(3))/2) SeatZone.FRONT else if(z < (busRow(6)+busRow(7))/2) SeatZone.MIDDLE else SeatZone.REAR
        private fun floorStrip(z0: Double, z1: Double) = CabinBox(V3(.7,0.0,z0),V3(2.5/2-.045,.02,z1))
        private fun numbered(seats: List<CabinSeat>, prefix: String) =
            seats.sortedWith(compareBy({ it.center.z }, { it.center.x })).mapIndexed { i, s -> s.copy(id="$prefix-${i+1}",number=i+1) }
        /**
         * 40-seat express coach model: 10 forward rows of 2+2 at 0.69m pitch, one centre aisle, 40 passenger seats
         * excluding the driver. Rows 1–3 front, 4–7 middle, 8–10 rear. Numbers run front to back, left window → right window.
         */
        private fun coach(): CabinProfile {
            val l = BUS_LENGTH/2
            val seats = buildList {
                for (row in 0 until 10) for (s in Side.entries) for (pos in SeatPosition.entries) {
                    val z = busRow(row)
                    val x = (if(s==Side.LEFT) -1 else 1)*(if(pos==SeatPosition.WINDOW) BUS_WINDOW_X else BUS_AISLE_X)
                    add(CabinSeat("",SeatSpot(s,busZone(z),pos),V3(x,.5,z),FORWARD))
                }
            }
            return CabinProfile(CabinKind.COACH,2.5,BUS_LENGTH,2.25,.85,1.95,numbered(seats,"coach"),busDriver(),listOf(floorStrip(-l+.1,-l+.9)))
        }
        /**
         * City bus model: front door and a middle door on the right, forward single seats along the left, two aisle-facing
         * seats behind the front door, forward pairs on the right before and after the middle door. Zones use the coach row boundaries.
         */
        private fun cityBus(): CabinProfile {
            val l = BUS_LENGTH/2
            val seats = buildList {
                for (i in 0 until 10) { val z = busRow(i); add(CabinSeat("",SeatSpot(Side.LEFT,busZone(z),SeatPosition.WINDOW),V3(-BUS_WINDOW_X,.5,z),FORWARD)) }
                for (z in listOf(-l+1.15,-l+1.62)) add(CabinSeat("",SeatSpot(Side.RIGHT,busZone(z),SeatPosition.WINDOW),V3(.88,.5,z),V3(-1.0,0.0,0.0)))
                for (z in listOf(-l+2.23,-l+2.93,-l+4.45,-l+5.15,-l+5.85,-l+6.55,-l+7.25)) for (pos in SeatPosition.entries)
                    add(CabinSeat("",SeatSpot(Side.RIGHT,busZone(z),pos),V3(if(pos==SeatPosition.WINDOW) BUS_WINDOW_X else BUS_AISLE_X,.5,z),FORWARD))
            }
            return CabinProfile(CabinKind.BUS,2.5,BUS_LENGTH,2.25,.85,1.95,numbered(seats,"city"),busDriver(),listOf(floorStrip(-l+.1,-l+.9),floorStrip(-l+3.3,-l+4.2)))
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
