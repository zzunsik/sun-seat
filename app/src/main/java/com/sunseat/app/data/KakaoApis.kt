package com.sunseat.app.data

import com.sunseat.app.BuildConfig
import com.sunseat.app.data.network.KakaoHttp
import com.sunseat.app.data.place.KakaoPlaceSearchApi
import com.sunseat.app.data.place.PlaceSearchApi
import okhttp3.OkHttpClient

/**
 * 카카오 API 구현을 한곳에서 만든다. 화면은 인터페이스만 받아 테스트에서 가짜로 바꿀 수 있다.
 * 키가 비어 있으면 모든 호출이 네트워크 없이 `ApiError.MISSING_KEY`로 실패한다.
 */
class KakaoApis(
    apiKey: String = BuildConfig.KAKAO_REST_API_KEY,
    client: OkHttpClient = KakaoHttp.okHttpClient(apiKey),
    dapiBaseUrl: String = KakaoHttp.DAPI_BASE_URL,
) {
    private val dapi by lazy { KakaoHttp.retrofit(dapiBaseUrl, client) }

    val placeSearch: PlaceSearchApi by lazy { KakaoPlaceSearchApi(dapi) }
}
