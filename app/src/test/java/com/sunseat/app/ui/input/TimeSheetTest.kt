package com.sunseat.app.ui.input

import android.app.Application
import androidx.compose.material3.Text
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.assertIsNotSelected
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performSemanticsAction
import com.sunseat.app.domain.TimeMode
import com.sunseat.app.domain.TravelMode
import com.sunseat.app.ui.theme.SunSeatTheme
import java.time.LocalDateTime
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** 시각 시트: 출발/도착 전환·날짜 칩은 적용을 눌러야 반영된다. */
@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class, sdk = [36], qualifiers = "w390dp-h844dp-xxhdpi")
class TimeSheetTest {
    @get:Rule val compose = createComposeRule()
    private val now = LocalDateTime.of(2026, 11, 16, 8, 30)
    private var applied: Pair<LocalDateTime?, TimeMode>? = null
    private var dismissed = false

    private fun show(value: LocalDateTime? = null, mode: TimeMode = TimeMode.DEPART, travel: TravelMode = TravelMode.TRANSIT) {
        compose.setContent {
            SunSeatTheme(darkTheme = false) {
                TimeSheet(value, mode, travel, onDismiss = { dismissed = true }, onApply = { v, m -> applied = v to m }, now = now)
            }
        }
        compose.waitForIdle()
    }

    private fun tap(label: String) {
        compose.onNode(hasClickAction() and hasText(label)).performSemanticsAction(SemanticsActions.OnClick)
        compose.waitForIdle()
    }

    @Test fun switchingToArrivalAndTomorrowAppliesOnlyOnConfirm() {
        show()
        compose.onNodeWithText("출발 시각").assertIsSelected()
        compose.onNodeWithText("오늘").assertIsSelected()
        tap("도착 시각")
        tap("내일")
        compose.onNodeWithText("도착 시각").assertIsSelected()
        compose.onNodeWithText("출발 시각").assertIsNotSelected()
        compose.onNodeWithText("11월 17일").assertExists()
        assertNull(applied)

        tap("적용")
        assertEquals(LocalDateTime.of(2026, 11, 17, 8, 30) to TimeMode.ARRIVE, applied)
        assertTrue(dismissed)
    }

    @Test fun savedArrivalOpensOnArrivalAndCanSwitchBackToDeparture() {
        show(LocalDateTime.of(2026, 11, 18, 19, 5), TimeMode.ARRIVE)
        compose.onNodeWithText("도착 시각").assertIsSelected()
        compose.onNodeWithText("모레").assertIsSelected()
        tap("출발 시각")
        tap("적용")
        assertEquals(LocalDateTime.of(2026, 11, 18, 19, 5) to TimeMode.DEPART, applied)
    }

    @Test fun nowClearsTimeAndReturnsToDeparture() {
        show(LocalDateTime.of(2026, 11, 17, 7, 0), TimeMode.ARRIVE)
        tap("지금")
        assertEquals(null to TimeMode.DEPART, applied)
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

    @Test fun scheduleLabelNamesDayAndDirection() {
        val labels = listOf(
            null to TimeMode.DEPART,
            now.withHour(9).withMinute(0) to TimeMode.DEPART,
            now.plusDays(1).withHour(9).withMinute(0) to TimeMode.ARRIVE,
            now.plusDays(2).withHour(21).withMinute(45) to TimeMode.DEPART,
            // 저장된 값이 없으면 도착 모드여도 "지금 출발"이다
            null to TimeMode.ARRIVE,
        )
        compose.setContent { SunSeatTheme { labels.forEach { (value, mode) -> Text(scheduleLabel(value, mode, now)) } } }
        listOf("오늘 09:00 출발", "내일 09:00 도착", "11월 18일 21:45 출발").forEach { compose.onNodeWithText(it).assertExists() }
        assertEquals(2, compose.onAllNodes(hasText("지금 출발")).fetchSemanticsNodes().size)
    }
}
