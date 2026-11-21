package com.sunseat.app

import android.os.Bundle
import android.os.SystemClock
import android.view.Window
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.testTagsAsResourceId
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
        // debug 빌드에서만 테스트 태그를 리소스 ID로 내보내 실기기 점검 스크립트(scripts/device-smoke.sh, #169)가 uiautomator로 찾게 한다.
        val root = if (BuildConfig.DEBUG) Modifier.semantics { testTagsAsResourceId = true } else Modifier
        setContent { SunSeatContent(graph, window, root) }
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
