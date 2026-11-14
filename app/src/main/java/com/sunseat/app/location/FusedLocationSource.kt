package com.sunseat.app.location

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.location.Location
import android.location.LocationManager
import android.os.SystemClock
import androidx.core.content.ContextCompat
import androidx.core.location.LocationManagerCompat
import com.google.android.gms.location.CurrentLocationRequest
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import com.google.android.gms.tasks.CancellationTokenSource
import kotlinx.coroutines.tasks.await

/** Fused Location Provider 기반 [LocationSource]. */
class FusedLocationSource(
    context: Context,
    private val client: FusedLocationProviderClient =
        LocationServices.getFusedLocationProviderClient(context.applicationContext),
    private val durationMillis: Long = LocationDefaults.TIMEOUT_MILLIS,
) : LocationSource {

    private val appContext = context.applicationContext

    override fun permissionLevel(): LocationPermissionLevel = locationPermissionLevel(appContext)

    override fun isLocationEnabled(): Boolean {
        val manager = appContext.getSystemService(Context.LOCATION_SERVICE) as? LocationManager
            ?: return false
        return LocationManagerCompat.isLocationEnabled(manager)
    }

    @SuppressLint("MissingPermission") // permissionLevel()을 먼저 확인하고, SecurityException은 호출부가 처리한다.
    override suspend fun fetchCurrent(precise: Boolean): LocationFix? {
        val request = CurrentLocationRequest.Builder()
            .setPriority(
                if (precise) Priority.PRIORITY_HIGH_ACCURACY else Priority.PRIORITY_BALANCED_POWER_ACCURACY,
            )
            .setDurationMillis(durationMillis)
            .setMaxUpdateAgeMillis(LocationDefaults.MAX_LAST_LOCATION_AGE_MILLIS)
            .build()
        val cancellation = CancellationTokenSource()
        return try {
            client.getCurrentLocation(request, cancellation.token).await()?.toFix()
        } finally {
            cancellation.cancel() // 시간 초과로 취소되면 Play 서비스 요청도 멈춘다.
        }
    }

    @SuppressLint("MissingPermission")
    override suspend fun lastKnown(): LocationFix? = client.lastLocation.await()?.toFix()

    private fun Location.toFix() = LocationFix(
        latitude = latitude,
        longitude = longitude,
        accuracyMeters = if (hasAccuracy()) accuracy else null,
        ageMillis = (SystemClock.elapsedRealtimeNanos() - elapsedRealtimeNanos) / 1_000_000L,
    )
}

/** 앱 컨텍스트로 기본 [LocationProvider]를 만든다. */
fun LocationProvider(context: Context): LocationProvider =
    DefaultLocationProvider(FusedLocationSource(context))

/** 현재 기기가 앱에 준 위치 권한 수준. */
fun locationPermissionLevel(context: Context): LocationPermissionLevel {
    fun granted(permission: String) =
        ContextCompat.checkSelfPermission(context, permission) == PackageManager.PERMISSION_GRANTED
    return when {
        granted(Manifest.permission.ACCESS_FINE_LOCATION) -> LocationPermissionLevel.Precise
        granted(Manifest.permission.ACCESS_COARSE_LOCATION) -> LocationPermissionLevel.Approximate
        else -> LocationPermissionLevel.None
    }
}
