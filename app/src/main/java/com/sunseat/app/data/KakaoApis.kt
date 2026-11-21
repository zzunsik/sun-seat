package com.sunseat.app.data

import com.sunseat.app.BuildConfig
import com.sunseat.app.data.network.KakaoHttp
import com.sunseat.app.data.place.KakaoPlaceSearchApi
import com.sunseat.app.data.place.PlaceSearchApi
import com.sunseat.app.data.route.CarRouteApi
import com.sunseat.app.data.route.KakaoCarRouteApi
import com.sunseat.app.data.route.KakaoTransitRouteApi
import com.sunseat.app.data.route.TransitRouteApi

/**
 * 카카오 API 구현을 한곳에서 만든다. 화면은 인터페이스만 받아 테스트에서 가짜로 바꿀 수 있다.
 * 모든 호출은 앱 API 게이트웨이(`workers/api`, #189)의 `kakao/` 경로로 간다. REST 키는 앱에 없다.
 * 앱 API 주소가 비어 있으면 모든 호출이 네트워크 없이 `ApiError.MISSING_KEY`로 실패한다.
 */
class KakaoApis(
    apiBaseUrl: String = BuildConfig.SUNSEAT_API_BASE_URL,
) {
    private val baseUrl = KakaoHttp.apiBaseUrl(apiBaseUrl)
    private val retrofit by lazy {
        KakaoHttp.retrofit(
            (baseUrl ?: KakaoHttp.UNCONFIGURED_BASE_URL) + KakaoHttp.GROUP_PATH,
            KakaoHttp.okHttpClient(configured = baseUrl != null),
        )
    }

    val placeSearch: PlaceSearchApi by lazy { KakaoPlaceSearchApi(retrofit) }

    val reverseGeocode: com.sunseat.app.data.place.ReverseGeocodeApi by lazy { com.sunseat.app.data.place.KakaoReverseGeocodeApi(retrofit) }

    val transitRoutes: TransitRouteApi by lazy { KakaoTransitRouteApi(retrofit) }

    val carRoutes: CarRouteApi by lazy { KakaoCarRouteApi(retrofit) }
}
