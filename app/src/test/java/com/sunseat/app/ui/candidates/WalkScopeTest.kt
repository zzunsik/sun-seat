package com.sunseat.app.ui.candidates

import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import com.sunseat.app.domain.Mode
import com.sunseat.app.domain.Route
import com.sunseat.app.domain.TravelMode
import com.sunseat.app.ui.candidates.CandidatesFixtures.transitRequest
import com.sunseat.app.ui.map.LocalMapRenderer
import com.sunseat.app.ui.map.MapRenderer
import com.sunseat.app.ui.theme.SunSeatTheme
import java.time.Duration
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * #105: 대중교통 응답의 도보는 승차~하차 사이(환승) 구간뿐이다. 출발지·도착지 접근 도보는 응답에 없으므로
 * '도보 0분'이 전체 경로에 걸음이 없다는 보장으로 읽히지 않게 범위를 밝히고, 없는 값을 만들지 않는다.
 */
@RunWith(RobolectricTestRunner::class)
@Config(application = android.app.Application::class, sdk = [36])
@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
class WalkScopeTest {
    @get:Rule val compose = createComposeRule()

    private fun card(item: CandidateUi) = compose.setContent { SunSeatTheme { CandidateCard(item, false, transitRequest, {}, {}) } }

    @Test fun directRideDoesNotClaimZeroWalk() {
        card(CandidatesFixtures.busOnly.toUi(0, null, false))
        compose.onNodeWithText("환승 0회", useUnmergedTree = true).assertIsDisplayed()
        compose.onAllNodesWithText("도보 0분", substring = true, useUnmergedTree = true).assertCountEquals(0)
    }

    @Test fun providedTransferWalkIsShownWithItsScope() {
        card(CandidatesFixtures.subwayOnly.toUi(0, null, false))
        compose.onNodeWithText("환승 1회 · 환승 도보 3분", useUnmergedTree = true).assertIsDisplayed()
    }

    @Test fun subMinuteTransferWalkIsNotRoundedToZero() {
        card(CandidatesFixtures.readable.toUi(0, null, false))
        compose.onNodeWithText("환승 2회 · 환승 도보 1분 미만", useUnmergedTree = true).assertIsDisplayed()
    }

    @Test fun zeroDurationWalkLegStaysAKnownZero() {
        val base = CandidatesFixtures.subwayOnly
        val option = base.copy(route = Route(base.route.legs.map { if (it.mode == Mode.WALK) it.copy(duration = Duration.ZERO) else it }))
        val item = option.toUi(0, null, false)
        compose.setContent { SunSeatTheme { CandidateCard(item, false, transitRequest, {}, {}) } }
        compose.onNodeWithText("환승 1회 · 환승 도보 0분", useUnmergedTree = true).assertIsDisplayed()
        // 0초 구간을 1분으로 부풀리지 않는다.
        compose.onNodeWithContentDescription("지하철 1호선 → 도보 0분 → 지하철 2호선; 1호선 12분 · 도보 0분 · 2호선 18분").assertIsDisplayed()
    }

    @Test fun uiModelSeparatesMissingWalkFromKnownWalkAndNeverFillsTheGap() {
        val direct = CandidatesFixtures.busOnly.toUi(0, null, false)
        assertNull(direct.providedWalk)
        assertEquals(0L, direct.walkMinutes)
        val subway = CandidatesFixtures.subwayOnly.toUi(0, null, false)
        // 전체 38분과 구간 합 33분의 차이 5분을 도보로 배분하지 않는다.
        assertEquals(Duration.ofMinutes(3), subway.providedWalk)
        assertEquals(3L, subway.walkMinutes)
        assertEquals(Duration.ofMinutes(38), subway.totalDuration)
        val readable = CandidatesFixtures.readable.toUi(0, null, false)
        assertEquals(Duration.ofSeconds(20), readable.providedWalk)
    }

    @Test fun walkSortUsesTheSameProvidedWalkTheCardShows() {
        val base = CandidatesFixtures.subwayOnly
        val walk = base.route.legs[1].copy(duration = Duration.ofSeconds(40))
        // 40초 + 40초: 카드는 '환승 도보 1분'이므로 정렬 값도 1분이어야 한다(구간별 내림 합 0분이 아님).
        val twoShort = base.copy(route = Route(listOf(base.route.legs[0], walk, base.route.legs[2], walk, base.route.legs[2]))).toUi(0, null, false)
        assertEquals(1L, twoShort.walkMinutes)
        val subMinute = CandidatesFixtures.readable.toUi(1, null, false)
        val direct = CandidatesFixtures.busOnly.toUi(2, null, false)
        val ordered = orderCandidates(listOf(twoShort, subMinute, direct), CandidateSort.WALK, emptySet(), com.sunseat.app.domain.SunGoal.AVOID).map { it.id }
        assertEquals(listOf(2, 1, 0), ordered)
    }

    private fun screen(state: CandidatesState, request: com.sunseat.app.domain.TripRequest = transitRequest) = compose.setContent {
        androidx.compose.runtime.CompositionLocalProvider(LocalMapRenderer provides MapRenderer.PREVIEW) {
            SunSeatTheme { CandidatesScreen(request, state, CandidatesActions(), animateLoader = false) }
        }
    }

    @Test fun transitListExplainsWalkAndBarScopeOnce() {
        screen(CandidatesState.Options(CandidatesFixtures.options.toUi(null)))
        compose.onNodeWithText("구간·햇빛 안내").performClick()
        compose.onAllNodesWithText("도보와 구간 표시는 승차부터 하차까지예요.", substring = true).assertCountEquals(1)
        compose.onAllNodesWithText("정류장을 오가는 도보와 대기 시간은 빠져요.", substring = true).assertCountEquals(1)
    }

    @Test fun walkSortExplainsItComparesOnlyProvidedTransferWalk() {
        screen(CandidatesState.Options(CandidatesFixtures.options.toUi(null), sort = CandidateSort.WALK))
        compose.onNodeWithText("구간·햇빛 안내").performClick()
        compose.onAllNodesWithText("최소 도보는 환승 도보만 비교해요.", substring = true).assertCountEquals(1)
    }

    @Test fun taxiHasNoTransitWalkNotice() {
        val item = CandidatesFixtures.busOnly.toUi(0, null, false).copy(walkMinutes = 0, chips = listOf(LegChip(Mode.CAR)))
        screen(CandidatesState.Options(listOf(item)), transitRequest.copy(mode = TravelMode.TAXI))
        compose.onAllNodesWithText("정류장을 오가는", substring = true).assertCountEquals(0)
    }
}
