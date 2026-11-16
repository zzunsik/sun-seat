package com.sunseat.app

import android.app.Application
import com.sunseat.app.data.KakaoApis
import com.sunseat.app.location.LocationProvider

/** 앱 전역 의존성. 화면은 인터페이스만 받아서 테스트에서 가짜로 바꾼다. */
class SunSeatApp : Application() {
    override fun onCreate() {
        super.onCreate()
        if (BuildConfig.KAKAO_NATIVE_APP_KEY.isNotBlank()) {
            com.kakao.vectormap.KakaoMapSdk.init(this, BuildConfig.KAKAO_NATIVE_APP_KEY)
        }
    }

    val graph: AppGraph by lazy { AppGraph(this) }
}

/** 화면 흐름 테스트(#60)는 하위 클래스로 저장소·카카오 API·위치를 가짜로 바꾼다. */
open class AppGraph(app: Application) {
    private val applicationScope = kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.SupervisorJob() + kotlinx.coroutines.Dispatchers.IO)
    open val favorites: com.sunseat.app.data.favorites.FavoritesRepository by lazy { com.sunseat.app.data.favorites.DataStoreFavoritesRepository(app, applicationScope) }
    open val settings: com.sunseat.app.data.settings.SettingsRepository by lazy { com.sunseat.app.data.settings.DataStoreSettingsRepository(app, applicationScope) }
    open val recent: com.sunseat.app.data.recent.RecentPlacesRepository by lazy { com.sunseat.app.data.recent.DataStoreRecentPlacesRepository(app, applicationScope) }
    open val kakao: KakaoApis by lazy { KakaoApis() }
    open val locationProvider: LocationProvider by lazy { LocationProvider(app) }
    /** 경로 후보·결과의 "지금". 흐름 테스트는 고정 시각을 넣어 해 위치를 정한다. */
    open val clock: java.time.Clock get() = java.time.Clock.systemDefaultZone()
}
