package com.sunseat.app.data.recent

import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.PreferencesSerializer
import androidx.datastore.core.okio.OkioStorage
import com.sunseat.app.data.place.Place
import com.sunseat.app.domain.LatLng
import com.sunseat.app.domain.TravelMode
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

class RecentPlacesRepositoryTest {
    @get:Rule val temporary = TemporaryFolder()
    private fun store(file: File, scope: CoroutineScope) = PreferenceDataStoreFactory.create(storage = OkioStorage(FileSystem.SYSTEM, PreferencesSerializer) { file.absolutePath.toPath() }, scope = scope)
    private fun place(index: Int) = Place("p$index", "Place $index", "Address", "", LatLng(37.0 + index / 1000.0, 127.0), "")

    @Test fun persistsCoordinatesAndModeAcrossReopen() = runTest {
        val file = File(temporary.root, "recent.preferences_pb")
        val dispatcher = StandardTestDispatcher(testScheduler)
        val scope = CoroutineScope(SupervisorJob() + dispatcher)
        val repository = DataStoreRecentPlacesRepository(store(file, scope), scope)
        repository.remember(place(1), TravelMode.CAR)
        val expected = repository.places.first { it.size == 1 }
        scope.coroutineContext[Job]!!.cancelAndJoin()
        val reopenedScope = CoroutineScope(SupervisorJob() + dispatcher)
        val reopened = DataStoreRecentPlacesRepository(store(file, reopenedScope), reopenedScope)
        val saved = reopened.places.first { it.isNotEmpty() }
        assertEquals(expected, saved)
        assertEquals(place(1).location, saved.single().asPlace().location)
        assertEquals(TravelMode.CAR, saved.single().mode)
        reopenedScope.coroutineContext[Job]!!.cancelAndJoin()
    }

    @Test fun boundsDuplicatesOrderingDeleteAndClear() = runTest {
        val repository = DataStoreRecentPlacesRepository(store(File(temporary.root, "recent.preferences_pb"), backgroundScope), backgroundScope)
        repeat(25) { repository.remember(place(it), TravelMode.TRANSIT) }
        val saved = repository.places.first { it.size == 20 && it.first().id == "p24" }
        assertEquals("p24", saved.first().id)
        repository.remember(place(10), TravelMode.CAR)
        val moved = repository.places.first { it.first().id == "p10" }
        assertEquals(20, moved.size)
        assertEquals(TravelMode.CAR, moved.first().mode)
        repository.remember(place(10).copy(id = "alias"), TravelMode.TRANSIT)
        assertEquals(20, repository.places.first { it.first().id == "alias" }.size)
        repository.remove("alias")
        assertEquals(19, repository.places.first { it.none { p -> p.id == "alias" } }.size)
        repository.clear()
        assertTrue(repository.places.first { it.isEmpty() }.isEmpty())
    }
}
