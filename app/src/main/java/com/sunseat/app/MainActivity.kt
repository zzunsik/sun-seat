package com.sunseat.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import com.sunseat.app.ui.sample.DesignSampleScreen
import com.sunseat.app.ui.sample.SampleSettings
import com.sunseat.app.ui.sample.ThemeMode
import com.sunseat.app.ui.theme.SunSeatTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        installSplashScreen()
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            // 1차 점검용: 디자인 샘플 화면. 실제 화면 흐름은 #11~#13에서 Navigation 3로 바꾼다.
            var theme by rememberSaveable { mutableStateOf(ThemeMode.System) }
            val dark = when (theme) {
                ThemeMode.System -> isSystemInDarkTheme()
                ThemeMode.Light -> false
                ThemeMode.Dark -> true
            }
            SunSeatTheme(darkTheme = dark) {
                DesignSampleScreen(
                    settings = SampleSettings(theme),
                    onSettingsChange = {
                        theme = it.theme
                    },
                )
            }
        }
    }
}
