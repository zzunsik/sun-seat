package com.sunseat.app.data.favorites

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import com.sunseat.app.data.place.Place
import com.sunseat.app.data.settings.userPreferences
import java.io.IOException
import java.util.UUID
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.retryWhen
import kotlinx.coroutines.launch
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray

/** 설정과 같은 DataStore 인스턴스의 독립 키에 사용자 선택 장소만 저장한다. */
class DataStoreFavoritesRepository(private val store: DataStore<Preferences>, scope: CoroutineScope, private val now: () -> Long = System::currentTimeMillis) : FavoritesRepository {
    constructor(context: Context, scope: CoroutineScope) : this(context.applicationContext.userPreferences, scope)
    private val current = MutableStateFlow<List<Favorite>>(emptyList())
    override val favorites = current.asStateFlow()
    private val json = Json { ignoreUnknownKeys = true }
    init {
        scope.launch {
            store.data.retryWhen { cause, _ -> if (cause is IOException) { delay(1_000); true } else false }.collect { current.value = decode(it) }
        }
    }
    override suspend fun put(place: Place, kind: FavoriteKind) { mutate { add(it, place, kind) } }
    override suspend fun toggle(place: Place) {
        mutate { list -> if (list.any { it.matches(place) }) list.filterNot { it.matches(place) } else add(list, place, FavoriteKind.CUSTOM) }
    }
    override suspend fun rename(id: String, name: String) {
        mutate { list -> list.map { if (it.id == id) it.copy(name = name.trim().take(MAX_NAME)) else it } }
    }
    override suspend fun remove(id: String): RemovedFavorite? {
        var removed: RemovedFavorite? = null
        mutate { list ->
            val index = list.indexOfFirst { it.id == id }
            if (index >= 0) removed = RemovedFavorite(list[index], index)
            list.filterNot { it.id == id }
        }
        return removed
    }
    override suspend fun restore(removed: RemovedFavorite) {
        mutate { list ->
            if (list.any { it.id == removed.favorite.id }) list
            else {
                if (list.size >= MAX_FAVORITES) throw FavoriteLimitException()
                if (list.any { it.location == removed.favorite.location || (removed.favorite.kind != FavoriteKind.CUSTOM && it.kind == removed.favorite.kind) }) throw FavoriteConflictException()
                list.toMutableList().apply { add(removed.index.coerceIn(0, size), removed.favorite) }
            }
        }
    }
    private fun add(list: List<Favorite>, place: Place, kind: FavoriteKind): List<Favorite> {
        require(place.location.latitude.isFinite() && place.location.latitude in -90.0..90.0 && place.location.longitude.isFinite() && place.location.longitude in -180.0..180.0)
        val previous = list.firstOrNull { it.matches(place) }
        val remaining = list.filterNot { it.matches(place) || (kind != FavoriteKind.CUSTOM && it.kind == kind) }
        if (remaining.size >= MAX_FAVORITES) throw FavoriteLimitException()
        val next = Favorite(previous?.id ?: UUID.randomUUID().toString(), kind, if (kind == FavoriteKind.CUSTOM) place.name.take(MAX_NAME) else "", place.name, place.roadAddress.ifBlank { place.address }, place.location.latitude, place.location.longitude, previous?.createdAt ?: now())
        return listOf(next) + remaining
    }
    private suspend fun mutate(transform: (List<Favorite>) -> List<Favorite>) {
        store.edit { prefs -> prefs[KEY] = json.encodeToString(transform(decode(prefs))) }
    }
    private fun decode(prefs: Preferences): List<Favorite> = runCatching {
        json.parseToJsonElement(prefs[KEY] ?: "[]").jsonArray.mapNotNull { element ->
            runCatching { json.decodeFromJsonElement(Favorite.serializer(), element) }.getOrNull()?.takeIf {
                it.id.isNotBlank() && it.placeName.isNotBlank() && it.lat.isFinite() && it.lat in -90.0..90.0 && it.lng.isFinite() && it.lng in -180.0..180.0
            }
        }.distinctBy { it.id }.take(MAX_FAVORITES)
    }.getOrDefault(emptyList())
    companion object {
        const val MAX_FAVORITES = 50
        const val MAX_NAME = 30
        private val KEY = stringPreferencesKey("favorites_json")
    }
}
