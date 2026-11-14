package com.sunseat.app.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontVariation
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import com.sunseat.app.R

private fun pretendard(weight: Int) = Font(
    resId = R.font.pretendard_variable,
    weight = FontWeight(weight),
    variationSettings = FontVariation.Settings(FontVariation.weight(weight)),
)

/** Pretendard 가변 글꼴. docs/DESIGN.md 3 */
val Pretendard = FontFamily(
    pretendard(400),
    pretendard(500),
    pretendard(600),
    pretendard(700),
    pretendard(800),
)

private fun style(size: Int, line: Int, weight: Int, tracking: Double = 0.0) = TextStyle(
    fontFamily = Pretendard,
    fontSize = size.sp,
    lineHeight = line.sp,
    fontWeight = FontWeight(weight),
    letterSpacing = tracking.em,
    fontFeatureSettings = "tnum",
)

internal val SunSeatTypography = Typography(
    displayLarge = style(48, 56, 800, -0.02),
    displayMedium = style(42, 50, 800, -0.02),
    displaySmall = style(36, 44, 800, -0.02),
    headlineLarge = style(32, 40, 800, -0.02),
    headlineMedium = style(28, 36, 800, -0.02),
    headlineSmall = style(24, 32, 700, -0.01),
    titleLarge = style(20, 28, 700, -0.01),
    titleMedium = style(17, 24, 700),
    titleSmall = style(15, 22, 600),
    bodyLarge = style(16, 24, 400),
    bodyMedium = style(15, 22, 400),
    bodySmall = style(13, 18, 400),
    labelLarge = style(16, 20, 700),
    labelMedium = style(13, 16, 600),
    labelSmall = style(12, 16, 500),
)
