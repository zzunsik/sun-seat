package com.sunseat.app.location

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import com.sunseat.app.ui.components.PrimaryButton
import com.sunseat.app.ui.components.QuietButton
import com.sunseat.app.ui.theme.Spacing
import com.sunseat.app.ui.theme.SunSeatTheme
import kotlinx.coroutines.launch

/** 디버그 빌드 전용 점검 화면. adb shell am start -n com.sunseat.app/.location.LocationDebugActivity */
class LocationDebugActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent { SunSeatTheme { LocationDebugScreen() } }
    }
}

@Composable
private fun LocationDebugScreen() {
    val context = LocalContext.current
    val provider = remember(context) { LocationProvider(context) }
    val scope = rememberCoroutineScope()
    var result by remember { mutableStateOf<String?>(null) }
    var resultMessage by remember { mutableStateOf<Int?>(null) }
    var lastResult by remember { mutableStateOf<LocationResult?>(null) }
    val fetch: () -> Unit = {
        result = "..."
        scope.launch {
            val r = provider.currentLocation()
            result = r.toString()
            lastResult = r
            resultMessage = r.messageRes()
        }
    }
    val permission = rememberLocationPermissionState {
        lastResult = null
        if (it.isGranted) fetch()
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(SunSeatTheme.scheme.background)
            .safeDrawingPadding()
            .padding(Spacing.screen),
        verticalArrangement = Arrangement.spacedBy(Spacing.m),
    ) {
        Text(
            "status=${permission.status}",
            modifier = Modifier.testTag("status"),
            style = MaterialTheme.typography.bodyLarge,
            color = SunSeatTheme.scheme.onBackground,
        )
        permission.status.messageRes()?.let {
            Text(stringResource(it), style = MaterialTheme.typography.bodyMedium, color = SunSeatTheme.scheme.onBackground)
        }
        result?.let {
            Text("result=$it", style = MaterialTheme.typography.bodyMedium, color = SunSeatTheme.scheme.onBackground)
        }
        resultMessage?.let {
            Text(stringResource(it), style = MaterialTheme.typography.bodyMedium, color = SunSeatTheme.scheme.onBackground)
        }
        PrimaryButton(onClick = {
            if (permission.status.isGranted) fetch() else permission.request()
        }) { Text(stringResource(com.sunseat.app.R.string.location_use_current)) }
        val action = lastResult?.action() ?: permission.status.action()
        if (action != null) {
            QuietButton(onClick = {
                when (action) {
                    LocationAction.RequestPermission -> permission.request()
                    LocationAction.OpenAppSettings -> permission.openAppSettings()
                    LocationAction.OpenLocationSettings -> context.startActivity(locationSettingsIntent())
                    LocationAction.Retry -> fetch()
                }
            }) { Text(stringResource(action.labelRes)) }
        }
    }
}
