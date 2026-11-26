package com.sunseat.app.ui.theme

import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Immutable
import androidx.compose.ui.graphics.Color

/** 해·그늘 의미 색. docs/DESIGN.md 2.1 */
@Immutable
data class SunSeatColors(
    val sun: Color,
    val onSun: Color,
    val sunContainer: Color,
    val onSunContainer: Color,
    val shade: Color,
    val shadeContainer: Color,
    val onShadeContainer: Color,
    val skyTop: Color,
    val skyMiddle: Color,
    val skyBottom: Color,
    val ground: Color,
    val groundFar: Color,
)

internal val LightSunSeatColors = SunSeatColors(
    sun = Color(0xFFF5A800),
    onSun = Color(0xFF1E1602),
    sunContainer = Color(0xFFFFEDC2),
    onSunContainer = Color(0xFF5C3D00),
    shade = Color(0xFF3D4FB8),
    shadeContainer = Color(0xFFE0E4FA),
    onShadeContainer = Color(0xFF1F2B7A),
    skyTop = Color(0xFF4E95DB),
    skyMiddle = Color(0xFFA9D2F2),
    skyBottom = Color(0xFFFFE3A3),
    ground = Color(0xFF4E8C55),
    groundFar = Color(0xFF7FB36A),
)

internal val DarkSunSeatColors = SunSeatColors(
    sun = Color(0xFFFFC550),
    onSun = Color(0xFF1E1602),
    sunContainer = Color(0xFF463710),
    onSunContainer = Color(0xFFFFE3A6),
    shade = Color(0xFFA3AEFF),
    shadeContainer = Color(0xFF252E66),
    onShadeContainer = Color(0xFFDDE1FF),
    skyTop = Color(0xFF1B2D48),
    skyMiddle = Color(0xFF2C4466),
    skyBottom = Color(0xFF6B5A3A),
    ground = Color(0xFF253B2C),
    groundFar = Color(0xFF34503A),
)

internal val LightScheme = lightColorScheme(
    primary = Color(0xFF0B6E75),
    onPrimary = Color(0xFFFFFFFF),
    primaryContainer = Color(0xFFC9EFE8),
    onPrimaryContainer = Color(0xFF00403A),
    secondary = Color(0xFF586663),
    onSecondary = Color(0xFFFFFFFF),
    secondaryContainer = Color(0xFFE6EEEC),
    onSecondaryContainer = Color(0xFF101917),
    tertiary = Color(0xFF7A5200),
    onTertiary = Color(0xFFFFFFFF),
    tertiaryContainer = LightSunSeatColors.sunContainer,
    onTertiaryContainer = LightSunSeatColors.onSunContainer,
    background = Color(0xFFFFFFFF),
    onBackground = Color(0xFF101917),
    surface = Color(0xFFFFFFFF),
    onSurface = Color(0xFF101917),
    surfaceVariant = Color(0xFFE6EEEC),
    onSurfaceVariant = Color(0xFF586663),
    surfaceContainerLowest = Color(0xFFFFFFFF),
    surfaceContainerLow = Color(0xFFF7FAF9),
    surfaceContainer = Color(0xFFF2F6F5),
    surfaceContainerHigh = Color(0xFFE6EEEC),
    surfaceContainerHighest = Color(0xFFDCE5E2),
    inverseSurface = Color(0xFF101917),
    inverseOnSurface = Color(0xFFF2F6F5),
    outline = Color(0xFF717E7B),
    outlineVariant = Color(0xFFDDE5E3),
    error = Color(0xFFB3261E),
    onError = Color(0xFFFFFFFF),
)

internal val DarkScheme = darkColorScheme(
    primary = Color(0xFF5FC6CC),
    onPrimary = Color(0xFF00332D),
    primaryContainer = Color(0xFF12453E),
    onPrimaryContainer = Color(0xFFBFF3EA),
    secondary = Color(0xFF9FB0AC),
    onSecondary = Color(0xFF0B1211),
    secondaryContainer = Color(0xFF1F2C29),
    onSecondaryContainer = Color(0xFFEDF5F3),
    tertiary = DarkSunSeatColors.sun,
    onTertiary = Color(0xFF2A1E00),
    tertiaryContainer = DarkSunSeatColors.sunContainer,
    onTertiaryContainer = DarkSunSeatColors.onSunContainer,
    background = Color(0xFF0B1211),
    onBackground = Color(0xFFEDF5F3),
    surface = Color(0xFF0B1211),
    onSurface = Color(0xFFEDF5F3),
    surfaceVariant = Color(0xFF1F2C29),
    onSurfaceVariant = Color(0xFF9FB0AC),
    surfaceContainerLowest = Color(0xFF070C0B),
    surfaceContainerLow = Color(0xFF101816),
    surfaceContainer = Color(0xFF16201E),
    surfaceContainerHigh = Color(0xFF1F2C29),
    surfaceContainerHighest = Color(0xFF283532),
    inverseSurface = Color(0xFFEDF5F3),
    inverseOnSurface = Color(0xFF0B1211),
    outline = Color(0xFF7A8B87),
    outlineVariant = Color(0xFF2A3835),
    error = Color(0xFFF2B8B5),
    onError = Color(0xFF601410),
)
