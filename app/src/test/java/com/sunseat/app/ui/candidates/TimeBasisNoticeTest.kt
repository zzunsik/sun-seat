package com.sunseat.app.ui.candidates

import android.app.Application
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.unit.Density
import com.sunseat.app.ui.map.LocalMapRenderer
import com.sunseat.app.ui.map.MapRenderer
import com.sunseat.app.ui.theme.Spacing
import com.sunseat.app.ui.theme.SunSeatTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * #134: 출발 시각을 고르면 목록 맨 위에 나오는 '경로는 지금 기준 · 햇빛은 … 기준' 줄은
 * 후보 행과 같은 화면 좌우 여백에서 시작한다(화면 끝에 붙지 않는다).
 */
@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class, sdk = [36], qualifiers = "w390dp-h844dp-xxhdpi")
@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
class TimeBasisNoticeTest {
    @get:Rule val compose = createComposeRule(effectContext = object : androidx.compose.ui.MotionDurationScale { override val scaleFactor = 0f })

    private fun assertAlignedWithCandidateRows(fontScale: Float = 1f) {
        val request = CandidatesFixtures.transitRequest.copy(departure = java.time.LocalDateTime.of(2026, 11, 16, 10, 33))
        val state = CandidatesState.Options(CandidatesFixtures.options.toUi(java.time.LocalDateTime.of(2026, 11, 15, 9, 0)))
        compose.setContent {
            val density = androidx.compose.ui.platform.LocalDensity.current
            CompositionLocalProvider(LocalMapRenderer provides MapRenderer.PREVIEW, androidx.compose.ui.platform.LocalDensity provides Density(density.density, fontScale)) {
                SunSeatTheme { CandidatesScreen(request, state, CandidatesActions(), animateLoader = false) }
            }
        }
        compose.mainClock.advanceTimeBy(5000)
        compose.waitForIdle()
        val notice = compose.onNodeWithText("경로는 지금 기준", substring = true, useUnmergedTree = true).getUnclippedBoundsInRoot()
        val rowStart = compose.onNodeWithTag("candidate_time_${state.items.first().id}", useUnmergedTree = true).getUnclippedBoundsInRoot().left
        val screen = compose.onNodeWithTag("candidate_list").getUnclippedBoundsInRoot()
        assertEquals("notice must start at the candidate row start", rowStart.value, notice.left.value, 0.5f)
        assertEquals("notice must keep the screen margin", (screen.left + Spacing.screen).value, notice.left.value, 0.5f)
        assertEquals("notice must keep the right screen margin", (screen.right - Spacing.screen).value, notice.right.value, 0.5f)
    }

    @Test fun noticeKeepsScreenMargin() = assertAlignedWithCandidateRows()

    @Test @Config(qualifiers = "w320dp-h640dp-xxhdpi")
    fun noticeKeepsScreenMarginSmallScreenLargeText() = assertAlignedWithCandidateRows(1.5f)
}
