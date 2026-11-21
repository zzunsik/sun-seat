package com.sunseat.app.data

import com.sunseat.app.data.network.ApiError
import com.sunseat.app.data.network.ApiResult
import com.sunseat.app.data.network.KakaoHttp
import com.sunseat.app.data.network.enqueueJson
import com.sunseat.app.data.network.fixture
import com.sunseat.app.data.network.retryable
import com.sunseat.app.domain.LatLng
import kotlinx.coroutines.runBlocking
import mockwebserver3.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/** 앱 API 게이트웨이(#189): 네 카카오 API가 게이트웨이의 `kakao/` 경로로 가고, 앱 API 주소가 없으면 호출하지 않는다. */
class KakaoApisTest {
    private lateinit var server: MockWebServer
    private val origin = LatLng(37.5, 127.0)
    private val destination = LatLng(37.52, 127.03)

    @Before fun setUp() {
        server = MockWebServer()
        server.start()
    }

    @After fun tearDown() = server.close()

    @Test fun allFourApisGoToTheGatewayWithoutAuthorization() = runBlocking {
        val apis = KakaoApis(apiBaseUrl = server.url("/").toString())
        server.enqueueJson(fixture("kakao/place_keyword.json"))
        server.enqueueJson(fixture("kakao/coord2address.json"))
        server.enqueueJson(fixture("kakao/transit_routes.json"))
        server.enqueueJson(fixture("kakao/car_directions.json"))

        assertTrue(apis.placeSearch.search("예시") is ApiResult.Success)
        assertTrue(apis.reverseGeocode.address(origin) is ApiResult.Success)
        assertTrue(apis.transitRoutes.search(origin, destination) is ApiResult.Success)
        assertTrue(apis.carRoutes.search(origin, destination) is ApiResult.Success)

        val requests = List(4) {
            val request = server.takeRequest()
            assertNull(request.headers["Authorization"])
            assertEquals("GET", request.method)
            request.url
        }
        assertEquals(
            listOf("/kakao/local/search/keyword", "/kakao/local/geo/coord2address", "/kakao/routing/publictraffic", "/kakao/navi/directions"),
            requests.map { it.encodedPath },
        )

        // 게이트웨이 허용 목록(workers/api/src/index.ts GROUPS.kakao)과 앱이 보내는 경로·파라미터가 어긋나면 실패한다.
        val gateway = gatewayKakaoRoutes()
        assertEquals(requests.map { it.encodedPath.removePrefix("/kakao/") }.toSet(), gateway.keys)
        for (url in requests) {
            val (allowed, required) = gateway.getValue(url.encodedPath.removePrefix("/kakao/"))
            assertTrue("${url.encodedPath} ${url.queryParameterNames}", allowed.containsAll(url.queryParameterNames))
            assertTrue("${url.encodedPath} ${url.queryParameterNames}", url.queryParameterNames.containsAll(required))
        }
    }

    @Test fun longQueryIsCutBelowTheGatewayParamLimit() = runBlocking {
        val apis = KakaoApis(apiBaseUrl = server.url("/").toString())
        server.enqueueJson(fixture("kakao/place_keyword.json"))
        apis.placeSearch.search("가".repeat(300))
        val sent = requireNotNull(server.takeRequest().url.queryParameter("query"))
        val gatewayLimit = Regex("""const MAX_PARAM_LENGTH = (\d+);""").find(gatewaySource())!!.groupValues[1].toInt()
        assertEquals(com.sunseat.app.data.place.KakaoPlaceSearchApi.MAX_QUERY_LENGTH, sent.length)
        assertTrue("$gatewayLimit", sent.length <= gatewayLimit)
    }

    @Test fun gatewayGroupMatchesTheAppPathPrefix() {
        // 앱의 기준 경로(KakaoHttp.GROUP_PATH)와 게이트웨이의 경로 묶음 이름이 같아야 한다.
        assertTrue(Regex("""\n  ${KakaoHttp.GROUP_PATH.removeSuffix("/")}: \{""").containsMatchIn(gatewaySource()))
    }

    /** 게이트웨이 소스. 테스트 작업 폴더는 app/이다. */
    private fun gatewaySource(): String = java.io.File("../workers/api/src/index.ts").readText()

    /** 게이트웨이 소스에서 kakao 경로 → (허용 파라미터, 필수 파라미터)를 읽는다. 경로 항목은 한 줄에 하나씩 쓴다는 가정. */
    private fun gatewayKakaoRoutes(): Map<String, Pair<Set<String>, Set<String>>> {
        val source = gatewaySource()
        val list = { s: String -> Regex("\"([a-z_]+)\"").findAll(s).map { it.groupValues[1] }.toSet() }
        return Regex("""\"([a-z0-9/]+)\": \{ upstream: [^,]+, params: \[([^\]]*)\], required: \[([^\]]*)\] \}""")
            .findAll(source)
            .associate { it.groupValues[1] to (list(it.groupValues[2]) to list(it.groupValues[3])) }
    }

    @Test fun gatewayUnderAPathKeepsThePrefix() = runBlocking {
        val apis = KakaoApis(apiBaseUrl = server.url("/sub").toString())
        server.enqueueJson(fixture("kakao/car_directions.json"))
        apis.carRoutes.search(origin, destination)
        assertEquals("/sub/kakao/navi/directions", server.takeRequest().url.encodedPath)
    }

    @Test fun blankApiUrlFailsEveryApiWithoutNetwork() = runBlocking {
        for (blank in listOf("", "   ", "not a url", "ftp://api.example/")) {
            val apis = KakaoApis(apiBaseUrl = blank)
            assertEquals(ApiResult.Failure(ApiError.MISSING_KEY), apis.placeSearch.search("예시"))
            assertEquals(ApiResult.Failure(ApiError.MISSING_KEY), apis.reverseGeocode.address(origin))
            assertEquals(ApiResult.Failure(ApiError.MISSING_KEY), apis.transitRoutes.search(origin, destination))
            assertEquals(ApiResult.Failure(ApiError.MISSING_KEY), apis.carRoutes.search(origin, destination))
        }
        assertEquals(0, server.requestCount)
    }

    @Test fun apiBaseUrlNormalization() {
        assertEquals("https://api.example/", KakaoHttp.apiBaseUrl("https://api.example"))
        assertEquals("https://api.example/", KakaoHttp.apiBaseUrl("  https://api.example/  "))
        assertEquals("https://api.example/sub/", KakaoHttp.apiBaseUrl("https://api.example/sub"))
        assertNull(KakaoHttp.apiBaseUrl(""))
        assertNull(KakaoHttp.apiBaseUrl("api.example"))
        assertNull(KakaoHttp.apiBaseUrl("https://api.example/?key=x"))
    }

    @Test fun onlyMissingApiUrlIsNotRetryable() {
        assertFalse(ApiError.MISSING_KEY.retryable)
        for (error in ApiError.entries - ApiError.MISSING_KEY) assertTrue(error.name, error.retryable)
    }
}
