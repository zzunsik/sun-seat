package com.sunseat.app.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.sunseat.app.data.settings.Settings
import com.sunseat.app.data.settings.SettingsRepository
import java.io.IOException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class SettingsViewModel(private val repository: SettingsRepository) : ViewModel() {
    val settings = repository.settings
    private val failed = MutableStateFlow(false)
    val saveError = failed.asStateFlow()
    fun update(transform: (Settings) -> Settings) {
        viewModelScope.launch {
            try { repository.update(transform); failed.value = false }
            catch (_: IOException) { failed.value = true }
        }
    }
}
