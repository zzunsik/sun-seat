package com.sunseat.app.data.favorites

import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.PreferencesSerializer
import androidx.datastore.core.okio.OkioStorage
import com.sunseat.app.data.place.Place
import com.sunseat.app.data.settings.DataStoreSettingsRepository
import com.sunseat.app.data.settings.ThemeMode
import com.sunseat.app.domain.LatLng
import java.io.File
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.runTest
import okio.FileSystem
import okio.Path.Companion.toPath
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class FavoritesRepositoryTest {
    @get:Rule val temporary = TemporaryFolder()
    private fun store(file: File, scope: CoroutineScope) = PreferenceDataStoreFactory.create(storage = OkioStorage(FileSystem.SYSTEM, PreferencesSerializer) { file.absolutePath.toPath() }, scope = scope)
    private fun place(index: Int) = Place("p" + index, "Place " + index, "Address", "", LatLng(37.0 + index / 1000.0, 127.0), "")
    @Test fun persistsNamesCoordinatesAndSharesSettingsWithoutOverwriting() = runTest {
        val file = File(temporary.root, "user.preferences_pb")
        val dispatcher = StandardTestDispatcher(testScheduler)
        val firstScope = CoroutineScope(SupervisorJob() + dispatcher)
        val shared = store(file, firstScope)
        val favorites = DataStoreFavoritesRepository(shared, firstScope)
        val settings = DataStoreSettingsRepository(shared, firstScope)
        favorites.put(place(1), FavoriteKind.HOME)
        val original = favorites.favorites.first { it.size == 1 }.single()
        favorites.rename(original.id, "Home alias")
        favorites.favorites.first { it.single().name == "Home alias" }
        settings.update { it.copy(theme = ThemeMode.LIGHT) }
        settings.settings.first { it.theme == ThemeMode.LIGHT }
        firstScope.coroutineContext[Job]!!.cancelAndJoin()
        val secondScope = CoroutineScope(SupervisorJob() + dispatcher)
        val reopened = store(file, secondScope)
        val restored = DataStoreFavoritesRepository(reopened, secondScope)
        val saved = restored.favorites.first { it.isNotEmpty() }.single()
        assertEquals(original.id, saved.id)
        assertEquals("Home alias", saved.name)
        assertEquals(place(1).location, saved.location)
        val restoredSettings = DataStoreSettingsRepository(reopened, secondScope)
        restoredSettings.loaded.first { it }
        assertEquals(ThemeMode.LIGHT, restoredSettings.settings.value.theme)
        secondScope.coroutineContext[Job]!!.cancelAndJoin()
    }
    @Test fun homeWorkReplacementToggleAndUndo() = runTest {
        val repository = DataStoreFavoritesRepository(store(File(temporary.root, "favorites.preferences_pb"), backgroundScope), backgroundScope)
        repository.put(place(1), FavoriteKind.HOME)
        repository.put(place(2), FavoriteKind.HOME)
        repository.put(place(3), FavoriteKind.WORK)
        val list = repository.favorites.first { it.size == 2 }
        assertEquals(1, list.count { it.kind == FavoriteKind.HOME })
        assertEquals(place(2).location, list.first { it.kind == FavoriteKind.HOME }.location)
        val removed = repository.remove(list[0].id)!!
        repository.favorites.first { it.size == 1 }
        repository.restore(removed)
        assertEquals(list, repository.favorites.first { it.size == 2 })
        repository.toggle(place(3))
        assertTrue(repository.favorites.first { it.size == 1 }.none { it.matches(place(3)) })
    }
    @Test fun limitAllowsSlotReplacementAndRejectsNewCustomPlace() = runTest {
        val repository = DataStoreFavoritesRepository(store(File(temporary.root, "limit.preferences_pb"), backgroundScope), backgroundScope)
        repeat(50) { repository.put(place(it), FavoriteKind.CUSTOM) }
        try { repository.put(place(100)); fail("Expected limit") } catch (_: FavoriteLimitException) { }
        repository.put(place(0), FavoriteKind.HOME)
        repository.put(place(101), FavoriteKind.HOME)
        val list = repository.favorites.first { it.any { item -> item.matches(place(101)) } }
        assertEquals(50, list.size)
        assertEquals(1, list.count { it.kind == FavoriteKind.HOME })
    }
}
