package com.sunseat.app

import android.app.Application
import com.sunseat.app.data.KakaoApis
import com.sunseat.app.location.LocationProvider

/** 앱 전역 의존성. 화면은 인터페이스만 받아서 테스트에서 가짜로 바꾼다. */
class SunSeatApp : Application() {
    val graph: AppGraph by lazy { AppGraph(this) }
}

class AppGraph(app: Application) {
    val kakao: KakaoApis by lazy { KakaoApis() }
    val locationProvider: LocationProvider by lazy { LocationProvider(app) }
}
