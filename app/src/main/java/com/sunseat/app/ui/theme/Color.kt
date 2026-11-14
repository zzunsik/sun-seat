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
    /** 유리 캡슐 면에 까는 반투명 층 */
    val glassSurface: Color,
)

internal val LightSunSeatColors = SunSeatColors(
    sun = Color(0xFFF2B01E),
    onSun = Color(0xFF1E1602),
    sunContainer = Color(0xFFFFF1C7),
    onSunContainer = Color(0xFF6B4700),
    shade = Color(0xFF2E4868),
    shadeContainer = Color(0xFFDCE5F0),
    onShadeContainer = Color(0xFF1E3350),
    skyTop = Color(0xFF4E95DB),
    skyMiddle = Color(0xFFA9D2F2),
    skyBottom = Color(0xFFFFE3A3),
    ground = Color(0xFF4E8C55),
    groundFar = Color(0xFF7FB36A),
    glassSurface = Color(0xCCFFFFFF),
)

internal val DarkSunSeatColors = SunSeatColors(
    sun = Color(0xFFF5BE3A),
    onSun = Color(0xFF1E1602),
    sunContainer = Color(0xFF3A2D0B),
    onSunContainer = Color(0xFFFFE3A0),
    shade = Color(0xFF5577A3),
    shadeContainer = Color(0xFF22344B),
    onShadeContainer = Color(0xFFD6E3F5),
    skyTop = Color(0xFF1B2D48),
    skyMiddle = Color(0xFF2C4466),
    skyBottom = Color(0xFF6B5A3A),
    ground = Color(0xFF253B2C),
    groundFar = Color(0xFF34503A),
    glassSurface = Color(0xCC18202A),
)

internal val LightScheme = lightColorScheme(
    primary = Color(0xFF2E4868),
    onPrimary = Color(0xFFFFFFFF),
    primaryContainer = Color(0xFFDCE5F0),
    onPrimaryContainer = Color(0xFF1E3350),
    secondary = Color(0xFF4A5666),
    onSecondary = Color(0xFFFFFFFF),
    secondaryContainer = Color(0xFFE3E8EF),
    onSecondaryContainer = Color(0xFF16202C),
    tertiary = Color(0xFF7A5200),
    onTertiary = Color(0xFFFFFFFF),
    tertiaryContainer = Color(0xFFFFF1C7),
    onTertiaryContainer = Color(0xFF6B4700),
    background = Color(0xFFF2F4F7),
    onBackground = Color(0xFF16202C),
    surface = Color(0xFFFFFFFF),
    onSurface = Color(0xFF16202C),
    surfaceVariant = Color(0xFFE3E8EF),
    onSurfaceVariant = Color(0xFF4A5666),
    surfaceContainerLowest = Color(0xFFFFFFFF),
    surfaceContainerLow = Color(0xFFF7F8FA),
    surfaceContainer = Color(0xFFEEF1F5),
    surfaceContainerHigh = Color(0xFFE3E8EF),
    surfaceContainerHighest = Color(0xFFD9DFE7),
    inverseSurface = Color(0xFF16202C),
    inverseOnSurface = Color(0xFFF2F4F7),
    outline = Color(0xFF7A8696),
    outlineVariant = Color(0xFFD3DAE3),
    error = Color(0xFFB3261E),
    onError = Color(0xFFFFFFFF),
)

internal val DarkScheme = darkColorScheme(
    primary = Color(0xFF9DB9DD),
    onPrimary = Color(0xFF10243D),
    primaryContainer = Color(0xFF22344B),
    onPrimaryContainer = Color(0xFFD6E3F5),
    secondary = Color(0xFFA9B5C3),
    onSecondary = Color(0xFF16202C),
    secondaryContainer = Color(0xFF242E3A),
    onSecondaryContainer = Color(0xFFE8EDF2),
    tertiary = Color(0xFFF5BE3A),
    onTertiary = Color(0xFF2A1E00),
    tertiaryContainer = Color(0xFF3A2D0B),
    onTertiaryContainer = Color(0xFFFFE3A0),
    background = Color(0xFF0F141A),
    onBackground = Color(0xFFE8EDF2),
    surface = Color(0xFF18202A),
    onSurface = Color(0xFFE8EDF2),
    surfaceVariant = Color(0xFF242E3A),
    onSurfaceVariant = Color(0xFFA9B5C3),
    surfaceContainerLowest = Color(0xFF0B1015),
    surfaceContainerLow = Color(0xFF141B23),
    surfaceContainer = Color(0xFF18202A),
    surfaceContainerHigh = Color(0xFF202A35),
    surfaceContainerHighest = Color(0xFF2B3643),
    inverseSurface = Color(0xFFE8EDF2),
    inverseOnSurface = Color(0xFF16202C),
    outline = Color(0xFF7D8998),
    outlineVariant = Color(0xFF2B3643),
    error = Color(0xFFF2B8B5),
    onError = Color(0xFF601410),
)
