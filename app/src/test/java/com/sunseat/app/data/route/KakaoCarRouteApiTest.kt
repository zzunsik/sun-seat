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

class KakaoCarRouteApiTest {
    private lateinit var server: MockWebServer
    private lateinit var api: CarRouteApi

    private val origin = LatLng(37.5, 127.0)
    private val destination = LatLng(37.52, 127.03)

    @Before
    fun setUp() {
        server = MockWebServer()
        server.start()
        api = KakaoCarRouteApi(server.kakaoRetrofit())
    }

    @After
    fun tearDown() {
        server.close()
    }

    private fun success(result: ApiResult<CarRouteResult>) = (result as ApiResult.Success).value

    @Test
    fun sendsOriginDestinationAsLngLat() = runBlocking {
        server.enqueueJson(fixture("kakao/car_directions.json"))
        api.search(origin, destination)

        val request = server.takeRequest()
        assertEquals("/v1/directions", request.url.encodedPath)
        assertEquals("127.0,37.5", request.url.queryParameter("origin"))
        assertEquals("127.03,37.52", request.url.queryParameter("destination"))
        assertEquals("KakaoAK test-key", request.headers["Authorization"])
    }

    @Test
    fun convertsToSingleCarLeg() = runBlocking {
        server.enqueueJson(fixture("kakao/car_directions.json"))
        val result = success(api.search(origin, destination))

        assertNull(result.noRouteReason)
        val option = requireNotNull(result.option)
        val legs = option.route.legs
        assertEquals(1, legs.size)
        assertEquals(Mode.CAR, legs[0].mode)
        // 도로 경계에서 겹치는 (127.02, 37.51)은 한 번만 들어간다.
        assertEquals(
            listOf(
                LatLng(37.5, 127.0),
                LatLng(37.505, 127.01),
                LatLng(37.51, 127.02),
                LatLng(37.52, 127.03),
            ),
            legs[0].path,
        )
        assertEquals(Duration.ofSeconds(905), option.duration)
        assertEquals(4321, option.distanceMeters)
        assertEquals(9800, option.taxiFare)
        assertEquals(0, option.tollFare)
    }

    @Test
    fun noRouteCodes() = runBlocking {
        val cases = mapOf(
            102 to CarNoRouteReason.NO_START_ROAD,
            103 to CarNoRouteReason.NO_END_ROAD,
            104 to CarNoRouteReason.SAME_POINTS,
            1 to CarNoRouteReason.NO_RESULTS,
        )
        for ((code, reason) in cases) {
            server.enqueueJson(
                """{"trans_id":"x","routes":[{"result_code":$code,"result_msg":"예시 실패 문구"}]}""",
            )
            val result = success(api.search(origin, destination))
            assertNull(result.option)
            assertEquals(reason, result.noRouteReason)
            assertEquals("예시 실패 문구", result.message)
        }
    }

    @Test
    fun emptyRoutesIsNoResults() = runBlocking {
        server.enqueueJson("""{"trans_id":"x","routes":[]}""")
        assertEquals(
            CarRouteResult(null, CarNoRouteReason.NO_RESULTS),
            success(api.search(origin, destination)),
        )
    }

    @Test
    fun errorClassification() = runBlocking {
        server.enqueueJson("""{"code":-401,"msg":"wrong appKey"}""", 401)
        assertEquals(ApiResult.Failure(ApiError.UNAUTHORIZED, 401), api.search(origin, destination))

        server.enqueueJson("""{"code":-10,"msg":"limit exceeded"}""", 400)
        assertEquals(ApiResult.Failure(ApiError.QUOTA_EXCEEDED, 400), api.search(origin, destination))

        server.enqueueJson("""{"code":-2,"msg":"invalid origin"}""", 400)
        assertEquals(ApiResult.Failure(ApiError.UNKNOWN, 400), api.search(origin, destination))

        val noKey = KakaoCarRouteApi(server.kakaoRetrofit(apiKey = ""))
        assertEquals(ApiResult.Failure(ApiError.MISSING_KEY), noKey.search(origin, destination))
        assertEquals(3, server.requestCount)
    }

    @Test
    fun flatVertexesToLatLngs() {
        assertEquals(
            listOf(LatLng(2.0, 1.0), LatLng(4.0, 3.0)),
            listOf(1.0, 2.0, 3.0, 4.0, 5.0).toLatLngs(),
        )
    }
}
