package com.sunseat.app.ui.theme

import android.provider.Settings
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.MaterialExpressiveTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MotionScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.platform.LocalContext

@Suppress("ComposeCompositionLocalUsage") // 테마 토큰 전달용
private val LocalSunSeatColors = staticCompositionLocalOf { LightSunSeatColors }
@Suppress("ComposeCompositionLocalUsage")
private val LocalReduceMotion = staticCompositionLocalOf { false }

/**
 * 햇빛 좌석 테마. Material 3 Expressive 위에 자체 토큰(docs/DESIGN.md)을 입힌다.
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun SunSeatTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    val reduceMotion = rememberReduceMotion()
    CompositionLocalProvider(
        LocalSunSeatColors provides if (darkTheme) DarkSunSeatColors else LightSunSeatColors,
        LocalReduceMotion provides reduceMotion,
    ) {
        MaterialExpressiveTheme(
            colorScheme = if (darkTheme) DarkScheme else LightScheme,
            motionScheme = MotionScheme.expressive(),
            shapes = SunSeatShapes,
            typography = SunSeatTypography,
            content = content,
        )
    }
}

/** 테마 토큰 접근점 */
object SunSeatTheme {
    val colors: SunSeatColors
        @Composable @ReadOnlyComposable get() = LocalSunSeatColors.current

    /** 시스템 "애니메이션 삭제"가 켜져 있으면 true */
    val reduceMotion: Boolean
        @Composable @ReadOnlyComposable get() = LocalReduceMotion.current

    val scheme
        @Composable @ReadOnlyComposable get() = MaterialTheme.colorScheme
}

@Composable
private fun rememberReduceMotion(): Boolean {
    val resolver = LocalContext.current.contentResolver
    return remember(resolver) {
        Settings.Global.getFloat(resolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f) == 0f
    }
}
