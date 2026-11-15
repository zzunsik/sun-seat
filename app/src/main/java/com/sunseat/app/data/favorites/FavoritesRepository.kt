package com.sunseat.app.data.favorites

import com.sunseat.app.data.place.Place
import com.sunseat.app.domain.LatLng
import kotlinx.coroutines.flow.StateFlow
import kotlinx.serialization.Serializable

@Serializable enum class FavoriteKind { HOME, WORK, CUSTOM }
@Serializable data class Favorite(
    val id: String,
    val kind: FavoriteKind,
    val name: String,
    val placeName: String,
    val address: String,
    val lat: Double,
    val lng: Double,
    val createdAt: Long,
) {
    val location: LatLng get() = LatLng(lat, lng)
    fun matches(place: Place): Boolean = lat == place.location.latitude && lng == place.location.longitude
    fun asPlace() = Place(id, placeName, address, address, location, "")
}
data class RemovedFavorite(val favorite: Favorite, val index: Int)
class FavoriteLimitException : IllegalStateException()
class FavoriteConflictException : IllegalStateException()
interface FavoritesRepository {
    val favorites: StateFlow<List<Favorite>>
    suspend fun put(place: Place, kind: FavoriteKind = FavoriteKind.CUSTOM)
    suspend fun toggle(place: Place)
    suspend fun rename(id: String, name: String)
    suspend fun remove(id: String): RemovedFavorite?
    suspend fun restore(removed: RemovedFavorite)
}
