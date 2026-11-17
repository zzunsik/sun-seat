package com.sunseat.app.ui.navigation

import android.app.Application
import androidx.datastore.core.okio.OkioStorage
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.PreferencesSerializer
import com.sunseat.app.AppGraph
import com.sunseat.app.data.KakaoApis
import com.sunseat.app.data.favorites.DataStoreFavoritesRepository
import com.sunseat.app.data.favorites.FavoritesRepository
import com.sunseat.app.data.network.TEST_KEY
import com.sunseat.app.data.network.fixture
import com.sunseat.app.data.recent.DataStoreRecentPlacesRepository
import com.sunseat.app.data.recent.RecentPlacesRepository
import com.sunseat.app.data.settings.Settings
import com.sunseat.app.data.settings.SettingsRepository
import com.sunseat.app.location.LocationProvider
import com.sunseat.app.location.LocationResult
import java.io.File
import java.time.Clock
import java.time.Instant
import java.time.ZoneId
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update
import mockwebserver3.Dispatcher
import mockwebserver3.MockResponse
import mockwebserver3.MockWebServer
import mockwebserver3.RecordedRequest
import mockwebserver3.SocketEffect
import okio.FileSystem
import okio.Path.Companion.toPath

/** 메모리 설정 저장소. 화면 흐름 테스트에서 설정 화면이 바꾼 값을 그대로 다른 화면에 흘린다. */
class InMemorySettings(initial: Settings = Settings.DEFAULT) : SettingsRepository {
    override val settings = MutableStateFlow(initial)
    override val loaded = MutableStateFlow(true)
    override suspend fun update(transform: (Settings) -> Settings) = settings.update(transform)
}

/**
 * 가짜 카카오 서버. 응답은 모두 직접 만든 값(`src/test/resources/kakao`)이고 실제 응답이 아니다.
 * 경로별로 응답을 바꿔 끼워 오류·경로 없음 상태를 만든다.
 */
class FakeKakaoServer : AutoCloseable {
    private val server = MockWebServer()
    @Volatile var placeRequests = 0
    @Volatile var places: () -> MockResponse = { json(fixture("kakao/place_keyword.json")) }
    @Volatile var transit: () -> MockResponse = { json(fixture("kakao/transit_routes.json")) }
    @Volatile var car: () -> MockResponse = { json(fixture("kakao/car_directions.json")) }

    init {
        server.dispatcher = object : Dispatcher() {
            override fun dispatch(request: RecordedRequest): MockResponse = when (request.url.encodedPath) {
                "/v2/local/search/keyword.json" -> { placeRequests++; places() }
                "/v2/routing/publictraffic" -> transit()
                "/v1/directions" -> car()
                else -> MockResponse.Builder().code(404).build()
            }
        }
        server.start()
    }

    val apis: KakaoApis by lazy {
        val url = server.url("/").toString()
        KakaoApis(apiKey = TEST_KEY, dapiBaseUrl = url, naviBaseUrl = url)
    }

    override fun close() = server.close()

    companion object {
        fun json(body: String, code: Int = 200): MockResponse = MockResponse.Builder()
            .code(code)
            .addHeader("Content-Type", "application/json; charset=UTF-8")
            .body(body)
            .build()

        /** 연결을 끊어 클라이언트에서 네트워크 오류(IOException)가 나게 한다. */
        fun disconnect(): MockResponse = MockResponse.Builder().onResponseStart(SocketEffect.ShutdownConnection).build()
    }
}

/** 흐름 테스트용 의존성. 즐겨찾기·최근 장소는 임시 폴더의 실제 DataStore 구현을 쓴다. */
class FlowTestGraph(
    app: Application,
    private val dir: File,
    val server: FakeKakaoServer,
    override val settings: InMemorySettings = InMemorySettings(),
    now: Instant = Instant.parse("2026-11-17T01:00:00Z"),
    override val locationProvider: LocationProvider = LocationProvider { LocationResult.Unavailable },
) : AppGraph(app), AutoCloseable {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private fun store(name: String) = PreferenceDataStoreFactory.create(
        storage = OkioStorage(FileSystem.SYSTEM, PreferencesSerializer) { File(dir, "$name.preferences_pb").absolutePath.toPath() },
        scope = scope,
    )
    override val favorites: FavoritesRepository = DataStoreFavoritesRepository(store("favorites"), scope)
    override val recent: RecentPlacesRepository = DataStoreRecentPlacesRepository(store("recent"), scope)
    override val kakao: KakaoApis get() = server.apis
    override val clock: Clock = Clock.fixed(now, ZoneId.systemDefault())

    override fun close() {
        scope.cancel()
        server.close()
    }
}
