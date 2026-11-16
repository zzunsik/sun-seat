package com.sunseat.app.ui.favorites

import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.PreferencesSerializer
import androidx.datastore.core.okio.OkioStorage
import com.sunseat.app.data.favorites.*
import com.sunseat.app.data.place.*
import com.sunseat.app.data.network.ApiResult
import com.sunseat.app.domain.LatLng
import com.sunseat.app.location.LocationProvider
import com.sunseat.app.location.LocationResult
import com.sunseat.app.ui.input.EndpointField
import com.sunseat.app.ui.input.InputViewModel
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.*
import okio.FileSystem
import okio.Path.Companion.toPath
import org.junit.Assert.*
import org.junit.*
import org.junit.rules.TemporaryFolder

@OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
class FavoritesViewModelTest {
    @get:Rule val temporary = TemporaryFolder()
    private val dispatcher = StandardTestDispatcher()
    @Before fun before() = Dispatchers.setMain(dispatcher)
    @After fun after() = Dispatchers.resetMain()
    private val place = Place("sample", "Station", "Sample address", "", LatLng(37.5, 127.0), "")
    private class Search(private val result: Place) : PlaceSearchApi {
        var calls = 0
        override suspend fun search(query: String, near: LatLng?, size: Int): ApiResult<List<Place>> { calls++; return ApiResult.Success(listOf(result)) }
    }
    private fun repository(scope: kotlinx.coroutines.CoroutineScope) = DataStoreFavoritesRepository(PreferenceDataStoreFactory.create(storage = OkioStorage(FileSystem.SYSTEM, PreferencesSerializer) { File(temporary.root, "favorites.preferences_pb").absolutePath.toPath() }, scope = scope), scope)
    @Test fun selectingSavedPlaceSetsDestinationImmediatelyWithoutSearch() = runTest(dispatcher) {
        val favorites = repository(backgroundScope)
        favorites.put(place, FavoriteKind.HOME)
        val saved = favorites.favorites.first { it.isNotEmpty() }.single()
        val api = Search(place)
        val input = InputViewModel(api, LocationProvider { LocationResult.Unavailable }, "Current location", favoriteRepository = favorites)
        input.openSearch(EndpointField.DESTINATION)
        input.chooseFavorite(saved)
        assertEquals(place.name, input.state.value.destination!!.place.name)
        assertEquals(place.location, input.state.value.destination!!.place.location)
        assertNull(input.state.value.search)
        assertEquals(0, api.calls)
    }
    @Test fun lateHomeSaveDoesNotCloseANewWorkSearch() = runTest(dispatcher) {
        val actual = repository(backgroundScope)
        val gate = kotlinx.coroutines.CompletableDeferred<Unit>()
        val delayed = object : FavoritesRepository by actual {
            override suspend fun put(place: Place, kind: FavoriteKind) { gate.await(); actual.put(place, kind) }
        }
        val model = FavoritesViewModel(delayed, Search(place), FavoriteKind.HOME)
        model.choosePlace(place)
        runCurrent()
        model.closeSearch()
        model.openSearch(FavoriteKind.WORK)
        gate.complete(Unit)
        actual.favorites.first { it.isNotEmpty() }
        runCurrent()
        assertNotNull(model.state.value.search)
        assertEquals(com.sunseat.app.R.string.favorites_search_work, model.state.value.search!!.hintRes)
    }
    @Test fun lateSaveErrorDoesNotAppearInANewSearch() = runTest(dispatcher) {
        val actual = repository(backgroundScope)
        val gate = kotlinx.coroutines.CompletableDeferred<Unit>()
        val delayed = object : FavoritesRepository by actual {
            override suspend fun put(place: Place, kind: FavoriteKind) { gate.await(); throw java.io.IOException("delayed save") }
        }
        val model = FavoritesViewModel(delayed, Search(place), FavoriteKind.HOME)
        model.choosePlace(place)
        runCurrent()
        model.closeSearch()
        model.openSearch(FavoriteKind.WORK)
        gate.complete(Unit)
        runCurrent()
        assertNull(model.state.value.errorRes)
        assertNotNull(model.state.value.search)
    }

    @Test fun setupRenameDeleteAndUndo() = runTest(dispatcher) {
        val favorites = repository(backgroundScope)
        val api = Search(place)
        val model = FavoritesViewModel(favorites, api, FavoriteKind.WORK)
        model.onQueryChange("station")
        model.searchNow()
        advanceUntilIdle()
        model.choosePlace(place)
        val saved = favorites.favorites.first { it.isNotEmpty() }.single()
        assertEquals(FavoriteKind.WORK, saved.kind)
        model.requestRename(saved)
        model.rename("Office")
        val renamed = favorites.favorites.first { it.single().name == "Office" }.single()
        model.remove(renamed)
        val deletion = model.deletions.first()
        favorites.favorites.first { it.isEmpty() }
        model.restore(deletion)
        assertEquals(renamed, favorites.favorites.first { it.isNotEmpty() }.single())
    }

    private val office = Place("office", "Office tower", "Office address", "", LatLng(37.56, 126.97), "")
    private val newHome = Place("new-home", "New home", "New address", "", LatLng(37.48, 127.05), "")
    private fun FavoritesRepository.kinds() = favorites.value.associate { it.kind to it.placeName }

    @Test fun replacingHomeKeepsWorkAndOnlyOneHome() = runTest(dispatcher) {
        val favorites = repository(backgroundScope)
        val model = FavoritesViewModel(favorites, Search(place), FavoriteKind.HOME)
        model.choosePlace(place)
        favorites.favorites.first { it.size == 1 }
        model.openSearch(FavoriteKind.WORK)
        model.choosePlace(office)
        favorites.favorites.first { it.size == 2 }
        model.openSearch(FavoriteKind.HOME)
        assertEquals(com.sunseat.app.R.string.favorites_search_home, model.state.value.search!!.hintRes)
        model.choosePlace(newHome)
        favorites.favorites.first { list -> list.any { it.placeName == newHome.name } }
        model.state.first { it.search == null && it.favorites.size == 2 && it.favorites.any { f -> f.placeName == newHome.name } }
        assertEquals(mapOf(FavoriteKind.HOME to newHome.name, FavoriteKind.WORK to office.name), favorites.kinds())
        assertEquals(2, model.state.value.favorites.size)
    }

    @Test fun choosingWorkAtHomeLocationMovesThePlaceInsteadOfDuplicating() = runTest(dispatcher) {
        val favorites = repository(backgroundScope)
        favorites.put(place, FavoriteKind.HOME)
        val model = FavoritesViewModel(favorites, Search(place), FavoriteKind.WORK)
        model.choosePlace(place)
        favorites.favorites.first { it.singleOrNull()?.kind == FavoriteKind.WORK }
        assertEquals(mapOf(FavoriteKind.WORK to place.name), favorites.kinds())
    }

    @Test fun deletingWorkKeepsHomeAndUndoAfterNewWorkConflicts() = runTest(dispatcher) {
        val favorites = repository(backgroundScope)
        favorites.put(place, FavoriteKind.HOME)
        favorites.put(office, FavoriteKind.WORK)
        val model = FavoritesViewModel(favorites, Search(place))
        val work = favorites.favorites.first { it.size == 2 }.single { it.kind == FavoriteKind.WORK }
        model.remove(work)
        val removed = model.deletions.first()
        assertEquals(mapOf(FavoriteKind.HOME to place.name), favorites.favorites.first { it.size == 1 }.associate { it.kind to it.placeName })

        // 되돌리기 전에 다른 회사를 정하면 되돌리지 않고 알린다
        model.openSearch(FavoriteKind.WORK)
        model.choosePlace(newHome)
        // 저장이 끝나 검색이 닫힌 뒤에 되돌린다(저장 완료가 오류 표시를 지우지 않게)
        model.state.first { it.search == null }
        runCurrent()
        model.restore(removed)
        model.state.first { it.errorRes != null }
        assertEquals(com.sunseat.app.R.string.favorites_undo_conflict, model.state.value.errorRes)
        assertEquals(mapOf(FavoriteKind.HOME to place.name, FavoriteKind.WORK to newHome.name), favorites.kinds())
    }

    @Test fun deletingHomeThenUndoRestoresIt() = runTest(dispatcher) {
        val favorites = repository(backgroundScope)
        favorites.put(place, FavoriteKind.HOME)
        favorites.put(office, FavoriteKind.WORK)
        val model = FavoritesViewModel(favorites, Search(place))
        val before = favorites.favorites.first { it.size == 2 }
        model.remove(before.single { it.kind == FavoriteKind.HOME })
        val removed = model.deletions.first()
        favorites.favorites.first { it.size == 1 }
        model.restore(removed)
        assertEquals(before, favorites.favorites.first { it.size == 2 })
        runCurrent()
        assertNull(model.state.value.errorRes)
    }
}
