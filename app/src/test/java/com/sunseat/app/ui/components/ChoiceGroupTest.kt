package com.sunseat.app.ui.components

import android.app.Application
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.unit.Density
import com.sunseat.app.ui.theme.Spacing
import com.sunseat.app.ui.theme.SunSeatTheme
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class, sdk = [36], qualifiers = "w320dp-h640dp-xxhdpi")
class ChoiceGroupTest {
    @get:Rule val compose = createComposeRule()

    @Test fun modeChoicesStayReadableWhenSelectedAtLargeFont() {
        val labels = listOf("대중교통", "택시", "자가용")
        compose.setContent {
            val density = LocalDensity.current
            CompositionLocalProvider(LocalDensity provides Density(density.density, 1.5f)) {
                SunSeatTheme {
                    var selected by remember { mutableIntStateOf(0) }
                    SectionCard(Modifier.padding(Spacing.screen)) {
                        ChoiceGroup("기본 이동 수단", labels.map { ChoiceOption(it) }, selected, { selected = it })
                    }
                }
            }
        }
        val initialLayouts = mutableListOf<TextLayoutResult>()
        compose.onNodeWithText("대중교통").performSemanticsAction(SemanticsActions.GetTextLayoutResult) { it(initialLayouts) }
        assertEquals(1, initialLayouts.single().lineCount)
        listOf("대중교통", "택시", "자가용", "대중교통").forEach { label ->
            compose.onNodeWithText(label).performSemanticsAction(SemanticsActions.OnClick)
            compose.waitForIdle()
            compose.onNodeWithText(label).assertIsOn().assertIsDisplayed()
            val layouts = mutableListOf<TextLayoutResult>()
            compose.onNodeWithText(label).performSemanticsAction(SemanticsActions.GetTextLayoutResult) { it(layouts) }
            assertEquals("$label must stay on one line", 1, layouts.single().lineCount)
            assertFalse("$label must not be clipped", layouts.single().hasVisualOverflow)
        }
    }
}
