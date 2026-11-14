package com.sunseat.app.location

import android.Manifest
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.annotation.StringRes
import androidx.core.content.edit
import com.sunseat.app.R

/** 화면이 다루는 위치 권한 상태. */
enum class LocationPermissionStatus {
    /** 아직 묻지 않았다. */
    NotRequested,

    /** 정확한 위치 허용. */
    Precise,

    /** 대략적 위치만 허용. 출발지로 쓰기엔 충분하다. */
    ApproximateOnly,

    /** 거부했지만 다시 물을 수 있다. */
    Denied,

    /** 다시 묻지 않음. 시스템 대화상자가 뜨지 않으므로 앱 설정으로 안내한다. */
    PermanentlyDenied,
    ;

    val isGranted: Boolean get() = this == Precise || this == ApproximateOnly
}

/** 요청할 권한. 정확한 위치를 함께 요청해야 사용자가 '대략적 위치'를 고를 수 있다. */
val LOCATION_PERMISSIONS: Array<String> = arrayOf(
    Manifest.permission.ACCESS_FINE_LOCATION,
    Manifest.permission.ACCESS_COARSE_LOCATION,
)

/**
 * 권한 상태 판정. Android는 '다시 묻지 않음'을 직접 알려주지 않아
 * shouldShowRequestPermissionRationale 값과 요청 이력으로 추정한다.
 */
object LocationPermissionLogic {

    /** 화면 진입·복귀 때의 상태. */
    fun current(
        level: LocationPermissionLevel,
        deniedBefore: Boolean,
        shouldShowRationale: Boolean,
    ): LocationPermissionStatus = when {
        level == LocationPermissionLevel.Precise -> LocationPermissionStatus.Precise
        level == LocationPermissionLevel.Approximate -> LocationPermissionStatus.ApproximateOnly
        shouldShowRationale -> LocationPermissionStatus.Denied
        deniedBefore -> LocationPermissionStatus.PermanentlyDenied
        else -> LocationPermissionStatus.NotRequested
    }

    data class AfterRequest(val status: LocationPermissionStatus, val deniedBefore: Boolean)

    /**
     * 요청 결과 직후의 상태.
     * - 거부 후 근거 표시가 가능하면: 다시 물을 수 있는 거부.
     * - 요청 전엔 근거 표시가 가능했는데 이제 불가능하면: 사용자가 다시 묻지 않음을 골랐다.
     * - 전후 모두 불가능하고 이전 거부 이력이 없으면: 대화상자를 닫은 것으로 보고 다음에 다시 묻는다.
     * - 전후 모두 불가능하고 이전 거부 이력이 있으면: 시스템이 대화상자를 띄우지 않았다(다시 묻지 않음).
     */
    fun afterRequest(
        level: LocationPermissionLevel,
        deniedBefore: Boolean,
        rationaleBefore: Boolean,
        rationaleAfter: Boolean,
    ): AfterRequest = when {
        level == LocationPermissionLevel.Precise ->
            AfterRequest(LocationPermissionStatus.Precise, deniedBefore)
        level == LocationPermissionLevel.Approximate ->
            AfterRequest(LocationPermissionStatus.ApproximateOnly, deniedBefore)
        rationaleAfter -> AfterRequest(LocationPermissionStatus.Denied, deniedBefore = true)
        rationaleBefore || deniedBefore ->
            AfterRequest(LocationPermissionStatus.PermanentlyDenied, deniedBefore = true)
        else -> AfterRequest(LocationPermissionStatus.NotRequested, deniedBefore = false)
    }
}

/** 거부 이력 저장소. 앱을 다시 켜도 '다시 묻지 않음'을 판정할 수 있게 한다. */
interface LocationPermissionHistory {
    var deniedBefore: Boolean
}

class SharedPrefsLocationPermissionHistory(context: Context) : LocationPermissionHistory {
    private val prefs = context.applicationContext
        .getSharedPreferences("location_permission", Context.MODE_PRIVATE)

    override var deniedBefore: Boolean
        get() = prefs.getBoolean(KEY_DENIED, false)
        set(value) = prefs.edit { putBoolean(KEY_DENIED, value) }

    private companion object {
        const val KEY_DENIED = "denied_before"
    }
}

/** 이 앱의 시스템 설정 화면(권한을 직접 켤 수 있는 곳). */
fun appSettingsIntent(context: Context): Intent =
    Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.fromParts("package", context.packageName, null))
        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)

/** 기기 위치 서비스 설정 화면. */
fun locationSettingsIntent(): Intent =
    Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)

/** 권한 상태별 안내 문구. 허용 상태(정확)는 안내가 필요 없어 null. */
@StringRes
fun LocationPermissionStatus.messageRes(): Int? = when (this) {
    LocationPermissionStatus.Precise -> null
    LocationPermissionStatus.ApproximateOnly -> R.string.location_approximate_only
    LocationPermissionStatus.NotRequested -> R.string.location_permission_rationale
    LocationPermissionStatus.Denied -> R.string.location_permission_denied
    LocationPermissionStatus.PermanentlyDenied -> R.string.location_permission_permanently_denied
}

/** 위치 결과별 안내 문구. 성공은 null. 실패는 모두 장소 검색으로 안내한다. */
@StringRes
fun LocationResult.messageRes(): Int? = when (this) {
    is LocationResult.Success -> null
    LocationResult.PermissionDenied -> R.string.location_permission_denied
    LocationResult.LocationDisabled -> R.string.location_disabled
    LocationResult.Timeout -> R.string.location_timeout
    LocationResult.Unavailable -> R.string.location_unavailable
}

/** 안내와 함께 보여줄 행동. 화면은 이 값에 맞는 버튼을 하나 둔다(없으면 장소 검색만). */
enum class LocationAction(@param:StringRes val labelRes: Int) {
    RequestPermission(R.string.location_request_permission),
    OpenAppSettings(R.string.location_open_app_settings),
    OpenLocationSettings(R.string.location_open_location_settings),
    Retry(R.string.location_use_current),
}

fun LocationPermissionStatus.action(): LocationAction? = when (this) {
    LocationPermissionStatus.NotRequested, LocationPermissionStatus.Denied -> LocationAction.RequestPermission
    LocationPermissionStatus.PermanentlyDenied -> LocationAction.OpenAppSettings
    LocationPermissionStatus.Precise, LocationPermissionStatus.ApproximateOnly -> null
}

fun LocationResult.action(): LocationAction? = when (this) {
    is LocationResult.Success -> null
    LocationResult.PermissionDenied -> LocationAction.RequestPermission
    LocationResult.LocationDisabled -> LocationAction.OpenLocationSettings
    LocationResult.Timeout, LocationResult.Unavailable -> LocationAction.Retry
}
