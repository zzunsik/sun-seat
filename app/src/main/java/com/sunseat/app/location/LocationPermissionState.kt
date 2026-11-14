package com.sunseat.app.location

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.core.app.ActivityCompat
import androidx.lifecycle.compose.LifecycleResumeEffect

/**
 * 위치 권한 상태와 요청 동작. [rememberLocationPermissionState]로 만든다.
 * 설정 화면에서 돌아오면(ON_RESUME) 상태를 다시 읽는다.
 */
@Stable
class LocationPermissionState internal constructor(
    private val context: Context,
    private val history: LocationPermissionHistory,
) {
    var status: LocationPermissionStatus by mutableStateOf(LocationPermissionStatus.NotRequested)
        private set

    internal var launch: (() -> Unit)? = null
    private var rationaleBefore = false

    /**
     * 시스템 권한 대화상자를 띄운다. [LocationPermissionStatus.PermanentlyDenied]이면
     * 대화상자가 뜨지 않으므로 대신 [openAppSettings]를 쓴다.
     */
    fun request() {
        rationaleBefore = shouldShowRationale()
        launch?.invoke()
    }

    /** 이 앱의 설정 화면을 연다. 돌아오면 상태가 갱신된다. */
    fun openAppSettings() {
        context.startActivity(appSettingsIntent(context))
    }

    fun refresh() {
        status = LocationPermissionLogic.current(
            level = locationPermissionLevel(context),
            deniedBefore = history.deniedBefore,
            shouldShowRationale = shouldShowRationale(),
        )
    }

    internal fun onRequestResult(): LocationPermissionStatus {
        val after = LocationPermissionLogic.afterRequest(
            level = locationPermissionLevel(context),
            deniedBefore = history.deniedBefore,
            rationaleBefore = rationaleBefore,
            rationaleAfter = shouldShowRationale(),
        )
        history.deniedBefore = after.deniedBefore
        status = after.status
        return after.status
    }

    private fun shouldShowRationale(): Boolean {
        val activity = context.findActivity() ?: return false
        return LOCATION_PERMISSIONS.any { ActivityCompat.shouldShowRequestPermissionRationale(activity, it) }
    }
}

/**
 * 위치 권한 흐름(허용·대략적 위치만 허용·거부·다시 묻지 않음)을 다루는 상태를 기억한다.
 * @param onResult 권한 요청이 끝날 때마다 새 상태로 불린다. 허용이면 이어서 위치를 조회하면 된다.
 */
@Composable
fun rememberLocationPermissionState(
    onResult: (LocationPermissionStatus) -> Unit = {},
): LocationPermissionState {
    val context = LocalContext.current
    val currentOnResult by rememberUpdatedState(onResult)
    val state = remember(context) {
        LocationPermissionState(context, SharedPrefsLocationPermissionHistory(context)).apply { refresh() }
    }
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {
        currentOnResult(state.onRequestResult())
    }
    SideEffect { state.launch = { launcher.launch(LOCATION_PERMISSIONS) } }
    LifecycleResumeEffect(state) {
        state.refresh()
        onPauseOrDispose { }
    }
    return state
}

internal tailrec fun Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}
