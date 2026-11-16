package com.sunseat.app.ui.favorites

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.sunseat.app.R
import com.sunseat.app.data.favorites.*
import com.sunseat.app.data.place.Place
import com.sunseat.app.data.place.PlaceSearchApi
import com.sunseat.app.data.network.ApiResult
import com.sunseat.app.ui.input.EndpointField
import com.sunseat.app.ui.input.SearchState
import com.sunseat.app.ui.input.messageRes
import java.io.IOException
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class FavoritesUiState(val favorites: List<Favorite> = emptyList(), val search: SearchState? = null, val rename: Favorite? = null, val errorRes: Int? = null)
class FavoritesViewModel(private val repository: FavoritesRepository, private val places: PlaceSearchApi, setup: FavoriteKind? = null) : ViewModel() {
    private val current = MutableStateFlow(FavoritesUiState())
    val state = current.asStateFlow()
    private val deleted = Channel<RemovedFavorite>(Channel.BUFFERED)
    val deletions = deleted.receiveAsFlow()
    private var searchJob: Job? = null
    private var interactionVersion = 0
    private var setupKind = FavoriteKind.HOME
    init {
        viewModelScope.launch { repository.favorites.collect { list -> current.update { it.copy(favorites = list) } } }
        if (setup != null) openSearch(setup)
    }
    fun openSearch(kind: FavoriteKind) {
        interactionVersion++
        searchJob?.cancel()
        setupKind = kind
        current.update { it.copy(search = SearchState(EndpointField.DESTINATION, hintRes = if (kind == FavoriteKind.HOME) R.string.favorites_search_home else R.string.favorites_search_work), errorRes = null) }
    }
    fun closeSearch() { interactionVersion++; searchJob?.cancel(); current.update { it.copy(search = null) } }
    fun onQueryChange(query: String) {
        current.update { it.copy(search = it.search?.copy(query = query, results = emptyList(), messageRes = null, loading = false, canRetry = false)) }
        search(debounce = true)
    }
    fun searchNow() = search(debounce = false)
    private fun search(debounce: Boolean) {
        searchJob?.cancel()
        val query = current.value.search?.query?.trim().orEmpty()
        if (query.isEmpty()) return
        searchJob = viewModelScope.launch {
            if (debounce) delay(300)
            current.update { it.copy(search = it.search?.copy(loading = true)) }
            when (val response = places.search(query)) {
                is ApiResult.Success -> current.update { it.copy(search = it.search?.copy(results = response.value, loading = false, messageRes = if (response.value.isEmpty()) R.string.search_no_results else null, canRetry = false)) }
                is ApiResult.Failure -> current.update { it.copy(search = it.search?.copy(loading = false, messageRes = response.error.messageRes(), canRetry = true)) }
            }
        }
    }
    fun choosePlace(place: Place) {
        val kind = setupKind
        val version = interactionVersion
        searchJob?.cancel()
        save { repository.put(place, kind); if (version == interactionVersion) current.update { it.copy(search = null) } }
    }
    fun requestRename(favorite: Favorite) { interactionVersion++; current.update { it.copy(rename = favorite) } }
    fun cancelRename() { interactionVersion++; current.update { it.copy(rename = null) } }
    fun rename(name: String) {
        val favorite = current.value.rename ?: return
        val version = interactionVersion
        save { repository.rename(favorite.id, name); if (version == interactionVersion) current.update { it.copy(rename = null) } }
    }
    fun remove(favorite: Favorite) { save { repository.remove(favorite.id)?.let { deleted.send(it) } } }
    fun restore(removed: RemovedFavorite) { save { repository.restore(removed) } }
    private fun save(block: suspend () -> Unit) {
        val version = interactionVersion
        viewModelScope.launch {
            try { block(); if (version == interactionVersion) current.update { it.copy(errorRes = null) } }
            catch (_: FavoriteLimitException) { if (version == interactionVersion) current.update { it.copy(errorRes = R.string.favorites_limit) } }
            catch (_: FavoriteConflictException) { if (version == interactionVersion) current.update { it.copy(errorRes = R.string.favorites_undo_conflict) } }
            catch (_: IOException) { if (version == interactionVersion) current.update { it.copy(errorRes = R.string.favorites_save_error) } }
        }
    }
}
