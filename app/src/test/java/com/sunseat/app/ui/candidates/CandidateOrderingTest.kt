package com.sunseat.app.ui.candidates

import com.sunseat.app.data.route.TransitRouteType
import com.sunseat.app.domain.*
import java.time.Duration
import java.time.Instant
import org.junit.Assert.*
import org.junit.Test

class CandidateOrderingTest {
    private val base = CandidatesFixtures.options.toUi(null)
    private fun withSun(item: CandidateUi, minutes: Long): CandidateUi {
        val result = TripAdvisor(ExposureAnalyzer { _, _, _ -> SunPosition(90.0, 30.0) })
            .advise(item.chosen.route.copy(legs = item.chosen.route.legs.map { it.copy(groundLevel = GroundLevel.ABOVE) }), Instant.EPOCH, SunGoal.AVOID).copy(sunIfFollowed = Duration.ofMinutes(minutes))
        return item.copy(chosen = item.chosen.copy(cachedResult = result))
    }
    private val items get() = listOf(withSun(base[0], 10), withSun(base[1], 20), base[1].copy(id = 2, type = TransitRouteType.BUS_AND_SUBWAY, totalDuration = Duration.ofMinutes(60), transfers = 2, walkMinutes = 2))
    private fun ids(sort: CandidateSort, goal: SunGoal = SunGoal.AVOID) = orderCandidates(items, sort, emptySet(), goal).map { it.id }

    @Test fun eachSortAndPendingSunOrdering() {
        assertEquals(listOf(0, 1, 2), ids(CandidateSort.RECOMMENDED))
        assertEquals(listOf(0, 1, 2), ids(CandidateSort.TIME))
        assertEquals(listOf(1, 0, 2), ids(CandidateSort.TRANSFERS))
        assertEquals(listOf(1, 2, 0), ids(CandidateSort.WALK))
        assertEquals(listOf(0, 1, 2), ids(CandidateSort.SUN))
        assertEquals(listOf(1, 0, 2), ids(CandidateSort.SUN, SunGoal.SEEK))
    }
    @Test fun unknownOrExcludedResultsCannotWinSunRanking() {
        val valid = withSun(base[1], 20)
        val unknown = withSun(base[0], 0).let { it.copy(chosen = it.chosen.copy(cachedResult = it.chosen.cachedResult!!.copy(legs = it.chosen.cachedResult!!.legs.map { leg -> leg.copy(advice = LegAdvice.UnknownGround) }))) }
        assertEquals(valid.id, orderCandidates(listOf(unknown, valid), CandidateSort.SUN, emptySet(), SunGoal.AVOID).first().id)
        val excluded = unknown.copy(chosen = unknown.chosen.copy(cachedResult = unknown.chosen.cachedResult!!.copy(sunIfFollowed = Duration.ZERO, shadeIfFollowed = Duration.ZERO)))
        assertEquals(valid.id, orderCandidates(listOf(excluded, valid), CandidateSort.SUN, emptySet(), SunGoal.SEEK).first().id)
    }
    @Test fun filtersUseOrAndCanReturnEmpty() {
        assertEquals(listOf(1, 2), orderCandidates(items, CandidateSort.RECOMMENDED, setOf(TransitRouteType.BUS, TransitRouteType.BUS_AND_SUBWAY), SunGoal.AVOID).map { it.id })
        assertTrue(orderCandidates(items, CandidateSort.TIME, setOf(TransitRouteType.OTHER), SunGoal.AVOID).isEmpty())
    }
    /** #82: 분류 미확인(OTHER)은 전체에만 들고, 받은 후보가 없는 범주는 0건이다. */
    @Test fun categoryCountsKeepOtherInAllOnlyAndReportZero() {
        val other = base[0].copy(id = 3, type = TransitRouteType.OTHER)
        val all = items + other
        assertEquals(mapOf(RouteCategory.ALL to 4, RouteCategory.BUS to 1, RouteCategory.SUBWAY to 1, RouteCategory.MIXED to 1), categoryCounts(all))
        assertEquals(listOf(0, 1, 2, 3), CandidatesState.Options(all).visible.map { it.id })
        assertEquals(listOf(1), CandidatesState.Options(all, category = RouteCategory.BUS).visible.map { it.id })
        assertTrue(RouteCategory.entries.filter { it != RouteCategory.ALL }.none { it.matches(other) })
        val busless = listOf(base[0])
        assertEquals(0, categoryCounts(busless)[RouteCategory.BUS])
        assertTrue(CandidatesState.Options(busless, category = RouteCategory.BUS).visible.isEmpty())
        assertEquals(mapOf(RouteCategory.ALL to 0, RouteCategory.BUS to 0, RouteCategory.SUBWAY to 0, RouteCategory.MIXED to 0), categoryCounts(emptyList()))
    }
}
