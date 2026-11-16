package com.sunseat.app.ui.settings

import android.content.Intent
import androidx.core.net.toUri
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.sunseat.app.BuildConfig
import com.sunseat.app.R
import com.sunseat.app.data.settings.Settings
import com.sunseat.app.data.settings.SettingsRepository
import com.sunseat.app.data.settings.ThemeMode
import com.sunseat.app.domain.BenchPreference
import com.sunseat.app.domain.SunGoal
import com.sunseat.app.domain.TravelMode
import com.sunseat.app.ui.components.ChoiceGroup
import com.sunseat.app.ui.components.ChoiceOption
import com.sunseat.app.ui.components.QuietButton
import com.sunseat.app.ui.components.SectionCard
import com.sunseat.app.ui.theme.Sizes
import com.sunseat.app.ui.theme.Spacing
import com.sunseat.app.ui.theme.SunSeatTheme
import com.sunseat.app.ui.theme.rememberSunSeatHaptics

@Composable
fun SettingsRoute(repository: SettingsRepository, onBack: () -> Unit, onLicenses: () -> Unit, modifier: Modifier = Modifier, onFavorites: () -> Unit = {},
    model: SettingsViewModel = viewModel(factory = viewModelFactory { initializer { SettingsViewModel(repository) } }),
) {
    val settings by model.settings.collectAsStateWithLifecycle()
    val failed by model.saveError.collectAsStateWithLifecycle()
    SettingsScreen(settings, model::update, onBack, onLicenses, modifier, failed, onFavorites)
}

@Composable
fun SettingsScreen(settings: Settings, update: ((Settings) -> Settings) -> Unit, onBack: () -> Unit, onLicenses: () -> Unit, modifier: Modifier = Modifier, saveError: Boolean = false, onFavorites: () -> Unit = {}) {
    val haptics = rememberSunSeatHaptics()
    SettingsPage(stringResource(R.string.settings_title), onBack, modifier) {
        if (saveError) Text(stringResource(R.string.settings_save_error), color = SunSeatTheme.scheme.error)
        SectionCard {
            ChoiceGroup(stringResource(R.string.settings_theme), listOf(ChoiceOption(stringResource(R.string.settings_system)), ChoiceOption(stringResource(R.string.settings_light)), ChoiceOption(stringResource(R.string.settings_dark))), settings.theme.ordinal,
                onSelect = { index -> update { it.copy(theme = ThemeMode.entries[index]) } })
        }
        SectionCard(title = stringResource(R.string.settings_defaults)) {
            ChoiceGroup(stringResource(R.string.settings_default_goal), listOf(ChoiceOption(stringResource(R.string.input_goal_avoid)), ChoiceOption(stringResource(R.string.input_goal_seek))), settings.goal.ordinal,
                onSelect = { index -> update { it.copy(goal = SunGoal.entries[index]) } })
            Spacer(Modifier.height(Spacing.s))
            ChoiceGroup(stringResource(R.string.settings_default_mode), listOf(ChoiceOption(stringResource(R.string.input_mode_transit)), ChoiceOption(stringResource(R.string.input_mode_car))), settings.mode.ordinal,
                onSelect = { index -> update { it.copy(mode = TravelMode.entries[index]) } })
        }
        SectionCard(title = stringResource(R.string.settings_bench)) {
            val labels = listOf(R.string.settings_avoid_back, R.string.settings_avoid_front, R.string.settings_min_total)
            BenchPreference.entries.forEachIndexed { index, option ->
                val checked = settings.benchPreference == option
                Row(Modifier.fillMaxWidth().heightIn(min = Spacing.touch).selectable(selected = checked, role = Role.RadioButton, onClick = {
                    if (!checked) { haptics.selectionChanged(); update { it.copy(benchPreference = option) } }
                }).padding(vertical = Spacing.s), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Spacing.m)) {
                    RadioButton(checked, onClick = null, colors = RadioButtonDefaults.colors(selectedColor = SunSeatTheme.scheme.onSurface, unselectedColor = SunSeatTheme.scheme.onSurfaceVariant))
                    Text(stringResource(labels[index]), color = SunSeatTheme.scheme.onSurface, style = MaterialTheme.typography.bodyLarge)
                }
            }
        }
        QuietButton(onClick = onFavorites) { Text(stringResource(R.string.favorites_title)) }
        SectionCard(title = stringResource(R.string.settings_info)) {
            Text(stringResource(R.string.settings_version, BuildConfig.VERSION_NAME), style = MaterialTheme.typography.bodyMedium)
            Text(stringResource(R.string.settings_source), style = MaterialTheme.typography.bodyMedium)
            QuietButton(onClick = onLicenses) { Text(stringResource(R.string.settings_licenses)) }
        }
    }
}

@Composable
private fun SettingsPage(title: String, onBack: () -> Unit, modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) {
    Surface(modifier.fillMaxSize(), color = SunSeatTheme.scheme.background, contentColor = SunSeatTheme.scheme.onSurface) {
        Column(Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.safeDrawing).verticalScroll(rememberScrollState()).padding(Spacing.screen), verticalArrangement = Arrangement.spacedBy(Spacing.m)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Spacing.m)) {
                IconButton(onClick = onBack) { Icon(painterResource(R.drawable.ic_ms_arrow_back), stringResource(R.string.common_back), modifier = Modifier.size(Sizes.iconMedium)) }
                Text(title, style = MaterialTheme.typography.headlineSmall, modifier = Modifier.semantics { heading() })
            }
            content()
        }
    }
}

@Composable
fun LicensesScreen(onBack: () -> Unit, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val resources = androidx.compose.ui.platform.LocalResources.current
    val license = remember(resources) { resources.openRawResource(R.raw.pretendard_ofl).bufferedReader().use { it.readText() } }
    SettingsPage(stringResource(R.string.settings_licenses), onBack, modifier) {
        SectionCard(title = stringResource(R.string.settings_pretendard)) {
            Text(license, style = MaterialTheme.typography.bodySmall)
        }
        QuietButton(onClick = { context.startActivity(Intent(Intent.ACTION_VIEW, "https://www.apache.org/licenses/LICENSE-2.0".toUri())) }) { Text(stringResource(R.string.settings_androidx)) }
        QuietButton(onClick = { context.startActivity(Intent(Intent.ACTION_VIEW, "https://developers.kakao.com/terms/latest/ko/site-policies".toUri())) }) { Text(stringResource(R.string.settings_kakao)) }
    }
}
