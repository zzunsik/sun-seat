package com.sunseat.app.ui.settings

import com.sunseat.app.data.settings.Settings
import com.sunseat.app.data.settings.SettingsRepository
import com.sunseat.app.data.settings.ThemeMode
import com.sunseat.app.domain.BenchPreference
import com.sunseat.app.domain.SunGoal
import com.sunseat.app.domain.TravelMode
import java.io.IOException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class SettingsViewModelTest {
    private val dispatcher = StandardTestDispatcher()

    private class FakeSettings : SettingsRepository {
        override val settings = MutableStateFlow(Settings.DEFAULT)
        override val loaded = MutableStateFlow(true)
        var fail = false
        override suspend fun update(transform: (Settings) -> Settings) {
            if (fail) throw IOException("disk full")
            settings.update(transform)
        }
    }

    @Before fun setUp() = Dispatchers.setMain(dispatcher)
    @After fun tearDown() = Dispatchers.resetMain()

    @Test fun eachChoiceIsSavedWithoutResettingTheOthers() = runTest(dispatcher) {
        val repository = FakeSettings()
        val vm = SettingsViewModel(repository)
        vm.update { it.copy(theme = ThemeMode.DARK) }
        vm.update { it.copy(mode = TravelMode.TAXI) }
        vm.update { it.copy(goal = SunGoal.SEEK) }
        vm.update { it.copy(benchPreference = BenchPreference.MIN_TOTAL) }
        advanceUntilIdle()
        assertEquals(Settings(ThemeMode.DARK, SunGoal.SEEK, TravelMode.TAXI, BenchPreference.MIN_TOTAL), vm.settings.value)
        assertFalse(vm.saveError.value)
    }

    @Test fun saveFailureShowsErrorKeepsOldValueAndClearsOnNextSuccess() = runTest(dispatcher) {
        val repository = FakeSettings()
        val vm = SettingsViewModel(repository)
        repository.fail = true
        vm.update { it.copy(theme = ThemeMode.LIGHT) }
        advanceUntilIdle()
        assertTrue(vm.saveError.value)
        assertEquals(ThemeMode.SYSTEM, vm.settings.value.theme)

        repository.fail = false
        vm.update { it.copy(theme = ThemeMode.LIGHT) }
        advanceUntilIdle()
        assertFalse(vm.saveError.value)
        assertEquals(ThemeMode.LIGHT, vm.settings.value.theme)
    }
}
