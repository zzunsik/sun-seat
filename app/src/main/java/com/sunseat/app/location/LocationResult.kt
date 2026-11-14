package com.sunseat.app.location

/** 현재 위치 1회 조회 결과. 실패도 값으로 돌려주며 예외를 던지지 않는다. */
sealed interface LocationResult {

    /**
     * 위치를 얻었다.
     * @param isApproximate 대략적 위치 권한만 있어 정밀도가 낮을 수 있다(수백 m~수 km).
     */
    data class Success(
        val latitude: Double,
        val longitude: Double,
        val accuracyMeters: Float?,
        val isApproximate: Boolean,
    ) : LocationResult

    /** 위치 권한이 없다. 권한을 요청하거나 장소 검색으로 안내한다. */
    data object PermissionDenied : LocationResult

    /** 기기의 위치 서비스가 꺼져 있다. */
    data object LocationDisabled : LocationResult

    /** [LocationDefaults.TIMEOUT_MILLIS] 안에 위치를 얻지 못했다. */
    data object Timeout : LocationResult

    /** 그 밖의 이유(Play 서비스 오류, 위치 없음 등)로 위치를 얻지 못했다. */
    data object Unavailable : LocationResult
}

object LocationDefaults {
    /** 현재 위치 1회 조회의 시간 제한. */
    const val TIMEOUT_MILLIS: Long = 10_000L

    /** 시간 초과·실패 때 대신 쓸 수 있는 마지막 위치의 최대 나이. */
    const val MAX_LAST_LOCATION_AGE_MILLIS: Long = 2 * 60_000L
}
