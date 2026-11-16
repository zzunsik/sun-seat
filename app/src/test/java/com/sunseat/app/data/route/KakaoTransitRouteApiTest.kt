package com.sunseat.app.data.route

import com.sunseat.app.data.network.ApiError
import com.sunseat.app.data.network.ApiResult
import com.sunseat.app.data.network.enqueueJson
import com.sunseat.app.data.network.fixture
import com.sunseat.app.data.network.kakaoRetrofit
import com.sunseat.app.domain.LatLng
import com.sunseat.app.domain.Mode
import java.time.Duration
import kotlinx.coroutines.runBlocking
import mockwebserver3.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test

class KakaoTransitRouteApiTest {
    private lateinit var server: MockWebServer
    private lateinit var api: TransitRouteApi

    private val origin = LatLng(37.50, 127.00)
    private val destination = LatLng(37.53, 127.04)

    @Before
    fun setUp() {
        server = MockWebServer()
        server.start()
        api = KakaoTransitRouteApi(server.kakaoRetrofit())
    }

    @After
    fun tearDown() {
        server.close()
    }

    private fun success(result: ApiResult<TransitRoutes>) = (result as ApiResult.Success).value

    @Test
    fun sendsCoordinatesAsStartEndXY() = runBlocking {
        server.enqueueJson(fixture("kakao/transit_routes.json"))
        api.search(origin, destination)

        val url = server.takeRequest().url
        assertEquals("/v2/routing/publictraffic", url.encodedPath)
        assertEquals("127.0", url.queryParameter("start_x"))
        assertEquals("37.5", url.queryParameter("start_y"))
        assertEquals("127.04", url.queryParameter("end_x"))
        assertEquals("37.53", url.queryParameter("end_y"))
    }

    @Test
    fun convertsSubwayTransferRoute() = runBlocking {
        server.enqueueJson(fixture("kakao/transit_routes.json"))
        val routes = success(api.search(origin, destination))

        assertNull(routes.noRouteReason)
        // 알 수 없는 단계 종류(FERRY)가 있는 세 번째 경로는 건너뛴다.
        assertEquals(2, routes.options.size)

        val subway = routes.options[0]
        assertEquals(TransitRouteType.SUBWAY, subway.type)
        assertEquals(listOf(Mode.SUBWAY, Mode.WALK, Mode.SUBWAY), subway.route.legs.map { it.mode })
        assertEquals(Duration.ofSeconds(1800), subway.totalDuration)
        // 구간 합은 대기·접근 시간을 뺀 이동 시간이다.
        assertEquals(Duration.ofSeconds(600 + 90 + 720), subway.route.duration)
        assertEquals(1, subway.transfers)
        assertEquals(1500, subway.fare)
        assertEquals(9000, subway.distanceMeters)

        val first = subway.route.legs[0]
        // [경도, 위도] → LatLng(위도, 경도)
        assertEquals(
            listOf(LatLng(37.50, 127.00), LatLng(37.51, 127.01), LatLng(37.52, 127.02)),
            first.path,
        )
        assertEquals("갑역", first.startName)
        assertEquals("을역", first.endName)
        assertEquals(listOf("갑역", "중간역", "을역"), subway.legDetails[0].stops)
        assertEquals(listOf(TransitVehicle("가상1호선", "일반")), subway.legDetails[0].vehicles)

        val walk = subway.route.legs[1]
        assertEquals(Duration.ofSeconds(90), walk.duration)
        assertEquals(emptyList<TransitVehicle>(), subway.legDetails[1].vehicles)
        assertEquals(subway.route.legs.size, subway.legDetails.size)
    }

    @Test
    fun convertsBusAndSubwayRoute() = runBlocking {
        server.enqueueJson(fixture("kakao/transit_routes.json"))
        val option = success(api.search(origin, destination)).options[1]

        assertEquals(TransitRouteType.BUS_AND_SUBWAY, option.type)
        assertEquals(listOf(Mode.BUS, Mode.WALK, Mode.SUBWAY), option.route.legs.map { it.mode })
        assertNull(option.fare)
        assertEquals(10500, option.distanceMeters)

        val bus = option.route.legs[0]
        assertEquals("정류장A", bus.startName)
        assertEquals("정류장B", bus.endName)
        assertEquals(Duration.ofSeconds(1200), bus.duration)
        // 세 번째 값(고도 등)이 붙은 좌표도 앞의 두 값만 쓴다.
        assertEquals(LatLng(37.53, 127.00), bus.path.last())
        assertEquals(listOf("100", "200"), option.legDetails[0].vehicles.map { it.name })
    }

    @Test
    fun marksSubwayPlatformsFromBundledData() {
        fun option(line: String, vararg stops: String) = PtRoute(steps = listOf(PtStep(
            properties = PtStepProperties(type = "SUBWAY", stops = stops.map { PtStop(it) }, vehicles = listOf(PtVehicle(line))),
        ))).toOption()!!.route.legs.single().groundLevel
        assertEquals(com.sunseat.app.domain.GroundLevel.ABOVE, option("2호선", "성수", "건대입구", "구의"))
        assertEquals(com.sunseat.app.domain.GroundLevel.BELOW, option("2호선", "시청", "을지로입구"))
        assertEquals(com.sunseat.app.domain.GroundLevel.UNKNOWN, option("가상노선", "갑역", "을역"))
    }

    @Test
    fun noResults() = runBlocking {
        server.enqueueJson(
            """{"status":"NO_RESULTS","properties":{"total":0,"bus":0,"subway":0,"busAndSubway":0},"routes":[]}""",
        )
        assertEquals(
            TransitRoutes(emptyList(), NoRouteReason.NO_RESULTS),
            success(api.search(origin, destination)),
        )
    }

    @Test
    fun noRouteStatuses() = runBlocking {
        val cases = mapOf(
            "STARTNODES_NULL" to NoRouteReason.NO_START_STOP,
            "ENDNODES_NULL" to NoRouteReason.NO_END_STOP,
            "EQUAL_POINTS" to NoRouteReason.SAME_POINTS,
        )
        for ((status, reason) in cases) {
            server.enqueueJson("""{"status":"$status","properties":{"total":0}}""")
            assertEquals(reason, success(api.search(origin, destination)).noRouteReason)
        }
    }

    @Test
    fun okWithoutUsableRoutesIsNoResults() = runBlocking {
        server.enqueueJson("""{"status":"OK","routes":[{"properties":{"type":"BUS"},"steps":[]}]}""")
        assertEquals(
            TransitRoutes(emptyList(), NoRouteReason.NO_RESULTS),
            success(api.search(origin, destination)),
        )
    }

    @Test
    fun invalidRequestIsUnknownError() = runBlocking {
        server.enqueueJson("""{"status":"INVALID_REQUEST","properties":{"total":0}}""")
        assertEquals(ApiResult.Failure(ApiError.UNKNOWN), api.search(origin, destination))
    }

    @Test
    fun errorClassification() = runBlocking {
        server.enqueueJson("""{"errorType":"AccessDeniedError","message":"wrong appKey"}""", 401)
        assertEquals(ApiResult.Failure(ApiError.UNAUTHORIZED, 401), api.search(origin, destination))

        server.enqueueJson("""{"code":-10,"msg":"limit exceeded"}""", 400)
        assertEquals(ApiResult.Failure(ApiError.QUOTA_EXCEEDED, 400), api.search(origin, destination))

        val noKey = KakaoTransitRouteApi(server.kakaoRetrofit(apiKey = ""))
        assertEquals(ApiResult.Failure(ApiError.MISSING_KEY), noKey.search(origin, destination))
        assertEquals(2, server.requestCount)
    }
}
