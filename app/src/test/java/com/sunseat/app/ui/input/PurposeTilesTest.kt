package com.sunseat.app.ui.input

import android.app.Application
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.accessibility.enableAccessibilityChecks
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.unit.Density
import com.github.takahirom.roborazzi.captureRoboImage
import com.sunseat.app.domain.SunGoal
import com.sunseat.app.ui.assertNoTextUnderOverlays
import com.sunseat.app.ui.map.LocalMapRenderer
import com.sunseat.app.ui.map.MapRenderer
import com.sunseat.app.ui.theme.SunSeatTheme
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.time.LocalDateTime

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(application = Application::class, sdk = [36], qualifiers = "w390dp-h844dp-xxhdpi")
class PurposeTilesTest {
    @get:Rule val compose = createComposeRule()

    private fun matrix(fontScale: Float, stacked: Boolean, dark: Boolean = false) {
        compose.enableAccessibilityChecks()
        val goal = mutableStateOf(SunGoal.AVOID)
        val changes = mutableListOf<SunGoal>()
        compose.setContent {
            CompositionLocalProvider(LocalMapRenderer provides MapRenderer.PREVIEW,
                LocalDensity provides Density(LocalDensity.current.density, fontScale)) {
                SunSeatTheme(darkTheme = dark) {
                    InputScreen(InputUiState(goal = goal.value), InputActions(setGoal = { changes += it; goal.value = it }),
                        now = LocalDateTime.of(2026, 11, 16, 8, 0))
                }
            }
        }
        compose.waitForIdle()
        val avoid = compose.onNodeWithText("햇빛 피하기")
        val seek = compose.onNodeWithText("햇빛 받기")
        val radio = SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.RadioButton)
        avoid.assert(radio).assertIsSelected()
        seek.assert(radio).assertIsNotSelected()
        compose.onNodeWithContentDescription("햇빛 목적").assertExists()
        val first = avoid.fetchSemanticsNode().boundsInRoot
        val second = seek.fetchSemanticsNode().boundsInRoot
        if (stacked) assertTrue(first.bottom <= second.top) else assertEquals(first.top, second.top, 0.5f)
        val density = compose.onRoot().fetchSemanticsNode().layoutInfo.density.density
        assertTrue(first.height / density >= 56f)
        assertTrue(first.width / density >= 48f)
        listOf("햇빛 피하기", "햇빛 받기").forEach { label ->
            val layouts = mutableListOf<TextLayoutResult>()
            compose.onNodeWithText(label, useUnmergedTree = true)
                .performSemanticsAction(SemanticsActions.GetTextLayoutResult) { it(layouts) }
            assertTrue("full label: $label", layouts.isNotEmpty() && layouts.none { it.hasVisualOverflow })
        }
        compose.assertNoTextUnderOverlays()
        compose.onRoot().tryPerformAccessibilityChecks()
        // 실제 터치: 같은 목적 재선택 → 다른 목적 → 같은 목적. 콜백은 한 번뿐이다.
        avoid.performTouchInput { click() }
        seek.performTouchInput { click() }
        seek.assertIsSelected()
        avoid.assertIsNotSelected()
        seek.performTouchInput { click() }
        assertEquals(listOf(SunGoal.SEEK), changes)
        compose.onRoot().captureRoboImage("src/test/screenshots/input_purpose_" +
            (if (stacked) "stacked" else "row") + "_" + fontScale + "_" + (if (dark) "dark" else "light") + ".png")
    }

    @Test fun regular() = matrix(1f, false)
    @Test fun large() = matrix(1.5f, true)
    @Test @Config(qualifiers = "w320dp-h844dp-xxhdpi") fun small() = matrix(1f, true)
    @Test @Config(qualifiers = "w320dp-h844dp-xxhdpi") fun smallLargeDark() = matrix(1.5f, true, true)
}
