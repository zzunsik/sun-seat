package com.sunseat.app.ui.result

import android.app.Application
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.accessibility.enableAccessibilityChecks
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.click
import androidx.compose.ui.test.tryPerformAccessibilityChecks
import androidx.compose.ui.unit.Density
import com.github.takahirom.roborazzi.captureRoboImage
import com.sunseat.app.domain.SunGoal
import com.sunseat.app.ui.map.LocalMapRenderer
import com.sunseat.app.ui.map.MapRenderer
import com.sunseat.app.ui.theme.SunSeatTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(application = Application::class, sdk = [36], qualifiers = "w390dp-h844dp-xxhdpi")
class ResultTilesTest {
    @get:Rule val compose = createComposeRule()

    private fun render(dark: Boolean = false, night: Boolean = false, fontScale: Float = 1f, actions: ResultActions = ResultActions()) {
        val result = ResultFixtures.advisor(90.0, if (night) -15.0 else 30.0)
            .advise(ResultFixtures.transitRoute, ResultFixtures.departure, SunGoal.AVOID)
        compose.setContent {
            val density = LocalDensity.current
            CompositionLocalProvider(LocalDensity provides Density(density.density, fontScale), LocalMapRenderer provides MapRenderer.PREVIEW) {
                SunSeatTheme(darkTheme = dark) {
                    ResultScreen(ResultFixtures.transitRequest, ResultUiState(SunGoal.AVOID, ResultFixtures.departure, result), actions,
                        animateIn = false, zone = ResultFixtures.zone)
                }
            }
        }
        compose.waitForIdle()
    }

    @Test fun daytimeTilesShareARowAndKeepCalculatedValues() {
        render()
        val shade = compose.onNodeWithTag("result_shade_tile").fetchSemanticsNode().boundsInRoot
        val sun = compose.onNodeWithTag("result_sun_tile").fetchSemanticsNode().boundsInRoot
        assertEquals(shade.top, sun.top)
        assertTrue(shade.right < sun.left)
        compose.onNodeWithTag("result_shade_tile").assert(hasText("31분"))
        compose.onNodeWithTag("result_sun_tile").assert(hasText("24분"))
    }

    private fun smallLarge(dark: Boolean) {
        compose.enableAccessibilityChecks()
        render(dark = dark, fontScale = 1.5f)
        val shade = compose.onNodeWithTag("result_shade_tile").fetchSemanticsNode().boundsInRoot
        val sun = compose.onNodeWithTag("result_sun_tile").fetchSemanticsNode().boundsInRoot
        assertEquals(shade.left, sun.left)
        assertEquals(shade.right, sun.right)
        assertTrue(shade.bottom < sun.top)
        compose.onRoot().tryPerformAccessibilityChecks()
        compose.onRoot().captureRoboImage("src/test/screenshots/result_tiles_small_large_${if (dark) "dark" else "light"}.png")
    }

    @Config(qualifiers = "w320dp-h844dp-xxhdpi")
    @Test fun smallLargeLight() = smallLarge(false)

    @Config(qualifiers = "w320dp-h844dp-xxhdpi")
    @Test fun smallLargeDark() = smallLarge(true)

    @Test fun nightLightHasNoDurationTiles() {
        compose.enableAccessibilityChecks()
        render(night = true)
        compose.onNodeWithTag("result_shade_tile").assertDoesNotExist()
        compose.onNodeWithTag("result_sun_tile").assertDoesNotExist()
        compose.onNodeWithText("이동 중에는 해가 없어요").assertIsDisplayed()
        compose.onRoot().tryPerformAccessibilityChecks()
        compose.onRoot().captureRoboImage("src/test/screenshots/result_no_sun_light.png")
    }

    @Test fun shareAndSimulationRemainReachableByTouch() {
        var shares = 0
        var simulations = 0
        render(actions = ResultActions(onShare = { shares++ }, onSimulation = { simulations++ }))
        compose.onNodeWithText("공유하기").performTouchInput { click() }
        compose.onNodeWithText("햇빛 따라가기").performScrollTo().performTouchInput { click() }
        assertEquals(1, shares)
        assertEquals(1, simulations)
    }

    @Config(qualifiers = "w320dp-h844dp-xxhdpi")
    @Test fun longDurationsStackWithoutClipping() {
        compose.setContent {
            val density = LocalDensity.current
            CompositionLocalProvider(LocalDensity provides Density(density.density, 1.5f)) {
                SunSeatTheme { ExposureTiles("12시간 34분", "10시간 56분") }
            }
        }
        val shade = compose.onNodeWithTag("result_shade_tile").fetchSemanticsNode().boundsInRoot
        val sun = compose.onNodeWithTag("result_sun_tile").fetchSemanticsNode().boundsInRoot
        assertTrue(shade.bottom < sun.top)
        listOf("12시간 34분", "10시간 56분").forEach {
            val text = compose.onNodeWithText(it, useUnmergedTree = true).fetchSemanticsNode().boundsInRoot
            assertTrue(text.left >= shade.left && text.right <= shade.right)
        }
    }
}
