package com.sunseat.app.ui.map

import androidx.compose.ui.unit.dp
import org.junit.Assert.assertFalse
import com.sunseat.app.domain.LatLng
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class MapProjectionTest {
    @Test fun sheetOverlapUsesOneCoordinateSpaceWithoutDoubleTopPadding() {
        assertEquals(MapPadding(bottom = 300.dp), sheetMapPadding(200.dp, 600.dp, 500.dp))
        assertEquals(MapPadding(bottom = 600.dp), sheetMapPadding(200.dp, 600.dp, 100.dp))
        assertEquals(MapPadding(), sheetMapPadding(200.dp, 600.dp, 900.dp))
    }

    @Test fun sheetDragChangesPaddingWithoutRepeatedFitAndExplicitFitStillWorks() {
        val policy = MapFitPolicy()
        val state = MapState(routes = listOf(com.sunseat.app.ui.result.ResultFixtures.transitRoute), automaticFit = false)
        assertTrue(policy.shouldFit(state))
        for (bottom in 100..500) assertFalse(policy.shouldFit(state.copy(padding = MapPadding(bottom = bottom.dp))))
        assertTrue(policy.shouldFit(state.copy(cameraRevision = 1)))
        assertFalse(policy.shouldFit(state.copy(cameraRevision = 1)))
    }

    @Test fun coveredMapDefersFitUntilVisibleAndAutomaticCallersKeepExistingBehavior() {
        val policy = MapFitPolicy()
        val state = MapState(routes = listOf(com.sunseat.app.ui.result.ResultFixtures.transitRoute), automaticFit = false)
        assertFalse(policy.shouldFit(state.copy(fitEnabled = false)))
        assertTrue(policy.shouldFit(state.copy(cameraRevision = 2)))
        assertFalse(policy.shouldFit(state.copy(cameraRevision = 2, fitEnabled = false)))
        assertFalse(policy.shouldFit(state.copy(cameraRevision = 2)))
        assertTrue(policy.shouldFit(state.copy(automaticFit = true, padding = MapPadding(bottom = 50.dp), cameraRevision = 2)))
    }

    @Test fun selectedRouteBoundsExcludeOtherCandidates() {
        val selected = com.sunseat.app.ui.result.ResultFixtures.transitRoute
        val far = selected.copy(legs = selected.legs.map { leg -> leg.copy(path = leg.path.map { LatLng(it.latitude + 5, it.longitude + 5) }) })
        val state = MapState(routes = listOf(selected, far), selectedRoute = 0)
        assertEquals(selected.legs.flatMap { it.path }, state.points())
        assertTrue(state.copy(selectedRoute = -1).points().any { it.latitude > 40 })
    }

    @Test fun northIsUpAndEastIsRightInsideTheBounds() {
        val southWest = LatLng(37.0, 127.0)
        val northEast = LatLng(37.1, 127.1)
        val projection = MapProjection(listOf(southWest, northEast), 400f, 240f, 40f)
        val a = projection.project(southWest)
        val b = projection.project(northEast)
        assertTrue(a.first < b.first)
        assertTrue(a.second > b.second)
        for ((x, y) in listOf(a, b)) { assertTrue(x in 40f..360f); assertTrue(y in 40f..200f) }
    }
    @Test fun singlePointAndEmptyBoundsDoNotProduceNonFiniteCoordinates() {
        val point = LatLng(37.5, 127.0)
        val centered = MapProjection(listOf(point), 200f, 100f, 20f).project(point)
        assertEquals(100f, centered.first, 0.01f)
        assertEquals(50f, centered.second, 0.01f)
        val empty = MapProjection(emptyList(), 0f, 0f, 40f).project(LatLng(0.0, 0.0))
        assertTrue(empty.first.isFinite() && empty.second.isFinite())
    }
}
