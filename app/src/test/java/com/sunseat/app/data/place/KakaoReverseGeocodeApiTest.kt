package com.sunseat.app.data.place

import com.sunseat.app.data.network.ApiError
import com.sunseat.app.data.network.ApiResult
import com.sunseat.app.data.network.enqueueJson
import com.sunseat.app.data.network.fixture
import com.sunseat.app.data.network.kakaoRetrofit
import com.sunseat.app.domain.LatLng
import kotlinx.coroutines.runBlocking
import mockwebserver3.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test

/** 좌표→주소(#85). 응답은 직접 만든 값이다. */
class KakaoReverseGeocodeApiTest {
    private lateinit var server: MockWebServer
    private lateinit var api: ReverseGeocodeApi

    @Before fun setUp() {
        server = MockWebServer()
        server.start()
        api = KakaoReverseGeocodeApi(server.kakaoRetrofit())
    }

    @After fun tearDown() = server.close()

    @Test fun prefersRoadAddressAndSendsLongitudeAsX() = runBlocking {
        server.enqueueJson(fixture("kakao/coord2address.json"))
        val result = api.address(LatLng(37.5, 127.01))
        assertEquals(ApiResult.Success("서울 가상구 예시로 200"), result)
        val url = server.takeRequest().url
        assertEquals("/v2/local/geo/coord2address.json", url.encodedPath)
        assertEquals("127.01", url.queryParameter("x"))
        assertEquals("37.5", url.queryParameter("y"))
    }

    @Test fun fallsBackToLotAddressAndReturnsNullWithoutAddress() = runBlocking {
        server.enqueueJson("""{"documents":[{"road_address":null,"address":{"address_name":"서울 가상구 견본동 3"}}]}""")
        assertEquals(ApiResult.Success("서울 가상구 견본동 3"), api.address(LatLng(37.5, 127.0)))
        server.enqueueJson("""{"meta":{"total_count":0},"documents":[]}""")
        assertNull((api.address(LatLng(37.5, 127.0)) as ApiResult.Success).value)
    }

    @Test fun classifiesErrorsLikeOtherKakaoCalls() = runBlocking {
        server.enqueueJson("""{"errorType":"AccessDeniedError","message":"denied"}""", code = 401)
        assertEquals(ApiResult.Failure(ApiError.UNAUTHORIZED, 401), api.address(LatLng(37.5, 127.0)))
        val noKey = KakaoReverseGeocodeApi(server.kakaoRetrofit(apiKey = ""))
        assertEquals(ApiResult.Failure(ApiError.MISSING_KEY), noKey.address(LatLng(37.5, 127.0)))
    }
}
