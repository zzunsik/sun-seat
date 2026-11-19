package com.sunseat.app.ui

import android.app.Application
import android.content.Context
import androidx.test.core.app.ApplicationProvider
import java.time.Duration
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class, sdk = [36])
class FormatTest {
    private val context: Context = ApplicationProvider.getApplicationContext()
    private fun minutes(seconds: Long, zeroOk: Boolean = false) = formatDuration(context, Duration.ofSeconds(seconds), zeroOk)

    @Test fun durationRoundsDownAndNeverShowsZeroUnlessAllowed() {
        assertEquals("1분", minutes(0))
        assertEquals("0분", minutes(0, zeroOk = true))
        assertEquals("0분", minutes(59, zeroOk = true))
        assertEquals("0분", minutes(-120, zeroOk = true))
        assertEquals("1분", minutes(-120))
        assertEquals("59분", minutes(59 * 60 + 59))
    }

    @Test fun durationSwitchesToHoursAtSixtyMinutes() {
        assertEquals("1시간", minutes(60 * 60))
        assertEquals("1시간 1분", minutes(61 * 60))
        assertEquals("2시간", minutes(120 * 60 + 30))
        assertEquals("25시간 5분", minutes((25 * 60 + 5) * 60L))
    }

    @Test fun wonUsesKoreanGrouping() {
        assertEquals("0원", formatWon(context, 0))
        assertEquals("999원", formatWon(context, 999))
        assertEquals("1,500원", formatWon(context, 1500))
        assertEquals("1,234,567원", formatWon(context, 1_234_567))
    }
}
