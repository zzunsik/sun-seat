package com.sunseat.app.ui.input

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.sunseat.app.R
import com.sunseat.app.data.place.Place
import com.sunseat.app.domain.SunGoal
import com.sunseat.app.domain.TravelMode
import com.sunseat.app.location.LocationAction
import com.sunseat.app.ui.components.QuietButton
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


/**
 * 입력 화면: 출발·도착, 수단, 출발 시각, 햇빛 목적을 한 표면의 여정 헤더(#103, DESIGN 5.1)로 보인다.
 * 지도 위쪽 여백은 헤더(와 그 아래 안내)의 실제 측정 높이다. 상태 없음(미리보기·스크린샷용).
 */
@Composable
fun InputScreen(
    state: InputUiState,
    actions: InputActions,
    modifier: Modifier = Modifier,
    now: java.time.LocalDateTime = java.time.LocalDateTime.now(),
) {
    val scheme = SunSeatTheme.scheme
    val density = LocalDensity.current
    var showTime by rememberSaveable { mutableStateOf(false) }
    var boxTop by remember { mutableStateOf(0.dp) }
    var panelBottom by remember { mutableStateOf(0.dp) }
    // 여백·안내 변화로는 카메라를 다시 맞추지 않는다(수동 이동 보존). 끝점이 바뀔 때만 맞춘다.
    val endpoints = state.origin?.place to state.destination?.place
    var fittedEndpoints by remember { mutableStateOf(endpoints) }
    var fitRevision by remember { mutableLongStateOf(0L) }
    // #180: 지도를 쓸 수 없으면 카메라만 옮기는 현재 위치 버튼을 숨긴다(없는 지도 위에 남지 않게).
    var mapUnavailable by remember { mutableStateOf(false) }
    LaunchedEffect(endpoints) { if (endpoints != fittedEndpoints) { fittedEndpoints = endpoints; fitRevision++ } }
    Surface(modifier.fillMaxSize(), color = scheme.background, contentColor = scheme.onSurface) {
        BoxWithConstraints(Modifier.fillMaxSize().onGloballyPositioned { boxTop = with(density) { it.positionInRoot().y.toDp() } }) {
            // 헤더 표면이 지도를 모두 덮지 않게 한다(가로·큰 글자). 넘치면 표면 안에서 스크롤하고 모양(모서리)은 유지한다.
            val topInset = WindowInsets.safeDrawing.asPaddingValues().calculateTopPadding()
            val headerLimit = (maxHeight - topInset - Spacing.screen - Spacing.touch * 3).coerceAtLeast(Spacing.touch)
            // 지도 위 여백 = 헤더(+안내)의 실제 아래끝. 안내까지 길어져도 지도 띠는 남긴다.
            val mapTop = panelBottom.coerceAtMost((maxHeight - Spacing.touch * 2).coerceAtLeast(0.dp))
            com.sunseat.app.ui.map.SunSeatMap(com.sunseat.app.ui.map.MapState(
                origin = state.origin?.place, destination = state.destination?.place,
                // 아래 여백: 세로 화면은 오른쪽 아래 현재 위치 버튼(터치 48 + 바깥 여백) 위로 표식을 맞춘다. 가로 화면은 지도 띠가 낮아 주지 않는다.
                padding = com.sunseat.app.ui.map.MapPadding(top = mapTop, bottom = if (maxHeight >= maxWidth) WindowInsets.safeDrawing.asPaddingValues().calculateBottomPadding() + Spacing.touch + Spacing.l * 2 else 0.dp),
                showDecorations = false,
                focus = state.mapFocus,
                automaticFit = false,
                cameraRevision = fitRevision,
                fitEnabled = panelBottom > 0.dp,
                onUnavailable = { mapUnavailable = true },
            ), Modifier.fillMaxSize().testTag(INPUT_MAP_TAG).semantics {
                this[com.sunseat.app.ui.map.MapTopPaddingKey] = mapTop.value
                this[com.sunseat.app.ui.map.MapCameraRevisionKey] = fitRevision
            })
            Column(Modifier.fillMaxWidth().windowInsetsPadding(WindowInsets.safeDrawing)
                .padding(Spacing.screen)
                .onGloballyPositioned { with(density) { panelBottom = (it.positionInRoot().y + it.size.height).toDp() - boxTop } },
                verticalArrangement = Arrangement.spacedBy(Spacing.s)) {
                Surface(color = scheme.surface, shape = MaterialTheme.shapes.large) {
                    val headerScroll = rememberScrollState()
                    Box {
                        JourneyHeader(
                            origin = JourneyEndpointUi(state.origin?.place?.name, currentLocation = state.origin?.isCurrentLocation == true, loading = state.locating),
                            destination = JourneyEndpointUi(state.destination?.place?.name),
                            mode = state.mode, departure = state.departure, goal = state.goal, now = now,
                            actions = JourneyHeaderActions(
                                onEdit = actions.openSearch, onSwap = actions.swap, onMode = actions.setMode,
                                onTime = { showTime = true }, onGoal = actions.setGoal, onSettings = actions.onSettings,
                            ),
                            swapEnabled = state.origin != null || state.destination != null,
                            modifier = Modifier.heightIn(max = headerLimit).verticalScroll(headerScroll).padding(Spacing.s),
                        )
                        // 표면 안에 더 내릴 내용이 있으면 아래 가장자리에 구분선을 둬 스크롤할 수 있음을 알린다.
                        if (headerScroll.canScrollForward) HorizontalDivider(Modifier.align(Alignment.BottomCenter), color = scheme.outlineVariant)
                    }
                }
                if (state.locationMessageRes != null) LocationNotice(state, actions)
                if (state.sameEndpoints) Surface(color = scheme.surface, shape = MaterialTheme.shapes.large) {
                    Text(stringResource(R.string.input_same_endpoints), Modifier.fillMaxWidth().padding(Spacing.l), style = MaterialTheme.typography.bodyMedium, color = scheme.error)
                }
            }
            if (!mapUnavailable) Surface(Modifier.align(Alignment.BottomEnd).windowInsetsPadding(WindowInsets.safeDrawing).padding(Spacing.l), shape = androidx.compose.foundation.shape.CircleShape, color = scheme.surface) {
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
