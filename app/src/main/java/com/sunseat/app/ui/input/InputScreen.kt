package com.sunseat.app.ui.input

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.layout.Spacer
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextOverflow
import com.sunseat.app.R
import com.sunseat.app.data.place.Place
import com.sunseat.app.domain.SunGoal
import com.sunseat.app.domain.TravelMode
import com.sunseat.app.location.LocationAction
import com.sunseat.app.ui.components.QuietButton
import com.sunseat.app.ui.components.SectionCard
import com.sunseat.app.ui.theme.Sizes
import com.sunseat.app.ui.theme.Spacing
import com.sunseat.app.ui.theme.SunSeatTheme
import java.time.LocalDateTime

/** 화면 동작 묶음. 상태를 가진 쪽(InputRoute)이 채운다. */
data class InputActions(
    val chooseRecent: (com.sunseat.app.data.recent.RecentPlace) -> Unit = {},
    val removeRecent: (String) -> Unit = {},
    val clearRecent: () -> Unit = {},
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
    val showCurrentLocationOnMap: () -> Unit = {},
    val onLocationAction: (LocationAction) -> Unit = {},
    val dismissLocationMessage: () -> Unit = {},
    val swap: () -> Unit = {},
    val setMode: (TravelMode) -> Unit = {},
    val setDeparture: (LocalDateTime?) -> Unit = {},
    val setSchedule: (LocalDateTime?) -> Unit = {},
    val setGoal: (SunGoal) -> Unit = {},
    val submit: () -> Unit = {},
    /** 지도에서 위치 선택(#85) */
    val openMapPick: () -> Unit = {},
    val cancelMapPick: () -> Unit = {},
    val confirmMapPick: () -> Unit = {},
    val onPickCameraMoving: () -> Unit = {},
    val onPickCameraIdle: (com.sunseat.app.domain.LatLng) -> Unit = {},
    val retryPickAddress: () -> Unit = {},
    val onPickMapUnavailable: () -> Unit = {},
)


/** 입력 화면: 출발·도착, 수단, 출발 시각, 햇빛 피하기/받기. 상태 없음(미리보기·스크린샷용). */
@Composable
fun InputScreen(
    state: InputUiState,
    actions: InputActions,
    modifier: Modifier = Modifier,
    now: java.time.LocalDateTime = java.time.LocalDateTime.now(),
) {
    val scheme = SunSeatTheme.scheme
    var showTime by rememberSaveable { mutableStateOf(false) }
    var showGoal by rememberSaveable { mutableStateOf(false) }
    Surface(modifier.fillMaxSize(), color = scheme.background, contentColor = scheme.onSurface) {
        Box(Modifier.fillMaxSize()) {
            com.sunseat.app.ui.map.SunSeatMap(com.sunseat.app.ui.map.MapState(
                origin = state.origin?.place, destination = state.destination?.place,
                padding = com.sunseat.app.ui.map.MapPadding(top = Sizes.mapHeight),
                showDecorations = false,
                focus = state.mapFocus,
            ), Modifier.fillMaxSize())
            Column(Modifier.fillMaxWidth().windowInsetsPadding(WindowInsets.safeDrawing)
                .padding(Spacing.screen), verticalArrangement = Arrangement.spacedBy(Spacing.s)) {
                EndpointsCard(state, actions)
                Surface(color = scheme.surface, shape = MaterialTheme.shapes.large) {
                    Column(Modifier.padding(Spacing.s), verticalArrangement = Arrangement.spacedBy(Spacing.s)) {
                        NeutralTabs(listOf(stringResource(R.string.input_mode_transit), stringResource(R.string.common_taxi), stringResource(R.string.common_car)), state.mode.ordinal) { actions.setMode(TravelMode.entries[it]) }
                        Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(Spacing.s)) {
                            QuietButton(onClick = { showTime = true }) { Text(scheduleLabel(state.departure, now)); DropArrow() }
                            QuietButton(onClick = { showGoal = true }) { Text(stringResource(if (state.goal == SunGoal.AVOID) R.string.input_goal_chip_avoid else R.string.input_goal_chip_seek)); DropArrow() }
                        }
                    }
                }
                if (state.locationMessageRes != null) LocationNotice(state, actions)
                if (state.sameEndpoints) Surface(color = scheme.surface) {
                    Text(stringResource(R.string.input_same_endpoints), Modifier.padding(Spacing.l), style = MaterialTheme.typography.bodyMedium, color = scheme.error)
                }
            }
            Surface(Modifier.align(Alignment.BottomEnd).windowInsetsPadding(WindowInsets.safeDrawing).padding(Spacing.l), shape = androidx.compose.foundation.shape.CircleShape, color = scheme.surface) {
                IconButton(onClick = actions.showCurrentLocationOnMap, enabled = !state.mapLocating, modifier = Modifier.size(Spacing.touch)) {
                    Icon(painterResource(R.drawable.ic_ms_my_location), stringResource(R.string.location_use_current), tint = scheme.onSurface)
                }
            }
            if (state.search != null && state.pick != null) MapPickScreen(state.pick, actions, state.origin?.place, state.destination?.place,
                mapLocating = state.mapLocating, notice = pickLocationNotice(state, actions))
            else if (state.search != null) PlaceSearchScreen(state.search, actions, favorites = state.favorites, noticeRes = state.favoriteMessageRes ?: state.recentMessageRes, recent = state.recent, showRecent = true, near = state.origin?.takeIf { it.isCurrentLocation }?.place?.location)
        }
    }
    if (showTime) TimeSheet(state.departure, state.mode, onDismiss = { showTime = false }, onApply = actions.setSchedule, now = now)
    if (showGoal) GoalSheet(state.goal, onDismiss = { showGoal = false }, onSelect = { actions.setGoal(it); showGoal = false })
}

@Composable private fun DropArrow() {
    Icon(painterResource(R.drawable.ic_ms_chevron_right), null, Modifier.size(Sizes.iconSmall).then(Modifier.rotate(90f)))
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable private fun GoalSheet(goal: SunGoal, onDismiss: () -> Unit, onSelect: (SunGoal) -> Unit) {
    androidx.compose.material3.ModalBottomSheet(onDismissRequest = onDismiss, containerColor = SunSeatTheme.scheme.surface) {
        Column(Modifier.fillMaxWidth().padding(Spacing.screen), verticalArrangement = Arrangement.spacedBy(Spacing.s)) {
            Text(stringResource(R.string.common_sun), style = MaterialTheme.typography.titleLarge)
            SunGoal.entries.forEach { option -> NeutralFilterChip(option == goal, stringResource(if (option == SunGoal.AVOID) R.string.input_goal_avoid else R.string.input_goal_seek)) { onSelect(option) } }
            Spacer(Modifier.size(Spacing.l))
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
            Column {
                IconButton(onClick = actions.onSettings) { Icon(painterResource(R.drawable.ic_ms_settings), stringResource(R.string.settings_title)) }
                IconButton(onClick = actions.swap, enabled = state.origin != null || state.destination != null) {
                    Icon(painterResource(R.drawable.ic_ms_swap_vert), contentDescription = stringResource(R.string.input_swap))
                }
            }
        }
    }
}

@Composable
internal fun EndpointRow(label: String, value: String?, placeholder: String, iconRes: Int, onClick: () -> Unit) {
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

/** 지도 선택 중의 위치 안내. '직접 검색'은 지도 선택을 닫고 아래 검색으로 돌아간다. */
internal fun pickLocationNotice(state: InputUiState, actions: InputActions): (@Composable () -> Unit)? =
    if (state.locationMessageRes == null) null
    else { { LocationNotice(state, actions.copy(openSearch = { actions.dismissLocationMessage(); actions.cancelMapPick() })) } }

@Composable
internal fun LocationNotice(state: InputUiState, actions: InputActions) {
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
