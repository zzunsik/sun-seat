package com.sunseat.app.location

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.withTimeoutOrNull

/** 현재 위치를 1회 가져온다. 화면은 이 인터페이스에만 의존하고 테스트에서는 가짜로 바꾼다. */
fun interface LocationProvider {
    suspend fun currentLocation(): LocationResult
}

/** 플랫폼에서 받은 위치 한 점. */
data class LocationFix(
    val latitude: Double,
    val longitude: Double,
    val accuracyMeters: Float?,
    val ageMillis: Long,
)

/** 기기 위치 기능을 감싼 얇은 경계. 실제 구현은 [FusedLocationSource]. */
interface LocationSource {
    fun permissionLevel(): LocationPermissionLevel
    fun isLocationEnabled(): Boolean

    /** 새 위치를 요청한다. 얻지 못하면 null. 취소에 응해야 한다. */
    suspend fun fetchCurrent(precise: Boolean): LocationFix?

    /** 캐시된 마지막 위치. 없으면 null. */
    suspend fun lastKnown(): LocationFix?
}

/** 기기가 앱에 준 위치 권한 수준. */
enum class LocationPermissionLevel { None, Approximate, Precise }

/**
 * 권한·위치 서비스 확인 → 새 위치 요청(시간 제한) → 실패 시 최근 마지막 위치 순으로 결과를 만든다.
 */
class DefaultLocationProvider(
    private val source: LocationSource,
    private val timeoutMillis: Long = LocationDefaults.TIMEOUT_MILLIS,
    private val maxLastLocationAgeMillis: Long = LocationDefaults.MAX_LAST_LOCATION_AGE_MILLIS,
) : LocationProvider {

    override suspend fun currentLocation(): LocationResult {
        val level = source.permissionLevel()
        if (level == LocationPermissionLevel.None) return LocationResult.PermissionDenied
        if (!source.isLocationEnabled()) return LocationResult.LocationDisabled
        val approximate = level == LocationPermissionLevel.Approximate

        var timedOut = false
        val fresh = try {
            val outcome = withTimeoutOrNull(timeoutMillis) {
                Fetched(source.fetchCurrent(precise = !approximate))
            }
            if (outcome == null) timedOut = true
            outcome?.fix
        } catch (e: CancellationException) {
            throw e
        } catch (_: SecurityException) {
            return LocationResult.PermissionDenied
        } catch (_: Exception) {
            null
        }
        if (fresh != null) return fresh.toSuccess(approximate)

        val last = try {
            source.lastKnown()
        } catch (e: CancellationException) {
            throw e
        } catch (_: SecurityException) {
            return LocationResult.PermissionDenied
        } catch (_: Exception) {
            null
        }
        if (last != null && last.ageMillis in 0..maxLastLocationAgeMillis) {
            return last.toSuccess(approximate)
        }
        return if (timedOut) LocationResult.Timeout else LocationResult.Unavailable
    }

    private class Fetched(val fix: LocationFix?)

    private fun LocationFix.toSuccess(approximate: Boolean) =
        LocationResult.Success(latitude, longitude, accuracyMeters, isApproximate = approximate)
}
