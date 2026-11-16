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

class AppGraph(app: Application) {
    private val applicationScope = kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.SupervisorJob() + kotlinx.coroutines.Dispatchers.IO)
    val favorites: com.sunseat.app.data.favorites.FavoritesRepository by lazy { com.sunseat.app.data.favorites.DataStoreFavoritesRepository(app, applicationScope) }
    val settings: com.sunseat.app.data.settings.SettingsRepository by lazy { com.sunseat.app.data.settings.DataStoreSettingsRepository(app, applicationScope) }
    val recent: com.sunseat.app.data.recent.RecentPlacesRepository by lazy { com.sunseat.app.data.recent.DataStoreRecentPlacesRepository(app, applicationScope) }
    val kakao: KakaoApis by lazy { KakaoApis() }
    val locationProvider: LocationProvider by lazy { LocationProvider(app) }
}
