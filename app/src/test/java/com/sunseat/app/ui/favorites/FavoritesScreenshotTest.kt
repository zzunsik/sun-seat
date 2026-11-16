package com.sunseat.app.ui.favorites

import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.junit4.accessibility.enableAccessibilityChecks
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.tryPerformAccessibilityChecks
import com.github.takahirom.roborazzi.captureRoboImage
import com.sunseat.app.data.favorites.*
import com.sunseat.app.ui.input.*
import com.sunseat.app.ui.theme.SunSeatTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(application = android.app.Application::class, sdk = [36], qualifiers = "w390dp-h1000dp-xxhdpi")
class FavoritesScreenshotTest {
    @get:Rule val compose = createComposeRule()
    private val home = Favorite("home", FavoriteKind.HOME, "", "서울역", "서울 중구 한강대로", 37.55, 126.97, 1)
    private val cafe = Favorite("cafe", FavoriteKind.CUSTOM, "자주 가는 곳", "카페", "서울 중구 세종대로", 37.56, 126.98, 2)
    private fun render(dark: Boolean, empty: Boolean = false) {
        compose.setContent { SunSeatTheme(darkTheme = dark) { FavoritesScreen(FavoritesUiState(if (empty) emptyList() else listOf(home, cafe)), FavoritesActions()) } }
    }
    @Test fun light() { render(false); compose.onRoot().captureRoboImage("src/test/screenshots/favorites_light.png") }
    @Test fun dark() { render(true); compose.onRoot().captureRoboImage("src/test/screenshots/favorites_dark.png") }
    @Test fun empty() { render(false, true); compose.onRoot().captureRoboImage("src/test/screenshots/favorites_empty_light.png") }
    @Test fun searchLight() {
        compose.setContent { SunSeatTheme(darkTheme = false) { InputScreen(InputUiState(favorites = listOf(home, cafe), search = SearchState(EndpointField.DESTINATION, query = "카페", results = listOf(cafe.asPlace()))), InputActions()) } }
        compose.onRoot().captureRoboImage("src/test/screenshots/favorites_search_light.png")
    }
    @Test fun searchDark() {
        compose.setContent { SunSeatTheme(darkTheme = true) { InputScreen(InputUiState(favorites = listOf(home, cafe), search = SearchState(EndpointField.ORIGIN)), InputActions()) } }
        compose.onRoot().captureRoboImage("src/test/screenshots/favorites_search_dark.png")
    }
    @Test fun accessibility() { compose.enableAccessibilityChecks(); render(false); compose.onRoot().tryPerformAccessibilityChecks() }
    @Test fun searchAccessibility() {
        compose.enableAccessibilityChecks()
        compose.setContent { SunSeatTheme(darkTheme = true) { InputScreen(InputUiState(favorites = listOf(home), search = SearchState(EndpointField.DESTINATION, query = "역", results = listOf(home.asPlace()))), InputActions()) } }
        compose.onRoot().tryPerformAccessibilityChecks()
    }
}
