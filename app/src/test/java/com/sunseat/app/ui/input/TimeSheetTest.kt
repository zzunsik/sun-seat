package com.sunseat.app.ui.input

import android.app.Application
import androidx.compose.runtime.*
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import com.sunseat.app.domain.TravelMode
import com.sunseat.app.ui.theme.SunSeatTheme
import java.time.LocalDateTime
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class, sdk = [36], qualifiers = "w390dp-h844dp-xxhdpi")
class TimeSheetTest {
    @get:Rule val compose = createComposeRule()
    private val now = LocalDateTime.of(2026, 12, 31, 8, 30)
    private var applied: LocalDateTime? = null
    private var applies = 0
    private var dismissed = false

    private fun show(value: LocalDateTime? = null, travel: TravelMode = TravelMode.TRANSIT) {
        compose.setContent { SunSeatTheme { TimeSheet(value, travel, { dismissed = true }, { applied = it; applies++ }, now = now) } }
        compose.waitForIdle()
    }
    private fun select(tag: String, index: Int) {
        compose.onNodeWithTag(tag).performScrollToIndex(index)
        compose.waitForIdle()
    }
    private fun tap(label: String) {
        compose.onNode(hasClickAction() and hasText(label)).performClick()
        compose.waitForIdle()
    }
    private fun current(tag: String) = compose.onNodeWithTag(tag).fetchSemanticsNode().config[SemanticsProperties.StateDescription]

    @Test fun departurePickerHasFourWheelsAndNoArrivalInput() {
        show()
        listOf("schedule_date", "schedule_period", "schedule_hour", "schedule_minute").forEach { compose.onNodeWithTag(it).assertExists() }
        compose.onNodeWithText("도착 시각").assertDoesNotExist()
        compose.onNodeWithText("완료").assertExists()
    }
    @Test fun wheelsKeepDraftUntilCompletionAndPreserveArbitraryMinute() {
        show()
        select("schedule_date", 1)
        select("schedule_period", 1)
        select("schedule_hour", 10)
        select("schedule_minute", 37)
        assertEquals(0, applies)
        tap("완료")
        assertEquals(LocalDateTime.of(2027, 1, 1, 23, 37), applied)
        assertEquals(1, applies)
        assertTrue(dismissed)
    }
    @Test fun midnightNoonAndLastMinuteUseTwelveHourConversion() {
        show(now.withHour(0).withMinute(5))
        assertEquals("12", current("schedule_hour"))
        assertEquals("오전", current("schedule_period"))
        tap("완료")
        assertEquals(now.withHour(0).withMinute(5), applied)
    }
    @Test fun noonAnd59ArePreserved() {
        show(now.withHour(12).withMinute(59))
        assertEquals("12", current("schedule_hour"))
        assertEquals("오후", current("schedule_period"))
        tap("완료")
        assertEquals(now.withHour(12).withMinute(59), applied)
    }
    @Test fun savedDateOutsideDefaultWindowIsNotClamped() {
        val saved = now.plusYears(2).withHour(23).withMinute(59)
        show(saved)
        tap("완료")
        assertEquals(saved, applied)
    }
    @Test fun savedPastDateAnd37ArePreserved() {
        val saved = now.minusYears(2).withHour(12).withMinute(37)
        show(saved)
        tap("완료")
        assertEquals(saved, applied)
    }
    @Test fun dismissDiscardsDraftAndReopeningStartsAtConfirmedValue() {
        var open by mutableStateOf(true)
        val saved = now.withMinute(5)
        compose.setContent { SunSeatTheme { if (open) TimeSheet(saved, TravelMode.CAR, { open = false }, { applies++ }, now = now) } }
        select("schedule_minute", 37)
        compose.onNode(SemanticsMatcher.keyIsDefined(SemanticsActions.Dismiss)).performSemanticsAction(SemanticsActions.Dismiss)
        compose.waitForIdle()
        assertEquals(0, applies)
        compose.runOnIdle { open = true }
        assertEquals("05", current("schedule_minute"))
    }
    @Test fun rowTapAndAccessibilityIncrementSelectRealValues() {
        show(now.withMinute(37))
        compose.onNodeWithTag("schedule_minute_38").performClick()
        compose.waitForIdle()
        assertEquals("38", current("schedule_minute"))
        val increment = compose.onNodeWithTag("schedule_minute").fetchSemanticsNode().config[SemanticsActions.CustomActions].last()
        compose.runOnIdle { assertTrue(increment.action()) }
        compose.waitForIdle()
        assertEquals("39", current("schedule_minute"))
        tap("완료")
        assertEquals(now.withMinute(39), applied)
    }
    @Test fun draggingDisablesCompletionUntilSnapThenAppliesVisibleValue() {
        show(now.withMinute(30))
        compose.mainClock.autoAdvance = false
        compose.onNodeWithTag("schedule_minute").performTouchInput { swipeUp(durationMillis = 180) }
        compose.mainClock.advanceTimeByFrame()
        compose.onNodeWithText("완료").assertIsNotEnabled()
        assertEquals(0, applies)
        compose.mainClock.autoAdvance = true
        compose.waitForIdle()
        val visible = current("schedule_minute").toInt()
        compose.onNodeWithText("완료").assertIsEnabled()
        tap("완료")
        assertEquals(now.withMinute(visible), applied)
    }
    @Test fun dateWindowDoesNotShiftWhileOpenAcrossMidnight() {
        var clock by mutableStateOf(now)
        compose.setContent { SunSeatTheme { TimeSheet(null, TravelMode.CAR, {}, { applied = it }, now = clock) } }
        select("schedule_date", 1)
        compose.runOnIdle { clock = now.plusDays(1) }
        tap("완료")
        assertEquals(now.plusDays(1), applied)
    }
    @Test fun nowCommitsNullOnce() {
        show(now.plusDays(1))
        tap("지금 출발")
        assertNull(applied)
        assertEquals(1, applies)
        assertTrue(dismissed)
    }
    @Test fun routeTimeNoteOnlyForTransit() {
        show(travel = TravelMode.CAR)
        compose.onNodeWithText("경로는 지금 기준으로 찾아요.", substring = true).assertDoesNotExist()
    }
    @Test fun transitShowsRouteTimeNote() {
        show()
        compose.onNodeWithText("경로는 지금 기준으로 찾아요.", substring = true).assertExists()
    }
}
