package com.sunseat.app.ui.result

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.sunseat.app.domain.CarSeat
import com.sunseat.app.domain.SunGoal
import com.sunseat.app.ui.formatDuration
import com.sunseat.app.ui.formatWon
import com.sunseat.app.ui.text.label
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.time.Duration

@RunWith(RobolectricTestRunner::class)
@Config(application = android.app.Application::class, sdk = [36])
class ResultTextTest {
    private val context: Context get() = ApplicationProvider.getApplicationContext()

    @Test fun durationAndFareKeepTheirDisplayRules() {
        assertEquals("0분", formatDuration(context, Duration.ZERO, zeroOk = true))
        assertEquals("1분", formatDuration(context, Duration.ofSeconds(30)))
        assertEquals("1시간", formatDuration(context, Duration.ofHours(1)))
        assertEquals("1시간 5분", formatDuration(context, Duration.ofMinutes(65)))
        assertEquals("1,550원", formatWon(context, 1550))
    }

    @Test fun carShareUsesSeatsFromTheRecommendation() {
        val result = ResultFixtures.advisor(90.0, 30.0).advise(ResultFixtures.carRoute, ResultFixtures.departure, SunGoal.AVOID)
        val text = shareText(context, ResultFixtures.carRequest, result, ResultFixtures.zone)
        assertTrue(text.contains("뒷좌석 왼쪽에 앉으세요"))
        assertTrue(text.contains("- 자가용: 뒷좌석 왼쪽"))
        assertEquals("조수석·뒷좌석 오른쪽", listOf(CarSeat.PASSENGER, CarSeat.REAR_RIGHT).label(context))
    }

    @Test fun shareStaysWithinSixLinesWithManyRecommendationsAndRisks() {
        val result = ResultFixtures.advisor(10.0, 10.0).advise(ResultFixtures.carRoute, ResultFixtures.departure, SunGoal.AVOID)
        val side = ResultFixtures.advisor(90.0, 30.0).advise(ResultFixtures.carRoute, ResultFixtures.departure, SunGoal.AVOID)
        val many = result.copy(legs = List(20) { side.legs.first() }, glare = List(20) { result.glare.first() })
        val request = ResultFixtures.carRequest.copy(origin = ResultFixtures.carRequest.origin.copy(name = "출발\n추가 행"))
        val text = shareText(context, request, many, ResultFixtures.zone)
        assertTrue(text.lines().size in 4..6)
        assertTrue(text.contains("외 19구간"))
        assertTrue(text.startsWith("출발 추가 행"))
    }

    @Test fun allWalkMessageDescribesExclusionInsteadOfNight() {
        val route = com.sunseat.app.domain.Route(ResultFixtures.transitRoute.legs.map { it.copy(mode = com.sunseat.app.domain.Mode.WALK) })
        val result = ResultFixtures.advisor(90.0, 30.0).advise(route, ResultFixtures.departure, SunGoal.AVOID)
        assertEquals(com.sunseat.app.R.string.result_no_outdoor, headlineRes(context, ResultFixtures.transitRequest, result).first)
        assertTrue(shareText(context, ResultFixtures.transitRequest, result).lines().size in 4..6)
    }

    @Test fun transitShareDistinguishesBenchAndWindow() {
        val result = ResultFixtures.advisor(90.0, 30.0).advise(ResultFixtures.transitRoute, ResultFixtures.departure, SunGoal.AVOID)
        val text = shareText(context, ResultFixtures.transitRequest, result, ResultFixtures.zone)
        assertTrue(text.contains("진행 방향 왼쪽 자리"))
        assertTrue(text.contains("진행 방향 왼쪽 창가"))
        assertTrue(text.contains("11/16 09:00 출발"))
    }
}
