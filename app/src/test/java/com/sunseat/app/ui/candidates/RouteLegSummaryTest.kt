package com.sunseat.app.ui.candidates

import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.onNodeWithContentDescription
import com.sunseat.app.domain.Mode
import com.sunseat.app.domain.Route
import com.sunseat.app.ui.theme.SunSeatTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(application = android.app.Application::class, sdk = [36])
class RouteLegSummaryTest {
    @get:Rule val compose = createComposeRule()

    @Test fun estimatedStationIntervalsRemainOneRideWithOuterStops() {
        val base = CandidatesFixtures.subwayOnly.toUi(0, null, false)
        val first = base.chosen.route.legs.first().copy(estimatedStationTiming = true, transitStepIndex = 0)
        val second = first.copy(startName = first.endName, endName = "견본 종점")
        val item = base.copy(chips = listOf(base.chips.first(), base.chips.first()), chosen = base.chosen.copy(route = Route(listOf(first, second))))
        compose.setContent { SunSeatTheme { RouteLegSummary(item, false) } }
        compose.onAllNodesWithText("지하철 1호선").assertCountEquals(1)
        compose.onNodeWithText("서울역 승차").assertIsDisplayed()
        compose.onNodeWithText("견본 종점 하차").assertIsDisplayed()
    }

    @Test fun segmentDescriptionFoldsEstimatedIntervalsOfOneRide() {
        val base = CandidatesFixtures.subwayOnly.toUi(0, null, false)
        val first = base.chosen.route.legs.first().copy(estimatedStationTiming = true, transitStepIndex = 0)
        val second = first.copy(startName = first.endName, endName = "견본 종점")
        val item = base.copy(chips = listOf(base.chips.first(), base.chips.first()), chosen = base.chosen.copy(route = Route(listOf(first, second))))
        compose.setContent { SunSeatTheme { RouteSegmentBar(item) } }
        compose.onNodeWithContentDescription("지하철 1호선; 1호선 24분").assertIsDisplayed()
    }

    @Test fun segmentDescriptionPreservesSeparateBoardingsOfSameLine() {
        val base = CandidatesFixtures.subwayOnly.toUi(0, null, false)
        val first = base.chosen.route.legs.first()
        val second = first.copy(startName = first.endName, endName = "다른 종점")
        val item = base.copy(chips = listOf(base.chips.first(), base.chips.first()), chosen = base.chosen.copy(route = Route(listOf(first, second))))
        compose.setContent { SunSeatTheme { RouteSegmentBar(item) } }
        compose.onNodeWithContentDescription("지하철 1호선 → 지하철 1호선; 1호선 12분 · 1호선 12분").assertIsDisplayed()
    }

    @Test fun sameLineEstimatedStepsDoNotMergeAcrossBoardings() {
        val base = CandidatesFixtures.subwayOnly.toUi(0, null, false)
        val first = base.chosen.route.legs.first().copy(estimatedStationTiming = true, transitStepIndex = 0)
        val second = first.copy(startName = first.endName, endName = "다른 종점", transitStepIndex = 1)
        val item = base.copy(chips = listOf(base.chips.first(), base.chips.first()), chosen = base.chosen.copy(route = Route(listOf(first, second))))
        compose.setContent { SunSeatTheme { RouteSegmentBar(item); RouteLegSummary(item, false) } }
        compose.onNodeWithContentDescription("지하철 1호선 → 지하철 1호선; 1호선 12분 · 1호선 12분").assertIsDisplayed()
        compose.onAllNodesWithText("지하철 1호선").assertCountEquals(2)
    }

    @Test fun walkBetweenSameLineRidesRemainsInDescription() {
        val base = CandidatesFixtures.subwayOnly.toUi(0, null, false)
        val item = base.copy(chips = listOf(base.chips.first(), base.chips[1], base.chips.first()))
        compose.setContent { SunSeatTheme { RouteSegmentBar(item) } }
        compose.onNodeWithContentDescription("지하철 1호선 → 도보 3분 → 지하철 1호선; 1호선 12분 · 도보 3분 · 1호선 18분").assertIsDisplayed()
    }

    @Test fun multipleShortWalksUseTheirCombinedDuration() {
        val base = CandidatesFixtures.readable.toUi(0, null, false)
        val walk = base.chosen.route.legs[1].copy(duration = java.time.Duration.ofSeconds(40))
        val item = base.copy(chips = listOf(LegChip(Mode.WALK), LegChip(Mode.WALK)), chosen = base.chosen.copy(route = Route(listOf(walk, walk))))
        compose.setContent { SunSeatTheme { CandidateCard(item, false, CandidatesFixtures.transitRequest, {}, {}) } }
        compose.onNodeWithText("환승 2회 · 환승 도보 1분", useUnmergedTree = true).assertIsDisplayed()
    }

    @Test fun missingLegNamesUseAvailableCandidateEndpoints() {
        val base = CandidatesFixtures.busOnly.toUi(0, null, false)
        val item = base.copy(chosen = base.chosen.copy(route = Route(base.chosen.route.legs.map { it.copy(mode = Mode.CAR, startName = "", endName = "") })), chips = listOf(LegChip(Mode.CAR)))
        compose.setContent { SunSeatTheme { RouteLegSummary(item, true) } }
        compose.onNodeWithText("서울역버스환승센터 승차").assertIsDisplayed()
        compose.onNodeWithText("강남역.강남역사거리 하차").assertIsDisplayed()
        compose.onNodeWithText("택시").assertIsDisplayed()
    }
}
