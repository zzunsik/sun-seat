package com.sunseat.app.data.route

import com.sunseat.app.data.network.ApiResult
import com.sunseat.app.data.network.kakaoCall
import com.sunseat.app.domain.LatLng
import com.sunseat.app.domain.Leg
import com.sunseat.app.domain.Mode
import com.sunseat.app.domain.Route
import java.time.Duration
import kotlin.math.roundToInt
import kotlin.math.roundToLong
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import retrofit2.Response
import retrofit2.Retrofit
import retrofit2.create
import retrofit2.http.GET
import retrofit2.http.Query

/** 카카오모빌리티 자동차 길찾기(`GET https://apis-navi.kakaomobility.com/v1/directions`). */
class KakaoCarRouteApi internal constructor(
    private val service: KakaoNaviService,
) : CarRouteApi {

    constructor(retrofit: Retrofit) : this(retrofit.create<KakaoNaviService>())

    override suspend fun search(origin: LatLng, destination: LatLng): ApiResult<CarRouteResult> =
        kakaoCall(
            request = {
                service.directions(
                    origin = "${origin.longitude},${origin.latitude}",
                    destination = "${destination.longitude},${destination.latitude}",
                )
            },
            transform = { ApiResult.Success(it.toCarRouteResult()) },
        )
}

internal interface KakaoNaviService {
    /** @param origin `경도,위도` */
    @GET("v1/directions")
    suspend fun directions(
        @Query("origin") origin: String,
        @Query("destination") destination: String,
    ): Response<DirectionsResponse>
}

@Serializable
internal data class DirectionsResponse(
    val routes: List<NaviRoute> = emptyList(),
)

@Serializable
internal data class NaviRoute(
    @SerialName("result_code") val resultCode: Int = -1,
    @SerialName("result_msg") val resultMsg: String = "",
    val summary: NaviSummary? = null,
    val sections: List<NaviSection> = emptyList(),
)

@Serializable
internal data class NaviSummary(
    val origin: NaviPoint? = null,
    val destination: NaviPoint? = null,
    val fare: NaviFare? = null,
    /** m */
    val distance: Double = 0.0,
    /** 초 */
    val duration: Double = 0.0,
)

@Serializable
internal data class NaviPoint(val name: String = "")

@Serializable
internal data class NaviFare(val taxi: Double = 0.0, val toll: Double = 0.0)

@Serializable
internal data class NaviSection(
    val roads: List<NaviRoad> = emptyList(),
)

@Serializable
internal data class NaviRoad(
    /** [경도, 위도, 경도, 위도, ...] 평면 배열 */
    val vertexes: List<Double> = emptyList(),
)

internal fun DirectionsResponse.toCarRouteResult(): CarRouteResult {
    val first = routes.firstOrNull()
        ?: return CarRouteResult(null, CarNoRouteReason.NO_RESULTS)
    if (first.resultCode != 0 || first.summary == null) {
        val reason = when (first.resultCode) {
            102 -> CarNoRouteReason.NO_START_ROAD
            103 -> CarNoRouteReason.NO_END_ROAD
            104 -> CarNoRouteReason.SAME_POINTS
            else -> CarNoRouteReason.NO_RESULTS
        }
        return CarRouteResult(null, reason, first.resultMsg)
    }
    val summary = first.summary
    val leg = Leg(
        mode = Mode.CAR,
        path = first.sections.flatMap { it.roads }.flatMap { it.vertexes.toLatLngs() }.dedupeConsecutive(),
        duration = Duration.ofSeconds(summary.duration.roundToLong()),
        startName = summary.origin?.name.orEmpty(),
        endName = summary.destination?.name.orEmpty(),
    )
    return CarRouteResult(
        option = CarRouteOption(
            route = Route(listOf(leg)),
            distanceMeters = summary.distance.roundToInt(),
            tollFare = summary.fare?.toll?.roundToInt() ?: 0,
            taxiFare = summary.fare?.taxi?.roundToInt() ?: 0,
        ),
        message = first.resultMsg,
    )
}

/** 평면 배열 [x0, y0, x1, y1, ...]을 좌표열로. 홀수 개면 마지막 값은 버린다. */
internal fun List<Double>.toLatLngs(): List<LatLng> =
    (0 until size / 2).map { i -> LatLng(latitude = this[2 * i + 1], longitude = this[2 * i]) }

/** 도로 경계에서 겹치는 연속 좌표를 하나로 합친다. */
internal fun List<LatLng>.dedupeConsecutive(): List<LatLng> =
    filterIndexed { i, p -> i == 0 || p != this[i - 1] }
