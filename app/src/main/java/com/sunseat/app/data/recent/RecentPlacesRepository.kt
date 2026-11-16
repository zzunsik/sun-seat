package com.sunseat.app.data.recent

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import com.sunseat.app.data.place.Place
import com.sunseat.app.data.settings.userPreferences
import com.sunseat.app.domain.LatLng
import com.sunseat.app.domain.TravelMode
import java.io.IOException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.retryWhen
import kotlinx.coroutines.launch
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray

@Serializable
data class RecentPlace(
    val id: String, val name: String, val address: String,
    val latitude: Double, val longitude: Double, val mode: TravelMode, val selectedAt: Long,
) {
    fun asPlace() = Place(id, name, address, address, LatLng(latitude, longitude), "")
}

interface RecentPlacesRepository {
    val places: StateFlow<List<RecentPlace>>
    suspend fun remember(place: Place, mode: TravelMode)
    suspend fun remove(id: String)
    suspend fun clear()
}

/** Only explicitly chosen places are stored, never a query response list or route. */
class DataStoreRecentPlacesRepository(
    private val store: DataStore<Preferences>, scope: CoroutineScope,
    private val now: () -> Long = System::currentTimeMillis,
) : RecentPlacesRepository {
    constructor(context: Context, scope: CoroutineScope) : this(context.applicationContext.userPreferences, scope)
    private val current = MutableStateFlow<List<RecentPlace>>(emptyList())
    override val places = current.asStateFlow()
    private val json = Json { ignoreUnknownKeys = true }
    init {
        scope.launch {
            store.data.retryWhen { error, _ -> if (error is IOException) { delay(1_000); true } else false }
                .collect { current.value = decode(it) }
        }
    }
    override suspend fun remember(place: Place, mode: TravelMode) {
        require(place.id.isNotBlank() && place.name.isNotBlank())
        require(place.location.latitude.isFinite() && place.location.latitude in -90.0..90.0)
        require(place.location.longitude.isFinite() && place.location.longitude in -180.0..180.0)
        store.edit { prefs ->
            val next = RecentPlace(place.id, place.name, place.roadAddress.ifBlank { place.address }, place.location.latitude, place.location.longitude, mode, now())
            prefs[KEY] = json.encodeToString((listOf(next) + decode(prefs).filterNot {
                it.id == place.id || (it.latitude == place.location.latitude && it.longitude == place.location.longitude)
            }).take(MAX_PLACES))
        }
    }
    override suspend fun remove(id: String) { store.edit { it[KEY] = json.encodeToString(decode(it).filterNot { place -> place.id == id }) } }
    override suspend fun clear() { store.edit { it.remove(KEY) } }
    private fun decode(prefs: Preferences): List<RecentPlace> = runCatching {
        json.parseToJsonElement(prefs[KEY] ?: "[]").jsonArray.mapNotNull { element ->
            runCatching { json.decodeFromJsonElement(RecentPlace.serializer(), element) }.getOrNull()?.takeIf {
                it.id.isNotBlank() && it.name.isNotBlank() && it.latitude.isFinite() && it.latitude in -90.0..90.0 && it.longitude.isFinite() && it.longitude in -180.0..180.0
            }
        }.distinctBy { it.id }.take(MAX_PLACES)
    }.getOrDefault(emptyList())
    companion object {
        const val MAX_PLACES = 20
        private val KEY = stringPreferencesKey("recent_places_json")
    }
}
