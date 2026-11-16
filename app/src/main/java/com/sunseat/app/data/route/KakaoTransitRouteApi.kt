package com.sunseat.app.data.route

import com.sunseat.app.data.network.ApiError
import com.sunseat.app.data.network.ApiResult
import com.sunseat.app.data.network.kakaoCall
import com.sunseat.app.domain.LatLng
import com.sunseat.app.domain.Leg
import com.sunseat.app.domain.Mode
import com.sunseat.app.domain.Route
import java.time.Duration
import kotlin.math.roundToInt
import kotlin.math.roundToLong
import kotlinx.serialization.Serializable
import retrofit2.Response
import retrofit2.Retrofit
import retrofit2.create
import retrofit2.http.GET
import retrofit2.http.Query

/** 카카오맵 대중교통 경로 조회(`GET /v2/routing/publictraffic`). */
class KakaoTransitRouteApi internal constructor(
    private val service: KakaoTransitService,
) : TransitRouteApi {

    constructor(retrofit: Retrofit) : this(retrofit.create<KakaoTransitService>())

    override suspend fun search(origin: LatLng, destination: LatLng): ApiResult<TransitRoutes> =
        kakaoCall(
            request = {
                service.publicTraffic(
                    startX = origin.longitude,
                    startY = origin.latitude,
                    endX = destination.longitude,
                    endY = destination.latitude,
                )
            },
            transform = { it.toTransitRoutes() },
        )
}

internal interface KakaoTransitService {
    @GET("v2/routing/publictraffic")
    suspend fun publicTraffic(
        @Query("start_x") startX: Double,
        @Query("start_y") startY: Double,
        @Query("end_x") endX: Double,
        @Query("end_y") endY: Double,
    ): Response<PublicTrafficResponse>
}

@Serializable
internal data class PublicTrafficResponse(
    val status: String = "",
    val routes: List<PtRoute> = emptyList(),
)

@Serializable
internal data class PtRoute(
    val properties: PtRouteProperties = PtRouteProperties(),
    val steps: List<PtStep> = emptyList(),
)

@Serializable
internal data class PtRouteProperties(
    val type: String = "",
    val totalDistance: Double = 0.0,
    val totalTime: Double = 0.0,
    val transfers: Int = 0,
    val fare: PtFare? = null,
)

@Serializable
internal data class PtFare(val value: Int? = null)

@Serializable
internal data class PtStep(
    val properties: PtStepProperties = PtStepProperties(),
    val path: PtPath? = null,
)

@Serializable
internal data class PtStepProperties(
    val guidance: String = "",
    val type: String = "",
    val distance: Double = 0.0,
    /** 초 */
    val time: Double = 0.0,
    val stops: List<PtStop> = emptyList(),
    val vehicles: List<PtVehicle> = emptyList(),
)

@Serializable
internal data class PtStop(val name: String = "")

@Serializable
internal data class PtVehicle(val name: String = "", val type: String = "")

@Serializable
internal data class PtPath(
    /** [경도, 위도] 쌍 */
    val points: List<List<Double>> = emptyList(),
)

internal fun PublicTrafficResponse.toTransitRoutes(): ApiResult<TransitRoutes> {
    val reason = when (status) {
        "OK" -> null
        "NO_RESULTS" -> NoRouteReason.NO_RESULTS
        "STARTNODES_NULL" -> NoRouteReason.NO_START_STOP
        "ENDNODES_NULL" -> NoRouteReason.NO_END_STOP
        "EQUAL_POINTS" -> NoRouteReason.SAME_POINTS
        "INVALID_REQUEST" -> return ApiResult.Failure(ApiError.UNKNOWN)
        // 문서에 없는 상태: 경로가 있으면 쓰고 없으면 결과 없음으로 본다.
        else -> if (routes.isEmpty()) NoRouteReason.NO_RESULTS else null
    }
    if (reason != null) return ApiResult.Success(TransitRoutes(emptyList(), reason))
    val options = routes.mapNotNull { it.toOption() }
    return ApiResult.Success(
        TransitRoutes(options, if (options.isEmpty()) NoRouteReason.NO_RESULTS else null),
    )
}

/** 알 수 없는 단계 종류가 섞인 경로는 노출 계산을 할 수 없으므로 건너뛴다. */
internal fun PtRoute.toOption(): TransitRouteOption? {
    if (steps.isEmpty()) return null
    val legs = ArrayList<Leg>(steps.size)
    val details = ArrayList<TransitLegDetail>(steps.size)
    for (step in steps) {
        val p = step.properties
        val mode = when (p.type) {
            "BUS" -> Mode.BUS
            "SUBWAY" -> Mode.SUBWAY
            "WALKING" -> Mode.WALK
            else -> return null
        }
        val stopNames = p.stops.map { it.name }
        legs += Leg(
            mode = mode,
            busType = if (mode == Mode.BUS) p.vehicles.firstOrNull()?.type else null,
            path = step.path?.points.orEmpty().mapNotNull { it.toLatLng() },
            duration = Duration.ofSeconds(p.time.roundToLong()),
            startName = stopNames.firstOrNull().orEmpty(),
            endName = stopNames.lastOrNull().orEmpty(),
            groundLevel = if (mode == Mode.SUBWAY) {
                val levels = p.vehicles.map { com.sunseat.app.domain.SubwayGroundData.bundled.section(it.name, stopNames) }
                levels.distinct().singleOrNull() ?: com.sunseat.app.domain.GroundLevel.UNKNOWN
            } else com.sunseat.app.domain.GroundLevel.UNKNOWN,
        )
        details += TransitLegDetail(
            vehicles = p.vehicles.map { TransitVehicle(it.name, it.type) },
            stops = stopNames,
            guidance = p.guidance,
        )
    }
    val expandedLegs = mutableListOf<Leg>()
    val expandedDetails = mutableListOf<TransitLegDetail>()
    legs.zip(details).forEach { (leg, detail) ->
        val levels = detail.stops.zipWithNext { a, b ->
            detail.vehicles.map { com.sunseat.app.domain.SubwayGroundData.bundled.section(it.name, listOf(a, b)) }
                .distinct().singleOrNull() ?: com.sunseat.app.domain.GroundLevel.UNKNOWN
        }
        // Only split mixed ground-status steps. Station times/coordinates are absent in this API;
        // equal station-interval fractions are an explicitly labeled estimate, never exact stop times.
        if (leg.mode == Mode.SUBWAY && levels.distinct().size > 1 && leg.path.size >= 2) {
            levels.forEachIndexed { i, level ->
                val from = i.toDouble() / levels.size
                val to = (i + 1).toDouble() / levels.size
                val startNanos = (leg.duration.toNanos() * from).toLong()
                val endNanos = (leg.duration.toNanos() * to).toLong()
                expandedLegs += leg.copy(path = slicePath(leg.path, from, to), duration = Duration.ofNanos(endNanos - startNanos),
                    startName = detail.stops[i], endName = detail.stops[i + 1], groundLevel = level, estimatedStationTiming = true)
                expandedDetails += detail.copy(stops = detail.stops.subList(i, i + 2))
            }
        } else {
            expandedLegs += leg
            expandedDetails += detail
        }
    }
    return TransitRouteOption(
        route = Route(expandedLegs),
        type = when (properties.type) {
            "BUS" -> TransitRouteType.BUS
            "SUBWAY" -> TransitRouteType.SUBWAY
            "BUS_AND_SUBWAY" -> TransitRouteType.BUS_AND_SUBWAY
            else -> TransitRouteType.OTHER
        },
        totalDuration = Duration.ofSeconds(properties.totalTime.roundToLong()),
        distanceMeters = properties.totalDistance.roundToInt(),
        transfers = properties.transfers,
        fare = properties.fare?.value,
        legDetails = expandedDetails,
    )
}

/** 카카오 좌표 [경도, 위도] → [LatLng]. 형식이 맞지 않으면 null. */
internal fun List<Double>.toLatLng(): LatLng? =
    if (size >= 2) LatLng(latitude = this[1], longitude = this[0]) else null

/** Preserve turns while cutting an estimated station interval by traveled distance. */
internal fun slicePath(path: List<LatLng>, from: Double, to: Double): List<LatLng> {
    val lengths = path.zipWithNext { a, b -> com.sunseat.app.domain.ExposureAnalyzer.distanceMeters(a, b) }
    val total = lengths.sum()
    if (total <= 0) return listOf(path.first(), path.last())
    val cumulative = listOf(0.0) + lengths.runningFold(0.0) { a, b -> a + b }.drop(1)
    fun at(fraction: Double): LatLng {
        val target = total * fraction
        val index = lengths.indices.firstOrNull { cumulative[it + 1] >= target && lengths[it] > 0 } ?: lengths.lastIndex
        val f = if (lengths[index] <= 0) 0.0 else ((target - cumulative[index]) / lengths[index]).coerceIn(0.0, 1.0)
        return LatLng(path[index].latitude + (path[index + 1].latitude - path[index].latitude) * f,
            path[index].longitude + (path[index + 1].longitude - path[index].longitude) * f)
    }
    return listOf(at(from)) + path.indices.filter { cumulative[it] > total * from && cumulative[it] < total * to }.map { path[it] } + at(to)
}
