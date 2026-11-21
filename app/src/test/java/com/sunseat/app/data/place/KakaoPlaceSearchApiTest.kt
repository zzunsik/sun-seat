package com.sunseat.app.data.place

import com.sunseat.app.data.network.ApiResult
import com.sunseat.app.data.network.enqueueJson
import com.sunseat.app.data.network.fixture
import com.sunseat.app.data.network.kakaoRetrofit
import com.sunseat.app.domain.LatLng
import kotlinx.coroutines.runBlocking
import mockwebserver3.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test

class KakaoPlaceSearchApiTest {
    private lateinit var server: MockWebServer
    private lateinit var api: PlaceSearchApi

    @Before
    fun setUp() {
        server = MockWebServer()
        server.start()
        api = KakaoPlaceSearchApi(server.kakaoRetrofit())
    }

    @After
    fun tearDown() {
        server.close()
    }

    @Test
    fun convertsSampleResponse() = runBlocking {
        server.enqueueJson(fixture("kakao/place_keyword.json"))

        val result = api.search("예시역", near = LatLng(37.5, 127.0), size = 5)

        val places = (result as ApiResult.Success).value
        // 좌표가 깨진 세 번째 문서는 건너뛴다.
        assertEquals(2, places.size)
        assertEquals(
            Place(
                id = "1000001",
                name = "예시역 1호선",
                address = "서울 가상구 예시동 1-1",
                roadAddress = "서울 가상구 예시로 100",
                location = LatLng(37.5, 127.01),
                category = "교통,수송 > 지하철,전철 > 예시선",
            ),
            places[0],
        )
        assertEquals("", places[1].roadAddress)

        val request = server.takeRequest()
        assertEquals("/kakao/local/search/keyword", request.url.encodedPath)
        assertEquals("예시역", request.url.queryParameter("query"))
        assertEquals("127.0", request.url.queryParameter("x"))
        assertEquals("37.5", request.url.queryParameter("y"))
        assertEquals("5", request.url.queryParameter("size"))
    }

    @Test
    fun emptyResult() = runBlocking {
        server.enqueueJson(
            """{"documents":[],"meta":{"is_end":true,"pageable_count":0,"total_count":0}}""",
        )

        val result = api.search("없는장소")

        assertEquals(ApiResult.Success(emptyList<Place>()), result)
        val request = server.takeRequest()
        assertEquals(null, request.url.queryParameter("x"))
        assertEquals("15", request.url.queryParameter("size"))
    }

    @Test
    fun blankQueryDoesNotCall() = runBlocking {
        assertEquals(ApiResult.Success(emptyList<Place>()), api.search("   "))
        assertEquals(0, server.requestCount)
    }
}
