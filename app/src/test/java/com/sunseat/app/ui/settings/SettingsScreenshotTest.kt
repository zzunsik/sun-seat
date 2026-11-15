package com.sunseat.app.ui.settings

import androidx.compose.runtime.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.junit4.accessibility.enableAccessibilityChecks
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.tryPerformAccessibilityChecks
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.github.takahirom.roborazzi.captureRoboImage
import com.sunseat.app.data.settings.Settings
import com.sunseat.app.data.settings.ThemeMode
import com.sunseat.app.ui.theme.SunSeatTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(application = android.app.Application::class, sdk = [36], qualifiers = "w390dp-h1000dp-xxhdpi")
class SettingsScreenshotTest {
    @get:Rule val compose = createComposeRule()
    private fun render(dark: Boolean, error: Boolean = false) {
        compose.setContent { SunSeatTheme(darkTheme = dark) { SettingsScreen(Settings(theme = if (dark) ThemeMode.DARK else ThemeMode.LIGHT), {}, {}, {}, saveError = error) } }
    }
    @Test fun light() { render(false); compose.onRoot().captureRoboImage("src/test/screenshots/settings_light.png") }
    @Test fun dark() { render(true); compose.onRoot().captureRoboImage("src/test/screenshots/settings_dark.png") }
    @Test fun saveError() { render(false, true); compose.onRoot().captureRoboImage("src/test/screenshots/settings_error_light.png") }
    @Test fun licensesLight() {
        compose.setContent { SunSeatTheme(darkTheme = false) { LicensesScreen({}) } }
        compose.onRoot().captureRoboImage("src/test/screenshots/licenses_light.png")
    }
    @Test fun licensesDark() {
        compose.setContent { SunSeatTheme(darkTheme = true) { LicensesScreen({}) } }
        compose.onRoot().captureRoboImage("src/test/screenshots/licenses_dark.png")
    }
    @Test fun accessibilityLight() { compose.enableAccessibilityChecks(); render(false); compose.onRoot().tryPerformAccessibilityChecks() }
    @Test fun accessibilityDark() { compose.enableAccessibilityChecks(); render(true); compose.onRoot().tryPerformAccessibilityChecks() }
    @Test fun themeChoiceUpdatesImmediately() {
        var state by mutableStateOf(Settings())
        compose.setContent { SunSeatTheme(darkTheme = state.theme == ThemeMode.DARK) { SettingsScreen(state, { transform -> state = transform(state) }, {}, {}) } }
        compose.onNodeWithText("어둡게").performClick()
        compose.runOnIdle { assertEquals(ThemeMode.DARK, state.theme) }
    }
}
