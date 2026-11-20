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
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.paneTitle
import androidx.compose.ui.semantics.onClick
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.ui.platform.LocalContext
import com.sunseat.app.ui.input.NeutralTabs
import com.sunseat.app.ui.components.CategoryTab
import com.sunseat.app.ui.components.CategoryTabs
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
    /** 경로 종류 범주 탭 선택(#82). 빈 범주의 "전체 보기"도 [RouteCategory.ALL]로 부른다. */
    val onCategory: (RouteCategory) -> Unit = {},
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
    val scope = rememberCoroutineScope()
    var fitTrigger by remember { mutableLongStateOf(0L) }
    var fitRevision by remember { mutableLongStateOf(0L) }
    var fittedTrigger by remember { mutableLongStateOf(-1L) }
    var userMoved by remember { mutableStateOf(false) }
    // 카메라 맞춤 트리거: 목록 개요의 후보 집합·선택 변경, 미리보기 진입(#102), 미리보기 닫기(#135), 명시적 "지도 맞춤".
    // 미리보기와 목록은 시트 높이(보이는 지도 영역)가 달라, 한쪽에서 맞춘 카메라는 다른 쪽에서 경로를 시트 뒤에 둔다.
    // 사용자가 지도를 직접 움직였으면(userMoved) 맞춤 요청이 와도 카메라를 옮기지 않는다(#71, DESIGN 12.3).
    val routes = options?.visible?.sortedBy { it.id }.orEmpty().map { it.chosen.route }
    val mapRoutes = candidateMapRoutes(options?.visible.orEmpty(), options?.selectedId, preview)
    fun closePreview() { showPreview = false; scope.launch { scaffoldState.bottomSheetState.partialExpand() } }
    BackHandler(enabled = preview) { closePreview() }
    LaunchedEffect(routes, options?.selectedId) { userMoved = false; fitTrigger++ }
    // 이 화면 안에서 미리보기를 연 적이 있을 때만 닫힘을 맞춤 시점으로 본다(첫 구성·결과에서 복귀는 위 후보 집합 맞춤이 맡는다).
    var previewShown by remember { mutableStateOf(false) }
    LaunchedEffect(preview) {
        if (preview) { previewShown = true; androidx.compose.runtime.withFrameNanos { }; scaffoldState.bottomSheetState.partialExpand(); fitTrigger++ }
        // #135: 목록으로 돌아오면 시트가 목록 높이에 자리 잡은 뒤(아래 맞춤 효과가 기다린다) 보이는 지도 띠에 선택 경로를 다시 맞춘다.
        else if (previewShown) { androidx.compose.runtime.withFrameNanos { }; fitTrigger++ }
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
    var previewSummaryHeight by remember { mutableStateOf(0.dp) }
    var fitActionBottom by remember { mutableStateOf(0.dp) }
    val navigationBottom = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
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
    // #102: 미리보기 접힌 높이는 실제로 잰 요약(총시간·막대·노선 조합·자리 보기) 높이이며, 상단 아래 영역의 절반을 넘지 않는다.
    val previewPeek = if (previewSummaryHeight > 0.dp) (if (dragHandleHeight > 0.dp) dragHandleHeight else Spacing.touch) + previewSummaryHeight + navigationBottom else Sizes.mapHeight
    val peek = (if (preview) previewPeek.coerceAtMost((maxHeight - peekTop).coerceAtLeast(Spacing.touch) / 2) else Sizes.mapHeight + Sizes.mapHeight / 2 + Spacing.touch + Spacing.xxl)
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
    // 미리보기의 지도 맞춤 행동 아래로 경로를 맞춰 출발·도착 표식이 행동에 가리지 않게 한다. 가로 화면은 지도가 낮아 위 여백을 빼면 경로가 거의 안 보이므로 주지 않는다.
    val mapPadding = com.sunseat.app.ui.map.sheetMapPadding(mapTop, mapHeight, scaffoldTop + offset)
        .let { if (preview && mapHeight >= mapWidth) it.copy(top = fitActionBottom) else it }
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
                // #102: 미리보기 상단은 축약 여정 바(뒤로 하나 + 2줄까지 여정). 편집은 목록에서 하므로 누름 동작은 없다(DESIGN 12.3).
                if (preview) JourneyBar(request, now, ::closePreview, Modifier.windowInsetsPadding(cutoutSides), backLabel = stringResource(R.string.candidates_preview_close), onExpand = null)
                else if (compactHeader) JourneyBar(request, now, actions.onBack, Modifier.windowInsetsPadding(cutoutSides).onSizeChanged { journeyBarHeight = with(density) { it.height.toDp() } }) {
                    scope.launch { scaffoldState.bottomSheetState.partialExpand() }
                } else Column(Modifier.onSizeChanged { fullHeaderHeight = with(density) { it.height.toDp() } }.fillMaxWidth().heightIn(max = headerLimit).verticalScroll(headerScroll).statusBarsPadding().windowInsetsPadding(cutoutSides).padding(horizontal = Spacing.screen, vertical = Spacing.s)) {
                    // #103: 입력 화면과 같은 펼친 여정 헤더(DESIGN 5.1). 앞에 뒤로 하나, 설정은 두지 않는다.
                    com.sunseat.app.ui.input.JourneyHeader(
                        origin = com.sunseat.app.ui.input.JourneyEndpointUi(request.origin.name),
                        destination = com.sunseat.app.ui.input.JourneyEndpointUi(request.destination.name),
                        mode = request.mode, departure = request.departure, goal = request.goal, now = now,
                        actions = com.sunseat.app.ui.input.JourneyHeaderActions(
                            onEdit = actions.onEditEndpoint, onSwap = actions.onSwap,
                            onMode = { actions.onRequestChange(request.copy(mode = it)) },
                            onTime = { showTime = true },
                            onGoal = { actions.onRequestChange(request.copy(goal = it)) },
                        ),
                        swapEnabled = request.origin.location != request.destination.location,
                        leading = { IconButton(onClick = actions.onBack) { Icon(painterResource(R.drawable.ic_ms_arrow_back), stringResource(R.string.common_back)) } },
                    )
                }
            }
        },
        sheetContent = {
            if (preview && previewCandidate != null) {
                val paneTitle = stringResource(R.string.candidates_preview_title)
                Column(Modifier.testTag("candidates_preview_sheet").semantics { this.paneTitle = paneTitle }.fillMaxWidth().heightIn(max = (viewportHeight - headerHeight - (if (dragHandleHeight > 0.dp) dragHandleHeight else Spacing.touch)).coerceAtLeast(Spacing.touch)).verticalScroll(rememberScrollState()).navigationBarsPadding()) {
                    RoutePreviewSheet(previewCandidate, request, onSeat = { showPreview = false; actions.onSelect(previewCandidate) }) { previewSummaryHeight = with(density) { it.toDp() } }
                }
            } else Column(Modifier.testTag("candidates_sheet").fillMaxWidth().heightIn(max = listSheetMax).navigationBarsPadding(), verticalArrangement = Arrangement.spacedBy(Spacing.s)) {
                if (options != null && request.mode == TravelMode.TRANSIT) {
                    CategoryTabs(RouteCategory.entries.map { category ->
                        val label = categoryLabel(category)
                        val count = options.counts[category] ?: 0
                        CategoryTab(label, count, stringResource(R.string.candidates_category_description, label, count))
                    }, options.category.ordinal) {
                        val category = RouteCategory.entries[it]
                        // #136: 범주를 바꾸면 정렬 변경(#83)처럼 새 범주의 1순위부터 보인다. 같은 탭을 다시 누르면 위치를 유지한다.
                        // 미리보기·결과에서 돌아올 때는 이 경로를 타지 않으므로 목록 위치 복원(#99)은 그대로다.
                        val changed = category != options.category
                        actions.onCategory(category)
                        if (changed) scope.launch { listScroll.scrollToItem(0) }
                    }
                    // #83: 정렬은 현재 기준이 보이는 드롭다운 하나. 기준을 바꾸면 목록을 맨 위(새 1순위)부터 보인다.
                    Row(Modifier.fillMaxWidth().padding(horizontal = Spacing.s), horizontalArrangement = Arrangement.End) {
                        SortDropdown(options.sort, request.goal) { sort ->
                            actions.onSort(sort)
                            scope.launch { listScroll.scrollToItem(0) }
                        }
                    }
                }
                // 범주 탭·정렬 행이 있으면 그 아래 간격(spacedBy)만 두고 목록 위 여백을 겹쳐 쌓지 않는다(#83 디자인 리뷰).
                val content = if (options != null && request.mode == TravelMode.TRANSIT) PaddingValues(start = Spacing.screen, end = Spacing.screen, bottom = Spacing.screen) else PaddingValues(Spacing.screen)
                when (state) {
                    is CandidatesState.Loading -> Loading(content, state.mode, animateLoader)
                    is CandidatesState.Options -> if (state.visible.isEmpty()) Message(content, R.drawable.ic_ms_search, stringResource(R.string.candidates_filtered_empty), null, stringResource(R.string.candidates_clear_filters) to { actions.onCategory(RouteCategory.ALL); scope.launch { listScroll.scrollToItem(0) } }, stringResource(R.string.candidates_edit_input) to actions.onBack)
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
        // #102: 선택 경로 맞춤은 카메라만 옮기는 조용한 행동이다(DESIGN 13). 다른 후보를 다시 그리지 않는다(#99).
        if (preview) {
            val fitLabel = stringResource(R.string.candidates_fit_route_action)
            FloatingCapsule(Modifier.align(Alignment.TopEnd).windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Horizontal)).padding(Spacing.s)
                .onGloballyPositioned { fitActionBottom = with(density) { (it.positionInRoot().y + it.size.height).toDp() } - mapTop }) {
                QuietButton(onClick = { userMoved = false; fitTrigger++ }, modifier = Modifier.semantics { onClick(label = fitLabel) { userMoved = false; fitTrigger++; true } }) {
                    Icon(painterResource(R.drawable.ic_ms_fit_screen), null, Modifier.size(Sizes.iconSmall))
                    Spacer(Modifier.width(Spacing.xs))
                    Text(stringResource(R.string.candidates_fit_route), style = MaterialTheme.typography.labelLarge)
                }
            }
        }
        }
    }
    }
    if (showTime) TimeSheet(request.departure, request.mode, { showTime = false }, { value -> actions.onRequestChange(request.copy(departure = value)) }, now = now)
}

@Composable private fun categoryLabel(category: RouteCategory): String = stringResource(when (category) {
    RouteCategory.ALL -> R.string.candidates_filter_all
    RouteCategory.BUS -> R.string.candidates_filter_bus
    RouteCategory.SUBWAY -> R.string.candidates_filter_subway
    RouteCategory.MIXED -> R.string.candidates_filter_mixed
})

/**
 * 정렬 드롭다운(#83, DESIGN 4.1·12.2): 현재 기준 이름 + 펼침 표시가 있는 버튼 하나.
 * 메뉴 안 현재 기준은 체크 + selected다. 다른 기준을 고르면 한 번 적용하고 닫는다.
 * 같은 기준·바깥 터치·Back은 기준을 바꾸지 않고 메뉴만 닫는다(Back 우선순위 ①, DESIGN 12.3).
 * 열림 상태는 회전·재생성 뒤에도 유지한다.
 */
@Composable
private fun SortDropdown(current: CandidateSort, goal: SunGoal, onSort: (CandidateSort) -> Unit) {
    var open by rememberSaveable { mutableStateOf(false) }
    val haptics = rememberSunSeatHaptics()
    val label = sortLabel(current, goal)
    val description = stringResource(R.string.candidates_sort_description, label)
    val stateText = stringResource(if (open) R.string.candidates_sort_expanded else R.string.candidates_sort_collapsed)
    BackHandler(enabled = open) { open = false }
    Box {
        QuietButton(onClick = { open = true }, modifier = Modifier.testTag("candidates_sort").semantics {
            role = Role.DropdownList
            contentDescription = description
            stateDescription = stateText
        }) {
            Text(label)
            Icon(painterResource(R.drawable.ic_ms_chevron_right), null, Modifier.padding(start = Spacing.xs).size(Sizes.iconSmall).rotate(90f))
        }
        DropdownMenu(expanded = open, onDismissRequest = { open = false }, containerColor = SunSeatTheme.scheme.surfaceContainerHigh,
            border = androidx.compose.foundation.BorderStroke(Sizes.hairline, SunSeatTheme.scheme.outlineVariant)) {
            CandidateSort.entries.forEach { sort ->
                val isCurrent = sort == current
                DropdownMenuItem(
                    text = { Text(sortLabel(sort, goal), fontWeight = if (isCurrent) androidx.compose.ui.text.font.FontWeight.SemiBold else null) },
                    leadingIcon = {
                        if (isCurrent) Icon(painterResource(R.drawable.ic_ms_check), null, Modifier.size(Sizes.iconSmall))
                        else Spacer(Modifier.size(Sizes.iconSmall))
                    },
                    onClick = {
                        open = false
                        if (!isCurrent) { haptics.selectionChanged(); onSort(sort) }
                    },
                    modifier = Modifier.heightIn(min = Spacing.touch).semantics {
                        selected = isCurrent
                        role = Role.RadioButton
                    },
                    colors = MenuDefaults.itemColors(textColor = SunSeatTheme.scheme.onSurface, leadingIconColor = SunSeatTheme.scheme.onSurface),
                )
            }
        }
    }
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
 * 선택 미리보기(#102)도 같은 바를 쓰되 [onExpand] 없이(누름 동작 없음) 뒤로가 목록 복귀다.
 */
@Composable
private fun JourneyBar(request: TripRequest, now: java.time.LocalDateTime, onBack: () -> Unit, modifier: Modifier = Modifier, backLabel: String = stringResource(R.string.common_back), onExpand: (() -> Unit)?) {
    val mode = com.sunseat.app.ui.input.travelModeLabel(request.mode)
    Row(modifier.fillMaxWidth().statusBarsPadding().heightIn(min = Sizes.journeyBar).padding(horizontal = Spacing.s), verticalAlignment = Alignment.CenterVertically) {
        IconButton(onClick = onBack) { Icon(painterResource(R.drawable.ic_ms_arrow_back), backLabel) }
        val expandLabel = stringResource(R.string.candidates_journey_expand)
        com.sunseat.app.ui.input.JourneySummary(
            request.origin.name, request.destination.name, "$mode · ${scheduleLabel(request.departure, now)}",
            Modifier.weight(1f).heightIn(min = Spacing.touch).testTag("candidates_journey_bar")
                .then(if (onExpand != null) Modifier.clickable(role = Role.Button, onClickLabel = expandLabel, onClick = onExpand) else Modifier)
                .padding(horizontal = Spacing.s, vertical = Spacing.xs),
        )
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
        // #134: 목록 항목은 자체 좌우 여백을 갖는다(LazyColumn은 위·아래 여백만 받는다). 안내 줄도 후보 행과 같은 화면 여백에서 시작한다.
        request.departure?.let { departure -> item(key = "time_basis") {
            Text(stringResource(R.string.candidates_time_basis, departure.format(DateTimeFormatter.ofPattern("M월 d일 HH:mm"))),
                Modifier.fillMaxWidth().padding(horizontal = Spacing.screen),
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
                CandidateScheduleFare(item, taxi)
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
                    val rides = routeLegGroups(item).filter { item.chosen.route.legs[it.first].mode != Mode.WALK }
                    val boardingLabels = rides.map { range ->
                        val leg = item.chosen.route.legs[range.first]
                        val stop = leg.startName.ifBlank { if (range == rides.firstOrNull()) item.boardAt else "" }
                        val line = item.chips.getOrNull(range.first)?.lineName.orEmpty().ifBlank { routeBoardingLabel(item, range.first, taxi) }
                        listOf(stop, line).filter { it.isNotBlank() }.joinToString(" ")
                    } + listOfNotNull(rides.lastOrNull()?.let { item.chosen.route.legs[it.last].endName.ifBlank { item.alightAt } }?.takeIf { it.isNotBlank() })
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
