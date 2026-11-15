package com.sunseat.app.data.settings

import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import com.sunseat.app.domain.BenchPreference
import com.sunseat.app.domain.SunGoal
import com.sunseat.app.domain.TravelMode
import androidx.datastore.preferences.core.PreferencesSerializer
import androidx.datastore.core.okio.OkioStorage
import okio.FileSystem
import java.io.File
import okio.Path.Companion.toPath
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.advanceTimeBy
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

@OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
class SettingsRepositoryTest {
    @get:Rule val temporary = TemporaryFolder()
    // Android FileStorage uses renameTo, whose overwrite behavior differs on Windows.
    // Keep the production preference serialization and use Okio atomic replacement in JVM tests.
    private fun store(file: File, scope: CoroutineScope) = PreferenceDataStoreFactory.create(
        storage = OkioStorage(FileSystem.SYSTEM, PreferencesSerializer) { file.absolutePath.toPath() },
        scope = scope,
    )
    @Test fun defaultsAndPersistenceAcrossRepositoryLifetimes() = runTest {
        val file = File(temporary.root, "settings.preferences_pb")
        val dispatcher = StandardTestDispatcher(testScheduler)
        val firstScope = CoroutineScope(SupervisorJob() + dispatcher)
        val first = DataStoreSettingsRepository(store(file, firstScope), firstScope)
        assertEquals(Settings.DEFAULT, first.settings.value)
        assertFalse(first.loaded.value)
        first.loaded.first { it }
        val expected = Settings(ThemeMode.DARK, SunGoal.SEEK, TravelMode.CAR, BenchPreference.AVOID_FRONT)
        first.update { expected }
        assertEquals(expected, first.settings.first { it == expected })
        firstScope.coroutineContext[Job]!!.cancelAndJoin()
        val secondScope = CoroutineScope(SupervisorJob() + dispatcher)
        val second = DataStoreSettingsRepository(store(file, secondScope), secondScope)
        second.loaded.first { it }
        assertEquals(expected, second.settings.value)
        second.update { it.copy(theme = ThemeMode.LIGHT) }
        assertEquals(expected.copy(theme = ThemeMode.LIGHT), second.settings.first { it.theme == ThemeMode.LIGHT })
        secondScope.coroutineContext[Job]!!.cancelAndJoin()
    }
    @Test fun readFailureRecoversAndLaterSavesStillPublish() = runTest {
        val saved = kotlinx.coroutines.flow.MutableStateFlow(androidx.datastore.preferences.core.preferencesOf(stringPreferencesKey("theme") to "DARK"))
        var reads = 0
        val fake = object : androidx.datastore.core.DataStore<androidx.datastore.preferences.core.Preferences> {
            override val data = kotlinx.coroutines.flow.flow {
                if (reads++ == 0) throw java.io.IOException("temporary read error")
                saved.collect { emit(it) }
            }
            override suspend fun updateData(transform: suspend (androidx.datastore.preferences.core.Preferences) -> androidx.datastore.preferences.core.Preferences): androidx.datastore.preferences.core.Preferences {
                val next = transform(saved.value)
                saved.value = next
                return next
            }
        }
        val repository = DataStoreSettingsRepository(fake, backgroundScope)
        runCurrent()
        assertTrue(repository.loaded.value)
        assertEquals(Settings.DEFAULT, repository.settings.value)
        advanceTimeBy(1_000)
        runCurrent()
        assertEquals(ThemeMode.DARK, repository.settings.value.theme)
        repository.update { it.copy(goal = SunGoal.SEEK) }
        runCurrent()
        assertEquals(SunGoal.SEEK, repository.settings.value.goal)
    }

    @Test fun unknownStoredValueFallsBackWithoutDiscardingOtherPreferences() = runTest {
        val scope = CoroutineScope(SupervisorJob() + StandardTestDispatcher(testScheduler))
        val store = store(File(temporary.root, "future.preferences_pb"), scope)
        store.edit { it[stringPreferencesKey("theme")] = "FUTURE"; it[stringPreferencesKey("goal")] = "SEEK" }
        val repository = DataStoreSettingsRepository(store, scope)
        repository.loaded.first { it }
        assertEquals(ThemeMode.SYSTEM, repository.settings.value.theme)
        assertEquals(SunGoal.SEEK, repository.settings.value.goal)
        scope.coroutineContext[Job]!!.cancelAndJoin()
    }
}
