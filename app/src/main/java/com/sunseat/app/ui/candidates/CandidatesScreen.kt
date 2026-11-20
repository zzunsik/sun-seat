package com.sunseat.app.ui.candidates

import androidx.activity.compose.BackHandler
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.layout.navigationBarsPadding
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.first
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
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
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.displayCutout
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.ui.semantics.stateDescription
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
    val onEditEndpoint: (com.sunseat.app.ui.input.EndpointField) -> Unit = {},
    val onSwap: () -> Unit = {},
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

/** 지도에 넘길 후보 ID·경로와 그 목록 안의 선택 index. [selectedRoute]는 항상 [routes] 기준이다. */
internal data class CandidateMapRoutes(val ids: List<Int>, val routes: List<com.sunseat.app.domain.Route>, val selectedRoute: Int)

/**
 * 목록 개요는 비교용으로 모든 후보를 id 순으로 넘기고, 선택 경로 미리보기는 선택 경로만 넘긴다(#99).
 * 다른 후보가 미리보기에서 현재 여정의 분기처럼 보이지 않게 하고, 줄어든 목록에 예전 index를 쓰지 않는다.
 */
internal fun candidateMapRoutes(visible: List<CandidateUi>, selectedId: Int?, preview: Boolean): CandidateMapRoutes {
    val shown = if (preview) visible.filter { it.id == selectedId } else visible.sortedBy { it.id }
    return CandidateMapRoutes(shown.map { it.id }, shown.map { it.chosen.route }, shown.indexOfFirst { it.id == selectedId })
}

/** 지도에 실제로 넘긴 후보 ID와 선택 index(테스트 관측용). */
internal val CandidateMapRouteIdsKey = androidx.compose.ui.semantics.SemanticsPropertyKey<List<Int>>("CandidateMapRouteIds")
internal val CandidateMapSelectedRouteKey = androidx.compose.ui.semantics.SemanticsPropertyKey<Int>("CandidateMapSelectedRoute")

private val ArrivalFormat = DateTimeFormatter.ofPattern("HH:mm")

/** 경로 후보 화면. 상태 없음(미리보기·스크린샷용). */
@OptIn(ExperimentalMaterial3Api::class, kotlinx.coroutines.FlowPreview::class)
@Composable
fun CandidatesScreen(
    request: TripRequest,
    state: CandidatesState,
    actions: CandidatesActions,
    modifier: Modifier = Modifier,
    now: java.time.LocalDateTime = java.time.LocalDateTime.now(),
    animateLoader: Boolean = true,
    scaffoldState: BottomSheetScaffoldState = rememberBottomSheetScaffoldState(),
) {
    val scheme = SunSeatTheme.scheme
    var showTime by rememberSaveable { mutableStateOf(false) }
    val options = state as? CandidatesState.Options
    var showPreview by rememberSaveable(request.origin, request.destination, request.mode) { mutableStateOf(false) }
    val previewCandidate = options?.visible?.firstOrNull { it.id == options.selectedId }
    val preview = showPreview && previewCandidate != null
    val listScroll = rememberLazyListState()
    val sortScroll = rememberScrollState(initial = if ((options?.sort?.ordinal ?: 0) >= 3) Int.MAX_VALUE else 0)
    val filterScroll = rememberScrollState()
    val scope = rememberCoroutineScope()
    var fitTrigger by remember { mutableLongStateOf(0L) }
    var fitRevision by remember { mutableLongStateOf(0L) }
    var fittedTrigger by remember { mutableLongStateOf(-1L) }
    var userMoved by remember { mutableStateOf(false) }
    // 카메라 맞춤 트리거는 목록 개요의 후보 집합 기준이라 미리보기 진입·닫기만으로 다시 맞추지 않는다.
    val routes = options?.visible?.sortedBy { it.id }.orEmpty().map { it.chosen.route }
    val mapRoutes = candidateMapRoutes(options?.visible.orEmpty(), options?.selectedId, preview)
    fun closePreview() { showPreview = false; scope.launch { scaffoldState.bottomSheetState.partialExpand() } }
    BackHandler(enabled = preview) { closePreview() }
    LaunchedEffect(routes, options?.selectedId) { userMoved = false; fitTrigger++ }
    LaunchedEffect(preview) {
        if (preview) { androidx.compose.runtime.withFrameNanos { }; scaffoldState.bottomSheetState.partialExpand(); fitTrigger++ }
    }
    val density = LocalDensity.current
    var headerHeight by remember { mutableStateOf(0.dp) }
    var mapTop by remember { mutableStateOf(0.dp) }
    var mapHeight by remember { mutableStateOf(0.dp) }
    var mapWidth by remember { mutableStateOf(0.dp) }
    var scaffoldTop by remember { mutableStateOf(0.dp) }
    // #101: 목록 상단은 펼친 헤더 전체 또는 축약 여정 바 전체 중 하나만 보인다(DESIGN 5.1, 12.3).
    var fullHeaderHeight by remember { mutableStateOf(0.dp) }
    var journeyBarHeight by remember { mutableStateOf(0.dp) }
    var dragHandleHeight by remember { mutableStateOf(0.dp) }
    val statusBarTop = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()
    // 가로 화면 옆 컷아웃 아래로 뒤로·편집 컨트롤이 들어가지 않게 한다.
    val cutoutSides = WindowInsets.displayCutout.only(WindowInsetsSides.Horizontal)
    // 펼친 헤더가 접혔다 다시 보여도 스크롤 위치를 유지한다.
    val headerScroll = rememberScrollState()
    // 축약 여정 바를 재기 전 어림값: 제목 2줄 + 보조 1줄(현재 글자 크기). 실제보다 작게 어림하면 시트가 바를 덮는다.
    val typography = MaterialTheme.typography
    val journeyBarEstimate = statusBarTop + with(density) { maxOf(Sizes.journeyBar, (typography.titleMedium.lineHeight * 2).toDp() + typography.bodySmall.lineHeight.toDp() + Spacing.xs * 2) }
    BoxWithConstraints(modifier.fillMaxSize()) {
    val viewportHeight = maxHeight
    val headerLimit = (maxHeight - Spacing.touch * 3).coerceAtLeast(Spacing.touch)
    // 펼친 헤더를 재기 전에는 가장 큰 경우(headerLimit)로 보아 기본 시트가 헤더를 덮지 않게 한다.
    val listHeader = if (fullHeaderHeight > 0.dp) fullHeaderHeight else headerLimit
    val peekTop = if (preview) headerHeight else listHeader
    val peek = (if (preview) Sizes.mapHeight.coerceAtMost((maxHeight - peekTop).coerceAtLeast(Spacing.touch) / 2) else Sizes.mapHeight + Sizes.mapHeight / 2 + Spacing.touch + Spacing.xxl)
        .coerceAtMost((maxHeight - peekTop).coerceAtLeast(Spacing.touch))
    // 완전 확장한 시트(손잡이 포함)의 위끝은 축약 여정 바 바로 아래다. 바·손잡이를 재기 전에는 최소 높이로 어림한다.
    val listSheetMax = (maxHeight - (if (journeyBarHeight > 0.dp) journeyBarHeight else journeyBarEstimate) - (if (dragHandleHeight > 0.dp) dragHandleHeight else Spacing.touch)).coerceAtLeast(Spacing.touch)
    // 시트가 펼친 헤더 아래끝보다 올라오면(완전 확장·중간 드래그) 펼친 헤더를 통째로 접고 축약 여정 바로 바꾼다.
    // 시트 앵커는 헤더 높이와 무관하므로 바뀌어도 시트가 움직이지 않는다.
    val fullHeaderPx = with(density) { listHeader.toPx() }
    val compactHeader by remember(preview, fullHeaderPx) {
        derivedStateOf {
            val top = runCatching { scaffoldState.bottomSheetState.requireOffset() }.getOrDefault(Float.NaN)
            !preview && top.isFinite() && top < fullHeaderPx - 0.5f
        }
    }
    val offset = runCatching { with(density) { scaffoldState.bottomSheetState.requireOffset().toDp() } }.getOrDefault(maxHeight - peek)
    val mapPadding = com.sunseat.app.ui.map.sheetMapPadding(mapTop, mapHeight, scaffoldTop + offset)
    LaunchedEffect(fitTrigger, mapHeight, mapWidth) {
        if (fittedTrigger == fitTrigger) return@LaunchedEffect
        snapshotFlow { runCatching { scaffoldState.bottomSheetState.requireOffset() }.getOrDefault(Float.NaN) }
            .debounce(com.sunseat.app.ui.theme.Motion.CAMERA_SETTLE_MS)
            .first { value -> value.isFinite() && !scaffoldState.bottomSheetState.isAnimationRunning &&
                mapHeight - com.sunseat.app.ui.map.sheetMapPadding(mapTop, mapHeight, scaffoldTop + with(density) { value.toDp() }).bottom > Sizes.mapInset * 2 }
        if (!userMoved) fitRevision++
        fittedTrigger = fitTrigger
    }
    BottomSheetScaffold(
        modifier = Modifier.fillMaxSize().onGloballyPositioned { scaffoldTop = with(density) { it.positionInRoot().y.toDp() } }, scaffoldState = scaffoldState, containerColor = scheme.background,
        sheetContainerColor = scheme.surface, sheetPeekHeight = peek,
        sheetDragHandle = { Box(Modifier.testTag("candidates_sheet_handle").onSizeChanged { dragHandleHeight = with(density) { it.height.toDp() } }) { BottomSheetDefaults.DragHandle() } },
        topBar = {
            Surface(color = scheme.surface, modifier = Modifier.testTag("candidates_header").onSizeChanged { headerHeight = with(density) { it.height.toDp() } }) {
                if (preview) Row(Modifier.fillMaxWidth().statusBarsPadding().windowInsetsPadding(cutoutSides).padding(horizontal = Spacing.s), verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = ::closePreview) { Icon(painterResource(R.drawable.ic_ms_arrow_back), stringResource(R.string.candidates_preview_close)) }
                    Text("${request.origin.name} → ${request.destination.name}", Modifier.weight(1f), style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    QuietButton(onClick = { userMoved = false; fitTrigger++ }) { Text(stringResource(R.string.candidates_fit_route)) }
                } else if (compactHeader) JourneyBar(request, now, actions.onBack, Modifier.windowInsetsPadding(cutoutSides).onSizeChanged { journeyBarHeight = with(density) { it.height.toDp() } }) {
                    scope.launch { scaffoldState.bottomSheetState.partialExpand() }
                } else Column(Modifier.onSizeChanged { fullHeaderHeight = with(density) { it.height.toDp() } }.fillMaxWidth().heightIn(max = headerLimit).verticalScroll(headerScroll).statusBarsPadding().windowInsetsPadding(cutoutSides).padding(horizontal = Spacing.screen, vertical = Spacing.s), verticalArrangement = Arrangement.spacedBy(Spacing.s)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        IconButton(onClick = actions.onBack) { Icon(painterResource(R.drawable.ic_ms_arrow_back), stringResource(R.string.common_back)) }
                        Column(Modifier.weight(1f)) {
                            com.sunseat.app.ui.input.EndpointRow(stringResource(R.string.input_origin), request.origin.name, stringResource(R.string.input_origin_placeholder), R.drawable.ic_ms_search) { actions.onEditEndpoint(com.sunseat.app.ui.input.EndpointField.ORIGIN) }
                            HorizontalDivider(color = scheme.outlineVariant)
                            com.sunseat.app.ui.input.EndpointRow(stringResource(R.string.input_destination), request.destination.name, stringResource(R.string.input_destination_placeholder), R.drawable.ic_ms_search) { actions.onEditEndpoint(com.sunseat.app.ui.input.EndpointField.DESTINATION) }
                        }
                        IconButton(onClick = actions.onSwap, enabled = request.origin.location != request.destination.location) {
                            Icon(painterResource(R.drawable.ic_ms_swap_vert), stringResource(R.string.input_swap))
                        }
                    }
                    NeutralTabs(listOf(stringResource(R.string.input_mode_transit), stringResource(R.string.common_taxi), stringResource(R.string.common_car)), request.mode.ordinal) { actions.onRequestChange(request.copy(mode = TravelMode.entries[it])) }
                    QuietButton(onClick = { showTime = true }) { Text(scheduleLabel(request.departure, now)); Icon(painterResource(R.drawable.ic_ms_chevron_right), null, Modifier.size(Sizes.iconSmall).rotate(90f)) }
                }
            }
        },
        sheetContent = {
            if (preview && previewCandidate != null) {
                Column(Modifier.fillMaxWidth().heightIn(max = (viewportHeight - headerHeight - (if (dragHandleHeight > 0.dp) dragHandleHeight else Spacing.touch)).coerceAtLeast(Spacing.touch)).verticalScroll(rememberScrollState()).navigationBarsPadding().padding(Spacing.screen), verticalArrangement = Arrangement.spacedBy(Spacing.s)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text(stringResource(R.string.candidates_preview_title), Modifier.semantics { heading() }, style = MaterialTheme.typography.titleMedium)
                            Text(formatDuration(previewCandidate.totalDuration), style = MaterialTheme.typography.bodyMedium)
                        }
                        IconButton(onClick = ::closePreview) { Icon(painterResource(R.drawable.ic_ms_close), stringResource(R.string.candidates_preview_close)) }
                    }
                    PrimaryButton(onClick = { showPreview = false; actions.onSelect(previewCandidate) }, modifier = Modifier.fillMaxWidth()) { Text(stringResource(R.string.candidates_view_seat)) }
                }
            } else Column(Modifier.testTag("candidates_sheet").fillMaxWidth().heightIn(max = listSheetMax).navigationBarsPadding(), verticalArrangement = Arrangement.spacedBy(Spacing.s)) {
                if (options != null && request.mode == TravelMode.TRANSIT) {
                    Row(Modifier.fillMaxWidth().horizontalScroll(sortScroll).padding(horizontal = Spacing.screen), horizontalArrangement = Arrangement.spacedBy(Spacing.s)) {
                        CandidateSort.entries.forEach { sort -> NeutralFilterChip(options.sort == sort, sortLabel(sort, request.goal)) { actions.onSort(sort) } }
                    }
                    Row(Modifier.fillMaxWidth().horizontalScroll(filterScroll).padding(horizontal = Spacing.screen), horizontalArrangement = Arrangement.spacedBy(Spacing.s)) {
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
                        else OptionList(state.visible, state.selectedId, request, content, if (request.mode == TravelMode.TRANSIT) stringResource(if (state.sort == CandidateSort.WALK) R.string.candidates_walk_scope_sort else R.string.candidates_walk_scope) else null, actions.copy(onHighlight = {
                            userMoved = false
                            showPreview = true
                            actions.onHighlight(it)
                        }), listScroll)
                    is CandidatesState.NoRoute -> Message(content, R.drawable.ic_ms_directions_bus, stringResource(state.titleRes), state.detailRes?.let { stringResource(it) }, if (state.suggestCar) stringResource(R.string.candidates_search_by_car) to actions.onSearchByCar else null, stringResource(R.string.candidates_edit_input) to actions.onBack)
                    is CandidatesState.Error -> Message(content, state.iconRes, stringResource(state.messageRes), null, if (state.canRetry) stringResource(R.string.search_retry) to actions.onRetry else null, stringResource(R.string.candidates_edit_input) to actions.onBack)
                }
            }
        },
    ) { _ ->
        // The map fills the scaffold body below topBar; measured sheet overlap supplies its bottom inset.
        Box(Modifier.fillMaxSize()) {
        com.sunseat.app.ui.map.SunSeatMap(com.sunseat.app.ui.map.MapState(
            routes = mapRoutes.routes,
            selectedRoute = mapRoutes.selectedRoute,
            origin = request.origin, destination = request.destination,
            padding = mapPadding, showDecorations = false,
            automaticFit = false, cameraRevision = fitRevision,
            fitEnabled = mapHeight - mapPadding.bottom > Sizes.mapInset * 2,
            onGesture = { userMoved = true },
        ), Modifier.fillMaxSize().testTag("candidate_map").semantics {
            this[com.sunseat.app.ui.map.MapCameraRevisionKey] = fitRevision
            this[CandidateMapRouteIdsKey] = mapRoutes.ids
            this[CandidateMapSelectedRouteKey] = mapRoutes.selectedRoute
            this[com.sunseat.app.ui.map.MapVisibleHeightKey] = (mapHeight - mapPadding.bottom).value
        }.onGloballyPositioned {
            with(density) { mapTop = it.positionInRoot().y.toDp(); mapHeight = it.size.height.toDp(); mapWidth = it.size.width.toDp() }
        })
        }
    }
    }
    if (showTime) TimeSheet(request.departure, request.mode, { showTime = false }, { value -> actions.onRequestChange(request.copy(departure = value)) }, now = now)
}

@Composable private fun sortLabel(sort: CandidateSort, goal: SunGoal): String = stringResource(when (sort) {
    CandidateSort.RECOMMENDED -> R.string.candidates_sort_recommended
    CandidateSort.TIME -> R.string.candidates_sort_time
    CandidateSort.TRANSFERS -> R.string.candidates_sort_transfers
    CandidateSort.WALK -> R.string.candidates_sort_walk
    CandidateSort.SUN -> if (goal == SunGoal.AVOID) R.string.candidates_sort_sun_less else R.string.candidates_sort_sun_more
})

/**
 * 축약 여정 바(DESIGN 5.1): 시트를 완전히 펼쳤을 때 펼친 헤더 대신 보인다.
 * 뒤로 + "출발 → 도착" / "수단 · 출발 시각". 바를 누르면 시트를 기본 높이로 내려 펼친 헤더(편집·맞바꾸기·시각)로 돌아간다.
 */
@Composable
private fun JourneyBar(request: TripRequest, now: java.time.LocalDateTime, onBack: () -> Unit, modifier: Modifier = Modifier, onExpand: () -> Unit) {
    val scheme = SunSeatTheme.scheme
    val mode = stringResource(when (request.mode) {
        TravelMode.TRANSIT -> R.string.input_mode_transit
        TravelMode.TAXI -> R.string.common_taxi
        TravelMode.CAR -> R.string.common_car
    })
    Row(modifier.fillMaxWidth().statusBarsPadding().heightIn(min = Sizes.journeyBar).padding(horizontal = Spacing.s), verticalAlignment = Alignment.CenterVertically) {
        IconButton(onClick = onBack) { Icon(painterResource(R.drawable.ic_ms_arrow_back), stringResource(R.string.common_back)) }
        Column(
            Modifier.weight(1f).heightIn(min = Spacing.touch).testTag("candidates_journey_bar")
                .clickable(role = Role.Button, onClickLabel = stringResource(R.string.candidates_journey_expand), onClick = onExpand)
                .padding(horizontal = Spacing.s, vertical = Spacing.xs),
            verticalArrangement = Arrangement.Center,
        ) {
            Text("${request.origin.name} → ${request.destination.name}", style = MaterialTheme.typography.titleMedium, color = scheme.onSurface, maxLines = 2, overflow = TextOverflow.Ellipsis)
            Text("$mode · ${scheduleLabel(request.departure, now)}", style = MaterialTheme.typography.bodySmall, color = scheme.onSurfaceVariant)
        }
    }
}

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
private fun OptionList(items: List<CandidateUi>, selectedId: Int?, request: TripRequest, content: PaddingValues, scopeNotice: String?, actions: CandidatesActions, listState: LazyListState) {
    val haptics = rememberSunSeatHaptics()
    var showScope by remember { mutableStateOf(false) }
    if (showScope && scopeNotice != null) AlertDialog(
        onDismissRequest = { showScope = false },
        title = { Text(stringResource(R.string.candidates_scope_action)) },
        text = { Column(verticalArrangement = Arrangement.spacedBy(Spacing.s)) {
            Text(scopeNotice)
            Text(stringResource(R.string.candidates_scope_detail))
        } },
        confirmButton = { QuietButton(onClick = { showScope = false }) { Text(stringResource(R.string.candidates_scope_close)) } },
    )
    LazyColumn(Modifier.fillMaxSize().testTag("candidate_list"), state = listState, contentPadding = PaddingValues(top = content.calculateTopPadding(), bottom = content.calculateBottomPadding())) {
        if (scopeNotice != null) item(key = "walk_scope") {
            QuietButton(onClick = { showScope = true }, modifier = Modifier.testTag("walk_scope_notice").padding(horizontal = Spacing.xs)) {
                Icon(painterResource(R.drawable.ic_ms_directions_walk), null, Modifier.size(Sizes.iconSmall))
                Spacer(Modifier.width(Spacing.s))
                Text(stringResource(R.string.candidates_scope_action), style = MaterialTheme.typography.bodySmall)
            }
        }
        request.departure?.let { departure -> item(key = "time_basis") {
            Text(stringResource(R.string.candidates_time_basis, departure.format(DateTimeFormatter.ofPattern("M월 d일 HH:mm"))),
                style = MaterialTheme.typography.bodySmall, color = SunSeatTheme.scheme.onSurfaceVariant)
        } }
        items(items, key = { it.id }) { item ->
            Column {
                CandidateCard(item, item.id == selectedId, request, onClick = { haptics.selectionChanged(); actions.onHighlight(item) }, onView = { haptics.press(); actions.onSelect(item) })
                HorizontalDivider(Modifier.padding(start = Spacing.l), thickness = Sizes.hairline, color = SunSeatTheme.scheme.outlineVariant)
            }
        }
    }
}

@Composable
internal fun CandidateCard(item: CandidateUi, selected: Boolean, request: TripRequest, onClick: () -> Unit, onView: () -> Unit) {
    val scheme = SunSeatTheme.scheme
    val routeColors = SunSeatTheme.route
    val taxi = request.mode == TravelMode.TAXI
    val selectedDescription = stringResource(R.string.candidates_map_selected)
    Surface(color = if (selected) routeColors.selectionContainer else scheme.surface,
        modifier = Modifier.fillMaxWidth().testTag("candidate_${item.id}").semantics { this.selected = selected; if (selected) stateDescription = selectedDescription }
            .clickable(role = Role.Button, onClickLabel = stringResource(R.string.candidates_highlight), onClick = onClick)) {
        Box {
            if (selected) androidx.compose.foundation.Canvas(Modifier.matchParentSize()) {
                drawRect(routeColors.selectionIndicator, size = androidx.compose.ui.geometry.Size(Sizes.selectionBar.toPx(), size.height))
            }
            Column(Modifier.padding(horizontal = Spacing.l, vertical = Spacing.m), verticalArrangement = Arrangement.spacedBy(Spacing.xs)) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Spacing.s)) {
                    Text(formatDuration(item.totalDuration), Modifier.weight(1f).testTag("candidate_time_${item.id}"),
                        style = MaterialTheme.typography.titleLarge, color = scheme.onSurface)
                    if (item.fastest) Surface(color = scheme.surfaceContainerHigh, shape = MaterialTheme.shapes.extraSmall) {
                        Text(stringResource(R.string.candidates_fastest), Modifier.heightIn(min = Sizes.badgeHeight).padding(horizontal = Spacing.xs), style = MaterialTheme.typography.labelMedium)
                    }
                }
                FlowRow(horizontalArrangement = Arrangement.spacedBy(Spacing.s), verticalArrangement = Arrangement.spacedBy(Spacing.xs)) {
                    item.arrival?.let { arrival ->
                        val departure = arrival.minus(item.totalDuration)
                        val arrivalLabel = if (arrival.toLocalDate() > departure.toLocalDate())
                            stringResource(R.string.candidates_next_day, arrival.format(ArrivalFormat)) else arrival.format(ArrivalFormat)
                        Text(stringResource(R.string.candidates_compact_schedule, departure.format(ArrivalFormat), arrivalLabel),
                            style = MaterialTheme.typography.bodyMedium, color = scheme.onSurfaceVariant)
                    }
                    item.fare?.let { Text(if (taxi) stringResource(R.string.candidates_taxi_fare, formatWon(it)) else formatWon(it),
                        style = MaterialTheme.typography.bodyMedium, color = scheme.onSurfaceVariant) }
                }
                RouteSegmentBar(item, taxi = taxi)
                if (request.mode == TravelMode.TRANSIT) {
                    val walkLabel = item.providedWalk?.let { walking ->
                        if (!walking.isZero && walking < java.time.Duration.ofMinutes(1)) stringResource(R.string.candidates_transfer_walk_under_minute)
                        else stringResource(R.string.candidates_transfer_walk, walking.toMinutes())
                    }
                    Text(listOfNotNull(stringResource(R.string.candidates_transfers, item.transfers), walkLabel).joinToString(" · "),
                        style = MaterialTheme.typography.bodySmall, color = scheme.onSurfaceVariant)
                }
                if (selected) RouteLegSummary(item, taxi) else {
                    val boardingLabels = routeLegGroups(item).filter { item.chosen.route.legs[it.first].mode != Mode.WALK }
                        .map { routeBoardingLabel(item, it.first, taxi) }
                    Text(boardingLabels.joinToString(" → "), Modifier.testTag("candidate_boarding_${item.id}"),
                        style = MaterialTheme.typography.bodySmall, color = scheme.onSurface)
                }
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(Spacing.s), verticalAlignment = Alignment.Bottom) {
                    FlowRow(Modifier.weight(1f), horizontalArrangement = Arrangement.spacedBy(Spacing.s), verticalArrangement = Arrangement.spacedBy(Spacing.xs)) {
                    val result = item.chosen.cachedResult
                    when {
                        result == null -> Text(stringResource(R.string.candidates_calculating_sun), style = MaterialTheme.typography.bodySmall, color = scheme.onSurfaceVariant)
                        result.advice == com.sunseat.app.domain.TripAdvice.NoSun -> Text(stringResource(R.string.candidates_no_sun), style = MaterialTheme.typography.bodySmall)
                        result.sunIfFollowed.isZero && result.shadeIfFollowed.isZero -> Text(stringResource(R.string.result_no_outdoor), style = MaterialTheme.typography.bodySmall)
                        else -> {
                            com.sunseat.app.ui.components.ExposurePill(com.sunseat.app.ui.components.Exposure.Shade,
                                stringResource(R.string.result_shade, formatDuration(result.shadeIfFollowed, zeroOk = true)), compact = true)
                            com.sunseat.app.ui.components.ExposurePill(com.sunseat.app.ui.components.Exposure.Sun,
                                stringResource(R.string.result_sun, formatDuration(result.sunIfFollowed, zeroOk = true)), compact = true)
                        }
                    }
                    }
                    Box(Modifier.heightIn(min = Spacing.touch), contentAlignment = Alignment.Center) {
                        OutlinedButton(onClick = onView, modifier = Modifier.heightIn(min = Sizes.secondaryButton).testTag("candidate_action_${item.id}"), shape = CapsuleShape,
                            border = androidx.compose.foundation.BorderStroke(Sizes.hairline, scheme.outline),
                            colors = ButtonDefaults.outlinedButtonColors(contentColor = scheme.onSurface)) {
                            Text(stringResource(R.string.candidates_view_seat))
                        }
                    }
                }
            }
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
