package com.sunseat.app.ui.icons

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.res.colorResource
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onRoot
import com.github.takahirom.roborazzi.captureRoboImage
import com.sunseat.app.R
import com.sunseat.app.ui.theme.Sizes
import com.sunseat.app.ui.theme.SunSeatTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(application = android.app.Application::class, sdk = [36])
class LauncherIconScreenshotTest {
    @get:Rule val compose = createComposeRule()

    private fun render(monochrome: Boolean, dark: Boolean) {
        compose.setContent {
            SunSeatTheme(darkTheme = dark) {
                IconPreview(monochrome, dark)
            }
        }
        compose.waitForIdle()
        compose.onRoot().captureRoboImage("src/test/screenshots/launcher_${if (monochrome) "mono" else "color"}_${if (dark) "dark" else "light"}.png")
    }

    @Composable private fun IconPreview(monochrome: Boolean, rounded: Boolean) {
        Box(Modifier.size(Sizes.mapHeight)
            .clip(if (rounded) MaterialTheme.shapes.extraLarge else CircleShape)
            .background(if (monochrome) SunSeatTheme.scheme.inverseSurface else colorResource(R.color.shade))) {
            Image(
                painterResource(if (monochrome) R.drawable.ic_launcher_monochrome else R.drawable.ic_launcher_foreground),
                "햇빛 좌석 앱 아이콘", Modifier.fillMaxSize(),
                colorFilter = if (monochrome) ColorFilter.tint(SunSeatTheme.scheme.inverseOnSurface) else null,
            )
        }
    }

    @Test fun colorLight() = render(false, false)
    @Test fun colorDark() = render(false, true)
    @Test fun monoLight() = render(true, false)
    @Test fun monoDark() = render(true, true)
}
