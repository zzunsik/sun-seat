package com.sunseat.app.data.network

import com.sunseat.app.data.place.KakaoPlaceSearchApi
import kotlinx.coroutines.runBlocking
import mockwebserver3.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test

/** 앱 API 게이트웨이(#189)가 직접 만드는 오류 응답(workers/api/src/index.ts)의 분류. */
class GatewayErrorTest {
    private lateinit var server: MockWebServer

    @Before fun setUp() {
        server = MockWebServer()
        server.start()
    }

    @After fun tearDown() = server.close()

    private suspend fun search() = KakaoPlaceSearchApi(server.kakaoRetrofit()).search("예시")

    @Test fun rateLimitedIsQuota() = runBlocking {
        server.enqueueJson("""{"error":"rate_limited"}""", 429)
        assertEquals(ApiResult.Failure(ApiError.QUOTA_EXCEEDED, 429), search())
    }

    @Test fun forbiddenIsRetryableRefusal() = runBlocking {
        server.enqueueJson("""{"error":"forbidden"}""", 403)
        val result = search() as ApiResult.Failure
        assertEquals(ApiResult.Failure(ApiError.UNAUTHORIZED, 403), result)
        assertEquals(true, result.error.retryable)
    }

    @Test fun gatewayServerErrorsAreUnknown() = runBlocking {
        for (code in listOf(500, 502, 503)) {
            server.enqueueJson("""{"error":"upstream_unavailable"}""", code)
            assertEquals(ApiResult.Failure(ApiError.UNKNOWN, code), search())
        }
    }
}
