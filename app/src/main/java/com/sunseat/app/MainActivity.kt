package com.sunseat.app

import android.os.Bundle
import android.os.SystemClock
import android.view.Window
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.Modifier
import androidx.core.view.WindowCompat
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.sunseat.app.data.settings.ThemeMode
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import com.sunseat.app.ui.navigation.SunSeatNavHost
import com.sunseat.app.ui.theme.SunSeatTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        val splash = installSplashScreen()
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val graph = (application as SunSeatApp).graph
        val startup = SystemClock.elapsedRealtime()
        splash.setKeepOnScreenCondition { !graph.settings.loaded.value && SystemClock.elapsedRealtime() - startup < 300L }
        setContent { SunSeatContent(graph, window) }
    }
}

/** 설정의 화면 모드를 테마·시스템 막대에 반영하고 화면 흐름을 그린다. 흐름 테스트(#60)도 이 함수를 쓴다. */
@Composable
fun SunSeatContent(graph: AppGraph, window: Window, modifier: Modifier = Modifier) {
    val settings by graph.settings.settings.collectAsStateWithLifecycle()
    val dark = when (settings.theme) {
        ThemeMode.SYSTEM -> isSystemInDarkTheme()
        ThemeMode.LIGHT -> false
        ThemeMode.DARK -> true
    }
    SideEffect {
        WindowCompat.getInsetsController(window, window.decorView).apply {
            isAppearanceLightStatusBars = !dark
            isAppearanceLightNavigationBars = !dark
        }
    }
    SunSeatTheme(darkTheme = dark) {
        SunSeatNavHost(graph, modifier)
    }
}
