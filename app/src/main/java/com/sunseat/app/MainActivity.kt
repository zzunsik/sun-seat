package com.sunseat.app

import android.os.Bundle
import android.os.SystemClock
import androidx.compose.runtime.getValue
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
        setContent {
            val settings by graph.settings.settings.collectAsStateWithLifecycle()
            val dark = when (settings.theme) {
                ThemeMode.SYSTEM -> isSystemInDarkTheme()
                ThemeMode.LIGHT -> false
                ThemeMode.DARK -> true
            }
            SunSeatTheme(darkTheme = dark) {
                SunSeatNavHost(graph)
            }
        }
    }
}
