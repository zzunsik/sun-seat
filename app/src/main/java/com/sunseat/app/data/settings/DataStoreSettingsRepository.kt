package com.sunseat.app.data.settings

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.sunseat.app.domain.BenchPreference
import com.sunseat.app.domain.SunGoal
import com.sunseat.app.domain.TravelMode
import java.io.IOException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.retryWhen
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

internal val Context.userPreferences by preferencesDataStore(name = "settings")
class DataStoreSettingsRepository(private val store: DataStore<Preferences>, scope: CoroutineScope) : SettingsRepository {
    constructor(context: Context, scope: CoroutineScope) : this(context.applicationContext.userPreferences, scope)
    private val current = MutableStateFlow(Settings.DEFAULT)
    private val ready = MutableStateFlow(false)
    override val settings = current.asStateFlow()
    override val loaded = ready.asStateFlow()

    init {
        scope.launch {
            store.data.retryWhen { error, _ ->
                if (error is IOException) {
                    ready.value = true
                    delay(1_000L)
                    true
                } else false
            }.collect {
                current.value = decode(it)
                ready.value = true
            }
        }
    }
    override suspend fun update(transform: (Settings) -> Settings) {
        store.edit { prefs ->
            val next = transform(decode(prefs))
            prefs[THEME] = next.theme.name
            prefs[GOAL] = next.goal.name
            prefs[MODE] = next.mode.name
            prefs[BENCH] = next.benchPreference.name
        }
    }
    private fun decode(prefs: Preferences) = Settings(
        theme = ThemeMode.entries.firstOrNull { it.name == prefs[THEME] } ?: Settings.DEFAULT.theme,
        goal = SunGoal.entries.firstOrNull { it.name == prefs[GOAL] } ?: Settings.DEFAULT.goal,
        mode = TravelMode.entries.firstOrNull { it.name == prefs[MODE] } ?: Settings.DEFAULT.mode,
        benchPreference = BenchPreference.entries.firstOrNull { it.name == prefs[BENCH] } ?: Settings.DEFAULT.benchPreference,
    )
    companion object {
        private val THEME = stringPreferencesKey("theme")
        private val GOAL = stringPreferencesKey("goal")
        private val MODE = stringPreferencesKey("mode")
        private val BENCH = stringPreferencesKey("bench_preference")
    }
}
