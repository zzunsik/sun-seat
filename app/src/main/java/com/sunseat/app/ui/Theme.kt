package com.sunseat.app.ui

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

// 임시 최소 테마. 디자인 토큰 기반 테마는 #18에서 만든다.
private val Light = lightColorScheme(
    primary = Color(0xFF2E4868),
    secondary = Color(0xFFF2B01E),
    background = Color(0xFFF2F4F7),
    surface = Color(0xFFFFFFFF),
)
private val Dark = darkColorScheme(
    primary = Color(0xFF86A8D2),
    secondary = Color(0xFFF2B01E),
    background = Color(0xFF10151C),
    surface = Color(0xFF18202A),
)

@Composable
fun SunSeatTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = if (isSystemInDarkTheme()) Dark else Light, content = content)
}
