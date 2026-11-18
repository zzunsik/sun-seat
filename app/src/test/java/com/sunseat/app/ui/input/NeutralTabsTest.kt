package com.sunseat.app.ui.input

import android.app.Application
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.unit.Density
import com.sunseat.app.ui.theme.SunSeatTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class, sdk = [36], qualifiers = "w320dp-h640dp-xxhdpi")
class NeutralTabsTest {
    @get:Rule val compose = createComposeRule()
    private val labels = listOf("대중교통", "택시", "자가용")

    private fun show(initial: Int = 0) {
        compose.setContent {
            val density = LocalDensity.current
            CompositionLocalProvider(LocalDensity provides Density(density.density, 1.5f)) {
                SunSeatTheme {
                    val selected = remember { mutableIntStateOf(initial) }
                    NeutralTabs(labels, selected.intValue) { selected.intValue = it }
                }
            }
        }
        compose.waitForIdle()
    }

    private fun assertUnbroken(label: String) {
        val results = mutableListOf<TextLayoutResult>()
        compose.onNodeWithText(label).performSemanticsAction(SemanticsActions.GetTextLayoutResult) { it(results) }
        assertEquals(1, results.single().lineCount)
        assertFalse(results.single().hasVisualOverflow)
    }

    @Test fun checkedTransitLabelNeverWrapsOrEllipsizes() {
        show()
        compose.onNodeWithText("대중교통").assertIsSelected().assertIsDisplayed()
        assertUnbroken("대중교통")
    }

    @Test fun eachSelectedModeIsBroughtIntoViewAndStaysUnbroken() {
        show(2)
        compose.onNodeWithText("자가용").assertIsSelected().assertIsDisplayed()
        assertUnbroken("자가용")
        labels.forEach { label ->
            compose.onNodeWithText(label).performSemanticsAction(SemanticsActions.OnClick)
            compose.waitForIdle()
            compose.onNodeWithText(label).assertIsSelected().assertIsDisplayed()
            assertUnbroken(label)
        }
    }
}
