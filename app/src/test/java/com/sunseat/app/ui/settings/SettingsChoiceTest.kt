package com.sunseat.app.ui.settings

import android.app.Application
import androidx.compose.runtime.*
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.height
import com.sunseat.app.data.settings.Settings
import com.sunseat.app.data.settings.ThemeMode
import com.sunseat.app.domain.BenchPreference
import com.sunseat.app.domain.SunGoal
import com.sunseat.app.domain.TravelMode
import com.sunseat.app.ui.theme.Sizes
import com.sunseat.app.ui.theme.SunSeatTheme
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** #104: 작은 화면·큰 글자에서도 설정 선택지가 가로로 숨지 않고 모두 보이며 고를 수 있다. */
@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class, sdk = [36], qualifiers = "w320dp-h1600dp-xxhdpi")
class SettingsChoiceTest {
    @get:Rule val compose = createComposeRule()

    private val choices = listOf("시스템", "밝게", "어둡게", "피하기", "받기", "대중교통", "택시", "자가용")

    private fun render(fontScale: Float): () -> Settings {
        var state by mutableStateOf(Settings())
        compose.setContent {
            val density = LocalDensity.current
            CompositionLocalProvider(LocalDensity provides Density(density.density, fontScale)) {
                SunSeatTheme { SettingsScreen(state, { transform -> state = transform(state) }, {}, {}) }
            }
        }
        return { state }
    }

    private fun assertNoHorizontalScroll() {
        val scrollers = compose.onAllNodes(SemanticsMatcher.keyIsDefined(SemanticsProperties.HorizontalScrollAxisRange)).fetchSemanticsNodes()
        assertTrue("settings must not scroll sideways", scrollers.isEmpty())
    }

    private fun assertFullyVisible(label: String) {
        val root = compose.onRoot().fetchSemanticsNode().boundsInRoot
        val bounds = compose.onNodeWithText(label).fetchSemanticsNode().boundsInRoot
        assertTrue("$label must be fully inside the screen: $bounds / $root", bounds.left >= root.left && bounds.right <= root.right)
        val layouts = mutableListOf<TextLayoutResult>()
        compose.onNodeWithText(label).performSemanticsAction(SemanticsActions.GetTextLayoutResult) { it(layouts) }
        assertEquals("$label must stay on one line", 1, layouts.single().lineCount)
        // A stretched one-line paragraph reports hasVisualOverflow, so compare the glyph run with the laid-out width instead.
        val layout = layouts.single()
        assertTrue("$label must not be clipped", layout.multiParagraph.intrinsics.maxIntrinsicWidth <= layout.size.width + 1f)
    }

    @Test fun smallLargeFontShowsEveryChoiceWithoutSideScroll() {
        val state = render(1.5f)
        assertNoHorizontalScroll()
        choices.forEach(::assertFullyVisible)
        compose.onNodeWithText("시스템").assertIsSelected()
        compose.onNodeWithText("어둡게").assertIsNotSelected().performClick()
        compose.onNodeWithText("자가용").performClick()
        compose.onNodeWithText("받기").performClick()
        compose.runOnIdle {
            assertEquals(ThemeMode.DARK, state().theme)
            assertEquals(TravelMode.CAR, state().mode)
            assertEquals(SunGoal.SEEK, state().goal)
        }
        compose.onNodeWithText("어둡게").assertIsSelected()
        compose.onNodeWithText("자가용").assertIsSelected()
        compose.onNodeWithText("대중교통").assertIsNotSelected().performClick()
        compose.onNodeWithText("정면 햇빛").assertIsNotSelected().performClick()
        compose.runOnIdle {
            assertEquals(TravelMode.TRANSIT, state().mode)
            assertEquals(BenchPreference.AVOID_FRONT, state().benchPreference)
        }
        compose.onNodeWithText("정면 햇빛").assertIsSelected()
        choices.forEach(::assertFullyVisible)
        assertNoHorizontalScroll()
    }

    @Test fun smallLargeFontUsesOneVerticalRadioListForEverySetting() {
        render(1.5f)
        // 한 화면에서 연결형 버튼과 라디오 목록이 섞이지 않는다: 들어가는 두 개짜리 햇빛 선호도 세로로 놓인다.
        listOf(listOf("시스템", "밝게", "어둡게"), listOf("피하기", "받기"), listOf("대중교통", "택시", "자가용")).forEach { group ->
            val tops = group.map { compose.onNodeWithText(it).fetchSemanticsNode().boundsInRoot.top }
            assertEquals("$group must stack vertically", group.size, tops.distinct().size)
        }
        (choices + listOf("등 뒤 햇빛", "정면 햇빛", "전체 햇빛")).forEach { label ->
            val row = compose.onNodeWithText(label)
            row.assert(SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.RadioButton))
            row.assert(SemanticsMatcher.keyIsDefined(SemanticsProperties.Selected))
            val height = row.getUnclippedBoundsInRoot().height
            assertTrue("$label row must be at least ${Sizes.listRowInteractive}: $height", height.value >= Sizes.listRowInteractive.value - 0.01f)
        }
        listOf("화면 모드", "햇빛 선호", "기본 이동 수단").forEach { compose.onNodeWithText(it).assert(SemanticsMatcher.keyIsDefined(SemanticsProperties.Heading)) }
    }

    @Config(qualifiers = "w390dp-h1600dp-xxhdpi")
    @Test fun roomyWidthKeepsConnectedButtonsOnOneRow() {
        render(1f)
        assertNoHorizontalScroll()
        listOf(listOf("시스템", "밝게", "어둡게"), listOf("피하기", "받기"), listOf("대중교통", "택시", "자가용")).forEach { group ->
            val tops = group.map { compose.onNodeWithText(it).fetchSemanticsNode().boundsInRoot.top }
            assertEquals("$group stay in one connected row", 1, tops.distinct().size)
            group.forEach(::assertFullyVisible)
        }
        compose.onNodeWithText("대중교통").assertIsOn()
    }
}
