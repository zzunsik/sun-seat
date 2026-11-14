package com.sunseat.app.data.network

import mockwebserver3.MockResponse
import mockwebserver3.MockWebServer
import retrofit2.Retrofit

/** 테스트용 가짜 키. 실제 키가 아니다. */
internal const val TEST_KEY = "test-key"

internal fun MockWebServer.kakaoRetrofit(apiKey: String = TEST_KEY): Retrofit =
    KakaoHttp.retrofit(url("/").toString(), KakaoHttp.okHttpClient(apiKey))

internal fun MockWebServer.enqueueJson(body: String, code: Int = 200) {
    enqueue(
        MockResponse.Builder()
            .code(code)
            .addHeader("Content-Type", "application/json; charset=UTF-8")
            .body(body)
            .build(),
    )
}

/** 테스트 리소스(`src/test/resources`)의 직접 만든 예시 응답을 읽는다. */
internal fun fixture(path: String): String =
    requireNotNull(Thread.currentThread().contextClassLoader?.getResource(path)) { "no fixture $path" }
        .readText(Charsets.UTF_8)

/** 이미 닫힌 서버 주소. 연결이 거부되어 네트워크 오류를 만든다. */
internal fun closedServerUrl(): String {
    val server = MockWebServer()
    server.start()
    val url = server.url("/").toString()
    server.close()
    return url
}
