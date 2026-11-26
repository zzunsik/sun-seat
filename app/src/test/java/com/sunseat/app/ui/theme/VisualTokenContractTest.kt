package com.sunseat.app.ui.theme

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class VisualTokenContractTest {
    @Test fun routeAndSemanticAliasesHaveOneSource() {
        listOf(LightScheme to LightRouteColors, DarkScheme to DarkRouteColors).forEach { (scheme, route) ->
            assertEquals(scheme.primary, route.transit)
            assertEquals(scheme.primary, route.selectionIndicator)
            assertEquals(scheme.surfaceContainerHigh, route.selectionContainer)
        }
        listOf(LightScheme to LightSunSeatColors, DarkScheme to DarkSunSeatColors).forEach { (scheme, semantic) ->
            assertEquals(semantic.sunContainer, scheme.tertiaryContainer)
            assertEquals(semantic.onSunContainer, scheme.onTertiaryContainer)
        }
        assertEquals(Color(0xFF0B6E75), MapRouteColors.transit)
        assertEquals(Color(0xFF4A5666), MapRouteColors.walk)
        assertEquals(Color(0xFF7A8696), MapRouteColors.alternate)
    }

    @Test fun textAndSelectionContrastHoldsOnEverySupportingSurface() {
        listOf(LightScheme, DarkScheme).forEach { s ->
            listOf(s.surface, s.surfaceContainer, s.surfaceContainerHigh, s.surfaceContainerHighest).forEach { bg ->
                assertContrast(s.onSurface, bg, 4.5)
                assertContrast(s.onSurfaceVariant, bg, 4.5)
                assertContrast(s.primary, bg, 3.0)
                assertContrast(s.outline, bg, 3.0)
            }
            assertContrast(s.onPrimary, s.primary, 4.5)
            assertContrast(s.onPrimaryContainer, s.primaryContainer, 4.5)
        }
        listOf(LightSunSeatColors, DarkSunSeatColors).forEach { s ->
            assertContrast(s.onSun, s.sun, 4.5)
            assertContrast(s.onSunContainer, s.sunContainer, 4.5)
            assertContrast(s.onShadeContainer, s.shadeContainer, 4.5)
        }
    }

    private fun assertContrast(fg: Color, bg: Color, minimum: Double) {
        val a = fg.luminance().toDouble()
        val b = bg.luminance().toDouble()
        assertTrue("$fg on $bg requires $minimum", (maxOf(a, b) + 0.05) / (minOf(a, b) + 0.05) >= minimum)
    }
}
