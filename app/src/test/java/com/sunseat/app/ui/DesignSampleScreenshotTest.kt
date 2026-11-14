package com.sunseat.app.ui

import androidx.compose.ui.test.junit4.accessibility.enableAccessibilityChecks
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.tryPerformAccessibilityChecks
import com.github.takahirom.roborazzi.captureRoboImage
import com.sunseat.app.ui.sample.DesignSampleScreen
import com.sunseat.app.ui.sample.SampleSettings
import com.sunseat.app.ui.theme.SunSeatTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * 디자인 샘플 화면의 스크린샷(밝은·어두운, 유리 켜기·끄기)과 접근성 검사.
 * 기준 이미지: ./gradlew recordRoborazziDebug, 비교: ./gradlew verifyRoborazziDebug
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [36], qualifiers = "w390dp-h844dp-xxhdpi")
class DesignSampleScreenshotTest {

    @get:Rule
    val compose = createComposeRule()

    private fun render(dark: Boolean, glass: Boolean) {
        compose.setContent {
            SunSeatTheme(darkTheme = dark, glassEnabled = glass) {
                DesignSampleScreen(
                    settings = SampleSettings(glass = glass),
                    onSettingsChange = {},
                    animateIn = false,
                )
            }
        }
    }

    @Test
    fun light() {
        render(dark = false, glass = false)
        compose.onRoot().captureRoboImage("src/test/screenshots/design_sample_light.png")
    }

    @Test
    fun dark() {
        render(dark = true, glass = false)
        compose.onRoot().captureRoboImage("src/test/screenshots/design_sample_dark.png")
    }

    @Test
    fun lightGlass() {
        render(dark = false, glass = true)
        compose.onRoot().captureRoboImage("src/test/screenshots/design_sample_light_glass.png")
    }

    @Test
    fun darkCompareRight() {
        render(dark = true, glass = false)
        compose.onNode(hasText("오른쪽") and hasClickAction()).performScrollTo().performClick()
        compose.waitForIdle()
        compose.onRoot().captureRoboImage("src/test/screenshots/design_sample_dark_compare_right.png")
    }

    @Test
    fun accessibilityLight() {
        compose.enableAccessibilityChecks()
        render(dark = false, glass = false)
        compose.onRoot().tryPerformAccessibilityChecks()
    }

    @Test
    fun accessibilityDark() {
        compose.enableAccessibilityChecks()
        render(dark = true, glass = false)
        compose.onRoot().tryPerformAccessibilityChecks()
    }
}
