package com.sunseat.app.ui.map

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.sunseat.app.domain.LatLng
import com.sunseat.app.domain.Leg
import com.sunseat.app.domain.Mode
import com.sunseat.app.domain.Route
import com.sunseat.app.ui.theme.MapRouteColors
import com.sunseat.app.ui.theme.Sizes
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Duration

/** docs/DESIGN.md 2.5·4.2·11.1 지도 경로선 계약(#100). */
class RouteLinePolicyTest {
    private val teal = Color(0xFF0B6E75)
    private val casing = Color(0xFFFFFFFF)
    private val walkInk = Color(0xFF4A5666)
    private val grey = Color(0xFF7A8696)

    @Test fun mapTokensMatchDesignTable() {
        assertEquals(teal, MapRouteColors.transit)
        assertEquals(casing, MapRouteColors.casing)
        assertEquals(walkInk, MapRouteColors.walk)
        assertEquals(grey, MapRouteColors.alternate)
        assertEquals(walkInk, MapRouteColors.outlineLowContrast)
        assertEquals(grey, MapRouteColors.passed)
        assertEquals(6.dp, Sizes.mapRouteSelected)
        assertEquals(4.dp, Sizes.mapRouteCore)
        assertEquals(1.dp, Sizes.mapRouteCasing)
        assertEquals(3.dp, Sizes.mapRouteWalk)
        assertEquals(3.dp, Sizes.mapRouteAlternate)
        assertEquals(14.dp, Sizes.mapStopMarker)
        assertEquals(Sizes.mapRouteSelected, Sizes.mapRouteCore + Sizes.mapRouteCasing * 2)
    }

    @Test fun selectedBusIsTealCoreWithWhiteCasingAndArrows() {
        val spec = RouteLinePolicy.forLeg(Mode.BUS, "402", RouteRole.SELECTED)
        assertEquals(teal, spec.color)
        assertEquals(4.dp, spec.core)
        assertEquals(casing, spec.edgeColor)
        assertEquals(1.dp, spec.edge)
        assertEquals(6.dp, spec.total)
        assertEquals(LinePattern.SOLID, spec.pattern)
        assertTrue(spec.arrows)
        assertEquals(3, spec.layer)
    }

    @Test fun selectedSubwayUsesOfficialLineColorWithCasingWhenContrastIsEnough() {
        val spec = RouteLinePolicy.forLeg(Mode.SUBWAY, "2호선", RouteRole.SELECTED)
        assertEquals(Color(0xFF00A84D), spec.color)
        assertEquals(casing, spec.edgeColor)
        assertEquals(6.dp, spec.total)
    }

    @Test fun lowContrastLinesSwapWhiteCasingForDarkOutlineKeepingTotalWidth() {
        val low = listOf("3호선", "4호선", "9호선", "경의중앙", "수인분당")
        val enough = listOf("신분당", "공항철도", "1호선", "2호선", "5호선", "6호선", "7호선", "8호선")
        low.forEach { name ->
            val spec = RouteLinePolicy.forLeg(Mode.SUBWAY, name, RouteRole.SELECTED)
            assertTrue("$name contrast", RouteLinePolicy.contrastOnWhite(spec.color) < 3.0)
            assertEquals("$name outline", walkInk, spec.edgeColor)
            assertEquals(6.dp, spec.total)
        }
        enough.forEach { name ->
            val spec = RouteLinePolicy.forLeg(Mode.SUBWAY, name, RouteRole.SELECTED)
            assertTrue("$name contrast", RouteLinePolicy.contrastOnWhite(spec.color) >= 3.0)
            assertEquals("$name casing", casing, spec.edgeColor)
        }
    }

    @Test fun unknownOrAmbiguousLineFallsBackToRouteTransitInsteadOfGuessing() {
        assertEquals(teal, RouteLinePolicy.forLeg(Mode.SUBWAY, "우이신설", RouteRole.SELECTED).color)
        assertEquals(teal, RouteLinePolicy.forLeg(Mode.SUBWAY, null, RouteRole.SELECTED).color)
        assertEquals(teal, RouteLinePolicy.forLeg(Mode.SUBWAY, "4호선 / 2호선", RouteRole.SELECTED).color)
        assertEquals(Color(0xFF00A5DE), RouteLinePolicy.forLeg(Mode.SUBWAY, "4호선 / 4호선급행", RouteRole.SELECTED).color)
        // 버스 이름에 숫자 노선이 있어도 지하철 색을 쓰지 않는다.
        assertEquals(teal, RouteLinePolicy.forLeg(Mode.BUS, "9호선연계", RouteRole.SELECTED).color)
        assertEquals(teal, RouteLinePolicy.forLeg(Mode.CAR, null, RouteRole.SELECTED).color)
    }

    @Test fun walkingIsThinDottedInkBelowRides() {
        val spec = RouteLinePolicy.forLeg(Mode.WALK, null, RouteRole.SELECTED)
        assertEquals(walkInk, spec.color)
        assertEquals(3.dp, spec.total)
        assertNull(spec.edgeColor)
        assertEquals(LinePattern.DOTTED, spec.pattern)
        assertFalse(spec.arrows)
        assertEquals(2, spec.layer)
    }

    @Test fun alternatesAreThinGreyWithoutCasingBelowEverything() {
        Mode.entries.forEach { mode ->
            val spec = RouteLinePolicy.forLeg(mode, "2호선", RouteRole.ALTERNATE)
            assertEquals(grey, spec.color)
            assertEquals(3.dp, spec.total)
            assertNull(spec.edgeColor)
            assertFalse(spec.arrows)
            assertEquals(1, spec.layer)
        }
    }

    @Test fun passedPlaybackPartIsGreyAndCoversTheRemainingLine() {
        val ride = RouteLinePolicy.forLeg(Mode.SUBWAY, "2호선", RouteRole.PASSED)
        assertEquals(grey, ride.color)
        assertEquals(RouteLinePolicy.forLeg(Mode.SUBWAY, "2호선", RouteRole.SELECTED).total, ride.total)
        assertFalse(ride.arrows)
        assertTrue(ride.layer > RouteLinePolicy.forLeg(Mode.SUBWAY, "2호선", RouteRole.SELECTED).layer)
        val walk = RouteLinePolicy.forLeg(Mode.WALK, null, RouteRole.PASSED)
        assertEquals(grey, walk.color)
        assertEquals(3.dp, walk.total)
    }

    @Test fun mapColorsMeetTheirContrastRules() {
        assertTrue(RouteLinePolicy.contrastOnWhite(teal) >= 4.5)
        assertTrue(RouteLinePolicy.contrastOnWhite(walkInk) >= 4.5)
        assertTrue(RouteLinePolicy.contrastOnWhite(grey) >= 3.0)
        assertEquals(21.0, RouteLinePolicy.contrastOnWhite(Color.Black), 0.01)
    }

    /** SDK multiplies lineWidth by KakaoMap.getMapDpScale(); measured 1.6977 on SM-S936N (density 3.75). */
    @Test fun sdkWidthCancelsMapDpScaleSoRenderedPixelsEqualLogicalDp() {
        listOf(1f to 1f, 2.625f to 1.19f, 3.75f to 1.6976587f, 3.75f to 3.75f).forEach { (density, scale) ->
            val argument = RouteLinePolicy.sdkWidth(6.dp, density, scale)
            assertEquals("density $density scale $scale", 6 * density, argument * scale, 0.001f)
        }
        // SDK not ready (-1): fall back to physical pixels rather than dividing by a guess.
        assertEquals(6 * 3.75f, RouteLinePolicy.sdkWidth(6.dp, 3.75f, -1f), 0.001f)
    }

    private fun leg(mode: Mode, from: String, to: String, line: String?, start: Double, step: Int? = null) =
        Leg(mode, listOf(LatLng(start, 127.0), LatLng(start + 0.01, 127.0)), Duration.ofMinutes(5), from, to, lineName = line, transitStepIndex = step)

    @Test fun stopsComeOnlyFromTransitLegEndpointsAndMergeStationIntervals() {
        val route = Route(listOf(
            leg(Mode.WALK, "", "서울역", null, 37.50),
            leg(Mode.SUBWAY, "서울역", "숙대입구", "4호선", 37.51, step = 1),
            leg(Mode.SUBWAY, "숙대입구", "사당", "4호선", 37.52, step = 1),
            leg(Mode.WALK, "사당", "사당", null, 37.53),
            leg(Mode.SUBWAY, "사당", "강남", "2호선", 37.54, step = 3),
        ))
        val stops = RouteLinePolicy.stops(route)
        // Station intervals of one ride merge; transfers get one marker at the next boarding point.
        assertEquals(listOf(StopKind.BOARD, StopKind.TRANSFER, StopKind.ALIGHT), stops.map { it.kind })
        assertEquals(LatLng(37.51, 127.0), stops[0].location)
        assertEquals(LatLng(37.54, 127.0), stops[1].location)
        assertEquals(LatLng(37.55, 127.0), stops[2].location)
        // Every boarding marker names the line it boards (not colour alone); the final alight has no label.
        assertEquals(listOf("4호선"), stops[0].lines)
        assertEquals(listOf("2호선"), stops[1].lines)
        assertEquals(listOf(true, true, false), stops.map { it.labeled })
        assertEquals(Mode.SUBWAY, stops[1].mode)
        assertEquals(Color(0xFF00A5DE), stops[0].color)
        assertEquals(Color(0xFF00A84D), stops[1].color)
        assertTrue(RouteLinePolicy.stops(Route(listOf(leg(Mode.WALK, "", "", null, 37.5)))).isEmpty())
        assertTrue(RouteLinePolicy.stops(Route(listOf(leg(Mode.CAR, "", "", null, 37.5)))).isEmpty())
    }

    @Test fun summaryListsBoardTransferAlightInRouteOrder() {
        val route = Route(listOf(
            leg(Mode.SUBWAY, "서울역", "시청", "1호선", 37.50, step = 0),
            leg(Mode.WALK, "시청", "시청", null, 37.51),
            leg(Mode.SUBWAY, "시청", "강남역", "2호선", 37.52, step = 2),
            leg(Mode.BUS, "강남역", "양재역", null, 37.53, step = 3),
        ))
        assertEquals(listOf(
            RideStep(StopKind.BOARD, Mode.SUBWAY, "1호선", "서울역"),
            RideStep(StopKind.TRANSFER, Mode.SUBWAY, "2호선", "시청"),
            RideStep(StopKind.TRANSFER, Mode.BUS, null, "강남역"),
            RideStep(StopKind.ALIGHT, Mode.BUS, null, "양재역"),
        ), RouteLinePolicy.rideSteps(route))
    }

    @Test fun walkDashesFollowThePathAtTheRequestedSpacing() {
        // ~1113 m due north; 10 m dots every 20 m.
        val path = listOf(LatLng(37.50, 127.0), LatLng(37.51, 127.0))
        val dashes = RouteGeometry.dashes(path, 10.0, 10.0)!!
        assertEquals(56, dashes.size)
        dashes.forEach { assertEquals(10.0, RouteGeometry.length(it), 0.5) }
        assertEquals(path.first(), dashes.first().first())
        assertTrue(RouteGeometry.dashes(listOf(path.first()), 10.0, 10.0)!!.isEmpty())
        // Over the cap the caller gets null and draws the whole walk, never a truncated part of it.
        assertNull(RouteGeometry.dashes(path, 10.0, 10.0, max = 50))
        assertEquals(56, RouteGeometry.dashes(path, 10.0, 10.0, max = 60)!!.size)
    }

    @Test fun directionMarksStartHalfwayFollowPathOrderAndSkipShortLegs() {
        val path = listOf(LatLng(37.50, 127.0), LatLng(37.51, 127.0), LatLng(37.51, 127.02))
        val marks = RouteGeometry.marks(path, 200.0)
        assertEquals(100.0, com.sunseat.app.domain.ExposureAnalyzer.distanceMeters(path.first(), marks.first().first), 0.5)
        assertEquals(0.0, marks.first().second, 0.5) // north first
        assertEquals(90.0, marks.last().second, 1.0) // then east
        assertTrue(RouteGeometry.marks(listOf(LatLng(37.5, 127.0), LatLng(37.5005, 127.0)), 200.0).isEmpty())
    }

    @Test fun boardingLabelSitsOnTheSideAwayFromTheOutgoingLineAndKeepsAllNames() {
        fun ride(to: LatLng, line: String?) = Route(listOf(Leg(Mode.BUS, listOf(LatLng(37.5, 127.0), to), Duration.ofMinutes(5), "A", "B", lineName = line, transitStepIndex = 0)))
        assertTrue(RouteLinePolicy.stops(ride(LatLng(37.5, 127.01), "402")).first().labelOnLeft) // heads east -> label west
        assertFalse(RouteLinePolicy.stops(ride(LatLng(37.5, 126.99), "402")).first().labelOnLeft) // heads west -> label east
        assertFalse(RouteLinePolicy.stops(ride(LatLng(37.51, 127.0), "402")).first().labelOnLeft) // north -> default right
        assertEquals(listOf("402", "405", "N16(심야)"), RouteLinePolicy.stops(ride(LatLng(37.51, 127.0), "402 / 405 / N16(심야)")).first().lines)
        assertTrue(RouteLinePolicy.stops(ride(LatLng(37.51, 127.0), null)).first().lines.isEmpty())
    }
}
