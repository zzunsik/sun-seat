package com.sunseat.app.data.settings

import com.sunseat.app.domain.BenchPreference
import com.sunseat.app.domain.SunGoal
import com.sunseat.app.domain.TravelMode
import kotlinx.coroutines.flow.StateFlow

enum class ThemeMode { SYSTEM, LIGHT, DARK }
data class Settings(
    val theme: ThemeMode = ThemeMode.SYSTEM,
    val goal: SunGoal = SunGoal.AVOID,
    val mode: TravelMode = TravelMode.TRANSIT,
    val benchPreference: BenchPreference = BenchPreference.AVOID_BACK,
) {
    companion object { val DEFAULT = Settings() }
}
interface SettingsRepository {
    val settings: StateFlow<Settings>
    val loaded: StateFlow<Boolean>
    suspend fun update(transform: (Settings) -> Settings)
}
