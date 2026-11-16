package com.sunseat.app.ui.candidates

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.material3.*
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.platform.LocalContext
import com.sunseat.app.ui.input.NeutralTabs
import com.sunseat.app.ui.input.NeutralFilterChip
import com.sunseat.app.ui.input.TimeSheet
import com.sunseat.app.ui.input.scheduleLabel
import com.sunseat.app.data.route.TransitRouteType
import com.sunseat.app.domain.SunGoal
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import com.sunseat.app.R
import com.sunseat.app.domain.Mode
import com.sunseat.app.domain.TravelMode
import com.sunseat.app.domain.TripRequest
import com.sunseat.app.ui.components.FloatingCapsule
import com.sunseat.app.ui.components.PrimaryButton
import com.sunseat.app.ui.components.QuietButton
import com.sunseat.app.ui.components.SunLoader
import com.sunseat.app.ui.formatDuration
import com.sunseat.app.ui.formatWon
import com.sunseat.app.ui.theme.CapsuleShape
import com.sunseat.app.ui.theme.Sizes
import com.sunseat.app.ui.theme.Spacing
import com.sunseat.app.ui.theme.SunSeatTheme
import com.sunseat.app.ui.theme.TransitLineColors
import com.sunseat.app.ui.theme.rememberSunSeatHaptics
import com.valentinilk.shimmer.shimmer
import java.time.format.DateTimeFormatter

data class CandidatesActions(
    val onHighlight: (CandidateUi) -> Unit = {},
    val onSort: (CandidateSort) -> Unit = {},
    val onFilter: (TransitRouteType) -> Unit = {},
    val onClearFilters: () -> Unit = {},
    val onRequestChange: (TripRequest) -> Unit = {},
    val onBack: () -> Unit = {},
    val onSelect: (CandidateUi) -> Unit = {},
    val onRetry: () -> Unit = {},
    val onSearchByCar: () -> Unit = {},
)

private val ArrivalFormat = DateTimeFormatter.ofPattern("HH:mm")

/** 경로 후보 화면. 상태 없음(미리보기·스크린샷용). */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CandidatesScreen(
    request: TripRequest,
    state: CandidatesState,
    actions: CandidatesActions,
    modifier: Modifier = Modifier,
    animateLoader: Boolean = true,
    scaffoldState: BottomSheetScaffoldState = rememberBottomSheetScaffoldState(),
) {
    val scheme = SunSeatTheme.scheme
    var showTime by rememberSaveable { mutableStateOf(false) }
    val options = state as? CandidatesState.Options
    val peek = Sizes.mapHeight + Sizes.mapHeight / 2 + Spacing.touch + Spacing.xxl
    BottomSheetScaffold(
        modifier = modifier.fillMaxSize(), scaffoldState = scaffoldState, containerColor = scheme.background,
        sheetContainerColor = scheme.surface, sheetPeekHeight = peek,
        topBar = {
            Surface(color = scheme.surface) {
                Column(Modifier.fillMaxWidth().statusBarsPadding().padding(horizontal = Spacing.screen, vertical = Spacing.s), verticalArrangement = Arrangement.spacedBy(Spacing.s)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        IconButton(onClick = actions.onBack) { Icon(painterResource(R.drawable.ic_ms_arrow_back), stringResource(R.string.common_back)) }
                        Text("${request.origin.name} → ${request.destination.name}", Modifier.weight(1f).heightIn(min = Spacing.touch).clickable(role = Role.Button, onClick = actions.onBack), style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                    NeutralTabs(listOf(stringResource(R.string.input_mode_transit), stringResource(R.string.input_mode_car)), request.mode.ordinal) { actions.onRequestChange(request.copy(mode = TravelMode.entries[it])) }
                    QuietButton(onClick = { showTime = true }) { Text(scheduleLabel(request.departure, request.timeMode)); Icon(painterResource(R.drawable.ic_ms_chevron_right), null, Modifier.size(Sizes.iconSmall).rotate(90f)) }
                }
            }
        },
        sheetContent = {
            Column(Modifier.fillMaxWidth().heightIn(max = Sizes.mapHeight * 3), verticalArrangement = Arrangement.spacedBy(Spacing.s)) {
                if (options != null) {
                    Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState(initial = if (options.sort.ordinal >= 3) Int.MAX_VALUE else 0)).padding(horizontal = Spacing.screen), horizontalArrangement = Arrangement.spacedBy(Spacing.s)) {
                        CandidateSort.entries.forEach { sort -> NeutralFilterChip(options.sort == sort, sortLabel(sort, request.goal)) { actions.onSort(sort) } }
                    }
                    Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = Spacing.screen), horizontalArrangement = Arrangement.spacedBy(Spacing.s)) {
                        listOf(TransitRouteType.BUS, TransitRouteType.SUBWAY, TransitRouteType.BUS_AND_SUBWAY).forEach { type ->
                            NeutralFilterChip(type in options.filters, stringResource(when (type) {
                                TransitRouteType.BUS -> R.string.candidates_filter_bus
                                TransitRouteType.SUBWAY -> R.string.candidates_filter_subway
                                else -> R.string.candidates_filter_mixed
                            })) { actions.onFilter(type) }
                        }
                    }
                }
                val content = PaddingValues(Spacing.screen)
                when (state) {
                    is CandidatesState.Loading -> Loading(content, state.mode, animateLoader)
                    is CandidatesState.Options -> if (state.visible.isEmpty()) Message(content, R.drawable.ic_ms_search, stringResource(R.string.candidates_filtered_empty), null, stringResource(R.string.candidates_clear_filters) to actions.onClearFilters, stringResource(R.string.candidates_edit_input) to actions.onBack)
                        else OptionList(state.visible, state.selectedId, request, content, actions)
                    is CandidatesState.NoRoute -> Message(content, R.drawable.ic_ms_directions_bus, stringResource(state.titleRes), state.detailRes?.let { stringResource(it) }, if (state.suggestCar) stringResource(R.string.candidates_search_by_car) to actions.onSearchByCar else null, stringResource(R.string.candidates_edit_input) to actions.onBack)
                    is CandidatesState.Error -> Message(content, state.iconRes, stringResource(state.messageRes), null, if (state.canRetry) stringResource(R.string.search_retry) to actions.onRetry else null, stringResource(R.string.candidates_edit_input) to actions.onBack)
                }
            }
        },
    ) { _ ->
        com.sunseat.app.ui.map.SunSeatMap(com.sunseat.app.ui.map.MapState(
            routes = options?.items?.map { it.chosen.route }.orEmpty(),
            selectedRoute = options?.items?.indexOfFirst { it.id == options.selectedId } ?: -1,
            origin = request.origin, destination = request.destination,
            padding = com.sunseat.app.ui.map.MapPadding(bottom = peek), showDecorations = false,
        ), Modifier.fillMaxSize())
    }
    if (showTime) TimeSheet(request.departure, request.timeMode, request.mode, { showTime = false }, { value, mode -> actions.onRequestChange(request.copy(departure = value, timeMode = mode)) })
}

@Composable private fun sortLabel(sort: CandidateSort, goal: SunGoal): String = stringResource(when (sort) {
    CandidateSort.RECOMMENDED -> R.string.candidates_sort_recommended
    CandidateSort.TIME -> R.string.candidates_sort_time
    CandidateSort.TRANSFERS -> R.string.candidates_sort_transfers
    CandidateSort.WALK -> R.string.candidates_sort_walk
    CandidateSort.SUN -> if (goal == SunGoal.AVOID) R.string.candidates_sort_sun_less else R.string.candidates_sort_sun_more
})

@Composable
private fun Loading(content: PaddingValues, mode: TravelMode, animate: Boolean) {
    val scheme = SunSeatTheme.scheme
    Column(
        Modifier.fillMaxSize().padding(content),
        verticalArrangement = Arrangement.spacedBy(Spacing.m),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Spacing.s)) {
            SunLoader(description = stringResource(R.string.candidates_loading), playing = animate)
            Text(stringResource(R.string.candidates_loading), style = MaterialTheme.typography.titleMedium, color = scheme.onSurface)
        }
        // 자가용은 고를 목록이 없으므로 카드 뼈대를 보여 주지 않는다
        if (mode == TravelMode.TRANSIT) repeat(3) { SkeletonCard() }
    }
}

/** 실제 후보 카드와 같은 구조의 뼈대: 시간+메타 / 구간 칩 / 승하차. 반짝임은 막대에만. */
@Composable
private fun SkeletonCard() {
    val scheme = SunSeatTheme.scheme
    Surface(color = scheme.surface, shape = MaterialTheme.shapes.large, modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(Spacing.l).shimmer(), verticalArrangement = Arrangement.spacedBy(Spacing.m)) {
            Row(verticalAlignment = Alignment.Bottom) {
                Bar(Sizes.skeletonNarrow, Sizes.skeletonTitle)
                Spacer(Modifier.weight(1f))
                Bar(Sizes.skeletonMedium, Sizes.skeletonLine)
            }
            Row(horizontalArrangement = Arrangement.spacedBy(Spacing.s)) {
                repeat(3) { Bar(Sizes.skeletonChip, Sizes.skeletonChipHeight, CapsuleShape) }
            }
            Bar(Sizes.skeletonWide, Sizes.skeletonLine)
        }
    }
}

@Composable
private fun Bar(width: Dp, height: Dp, shape: androidx.compose.ui.graphics.Shape = MaterialTheme.shapes.extraSmall) {
    Box(Modifier.width(width).height(height).clip(shape).background(SunSeatTheme.scheme.surfaceContainerHighest))
}

@Composable
private fun OptionList(items: List<CandidateUi>, selectedId: Int?, request: TripRequest, content: PaddingValues, actions: CandidatesActions) {
    val haptics = rememberSunSeatHaptics()
    LazyColumn(Modifier.fillMaxSize(), contentPadding = content, verticalArrangement = Arrangement.spacedBy(Spacing.m)) {
        items(items, key = { it.id }) { item ->
            CandidateCard(item, item.id == selectedId, request, onClick = { haptics.selectionChanged(); actions.onHighlight(item) }, onView = { haptics.press(); actions.onSelect(item) })
        }
    }
}

@Composable
private fun CandidateCard(item: CandidateUi, selected: Boolean, request: TripRequest, onClick: () -> Unit, onView: () -> Unit) {
    val scheme = SunSeatTheme.scheme
    Surface(color = if (selected) scheme.surfaceContainerHigh else scheme.surface,
        shape = MaterialTheme.shapes.large,
        modifier = Modifier.fillMaxWidth().semantics { this.selected = selected }
            .then(if (selected) Modifier.border(Sizes.hairline, scheme.onSurface, MaterialTheme.shapes.large) else Modifier)
            .clickable(role = Role.Button, onClickLabel = stringResource(R.string.candidates_highlight), onClick = onClick)) {
        Column(Modifier.padding(Spacing.l), verticalArrangement = Arrangement.spacedBy(Spacing.s)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Spacing.s)) {
                Text(formatDuration(item.totalDuration), style = MaterialTheme.typography.headlineSmall, color = scheme.onSurface)
                item.arrival?.let { Text(stringResource(R.string.candidates_arrival, it.format(ArrivalFormat)), Modifier.weight(1f), style = MaterialTheme.typography.bodySmall, color = scheme.onSurfaceVariant) }
                item.fare?.let { Text(formatWon(it), style = MaterialTheme.typography.bodySmall, color = scheme.onSurfaceVariant) }
                if (selected) Icon(painterResource(R.drawable.ic_ms_check), stringResource(R.string.candidates_selected), Modifier.size(Sizes.iconSmall))
            }
            RouteSegmentBar(item)
            Text(listOf(stringResource(R.string.candidates_transfers, item.transfers), stringResource(R.string.candidates_walk, item.walkMinutes)).joinToString(" · "), style = MaterialTheme.typography.bodySmall, color = scheme.onSurfaceVariant)
            val result = item.chosen.cachedResult
            if (result == null) {
                Box(Modifier.fillMaxWidth().height(Sizes.timelineBar).background(scheme.surfaceContainerHighest))
                Text(stringResource(R.string.candidates_calculating_sun), style = MaterialTheme.typography.bodySmall, color = scheme.onSurfaceVariant)
            } else {
                if (result.sunIfFollowed.isZero && result.shadeIfFollowed.isZero) Text(stringResource(R.string.result_no_outdoor), style = MaterialTheme.typography.bodyMedium)
                else {
                    Row(horizontalArrangement = Arrangement.spacedBy(Spacing.s)) {
                        com.sunseat.app.ui.components.ExposurePill(com.sunseat.app.ui.components.Exposure.Shade, stringResource(R.string.result_shade, formatDuration(result.shadeIfFollowed, zeroOk = true)))
                        com.sunseat.app.ui.components.ExposurePill(com.sunseat.app.ui.components.Exposure.Sun, stringResource(R.string.result_sun, formatDuration(result.sunIfFollowed, zeroOk = true)))
                    }
                    val (res, arg) = com.sunseat.app.ui.result.headlineRes(LocalContext.current, request, result)
                    Text(if (arg == null) stringResource(res) else stringResource(res, arg), style = MaterialTheme.typography.bodySmall, color = scheme.onSurfaceVariant)
                }
            }
            PrimaryButton(onClick = onView, modifier = Modifier.fillMaxWidth()) { Text(stringResource(R.string.candidates_view_seat)) }
        }
    }
}

@Composable
private fun Message(
    content: PaddingValues,
    iconRes: Int,
    title: String,
    detail: String?,
    primary: Pair<String, () -> Unit>?,
    secondary: Pair<String, () -> Unit>,
) {
    val scheme = SunSeatTheme.scheme
    Column(
        Modifier.fillMaxSize().padding(content),
        verticalArrangement = Arrangement.spacedBy(Spacing.m),
    ) {
        Spacer(Modifier.height(Spacing.xl))
        Icon(painterResource(iconRes), contentDescription = null, tint = scheme.onSurfaceVariant, modifier = Modifier.size(Sizes.iconLarge))
        Text(title, style = MaterialTheme.typography.titleLarge, color = scheme.onSurface, modifier = Modifier.semantics { heading() })
        if (detail != null) Text(detail, style = MaterialTheme.typography.bodyLarge, color = scheme.onSurfaceVariant)
        Spacer(Modifier.height(Spacing.s))
        if (primary != null) {
            PrimaryButton(onClick = primary.second, modifier = Modifier.fillMaxWidth()) {
                Text(primary.first, style = MaterialTheme.typography.labelLarge)
            }
        }
        QuietButton(onClick = secondary.second, modifier = Modifier.fillMaxWidth()) {
            Text(secondary.first, style = MaterialTheme.typography.labelLarge)
        }
    }
}
