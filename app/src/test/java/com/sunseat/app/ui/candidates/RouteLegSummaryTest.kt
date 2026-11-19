package com.sunseat.app.ui.candidates

import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.assertIsDisplayed
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
        val first = base.chosen.route.legs.first().copy(estimatedStationTiming = true)
        val second = first.copy(startName = first.endName, endName = "견본 종점")
        val item = base.copy(chips = listOf(base.chips.first(), base.chips.first()), chosen = base.chosen.copy(route = Route(listOf(first, second))))
        compose.setContent { SunSeatTheme { RouteLegSummary(item, false) } }
        compose.onAllNodesWithText("지하철 1호선").assertCountEquals(1)
        compose.onNodeWithText("서울역 승차 → 견본 종점 하차").assertIsDisplayed()
    }

    @Test fun missingLegNamesUseAvailableCandidateEndpoints() {
        val base = CandidatesFixtures.busOnly.toUi(0, null, false)
        val item = base.copy(chosen = base.chosen.copy(route = Route(base.chosen.route.legs.map { it.copy(mode = Mode.CAR, startName = "", endName = "") })), chips = listOf(LegChip(Mode.CAR)))
        compose.setContent { SunSeatTheme { RouteLegSummary(item, true) } }
        compose.onNodeWithText("서울역버스환승센터 승차 → 강남역.강남역사거리 하차").assertIsDisplayed()
        compose.onNodeWithText("택시").assertIsDisplayed()
    }
}
