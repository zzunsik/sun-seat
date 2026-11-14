package com.sunseat.app.data.route

import com.sunseat.app.data.network.ApiResult
import com.sunseat.app.domain.LatLng
import com.sunseat.app.domain.Route
import java.time.Duration

/**
 * 출발·도착 좌표로 대중교통 경로 후보를 찾는다.
 * 출발 시각은 지정할 수 없고, 호출 시점 기준 경로를 돌려준다.
 */
interface TransitRouteApi {
    suspend fun search(origin: LatLng, destination: LatLng): ApiResult<TransitRoutes>
}

/**
 * 경로 후보 목록. 경로가 없으면 [options]가 비고 [noRouteReason]에 이유가 있다.
 */
data class TransitRoutes(
    val options: List<TransitRouteOption>,
    val noRouteReason: NoRouteReason? = null,
)

/** 경로가 없는 이유(카카오 `status`). */
enum class NoRouteReason {
    /** `NO_RESULTS`: 대중교통으로 갈 수 없음(도시 간 장거리 포함) */
    NO_RESULTS,

    /** `STARTNODES_NULL`: 출발지 근처에 정류장·역이 없음 */
    NO_START_STOP,

    /** `ENDNODES_NULL`: 도착지 근처에 정류장·역이 없음 */
    NO_END_STOP,

    /** `EQUAL_POINTS`: 출발지와 도착지가 같음 */
    SAME_POINTS,
}

/** 경로 전체 분류(카카오 `routes[].properties.type`). */
enum class TransitRouteType { BUS, SUBWAY, BUS_AND_SUBWAY, OTHER }

/**
 * 대중교통 경로 후보 하나.
 *
 * [route]의 구간은 승차 정류장에서 하차 정류장까지다. 출발지→첫 승차, 마지막 하차→도착지 도보는
 * 카카오 응답에 없으므로 포함하지 않는다. 그래서 `route.duration`(구간 합)은 대기·접근 시간을 뺀
 * 이동 시간이고, 전체 소요시간은 [totalDuration]이다.
 *
 * @param legDetails [route]의 `legs`와 같은 순서·개수인 구간별 부가 정보
 */
data class TransitRouteOption(
    val route: Route,
    val type: TransitRouteType,
    val totalDuration: Duration,
    val distanceMeters: Int,
    val transfers: Int,
    /** 요금(원). 응답에 없으면 null(도시 간 시외버스 경로 등) */
    val fare: Int?,
    val legDetails: List<TransitLegDetail>,
)

/** 구간별 부가 정보. */
data class TransitLegDetail(
    /** 탈 수 있는 노선(예: 이름 "2호선"·종류 "일반", 이름 "146"·종류 "간선"). 도보는 빈 목록 */
    val vehicles: List<TransitVehicle>,
    /** 지나는 정류장·역 이름(승차~하차). 도보는 출발·도착 지점 이름 */
    val stops: List<String>,
    /** 카카오 안내 문구 */
    val guidance: String,
)

data class TransitVehicle(val name: String, val type: String)
