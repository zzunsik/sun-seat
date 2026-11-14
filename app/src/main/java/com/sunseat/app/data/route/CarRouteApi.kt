package com.sunseat.app.data.route

import com.sunseat.app.data.network.ApiResult
import com.sunseat.app.domain.LatLng
import com.sunseat.app.domain.Route
import java.time.Duration

/**
 * 출발·도착 좌표로 자동차 경로를 찾는다. 호출 시점의 교통 정보 기준이며 결과는 저장하지 않는다.
 */
interface CarRouteApi {
    suspend fun search(origin: LatLng, destination: LatLng): ApiResult<CarRouteResult>
}

/** 경로가 있으면 [option], 없으면 [noRouteReason]과 카카오 안내 문구 [message]. */
data class CarRouteResult(
    val option: CarRouteOption?,
    val noRouteReason: CarNoRouteReason? = null,
    val message: String = "",
)

/** 자동차 경로가 없는 이유(카카오모빌리티 `result_code`). */
enum class CarNoRouteReason {
    /** 102: 출발지 주변에 도로가 없음 */
    NO_START_ROAD,

    /** 103: 도착지 주변에 도로가 없음 */
    NO_END_ROAD,

    /** 104: 출발지와 도착지가 5m 이내 */
    SAME_POINTS,

    /** 그 밖의 실패 코드 */
    NO_RESULTS,
}

/**
 * 자동차 경로. [route]는 CAR 구간 1개이고 그 소요시간은 [Route.duration]과 같다.
 * @param tollFare 통행료(원)
 * @param taxiFare 예상 택시 요금(원)
 */
data class CarRouteOption(
    val route: Route,
    val distanceMeters: Int,
    val tollFare: Int,
    val taxiFare: Int,
) {
    val duration: Duration get() = route.duration
}
