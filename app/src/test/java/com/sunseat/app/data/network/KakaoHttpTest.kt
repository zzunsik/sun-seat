package com.sunseat.app.data.network

import com.sunseat.app.data.place.KakaoPlaceSearchApi
import kotlinx.coroutines.runBlocking
import mockwebserver3.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test

/** 앱 API 게이트웨이 호출의 헤더와 오류 구분. 장소 검색 호출로 확인한다. */
class KakaoHttpTest {
    private lateinit var server: MockWebServer

    @Before
    fun setUp() {
        server = MockWebServer()
        server.start()
    }

    @After
    fun tearDown() {
        server.close()
    }

    private suspend fun searchWith(apiConfigured: Boolean = true) =
        KakaoPlaceSearchApi(server.kakaoRetrofit(apiConfigured)).search("예시")

    @Test
    fun sendsNoAuthorizationHeader() = runBlocking {
        server.enqueueJson("""{"documents":[]}""")
        searchWith()
        // REST 키는 앱 API 게이트웨이에만 있다(#189). 앱은 인증 헤더를 보내지 않는다.
        assertNull(server.takeRequest().headers["Authorization"])
    }

    @Test
    fun missingKeyFailsWithoutNetwork() = runBlocking {
        assertEquals(ApiResult.Failure(ApiError.MISSING_KEY), searchWith(apiConfigured = false))
        assertEquals(0, server.requestCount)
    }

    @Test
    fun unauthorized() = runBlocking {
        server.enqueueJson("""{"errorType":"AccessDeniedError","message":"wrong appKey"}""", 401)
        assertEquals(ApiResult.Failure(ApiError.UNAUTHORIZED, 401), searchWith())
    }

    @Test
    fun forbiddenIsUnauthorized() = runBlocking {
        server.enqueueJson("""{"errorType":"AccessDeniedError","message":"disabled service"}""", 403)
        assertEquals(ApiResult.Failure(ApiError.UNAUTHORIZED, 403), searchWith())
    }

    @Test
    fun tooManyRequestsIsQuota() = runBlocking {
        server.enqueueJson("""{"errorType":"RequestThrottled","message":"too many"}""", 429)
        assertEquals(ApiResult.Failure(ApiError.QUOTA_EXCEEDED, 429), searchWith())
    }

    @Test
    fun limitErrorBodyIsQuota() = runBlocking {
        server.enqueueJson("""{"code":-10,"msg":"API limit has been exceeded."}""", 400)
        assertEquals(ApiResult.Failure(ApiError.QUOTA_EXCEEDED, 400), searchWith())
    }

    @Test
    fun serverErrorIsUnknown() = runBlocking {
        server.enqueueJson("""{"errorType":"InternalError","message":"x"}""", 500)
        assertEquals(ApiResult.Failure(ApiError.UNKNOWN, 500), searchWith())
    }

    @Test
    fun malformedBodyIsUnknown() = runBlocking {
        server.enqueueJson("""{"documents": [ {"id": """)
        assertEquals(ApiResult.Failure(ApiError.UNKNOWN), searchWith())
    }

    @Test
    fun connectionFailureIsNetwork() = runBlocking {
        val retrofit = KakaoHttp.retrofit(closedServerUrl(), KakaoHttp.okHttpClient())
        assertEquals(
            ApiResult.Failure(ApiError.NETWORK),
            KakaoPlaceSearchApi(retrofit).search("예시"),
        )
    }

    @Test
    fun classifyHttpErrorTable() {
        assertEquals(ApiError.UNAUTHORIZED, classifyHttpError(401, null))
        assertEquals(ApiError.QUOTA_EXCEEDED, classifyHttpError(429, ""))
        assertEquals(ApiError.QUOTA_EXCEEDED, classifyHttpError(403, """{"message":"quota exceeded"}"""))
        assertEquals(ApiError.QUOTA_EXCEEDED, classifyHttpError(400, """{"code":-11,"msg":"x"}"""))
        assertEquals(ApiError.UNAUTHORIZED, classifyHttpError(400, """{"code":-903,"msg":"x"}"""))
        assertEquals(ApiError.UNKNOWN, classifyHttpError(400, """{"code":-2,"msg":"bad param"}"""))
        assertEquals(ApiError.UNKNOWN, classifyHttpError(400, """{"code":-100,"msg":"x"}"""))
    }
}
