package com.sunseat.app.data.network

import mockwebserver3.MockResponse
import mockwebserver3.MockWebServer
import retrofit2.Retrofit

/** 가짜 서버를 앱 API 게이트웨이로 쓰는 Retrofit(기준 주소 `/kakao/`). [apiConfigured]가 false면 앱 API 주소가 없는 빌드와 같다. */
internal fun MockWebServer.kakaoRetrofit(apiConfigured: Boolean = true): Retrofit =
    KakaoHttp.retrofit(url("/" + KakaoHttp.GROUP_PATH).toString(), KakaoHttp.okHttpClient(configured = apiConfigured))

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
