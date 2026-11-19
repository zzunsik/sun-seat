package com.sunseat.app.ui.simulation

import com.sunseat.app.domain.*
import kotlin.math.*

/** Look-at camera with a vertical field of view, matching Filament's Camera.lookAt/setProjection. */
internal data class CabinView(val eye: V3, val target: V3, val fovDegrees: Double, val near: Double, val far: Double)

/** Top-end overlay (view toggle) as fractions of the viewport. Seats must stay out of it. */
internal data class CabinInset(val width: Double = 0.0, val height: Double = 0.0) {
    companion object { val NONE = CabinInset() }
}

/**
 * Pure camera framing (#75). Only the camera moves: seat/vehicle dimensions and light results stay physical.
 * Overview fits the whole visible vehicle to the viewport; the seat view fits the selected seat and keeps an
 * unobstructed line of sight from inside the cabin.
 */
internal object CabinCamera {
    private const val MARGIN = .92
    private const val SEAT_MARGIN = .84
    private const val SEAT_FOV = 50.0
    private const val OVERVIEW_FOV = 30.0
    private const val MAX_FOV = 80.0
    private const val CLEARANCE = .15
    private val UP = V3(0.0, 1.0, 0.0)

    /** Normalized device coordinates (-1..1, +y up) or null behind the camera. */
    fun project(view: CabinView, aspect: Double, p: V3): Pair<Double, Double>? {
        val f = (view.target - view.eye).unit(); val r = f.cross(UP).unit(); val u = r.cross(f)
        val d = p - view.eye; val z = d.dot(f)
        if (z <= 1e-6) return null
        val t = tan(Math.toRadians(view.fovDegrees / 2))
        return d.dot(r) / (z * t * aspect) to d.dot(u) / (z * t)
    }

    fun overview(p: CabinProfile, aspect: Double, inset: CabinInset = CabinInset.NONE): CabinView {
        val bounds = CabinMesh.visualBounds(p)
        val corners = buildList { for (x in listOf(bounds.min.x, bounds.max.x)) for (y in listOf(bounds.min.y, bounds.max.y)) for (z in listOf(bounds.min.z, bounds.max.z)) add(V3(x, y, z)) }
        val seats = p.seats.flatMap { s -> listOf(0.0 to 0.0, 1.0 to 0.0, 0.0 to 1.0, 1.0 to 1.0).map { (u, v) -> s.sample(SeatSurface.CUSHION, u, v) } + CabinMesh.badgeCenter(s) }
        // Always from the front-right above, so the cut-away walls face the camera and the front stays identifiable.
        // Within that quadrant the angle whose farthest seat projects largest wins: wide viewports see the
        // vehicle more broadside. Depends only on profile and viewport, so changing seats never moves it.
        val cushions = p.seats.map { s -> listOf(0.0 to 0.0, 1.0 to 0.0, 1.0 to 1.0, 0.0 to 1.0).map { (u, v) -> s.sample(SeatSurface.CUSHION, u, v) } }
        return (40..70 step 5).flatMap { yaw -> listOf(40, 45, 50, 55).map { el ->
            val y = Math.toRadians(yaw.toDouble()); val e = Math.toRadians(el.toDouble())
            fit(corners + seats, seats, V3(sin(y) * cos(e), sin(e), -cos(y) * cos(e)), OVERVIEW_FOV, aspect, inset, MARGIN)
        } }.maxBy { view -> cushions.minOf { area(view, aspect, it) } }
    }

    /** Projected polygon area in NDC² scaled by aspect (proportional to on-screen area). */
    private fun area(view: CabinView, aspect: Double, polygon: List<V3>): Double {
        val q = polygon.map { project(view, aspect, it) ?: return 0.0 }
        return abs(q.indices.sumOf { q[it].first * q[(it + 1) % q.size].second - q[(it + 1) % q.size].first * q[it].second }) * aspect / 2
    }

    fun seat(p: CabinProfile, seat: CabinSeat, aspect: Double, inset: CabinInset = CabinInset.NONE): CabinView {
        val r = seat.right
        // Cushion, backrest top and the selection check must all be readable.
        val frame = listOf(0.0 to 0.0, 1.0 to 0.0, 0.0 to 1.0, 1.0 to 1.0).map { (u, v) -> seat.sample(SeatSurface.CUSHION, u, v) } +
            listOf(0.0, 1.0).map { seat.sample(SeatSurface.BACK, it, 1.0) }
        val lateral = if (abs(seat.facing.z) > .5) r * (if (seat.center.x * r.x > 0) -1.0 else 1.0)
            else V3(0.0, 0.0, if (seat.center.z > 0) -1.0 else 1.0)
        val samples = SeatSurface.entries.flatMap { s -> (0 until CabinSimulation.GRID).flatMap { i -> (0 until CabinSimulation.GRID).map { j ->
            seat.sample(s, (j + .5) / CabinSimulation.GRID, (i + .5) / CabinSimulation.GRID) } } }
        val blockers = othersBlockers(p, seat)
        var fallback: CabinView? = null
        for (el in 50..80 step 5) for (az in listOf(20, 0, 35)) {
            val e = Math.toRadians(el.toDouble()); val a = Math.toRadians(az.toDouble())
            val dir = (seat.facing * (cos(e) * cos(a)) + lateral * (cos(e) * sin(a)) + UP * sin(e)).unit()
            var fov = SEAT_FOV
            var view = fit(frame, frame, dir, fov, aspect, inset, SEAT_MARGIN, near = .03)
            val limit = maxDistance(p, blockers, view.target, dir)
            while (distance(view) > limit && fov < MAX_FOV) {
                fov += 2.0; view = fit(frame, frame, dir, fov, aspect, inset, SEAT_MARGIN, near = .03)
            }
            if (fallback == null) fallback = view
            if (distance(view) > limit) continue
            if (samples.all { visible(it, view.eye, blockers) }) return view
        }
        return fallback!!
    }

    /** Calculation blockers that are not part of [seat] itself (its own back/headrest sit behind its surfaces). */
    internal fun othersBlockers(p: CabinProfile, seat: CabinSeat): List<CabinBox> {
        val own = buildList {
            add(seat.backBox)
            val head = seat.center + seat.facing * (-seat.depth / 2) + V3(0.0, .66, 0.0)
            add(CabinBox(head - V3(.15, .1, .06), head + V3(.15, .1, .06)))
        }
        return p.blockers.filter { it !in own }
    }

    internal fun visible(point: V3, eye: V3, blockers: List<CabinBox>): Boolean {
        val d = eye - point; val length = sqrt(d.dot(d)); val dir = d * (1 / length)
        return blockers.none { b -> b.hit(point, dir)?.let { it < length } == true }
    }

    internal fun insideCabin(p: CabinProfile, eye: V3, blockers: List<CabinBox>): Boolean =
        abs(eye.x) < p.width / 2 - .1 && eye.y > .1 && eye.y < p.height - .1 && abs(eye.z) < p.length / 2 - .1 &&
            blockers.none { distance(it, eye) < CLEARANCE }

    private fun maxDistance(p: CabinProfile, blockers: List<CabinBox>, target: V3, dir: V3): Double {
        var lo = 0.0; var hi = 4.0
        repeat(40) { val mid = (lo + hi) / 2; if (insideCabin(p, target + dir * mid, blockers)) lo = mid else hi = mid }
        return lo
    }

    private fun distance(b: CabinBox, p: V3): Double {
        val dx = maxOf(b.min.x - p.x, 0.0, p.x - b.max.x)
        val dy = maxOf(b.min.y - p.y, 0.0, p.y - b.max.y)
        val dz = maxOf(b.min.z - p.z, 0.0, p.z - b.max.z)
        return sqrt(dx * dx + dy * dy + dz * dz)
    }

    private fun distance(v: CabinView) = (v.eye - v.target).let { sqrt(it.dot(it)) }

    /**
     * Smallest camera distance along [dir] where every [points] projects inside ±[margin] and no [guarded] point
     * falls under [inset]. The target is re-centred so the projected extent is balanced.
     */
    private fun fit(points: List<V3>, guarded: List<V3>, dir: V3, fov: Double, aspect: Double, inset: CabinInset, margin: Double, near: Double = .05): CabinView {
        val center = points.fold(V3(0.0, 0.0, 0.0)) { a, b -> a + b } * (1.0 / points.size)
        val f = dir * -1.0; val r = f.cross(UP).unit(); val u = r.cross(f)
        val t = tan(Math.toRadians(fov / 2))
        fun at(d: Double): Pair<CabinView, Boolean> {
            var target = center
            var view = CabinView(target + dir * d, target, fov, near, d * 3 + 10)
            repeat(4) {
                val ps = points.mapNotNull { project(view, aspect, it) }
                if (ps.size < points.size) return view to false
                val cx = (ps.maxOf { it.first } + ps.minOf { it.first }) / 2
                val cy = (ps.maxOf { it.second } + ps.minOf { it.second }) / 2
                target += r * (cx * d * t * aspect) + u * (cy * d * t)
                view = CabinView(target + dir * d, target, fov, near, d * 3 + 10)
            }
            val ps = points.map { project(view, aspect, it) ?: return view to false }
            val fits = ps.all { abs(it.first) <= margin && abs(it.second) <= margin } && guarded.none { g ->
                project(view, aspect, g)?.let { it.first > 1 - 2 * inset.width && it.second > 1 - 2 * inset.height } ?: true
            }
            return view to fits
        }
        // ~1mm precision over a 60m range; runs only when profile, seat, view or viewport change.
        var lo = .05; var hi = 60.0
        repeat(26) { val mid = (lo + hi) / 2; if (at(mid).second) hi = mid else lo = mid }
        return at(hi).first
    }
}
