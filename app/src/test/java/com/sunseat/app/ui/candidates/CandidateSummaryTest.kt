package com.sunseat.app.ui.candidates

import androidx.compose.foundation.layout.Column
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import com.sunseat.app.domain.*
import com.sunseat.app.ui.theme.SunSeatTheme
import java.time.Duration
import java.time.Instant
import java.time.LocalDateTime
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(application = android.app.Application::class, sdk = [36], qualifiers = "w390dp-h844dp-xxhdpi")
class CandidateSummaryTest {
    @get:Rule val compose = createComposeRule()

    @Test fun barUsesProvidedDurationsWithoutInventingTotalRemainder() {
        val item = CandidatesFixtures.subwayOnly.toUi(0, null, false)
        assertEquals(Duration.ofMinutes(38), item.totalDuration)
        assertEquals(listOf(Duration.ofMinutes(12), Duration.ofMinutes(3), Duration.ofMinutes(18)), routeTimeParts(item).map { it.duration })
        val fractions = routeTimeFractions(item)
        assertEquals(12f / 33, fractions[0], .00001f)
        assertEquals(3f / 33, fractions[1], .00001f)
        assertEquals(18f / 33, fractions[2], .00001f)
        assertEquals(1f, fractions.sum(), .00001f)
    }

    @Test fun zeroAndShortWalkStayExactAndSeparateBoardingsRemainSeparate() {
        val base = CandidatesFixtures.subwayOnly.toUi(0, null, false)
        val ride = base.chosen.route.legs.first().copy(estimatedStationTiming = true, transitStepIndex = 0)
        val walk = base.chosen.route.legs[1].copy(duration = Duration.ofSeconds(20))
        val legs = listOf(ride, ride.copy(startName = ride.endName), walk, ride.copy(transitStepIndex = 1))
        val item = base.copy(chosen = base.chosen.copy(route = Route(legs)), chips = listOf(base.chips[0], base.chips[0], base.chips[1], base.chips[0]))
        assertEquals(listOf(0..1, 2..2, 3..3), routeLegGroups(item))
        assertEquals(Duration.ofSeconds(20), routeTimeParts(item)[1].duration)
        assertEquals(20f / 2180, routeTimeFractions(item)[1], .00001f)
        val zero = item.copy(chosen = item.chosen.copy(route = Route(legs.map { it.copy(duration = Duration.ZERO) })))
        assertEquals(listOf(0f, 0f, 0f), routeTimeFractions(zero))
    }

    @Test fun nightHasOnePlainSummaryAndExplicitSeatAction() {
        val base = CandidatesFixtures.busOnly.toUi(0, LocalDateTime.of(2026, 11, 15, 23, 50), false)
        val result = TripAdvisor(ExposureAnalyzer { _, _, _ -> SunPosition(90.0, -20.0) })
            .advise(base.chosen.route, Instant.parse("2026-11-15T14:50:00Z"), SunGoal.AVOID, totalDuration = base.totalDuration)
        var previews = 0
        var seats = 0
        compose.setContent { SunSeatTheme {
            CandidateCard(base.copy(chosen = base.chosen.copy(cachedResult = result)), false,
                CandidatesFixtures.transitRequest, { previews++ }, { seats++ })
        } }
        compose.onAllNodesWithText("해 없음", useUnmergedTree = true).assertCountEquals(1)
        compose.onAllNodesWithText("그늘", substring = true, useUnmergedTree = true).assertCountEquals(0)
        compose.onNodeWithTag("candidate_0").performClick().performClick()
        assertEquals(2, previews)
        assertEquals(0, seats)
        compose.onNodeWithText("자리 보기").performClick()
        assertEquals(1, seats)
        assertEquals(2, previews)
        compose.onNodeWithText("23:50 → 다음 날 00:42", useUnmergedTree = true).assertExists()
    }

    @Test fun selectedTimelineKeepsOneOriginalRideAndKnownOuterStops() {
        val base = CandidatesFixtures.subwayOnly.toUi(0, null, false)
        val first = base.chosen.route.legs.first().copy(estimatedStationTiming = true, transitStepIndex = 0)
        val second = first.copy(startName = first.endName, endName = "견본 종점")
        val item = base.copy(chips = listOf(base.chips[0], base.chips[0]), chosen = base.chosen.copy(route = Route(listOf(first, second))))
        compose.setContent { SunSeatTheme { CandidateCard(item, true, CandidatesFixtures.transitRequest, {}, {}) } }
        compose.onAllNodesWithText("지하철 1호선", useUnmergedTree = true).assertCountEquals(1)
        compose.onNodeWithText("서울역 승차", useUnmergedTree = true).assertExists()
        compose.onNodeWithText("견본 종점 하차", useUnmergedTree = true).assertExists()
        compose.onNodeWithTag("candidate_0").assertIsSelected()
    }
}
