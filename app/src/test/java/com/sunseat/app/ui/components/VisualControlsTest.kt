package com.sunseat.app.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.junit4.accessibility.enableAccessibilityChecks
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import com.github.takahirom.roborazzi.captureRoboImage
import com.sunseat.app.ui.input.NeutralFilterChip
import com.sunseat.app.ui.theme.Spacing
import com.sunseat.app.ui.theme.SunSeatTheme
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(application = android.app.Application::class, sdk = [36], qualifiers = "w320dp-h900dp-xxhdpi")
class VisualControlsTest {
    @get:Rule val compose = createComposeRule()
    private fun render(dark: Boolean) {
        compose.enableAccessibilityChecks()
        compose.setContent {
            val density = LocalDensity.current
            CompositionLocalProvider(LocalDensity provides Density(density.density, 1.5f)) {
                SunSeatTheme(darkTheme = dark) {
                    var selected by remember { mutableIntStateOf(0) }
                    var value by remember { mutableFloatStateOf(0.5f) }
                    Surface(color = SunSeatTheme.scheme.surface) {
                        Column(Modifier.padding(Spacing.l), verticalArrangement = Arrangement.spacedBy(Spacing.s)) {
                            PrimaryButton({}, Modifier.testTag("primary")) { Text("길찾기") }
                            ChoiceGroup("화면 모드", listOf(ChoiceOption("시스템"), ChoiceOption("밝게"), ChoiceOption("어둡게")), selected, { selected = it })
                            NeutralFilterChip(true, "지금 출발") {}
                            CategoryTabs(listOf(CategoryTab("전체", 12, "전체 12개"), CategoryTab("대중교통", 9, "대중교통 9개"), CategoryTab("자가용", 3, "자가용 3개")), selected, onSelect = { selected = it })
                            SectionCard(title = "햇빛과 그늘") {
                                ExposurePill(Exposure.Sun, "햇빛 12분")
                                ExposurePill(Exposure.Shade, "그늘 18분")
                            }
                            SunSeatSlider(value, { value = it }, Modifier.fillMaxWidth().testTag("slider"))
                        }
                    }
                }
            }
        }
    }
    @Test fun lightControls() { render(false); compose.onRoot().tryPerformAccessibilityChecks(); compose.onRoot().captureRoboImage("src/test/screenshots/visual_controls_light.png") }
    @Test fun darkControls() { render(true); compose.onRoot().tryPerformAccessibilityChecks(); compose.onRoot().captureRoboImage("src/test/screenshots/visual_controls_dark.png") }
    @Test fun selectedTabsRemainVisibleAndLabelsUnbrokenAfterTouch() {
        render(false)
        compose.onNodeWithTag("primary").assertHeightIsAtLeast(48.dp)
        compose.onNodeWithTag("slider").assertHeightIsAtLeast(48.dp)
        listOf(2, 1, 0).forEach { index ->
            compose.onNodeWithTag("category_tab_$index").performScrollTo().performTouchInput { click() }
            compose.onNodeWithTag("category_tab_$index").assertIsSelected().assertIsDisplayed().assertHeightIsAtLeast(48.dp)
            val label = listOf("전체", "대중교통", "자가용")[index]
            val layouts = mutableListOf<TextLayoutResult>()
            compose.onNodeWithText(label, useUnmergedTree = true).performSemanticsAction(SemanticsActions.GetTextLayoutResult) { it(layouts) }
            assertEquals(1, layouts.single().lineCount)
            assertFalse(layouts.single().isLineEllipsized(0))
            // Native text can report didOverflowWidth for subpixel intrinsic rounding.
            // Verify the actual glyph line fits its rendered bounds instead.
            assertTrue(layouts.single().getLineRight(0) <= layouts.single().size.width)
        }
        compose.onNodeWithTag("slider").performTouchInput { swipe(center, androidx.compose.ui.geometry.Offset(width * 0.75f, centerY)) }
        compose.onNodeWithTag("slider").performTouchInput { click(androidx.compose.ui.geometry.Offset(width * 0.25f, centerY)) }
        val progress = compose.onNodeWithTag("slider").fetchSemanticsNode().config[androidx.compose.ui.semantics.SemanticsProperties.ProgressBarRangeInfo]
        assertTrue("Touch after drag must seek to the tapped quarter", progress.current in 0.2f..0.3f)
    }
}
