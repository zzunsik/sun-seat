package com.sunseat.app.ui.simulation

import com.sunseat.app.domain.*
import com.sunseat.app.ui.theme.Sizes
import com.sunseat.app.ui.theme.Spacing
import java.time.Duration
import kotlin.math.abs
import org.junit.Assert.*
import org.junit.Test

/** #75: transit seats looked tiny. Framing is camera-only and must keep seats visible, unobstructed and large enough. */
class CabinCameraTest {
    private val a = LatLng(37.0, 127.0)
    private fun profile(mode: Mode, travel: TravelMode = TravelMode.TRANSIT, busType: String? = null) =
        CabinProfile.forLeg(Leg(mode, listOf(a, a.copy(latitude = 38.0)), Duration.ofMinutes(20), "A", "B", GroundLevel.ABOVE, busType = busType), travel)!!
    private val profiles get() = mapOf(
        "bus" to profile(Mode.BUS), "coach" to profile(Mode.BUS, busType = "광역"),
        "subway" to profile(Mode.SUBWAY), "taxi" to profile(Mode.CAR, TravelMode.TAXI),
    )
    /** Scene viewport (dp) on a 390x844 phone: full width minus screen padding; landscape uses half width. */
    private data class Viewport(val name: String, val width: Double, val height: Double, val inset: CabinInset) {
        val aspect get() = width / height
    }
    // The view toggle overlays the top-end corner (about 112x48dp).
    private val viewports = listOf(
        Viewport("portrait", 390.0 - 2 * Spacing.screen.value, Sizes.cabinHeight.value.toDouble(), CabinInset(112 / 358.0, 48 / Sizes.cabinHeight.value.toDouble())),
        Viewport("landscape", 844.0 / 2 - 2 * Spacing.screen.value, Sizes.cabinLandscapeHeight.value.toDouble(), CabinInset(112 / 390.0, 48 / Sizes.cabinLandscapeHeight.value.toDouble())),
    )
    private fun cushion(s: CabinSeat) = listOf(0.0 to 0.0, 1.0 to 0.0, 1.0 to 1.0, 0.0 to 1.0).map { (u, v) -> s.sample(SeatSurface.CUSHION, u, v) }
    /** Projected cushion area in dp². */
    private fun area(view: CabinView, vp: Viewport, s: CabinSeat): Double {
        val q = cushion(s).map { CabinCamera.project(view, vp.aspect, it)!! }.map { (x, y) -> x * vp.width / 2 to y * vp.height / 2 }
        return abs((0 until 4).sumOf { q[it].first * q[(it + 1) % 4].second - q[(it + 1) % 4].first * q[it].second }) / 2
    }

    @Test fun seatViewSeesWholeSelectedSeatWithoutOcclusion() {
        val failures = mutableListOf<String>()
        for ((name, p) in profiles) for (vp in viewports) for (seat in p.seats) {
            val view = CabinCamera.seat(p, seat, vp.aspect, vp.inset)
            val blockers = CabinCamera.othersBlockers(p, seat)
            val samples = SeatSurface.entries.flatMap { s -> (0 until CabinSimulation.GRID).flatMap { i -> (0 until CabinSimulation.GRID).map { j ->
                seat.sample(s, (j + .5) / CabinSimulation.GRID, (i + .5) / CabinSimulation.GRID) } } }
            val hidden = samples.count { !CabinCamera.visible(it, view.eye, blockers) }
            if (hidden > 0) failures += "$name/${vp.name}/${seat.id}: $hidden of ${samples.size} cushion/back cells hidden by other seats or pillars"
            if (!CabinCamera.insideCabin(p, view.eye, blockers)) failures += "$name/${vp.name}/${seat.id}: camera outside cabin or inside furniture ${view.eye}"
            // Close-up keeps the whole cushion (with its selection outline) and backrest in frame.
            val frame = cushion(seat) + listOf(0.0, 1.0).map { seat.sample(SeatSurface.BACK, it, 1.0) }
            frame.forEach { point ->
                val ndc = CabinCamera.project(view, vp.aspect, point)
                if (ndc == null || abs(ndc.first) > 1 || abs(ndc.second) > 1) failures += "$name/${vp.name}/${seat.id}: $point out of frame"
                else if (ndc.first > 1 - 2 * vp.inset.width && ndc.second > 1 - 2 * vp.inset.height) failures += "$name/${vp.name}/${seat.id}: $point under view toggle"
            }
            // The selected cushion must be a readable target: at least 44dp across (before #75 landscape: 38dp and mostly hidden).
            val q = cushion(seat).map { CabinCamera.project(view, vp.aspect, it)!! }
            val w = maxOf(abs(q[1].first - q[0].first) * vp.width / 2, abs(q[1].second - q[0].second) * vp.height / 2)
            if (w < 44) failures += "$name/${vp.name}/${seat.id}: cushion only ${w.toInt()}dp wide"
        }
        assertTrue(failures.take(20).joinToString("\n") + "\n(${failures.size} failures)", failures.isEmpty())
    }

    @Test fun overviewFitsWholeVehicleAndUsesTheFrame() {
        val failures = mutableListOf<String>()
        for ((name, p) in profiles) for (vp in viewports) {
            val view = CabinCamera.overview(p, vp.aspect, vp.inset)
            val b = CabinMesh.visualBounds(p)
            val corners = buildList { for (x in listOf(b.min.x, b.max.x)) for (y in listOf(b.min.y, b.max.y)) for (z in listOf(b.min.z, b.max.z)) add(V3(x, y, z)) }
                .map { CabinCamera.project(view, vp.aspect, it) ?: (9.0 to 9.0) }
            if (corners.any { abs(it.first) > 1 || abs(it.second) > 1 }) failures += "$name/${vp.name}: vehicle cropped"
            val fillW = (corners.maxOf { it.first } - corners.minOf { it.first }) / 2
            val fillH = (corners.maxOf { it.second } - corners.minOf { it.second }) / 2
            // Empty space must not shrink the vehicle: it spans the frame in one direction and most of the other.
            if (maxOf(fillW, fillH) < .85 || fillW * fillH < .4) failures += "$name/${vp.name}: vehicle fills only ${(fillW * 100).toInt()}% x ${(fillH * 100).toInt()}%"
            for (seat in p.seats) for (point in cushion(seat) + CabinMesh.badgeCenter(seat)) {
                val ndc = CabinCamera.project(view, vp.aspect, point)!!
                if (ndc.first > 1 - 2 * vp.inset.width && ndc.second > 1 - 2 * vp.inset.height) failures += "$name/${vp.name}/${seat.id}: seat or its check under view toggle"
            }
            val smallest = p.seats.minOf { area(view, vp, it) }
            println("$name/${vp.name}: fill ${(fillW * 100).toInt()}%x${(fillH * 100).toInt()}% smallest seat ${smallest.toInt()}dp²")
            // Even the farthest seat stays a recognisable shape (before #75: 13–55dp² for bus/subway).
            if (smallest < 80) failures += "$name/${vp.name}: smallest seat only ${smallest.toInt()}dp²"
        }
        assertTrue(failures.joinToString("\n"), failures.isEmpty())
    }

    @Test fun framingNeverResizesPhysicalVehicleOrSeats() {
        val bus = profile(Mode.BUS); val subway = profile(Mode.SUBWAY)
        assertEquals(7.0, bus.length, 0.0); assertEquals(2.5, bus.width, 0.0); assertEquals(8.0, subway.length, 0.0)
        (profiles.values.flatMap { it.seats }).forEach { assertEquals(.46, it.width, 0.0); assertEquals(.46, it.depth, 0.0) }
        val before = CabinSimulation.calculate(bus, SunPosition(90.0, 20.0), 0.0, true)
        viewports.forEach { vp -> bus.seats.forEach { CabinCamera.seat(bus, it, vp.aspect, vp.inset) }; CabinCamera.overview(bus, vp.aspect, vp.inset) }
        assertEquals(before, CabinSimulation.calculate(bus, SunPosition(90.0, 20.0), 0.0, true))
    }
}
