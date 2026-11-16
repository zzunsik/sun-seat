package com.sunseat.app.ui.input

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimePicker
import androidx.compose.material3.TimePickerDialog
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import com.sunseat.app.R
import com.sunseat.app.data.place.Place
import com.sunseat.app.domain.SunGoal
import com.sunseat.app.domain.TravelMode
import com.sunseat.app.location.LocationAction
import com.sunseat.app.ui.components.ChoiceGroup
import com.sunseat.app.ui.components.ChoiceOption
import com.sunseat.app.ui.components.FloatingCapsule
import com.sunseat.app.ui.components.PrimaryButton
import com.sunseat.app.ui.components.QuietButton
import com.sunseat.app.ui.components.SectionCard
import com.sunseat.app.ui.theme.Sizes
import com.sunseat.app.ui.theme.Spacing
import com.sunseat.app.ui.theme.SunSeatTheme
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.util.Locale

/** 화면 동작 묶음. 상태를 가진 쪽(InputRoute)이 채운다. */
data class InputActions(
    val manageFavorites: (com.sunseat.app.data.favorites.FavoriteKind?) -> Unit = {},
    val chooseFavorite: (com.sunseat.app.data.favorites.Favorite) -> Unit = {},
    val toggleFavorite: (Place) -> Unit = {},
    val onSettings: () -> Unit = {},
    val openSearch: (EndpointField) -> Unit = {},
    val closeSearch: () -> Unit = {},
    val onQueryChange: (String) -> Unit = {},
    val searchNow: () -> Unit = {},
    val choosePlace: (Place) -> Unit = {},
    val useCurrentLocation: () -> Unit = {},
    val onLocationAction: (LocationAction) -> Unit = {},
    val dismissLocationMessage: () -> Unit = {},
    val swap: () -> Unit = {},
    val setMode: (TravelMode) -> Unit = {},
    val setDeparture: (LocalDateTime?) -> Unit = {},
    val setGoal: (SunGoal) -> Unit = {},
    val submit: () -> Unit = {},
)


/** 입력 화면: 출발·도착, 수단, 출발 시각, 햇빛 피하기/받기. 상태 없음(미리보기·스크린샷용). */
@Composable
fun InputScreen(
    state: InputUiState,
    actions: InputActions,
    modifier: Modifier = Modifier,
) {
    val scheme = SunSeatTheme.scheme
    val insets = WindowInsets.safeDrawing.asPaddingValues()
    Surface(modifier.fillMaxSize(), color = scheme.background, contentColor = scheme.onSurface) {
    Box(Modifier.fillMaxSize()) {
        Column(
            Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = Spacing.screen)
                .padding(
                    top = insets.calculateTopPadding() + Spacing.xl,
                    bottom = insets.calculateBottomPadding() + Sizes.bottomCapsuleClearance,
                ),
            verticalArrangement = Arrangement.spacedBy(Spacing.m),
        ) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text(
                stringResource(R.string.input_title),
                style = MaterialTheme.typography.headlineSmall,
                color = scheme.onSurface,
                modifier = Modifier.weight(1f).semantics { heading() }.padding(bottom = Spacing.s),
            )
            IconButton(onClick = actions.onSettings) {
                Icon(painterResource(R.drawable.ic_ms_settings), contentDescription = stringResource(R.string.settings_title), tint = scheme.onSurface, modifier = Modifier.size(Sizes.iconMedium))
            }
            }
            EndpointsCard(state, actions)
            if (state.locationMessageRes != null) LocationNotice(state, actions)
            if (state.sameEndpoints) {
                Text(stringResource(R.string.input_same_endpoints), style = MaterialTheme.typography.bodyMedium, color = scheme.error)
            }
            OptionsCard(state, actions)
        }

        FloatingCapsule(
            Modifier
                .align(Alignment.BottomCenter)
                .windowInsetsPadding(WindowInsets.safeDrawing)
                .padding(horizontal = Spacing.xl, vertical = Spacing.m)
                .fillMaxWidth(),
        ) {
            PrimaryButton(
                onClick = actions.submit,
                enabled = state.submitEnabled,
                modifier = Modifier.fillMaxWidth().padding(Spacing.s),
            ) {
                Text(stringResource(R.string.input_submit), style = MaterialTheme.typography.labelLarge)
            }
        }

        if (state.search != null) SearchOverlay(state.search, actions, favorites = state.favorites, noticeRes = state.favoriteMessageRes)
    }
    }
}

@Composable
private fun EndpointsCard(state: InputUiState, actions: InputActions) {
    SectionCard {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                EndpointRow(
                    label = stringResource(R.string.input_origin),
                    value = when {
                        state.locating -> stringResource(R.string.input_locating)
                        state.origin == null -> null
                        else -> state.origin.place.name
                    },
                    placeholder = stringResource(R.string.input_origin_placeholder),
                    iconRes = if (state.origin?.isCurrentLocation == true) R.drawable.ic_ms_my_location else R.drawable.ic_ms_search,
                    onClick = { actions.openSearch(EndpointField.ORIGIN) },
                )
                if (state.locating) LinearProgressIndicator(Modifier.fillMaxWidth())
                HorizontalDivider(color = SunSeatTheme.scheme.outlineVariant)
                EndpointRow(
                    label = stringResource(R.string.input_destination),
                    value = state.destination?.place?.name,
                    placeholder = stringResource(R.string.input_destination_placeholder),
                    iconRes = R.drawable.ic_ms_search,
                    onClick = { actions.openSearch(EndpointField.DESTINATION) },
                )
            }
            IconButton(onClick = actions.swap, enabled = state.origin != null || state.destination != null) {
                Icon(painterResource(R.drawable.ic_ms_swap_vert), contentDescription = stringResource(R.string.input_swap))
            }
        }
    }
}

@Composable
private fun EndpointRow(label: String, value: String?, placeholder: String, iconRes: Int, onClick: () -> Unit) {
    val scheme = SunSeatTheme.scheme
    Row(
        Modifier
            .fillMaxWidth()
            .heightIn(min = Spacing.touch + Spacing.m)
            .clickable(role = Role.Button, onClickLabel = placeholder, onClick = onClick),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Spacing.m),
    ) {
        Icon(painterResource(iconRes), contentDescription = null, tint = scheme.onSurfaceVariant, modifier = Modifier.size(Sizes.iconMedium))
        Column(Modifier.weight(1f)) {
            Text(label, style = MaterialTheme.typography.labelMedium, color = scheme.onSurfaceVariant)
            Text(
                value ?: placeholder,
                style = if (value == null) MaterialTheme.typography.bodyLarge else MaterialTheme.typography.titleMedium,
                color = if (value == null) scheme.onSurfaceVariant else scheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@Composable
private fun LocationNotice(state: InputUiState, actions: InputActions) {
    val scheme = SunSeatTheme.scheme
    Surface(color = scheme.surfaceContainerHigh, contentColor = scheme.onSurface, shape = MaterialTheme.shapes.large, modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(start = Spacing.l, top = Spacing.s, end = Spacing.xs, bottom = Spacing.s)) {
            Row(verticalAlignment = Alignment.Top, horizontalArrangement = Arrangement.spacedBy(Spacing.m)) {
                Icon(painterResource(R.drawable.ic_ms_location_off), contentDescription = null, modifier = Modifier.padding(top = Spacing.m).size(Sizes.iconMedium))
                Text(
                    stringResource(state.locationMessageRes!!),
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.weight(1f).padding(top = Spacing.m),
                )
                IconButton(onClick = actions.dismissLocationMessage) {
                    Icon(painterResource(R.drawable.ic_ms_close), contentDescription = stringResource(R.string.input_dismiss))
                }
            }
            Row(Modifier.align(Alignment.End), horizontalArrangement = Arrangement.spacedBy(Spacing.xs)) {
                state.locationAction?.let { action ->
                    QuietButton(onClick = { actions.onLocationAction(action) }) {
                        Text(stringResource(action.labelRes), style = MaterialTheme.typography.labelLarge)
                    }
                }
                QuietButton(onClick = { actions.openSearch(EndpointField.ORIGIN) }) {
                    Text(stringResource(R.string.input_search_manually), style = MaterialTheme.typography.labelLarge)
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun OptionsCard(state: InputUiState, actions: InputActions) {
    val scheme = SunSeatTheme.scheme
    var picking by rememberSaveable { mutableStateOf(DepartureStep.None) }
    var pickedDateMillis by rememberSaveable { mutableStateOf<Long?>(null) }

    SectionCard {
        ChoiceGroup(
            label = stringResource(R.string.input_mode),
            options = listOf(
                ChoiceOption(stringResource(R.string.input_mode_transit)),
                ChoiceOption(stringResource(R.string.input_mode_car)),
            ),
            selected = state.mode.ordinal,
            onSelect = { actions.setMode(TravelMode.entries[it]) },
        )
        ChoiceGroup(
            label = stringResource(R.string.input_departure),
            options = listOf(
                ChoiceOption(stringResource(R.string.input_departure_now)),
                ChoiceOption(stringResource(R.string.input_departure_at)),
            ),
            selected = if (state.departure == null) 0 else 1,
            onSelect = { if (it == 0) actions.setDeparture(null) else picking = DepartureStep.Date },
        )
        if (state.departure != null) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    state.departure.format(DateTimeFormatter.ofPattern(stringResource(R.string.input_date_pattern), Locale.KOREAN)),
                    style = MaterialTheme.typography.titleMedium,
                    color = scheme.onSurface,
                    modifier = Modifier.weight(1f),
                )
                QuietButton(onClick = { picking = DepartureStep.Date }) {
                    Text(stringResource(R.string.input_departure_change), style = MaterialTheme.typography.labelLarge)
                }
            }
            if (state.mode == TravelMode.TRANSIT) {
                Text(stringResource(R.string.input_departure_transit_note), style = MaterialTheme.typography.bodySmall, color = scheme.onSurfaceVariant)
            }
        }
        ChoiceGroup(
            label = stringResource(R.string.input_goal),
            options = listOf(
                ChoiceOption(stringResource(R.string.input_goal_avoid)),
                ChoiceOption(stringResource(R.string.input_goal_seek)),
            ),
            selected = state.goal.ordinal,
            onSelect = { actions.setGoal(SunGoal.entries[it]) },
        )
    }

    when (picking) {
        DepartureStep.Date -> {
            val initial = (state.departure ?: LocalDateTime.now()).toLocalDate().atStartOfDay().toInstant(ZoneOffset.UTC).toEpochMilli()
            val dateState = rememberDatePickerState(initialSelectedDateMillis = initial)
            DatePickerDialog(
                onDismissRequest = { picking = DepartureStep.None },
                confirmButton = {
                    TextButton(onClick = {
                        pickedDateMillis = dateState.selectedDateMillis
                        picking = DepartureStep.Time
                    }) { Text(stringResource(R.string.common_ok)) }
                },
                dismissButton = { TextButton(onClick = { picking = DepartureStep.None }) { Text(stringResource(R.string.common_cancel)) } },
            ) { DatePicker(dateState) }
        }
        DepartureStep.Time -> {
            val base = state.departure ?: LocalDateTime.now()
            val is24 = android.text.format.DateFormat.is24HourFormat(LocalContext.current)
            val timeState = rememberTimePickerState(initialHour = base.hour, initialMinute = base.minute, is24Hour = is24)
            TimePickerDialog(
                onDismissRequest = { picking = DepartureStep.None },
                title = { Text(stringResource(R.string.input_departure)) },
                confirmButton = {
                    TextButton(onClick = {
                        val date = Instant.ofEpochMilli(pickedDateMillis ?: System.currentTimeMillis()).atZone(ZoneOffset.UTC).toLocalDate()
                        actions.setDeparture(date.atTime(timeState.hour, timeState.minute))
                        picking = DepartureStep.None
                    }) { Text(stringResource(R.string.common_ok)) }
                },
                dismissButton = { TextButton(onClick = { picking = DepartureStep.None }) { Text(stringResource(R.string.common_cancel)) } },
            ) { TimePicker(timeState) }
        }
        DepartureStep.None -> Unit
    }
}

private enum class DepartureStep { None, Date, Time }

@Composable
internal fun SearchOverlay(search: SearchState, actions: InputActions, modifier: Modifier = Modifier, favorites: List<com.sunseat.app.data.favorites.Favorite> = emptyList(), noticeRes: Int? = null, showFavorites: Boolean = true, showLocation: Boolean = true) {
    val scheme = SunSeatTheme.scheme
    val focus = remember { FocusRequester() }
    val keyboard = LocalSoftwareKeyboardController.current
    BackHandler(onBack = actions.closeSearch)
    LaunchedEffect(search.field) { runCatching { focus.requestFocus() } }

    Surface(modifier.fillMaxSize(), color = scheme.background, contentColor = scheme.onSurface) {
    Column(Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.safeDrawing)) {
        Row(
            Modifier.fillMaxWidth().padding(start = Spacing.xs, end = Spacing.screen, top = Spacing.s, bottom = Spacing.s),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(onClick = actions.closeSearch) {
                Icon(painterResource(R.drawable.ic_ms_arrow_back), contentDescription = stringResource(R.string.input_search_close))
            }
            OutlinedTextField(
                value = search.query,
                onValueChange = actions.onQueryChange,
                singleLine = true,
                textStyle = MaterialTheme.typography.bodyLarge,
                placeholder = {
                    Text(
                        stringResource(search.hintRes ?: if (search.field == EndpointField.ORIGIN) R.string.input_search_hint_origin else R.string.input_search_hint_destination),
                        style = MaterialTheme.typography.bodyLarge,
                    )
                },
                trailingIcon = if (search.query.isNotEmpty()) {
                    {
                        IconButton(onClick = { actions.onQueryChange("") }) {
                            Icon(painterResource(R.drawable.ic_ms_close), contentDescription = stringResource(R.string.input_search_clear))
                        }
                    }
                } else null,
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                keyboardActions = KeyboardActions(onSearch = {
                    actions.searchNow()
                    keyboard?.hide()
                }),
                shape = MaterialTheme.shapes.medium,
                modifier = Modifier.weight(1f).focusRequester(focus),
            )
        }
        if (showFavorites) {
            Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = Spacing.screen), horizontalArrangement = Arrangement.spacedBy(Spacing.s)) {
                listOf(com.sunseat.app.data.favorites.FavoriteKind.HOME, com.sunseat.app.data.favorites.FavoriteKind.WORK).forEach { kind ->
                    val saved = favorites.firstOrNull { it.kind == kind }
                    if (saved == null) com.sunseat.app.ui.components.QuietButton(onClick = { actions.manageFavorites(kind) }) { Text(stringResource(if (kind == com.sunseat.app.data.favorites.FavoriteKind.HOME) R.string.favorites_set_home else R.string.favorites_set_work)) }
                    else com.sunseat.app.ui.components.QuietButton(onClick = { actions.chooseFavorite(saved) }) { Text(com.sunseat.app.ui.favorites.favoriteName(saved)) }
                }
                favorites.filter { it.kind == com.sunseat.app.data.favorites.FavoriteKind.CUSTOM }.forEach { favorite ->
                    com.sunseat.app.ui.components.QuietButton(onClick = { actions.chooseFavorite(favorite) }) { Text(com.sunseat.app.ui.favorites.favoriteName(favorite)) }
                }
                com.sunseat.app.ui.components.QuietButton(onClick = { actions.manageFavorites(null) }) { Text(stringResource(R.string.favorites_edit)) }
            }
        }
        if (noticeRes != null) Text(stringResource(noticeRes), Modifier.padding(Spacing.screen), color = scheme.error)
        if (search.loading) LinearProgressIndicator(Modifier.fillMaxWidth())
        LazyColumn(Modifier.fillMaxSize()) {
            if (showLocation && search.field == EndpointField.ORIGIN && search.query.isBlank()) {
                item {
                    ResultRow(
                        title = stringResource(R.string.location_use_current),
                        subtitle = null,
                        iconRes = R.drawable.ic_ms_my_location,
                        onClick = actions.useCurrentLocation,
                    )
                    HorizontalDivider(color = scheme.outlineVariant, modifier = Modifier.padding(horizontal = Spacing.screen))
                }
            }
            if (showFavorites && search.query.isBlank()) {
                items(favorites, key = { "favorite-" + it.id }) { favorite ->
                    ResultRow(com.sunseat.app.ui.favorites.favoriteName(favorite), favorite.address, R.drawable.ic_ms_star_filled, { actions.chooseFavorite(favorite) })
                }
            }
            if (search.messageRes != null) {
                item {
                    Column(Modifier.padding(horizontal = Spacing.screen, vertical = Spacing.l), verticalArrangement = Arrangement.spacedBy(Spacing.s)) {
                        Text(
                            stringResource(search.messageRes, search.query.trim()),
                            style = MaterialTheme.typography.bodyLarge,
                            color = scheme.onSurface,
                        )
                        if (search.canRetry) {
                            QuietButton(onClick = actions.searchNow) {
                                Icon(painterResource(R.drawable.ic_ms_refresh), contentDescription = null, modifier = Modifier.size(Sizes.iconMedium))
                                Spacer(Modifier.size(Spacing.s))
                                Text(stringResource(R.string.search_retry), style = MaterialTheme.typography.labelLarge)
                            }
                        }
                    }
                }
            }
            items(search.results, key = { it.id }) { place ->
                ResultRow(
                    title = place.name,
                    subtitle = place.roadAddress.ifBlank { place.address },
                    iconRes = R.drawable.ic_ms_location_on,
                    onClick = { actions.choosePlace(place) },
                    trailing = if (showFavorites) { {
                        val saved = favorites.any { it.matches(place) }
                        IconButton(onClick = { actions.toggleFavorite(place) }) {
                            Icon(painterResource(if (saved) R.drawable.ic_ms_star_filled else R.drawable.ic_ms_star), contentDescription = stringResource(if (saved) R.string.favorites_remove else R.string.favorites_add), tint = scheme.onSurface, modifier = Modifier.size(Sizes.iconMedium))
                        }
                    } } else null,
                )
            }
        }
    }
    }
}

@Composable
private fun ResultRow(title: String, subtitle: String?, iconRes: Int, onClick: () -> Unit, trailing: (@Composable () -> Unit)? = null) {
    val scheme = SunSeatTheme.scheme
    Row(
        Modifier
            .fillMaxWidth()
            .heightIn(min = Spacing.touch + Spacing.m)
            .clickable(role = Role.Button, onClick = onClick)
            .padding(horizontal = Spacing.screen, vertical = Spacing.s),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Spacing.m),
    ) {
        Icon(painterResource(iconRes), contentDescription = null, tint = scheme.onSurfaceVariant, modifier = Modifier.size(Sizes.iconMedium))
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.titleSmall, color = scheme.onSurface, maxLines = 1, overflow = TextOverflow.Ellipsis)
            if (!subtitle.isNullOrBlank()) {
                Text(subtitle, style = MaterialTheme.typography.bodySmall, color = scheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
        trailing?.invoke()
    }
}
