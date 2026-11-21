package com.sunseat.app.ui.candidates

import androidx.activity.compose.BackHandler
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.layout.findRootCoordinates
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.layout.navigationBarsPadding
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.cancelAndJoin
import androidx.lifecycle.withResumed
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.first
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.layout.layout
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
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.consumeWindowInsets
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
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import com.sunseat.app.R
import com.sunseat.app.ui.components.overlay
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
    // #144: 가로 화면은 높이가 낮아 아래 시트로는 펼친 헤더·범주·정렬 아래에 후보 카드가 들어가지 않고 지도도 시트에 덮인다.
    // 가로에서는 왼쪽에 펼친 헤더와 지도, 오른쪽에 화면 높이 전체의 목록(미리보기) 패널을 나란히 둔다(DESIGN 12.3).
    val configuration = androidx.compose.ui.platform.LocalConfiguration.current
    val landscape = configuration.screenWidthDp > configuration.screenHeightDp
    // #171: 시트 자리·미리보기·결과에서 돌아오기·시트 움직임·방향·시트 앵커 측정값은 상태 하나다(CandidatesUiState).
    // 화면은 이벤트만 보내고, 전환 함수가 돌려준 효과(시트 이동·스크롤·초점·카메라 맞춤)를 아래 효과 실행기가 실행한다.
    // 회전·재생성·결과 화면 왕복에 남긴다. 상태 객체는 화면이 사는 동안 하나라서 오래 도는 효과도 늘 같은 상태에 보낸다.
    var ui by rememberSaveable(stateSaver = CandidatesUiStateSaver) {
        mutableStateOf(CandidatesUiState(orientation = if (landscape) ScreenOrientation.Landscape else ScreenOrientation.Portrait))
    }
    // 전환 하나가 낸 효과 묶음.
    val effects = remember { kotlinx.coroutines.channels.Channel<List<CandidatesEffect>>(kotlinx.coroutines.channels.Channel.UNLIMITED) }
    fun dispatch(event: CandidatesEvent) {
        val transition = reduceCandidates(ui, event)
        ui = transition.state
        if (transition.effects.isNotEmpty()) effects.trySend(transition.effects)
    }
    fun measuredJourneyBar(height: Dp) { if (height.value != (ui.pendingJourneyBar ?: ui.journeyBar)) dispatch(CandidatesEvent.JourneyBarMeasured(height.value)) }
    // 같은 화면에서 여정(출발·도착·수단)이 바뀌면 미리보기·결과 왕복을 버린다.
    val trip = Triple(request.origin, request.destination, request.mode)
    var lastTrip by remember { mutableStateOf(trip) }
    if (lastTrip != trip) SideEffect { lastTrip = trip; dispatch(CandidatesEvent.TripChanged) }
    val previewCandidate = ui.preview?.let { entry -> options?.visible?.firstOrNull { it.id == entry.id } }
    val preview = previewCandidate != null
    // 열린 미리보기의 후보가 새 목록에 없으면 닫은 것으로 본다(그대로 두면 행 탭이 막힌다).
    if (options != null && ui.preview != null && previewCandidate == null) SideEffect { if (ui.preview != null) dispatch(CandidatesEvent.PreviewLost) }
    val listScroll = rememberLazyListState()
    var fitTrigger by remember { mutableLongStateOf(0L) }
    var fitRevision by remember { mutableLongStateOf(0L) }
    var fittedTrigger by remember { mutableLongStateOf(-1L) }
    // #168: 맞춤 요청 이유. 사용자가 지도를 옮긴 뒤 맞출지는 카메라 정책이 이유와 모드로 정한다. 기다리는 동안 여러 요청이 겹치면 사용자 이동을 넘는 이유를 남긴다.
    var fitReason by remember { mutableStateOf(com.sunseat.app.ui.map.FitReason.INITIAL) }
    var pendingReason by remember { mutableStateOf<com.sunseat.app.ui.map.FitReason?>(null) }
    fun requestFit(reason: com.sunseat.app.ui.map.FitReason) {
        val pending = pendingReason
        pendingReason = if (pending != null && pending.overridesUserMove && !reason.overridesUserMove) pending else reason
        fitTrigger++
    }
    // 정책이 알린 카메라 모드와 실제로 맞춘 횟수(#141: 사용자가 옮긴 뒤 목록 지도 띠에 "지도 맞춤").
    var camera by remember { mutableStateOf(com.sunseat.app.ui.map.CameraStatus(com.sunseat.app.ui.map.CameraMode.Unset, 0L)) }
    // 카메라 맞춤 트리거: 목록 개요의 후보 집합·선택 변경, 미리보기 진입(#102), 미리보기 닫기(#135), 명시적 "지도 맞춤".
    // 미리보기와 목록은 시트 높이(보이는 지도 영역)가 달라, 한쪽에서 맞춘 카메라는 다른 쪽에서 경로를 시트 뒤에 둔다.
    // 사용자가 지도를 직접 움직였으면(카메라 모드 UserMoved) 미리보기 닫기만으로는 다시 맞추지 않는다(#71, DESIGN 12.3, 카메라 정책).
    val routes = options?.visible?.sortedBy { it.id }.orEmpty().map { it.chosen.route }
    val mapRoutes = candidateMapRoutes(options?.visible.orEmpty(), options?.selectedId, preview)
    val sheetState = scaffoldState.bottomSheetState
    fun sheetTarget() = if (sheetState.targetValue == SheetValue.Expanded) SheetAnchor.Expanded else SheetAnchor.Default
    BackHandler(enabled = preview) { dispatch(CandidatesEvent.PreviewClose(CloseSource.Back)) }
    LaunchedEffect(routes, options?.selectedId) { dispatch(CandidatesEvent.CandidatesChanged) }
    LaunchedEffect(landscape) { dispatch(CandidatesEvent.Rotate(if (landscape) ScreenOrientation.Landscape else ScreenOrientation.Portrait)) }
    // 미리보기에서 자리 결과로 갔다가 Back으로 돌아오면(새 구성) 들어가기 전 시트 높이·선택 카드·초점으로 돌아간다(#145 #158).
    // 수명 주기가 RESUMED일 때 처리한다. 내비게이션이 항목별 수명 주기를 주면 예측 뒤로 가기 도중(취소될 수 있음)에는 처리하지 않는다(지금은 활동 수명 주기라 화면이 앞에 있을 때와 같다).
    val lifecycle = androidx.lifecycle.compose.LocalLifecycleOwner.current.lifecycle
    LaunchedEffect(lifecycle) { lifecycle.withResumed { }; if (ui.away != null) dispatch(CandidatesEvent.ReturnFromResult) }
    // 목록 시트가 향하는 자리(끌기 중 가장 가까운 앵커). 미리보기·복원 중의 값은 전환 함수가 거른다.
    LaunchedEffect(sheetState, landscape) { if (!landscape) snapshotFlow { sheetTarget() }.collect { dispatch(CandidatesEvent.SheetTarget(it)) } }
    // #162: 시트가 끌리거나 움직이는 동안은 시트 앵커 측정값을 바꾸지 않는다. 움직임을 구성 시점에 읽어 같은 프레임의 측정보다 먼저 알린다.
    val sheetMoving = !landscape && sheetState.isAnimationRunning
    if (sheetMoving != (ui.gesture == SheetGesture.Moving)) SideEffect { dispatch(CandidatesEvent.SheetMotion(sheetMoving)) }
    val density = LocalDensity.current
    val currentDensity by rememberUpdatedState(density)
    val currentLandscape by rememberUpdatedState(landscape)
    // #145: 목록이 화면에서 실제로 보이는 아래끝(목록 좌표). 기본 높이 시트에서는 목록 아랫부분이 화면 밖에 있다. 목록이 없으면 음의 무한대.
    var listVisibleEnd by remember { mutableFloatStateOf(Float.MAX_VALUE) }
    // 시트·패널에 지금 배치된 내용이 미리보기인가. 시트 애니메이션은 내용이 바뀐 뒤의 앵커로 움직인다.
    var panelShowsPreview by remember { mutableStateOf<Boolean?>(null) }
    // #158: 카드마다 초점 요청자. 화면 읽기가 켜져 있으면 터치 모드에서도 카드가 초점을 받을 수 있게 한다.
    val cardFocus = remember { HashMap<Int, androidx.compose.ui.focus.FocusRequester>() }
    val screenReader = rememberTouchExplorationEnabled()
    val currentScreenReader by rememberUpdatedState(screenReader)
    val currentOptions by rememberUpdatedState(options)
    val currentRequest by rememberUpdatedState(request)
    // 효과 실행기. 시트를 움직이는 전환(미리보기 열기·닫기, 결과에서 돌아오기, 회전 등)의 효과는 차례로 실행하고, 새 전환이 오면
    // 앞 전환의 남은 효과(복원·카드 보이기·초점·닫기 맞춤)를 버린다. 맞춤·맨 위로 같은 즉시 효과는 기다리지 않고 바로 실행한다.
    // 시트 애니메이션이 사용자 끌기로 끊겨도 같은 전환의 다음 효과는 실행한다.
    LaunchedEffect(effects) {
        suspend fun guarded(block: suspend () -> Unit) {
            try { block() } catch (e: kotlinx.coroutines.CancellationException) { if (!kotlinx.coroutines.currentCoroutineContext().isActive) throw e }
        }
        suspend fun run(effect: CandidatesEffect) {
            when (effect) {
                is CandidatesEffect.AnimateSheet -> try {
                    guarded {
                        // 시트 내용(목록과 미리보기)이 바뀌어 배치된 뒤의 앵커로 움직인다.
                        var frames = 0
                        do { androidx.compose.runtime.withFrameNanos { }; frames++ } while (frames < REVEAL_WAIT_FRAMES && panelShowsPreview != (ui.preview != null))
                        if (!currentLandscape) { if (effect.anchor == SheetAnchor.Expanded) sheetState.expand() else sheetState.partialExpand() }
                    }
                } finally { dispatch(CandidatesEvent.SheetAnimated(effect.anchor, effect.transition)) }
                CandidatesEffect.ScrollToTop -> guarded { listScroll.scrollToItem(0) }
                is CandidatesEffect.Reveal -> guarded {
                    // 목록이 다시 구성되고 시트가 멈춘 뒤 판단한다. 미리보기 요약 높이에서 목록 높이로 시트 앵커가 바뀌는 데 몇 프레임 걸리므로
                    // 목록 윗부분이 화면에 들어올 때(목록 시트는 접혀도 최소 높이가 보인다)까지 기다린다.
                    val minVisible = with(currentDensity) { Spacing.touch.toPx() }
                    var frames = 0
                    do { androidx.compose.runtime.withFrameNanos { }; frames++ } while (frames < REVEAL_WAIT_FRAMES &&
                        (listScroll.layoutInfo.totalItemsCount == 0 || sheetState.isAnimationRunning || listVisibleEnd < minVisible))
                    val position = currentOptions?.visible.orEmpty().indexOfFirst { it.id == effect.id }
                    if (position >= 0 && listVisibleEnd.isFinite()) {
                        val index = position + optionListLeadingItems(currentRequest)
                        val item = listScroll.layoutInfo.visibleItemsInfo.firstOrNull { it.key == effect.id }
                        // 선택 카드의 윗부분(시간 줄)이 터치 높이만큼 보이면 목록 위치를 그대로 둔다(#99 스크롤 보존).
                        if (item == null || item.offset < 0 || item.offset + minVisible > listVisibleEnd) listScroll.scrollToItem(index)
                    }
                }
                // 화면 읽기가 켜져 있을 때만 옮긴다. 초점을 받은 카드는 목록이 카드 전체를 보이게 스크롤하므로, 터치로 쓸 때는 보던 위치(#99)를 지킨다.
                is CandidatesEffect.FocusCard -> if (currentScreenReader) guarded {
                    // 카드가 구성되어 초점 요청자가 붙을 때까지 몇 프레임 기다린다(스크롤 직후).
                    for (attempt in 0 until REVEAL_WAIT_FRAMES) {
                        androidx.compose.runtime.withFrameNanos { }
                        val requester = cardFocus[effect.id] ?: continue
                        if (runCatching { requester.requestFocus(androidx.compose.ui.focus.FocusDirection.Enter) }.getOrDefault(false)) break
                    }
                }
                is CandidatesEffect.Camera -> requestFit(effect.reason)
                CandidatesEffect.CancelPending -> Unit
            }
        }
        var running: kotlinx.coroutines.Job? = null
        for (batch in effects) {
            if (batch.any { it is CandidatesEffect.AnimateSheet || it is CandidatesEffect.Reveal || it is CandidatesEffect.FocusCard || it is CandidatesEffect.CancelPending }) {
                running?.cancelAndJoin()
                running = launch { batch.forEach { run(it) } }
            } else launch { batch.forEach { run(it) } }
        }
    }
    var headerHeight by remember { mutableStateOf(0.dp) }
    var mapTop by remember { mutableStateOf(0.dp) }
    var mapHeight by remember { mutableStateOf(0.dp) }
    var mapWidth by remember { mutableStateOf(0.dp) }
    var scaffoldTop by remember { mutableStateOf(0.dp) }
    // #101: 목록 상단은 펼친 헤더 전체 또는 축약 여정 바 전체 중 하나만 보인다(DESIGN 5.1, 12.3).
    var fullHeaderHeight by remember { mutableStateOf(0.dp) }
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
    // #162: 끄는 도중 축약 여정 바가 처음 나타나 높이를 재면 시트 최대 높이(앵커)가 드래그 중에 바뀌고, 그러면 시트가 드래그 상태에 멈춰
    // 이후 카드·탭·정렬·여정 바 누름을 모두 가져간다. 시트가 끌리거나 움직이는 동안에는 직전 값을 쓰고 멈춘 뒤에 새 높이를 반영한다.
    // #171: 이 규칙은 상태 모델의 일반 규칙이다(CandidatesUiState.journeyBar, 전환 함수가 움직이는 동안의 측정을 미룬다).
    val sheetJourneyBar = ui.journeyBar.dp
    val listSheetMax = (maxHeight - (if (sheetJourneyBar > 0.dp) sheetJourneyBar else journeyBarEstimate) - (if (dragHandleHeight > 0.dp) dragHandleHeight else Spacing.touch)).coerceAtLeast(Spacing.touch)
    // 시트가 펼친 헤더 아래끝보다 올라오면(완전 확장·중간 드래그) 펼친 헤더를 통째로 접고 축약 여정 바로 바꾼다.
    // 시트 앵커는 헤더 높이와 무관하므로 바뀌어도 시트가 움직이지 않는다.
    val fullHeaderPx = with(density) { listHeader.toPx() }
    val compactHeader by remember(preview, fullHeaderPx, landscape, ui.holdCompactHeader) {
        derivedStateOf {
            val top = runCatching { scaffoldState.bottomSheetState.requireOffset() }.getOrDefault(Float.NaN)
            !landscape && !preview && (ui.holdCompactHeader || top.isFinite() && top < fullHeaderPx - 0.5f)
        }
    }
    val offset = runCatching { with(density) { scaffoldState.bottomSheetState.requireOffset().toDp() } }.getOrDefault(maxHeight - peek)
    // 미리보기의 지도 맞춤 행동 아래로 경로를 맞춰 출발·도착 표식이 행동에 가리지 않게 한다. 세로 비율 지도와 가로 미리보기(#144: 지도가 화면 높이 대부분)에 준다.
    // 가로(#144)는 지도가 패널에 덮이지 않고, 아래 내비게이션 바(제스처 막대)만 피한다.
    val mapPadding = com.sunseat.app.ui.map.sheetMapPadding(mapTop, mapHeight, if (landscape) mapTop + mapHeight - navigationBottom else scaffoldTop + offset)
        .let { if (preview && (landscape || mapHeight >= mapWidth)) it.copy(top = fitActionBottom) else it }
    LaunchedEffect(fitTrigger, mapHeight, mapWidth, landscape) {
        if (fittedTrigger == fitTrigger) return@LaunchedEffect
        // #144: 가로는 시트가 없어 지도 영역 크기가 자리 잡으면(같은 대기 시간) 바로 맞춘다.
        if (landscape) snapshotFlow { mapHeight to mapWidth }
            .debounce(com.sunseat.app.ui.theme.Motion.CAMERA_SETTLE_MS)
            .first { (height, _) -> height > Sizes.mapInset * 2 }
        else snapshotFlow { runCatching { scaffoldState.bottomSheetState.requireOffset() }.getOrDefault(Float.NaN) }
            .debounce(com.sunseat.app.ui.theme.Motion.CAMERA_SETTLE_MS)
            .first { value -> value.isFinite() && !scaffoldState.bottomSheetState.isAnimationRunning &&
                mapHeight - com.sunseat.app.ui.map.sheetMapPadding(mapTop, mapHeight, scaffoldTop + with(density) { value.toDp() }).bottom > Sizes.mapInset * 2 }
        fitReason = pendingReason ?: com.sunseat.app.ui.map.FitReason.SELECTION
        pendingReason = null
        fitRevision++
        fittedTrigger = fitTrigger
    }
    val header: @Composable () -> Unit = {
            Surface(color = scheme.surface, modifier = Modifier.testTag("candidates_header").onSizeChanged { headerHeight = with(density) { it.height.toDp() } }) {
                // #102: 미리보기 상단은 축약 여정 바(뒤로 하나 + 2줄까지 여정). 편집은 목록에서 하므로 누름 동작은 없다(DESIGN 12.3).
                if (preview) JourneyBar(request, now, { dispatch(CandidatesEvent.PreviewClose(CloseSource.Button)) }, Modifier.windowInsetsPadding(cutoutSides), backLabel = stringResource(R.string.candidates_preview_close), onExpand = null)
                else if (compactHeader) JourneyBar(request, now, actions.onBack, Modifier.windowInsetsPadding(cutoutSides).onSizeChanged { measuredJourneyBar(with(density) { it.height.toDp() }) }) {
                    dispatch(CandidatesEvent.JourneyBarTap)
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
                // #162: 완전 확장 시트의 위끝(listSheetMax)은 축약 여정 바 높이로 정한다. 바를 처음 보이는 순간(손잡이로 끄는 도중)에 재면
                // 시트 높이·앵커가 드래그 중에 바뀌고, 그러면 시트가 드래그 상태에 멈춰 이후 카드·탭 누름을 모두 가져간다.
                // 그래서 펼친 헤더가 보이는 동안에도 같은 바를 그리지 않고(배치·접근성 제외) 높이만 미리 잰다.
                if (!preview && !compactHeader && !landscape) Box(Modifier.clearAndSetSemantics { }.layout { measurable, constraints ->
                    val bar = measurable.measure(constraints.copy(minWidth = 0, minHeight = 0))
                    measuredJourneyBar(with(density) { bar.height.toDp() })
                    layout(0, 0) { }
                }) { JourneyBar(request, now, {}, Modifier.windowInsetsPadding(cutoutSides), onExpand = {}) }
            }
    }
    // 시트(세로) 또는 오른쪽 패널(가로, #144)의 내용. 가로 패널은 화면 높이 전체를 쓴다.
    val panel: @Composable () -> Unit = {
            if (preview && previewCandidate != null) {
                val paneTitle = stringResource(R.string.candidates_preview_title)
                Column(Modifier.testTag("candidates_preview_sheet").onGloballyPositioned { panelShowsPreview = true }.semantics { this.paneTitle = paneTitle }.fillMaxWidth()
                    .then(if (landscape) Modifier.fillMaxHeight() else Modifier.heightIn(max = (viewportHeight - headerHeight - (if (dragHandleHeight > 0.dp) dragHandleHeight else Spacing.touch)).coerceAtLeast(Spacing.touch)))
                    .verticalScroll(rememberScrollState()).navigationBarsPadding()) {
                    RoutePreviewSheet(previewCandidate, request, onSeat = { dispatch(CandidatesEvent.SeatOpen); actions.onSelect(previewCandidate) }) { previewSummaryHeight = with(density) { it.toDp() } }
                }
            } else Column(Modifier.testTag("candidates_sheet").onGloballyPositioned { panelShowsPreview = false }.fillMaxWidth().then(if (landscape) Modifier.fillMaxHeight() else Modifier.heightIn(max = listSheetMax)).navigationBarsPadding(), verticalArrangement = Arrangement.spacedBy(Spacing.s)) {
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
                        dispatch(CandidatesEvent.CategoryChange(changed))
                    }
                    // #83: 정렬은 현재 기준이 보이는 드롭다운 하나. 기준을 바꾸면 목록을 맨 위(새 1순위)부터 보인다.
                    Row(Modifier.fillMaxWidth().padding(horizontal = Spacing.s), horizontalArrangement = Arrangement.End) {
                        SortDropdown(options.sort, request.goal) { sort ->
                            actions.onSort(sort)
                            dispatch(CandidatesEvent.SortChange(changed = true))
                        }
                    }
                }
                // 범주 탭·정렬 행이 있으면 그 아래 간격(spacedBy)만 두고 목록 위 여백을 겹쳐 쌓지 않는다(#83 디자인 리뷰).
                val content = if (options != null && request.mode == TravelMode.TRANSIT) PaddingValues(start = Spacing.screen, end = Spacing.screen, bottom = Spacing.screen) else PaddingValues(Spacing.screen)
                when (state) {
                    is CandidatesState.Loading -> Loading(content, state.mode, animateLoader)
                    is CandidatesState.Options -> if (state.visible.isEmpty()) Message(content, R.drawable.ic_ms_search, stringResource(R.string.candidates_filtered_empty), null, stringResource(R.string.candidates_clear_filters) to { actions.onCategory(RouteCategory.ALL); dispatch(CandidatesEvent.CategoryChange(changed = true)) }, stringResource(R.string.candidates_edit_input) to actions.onBack)
                        else OptionList(state.visible, state.selectedId, request, content, if (request.mode == TravelMode.TRANSIT) stringResource(if (state.sort == CandidateSort.WALK) R.string.candidates_walk_scope_sort else R.string.candidates_walk_scope) else null, actions.copy(onHighlight = {
                            // 시트가 끌리는 중이면 가장 가까운 앵커(targetValue)를 들어가기 전 높이로 본다(#145).
                            if (!landscape) dispatch(CandidatesEvent.SheetTarget(sheetTarget()))
                            dispatch(CandidatesEvent.CardTap(it.id))
                            actions.onHighlight(it)
                        }), listScroll, { listVisibleEnd = it }, { cardFocus.getOrPut(it) { androidx.compose.ui.focus.FocusRequester() } }, screenReader)
                    is CandidatesState.NoRoute -> Message(content, R.drawable.ic_ms_directions_bus, stringResource(state.titleRes), state.detailRes?.let { stringResource(it) }, if (state.suggestCar) stringResource(R.string.candidates_search_by_car) to actions.onSearchByCar else null, stringResource(R.string.candidates_edit_input) to actions.onBack)
                    is CandidatesState.Error -> Message(content, state.iconRes, stringResource(state.messageRes), null, if (state.canRetry) stringResource(R.string.search_retry) to actions.onRetry else null, stringResource(R.string.candidates_edit_input) to actions.onBack)
                }
            }
    }
    // The map fills the scaffold body below topBar (portrait) or the area below the header (landscape); measured sheet overlap supplies its bottom inset.
    val mapArea: @Composable () -> Unit = {
        Box(Modifier.fillMaxSize()) {
        com.sunseat.app.ui.map.SunSeatMap(com.sunseat.app.ui.map.MapState(
            routes = mapRoutes.routes,
            selectedRoute = mapRoutes.selectedRoute,
            origin = request.origin, destination = request.destination,
            padding = mapPadding, showDecorations = false,
            automaticFit = false, cameraRevision = fitRevision, fitReason = fitReason,
            fitEnabled = mapHeight - mapPadding.bottom > Sizes.mapInset * 2,
            onCameraStatus = { camera = it },
        ), Modifier.fillMaxSize().testTag("candidate_map").semantics {
            this[com.sunseat.app.ui.map.MapCameraRevisionKey] = camera.fits
            this[CandidateMapRouteIdsKey] = mapRoutes.ids
            this[CandidateMapSelectedRouteKey] = mapRoutes.selectedRoute
            this[com.sunseat.app.ui.map.MapVisibleHeightKey] = (mapHeight - mapPadding.bottom).value
        }.onGloballyPositioned {
            with(density) { mapTop = it.positionInRoot().y.toDp(); mapHeight = it.size.height.toDp(); mapWidth = it.size.width.toDp() }
        })
        // #102: 선택 경로 맞춤은 카메라만 옮기는 조용한 행동이다(DESIGN 13). 다른 후보를 다시 그리지 않는다(#99).
        // #141: 목록에서는 카메라 모드가 사용자 이동(UserMoved)일 때만(미리보기에서 움직이고 닫은 경우 포함) 보인다. 맞춰져 있는 동안에는 낮은 지도 띠를 가리지 않는다.
        // 캡슐(위 틈 + 터치 높이) 아래로 맞춤 여백만큼은 띠가 남을 때만 둔다. 더 낮으면 캡슐이 띠 대부분을 가린다.
        val listFit = !preview && camera.mode == com.sunseat.app.ui.map.CameraMode.UserMoved && mapRoutes.routes.isNotEmpty() && mapHeight - mapPadding.bottom >= Spacing.s + Spacing.touch + Sizes.mapInset
        if (preview || listFit) {
            // 목록에서 고른 후보가 없으면 모든 후보 개요에 맞춘다(#141).
            val fitLabel = stringResource(if (mapRoutes.selectedRoute < 0) R.string.candidates_fit_all_action else R.string.candidates_fit_route_action)
            FloatingCapsule(Modifier.align(Alignment.TopEnd).windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Horizontal)).padding(Spacing.s)
                .onGloballyPositioned { fitActionBottom = with(density) { (it.positionInRoot().y + it.size.height).toDp() } - mapTop }.overlay("map_fit_action")) {
                QuietButton(onClick = { dispatch(CandidatesEvent.FitAction) }, modifier = Modifier.semantics { onClick(label = fitLabel) { dispatch(CandidatesEvent.FitAction); true } }) {
                    Icon(painterResource(R.drawable.ic_ms_fit_screen), null, Modifier.size(Sizes.iconSmall))
                    Spacer(Modifier.width(Spacing.xs))
                    Text(stringResource(R.string.candidates_fit_route), style = MaterialTheme.typography.labelLarge)
                }
            }
        }
        }
    }
    if (landscape) Row(Modifier.fillMaxSize().background(scheme.background).onGloballyPositioned { scaffoldTop = with(density) { it.positionInRoot().y.toDp() } }) {
        // #144: 왼쪽은 펼친 헤더(또는 미리보기 축약 여정 바)와 그 아래 지도, 오른쪽은 화면 높이 전체의 목록·미리보기 패널.
        // 시트가 없으므로 완전 확장·축약 여정 바 전환(#101)은 없고, 헤더는 항상 통째로 보인다.
        Column(Modifier.weight(1f).fillMaxHeight().windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Start)).consumeWindowInsets(WindowInsets.safeDrawing.only(WindowInsetsSides.End))) {
            header()
            Box(Modifier.weight(1f).fillMaxWidth()) { mapArea() }
        }
        VerticalDivider(thickness = Sizes.hairline, color = scheme.outlineVariant)
        Surface(color = scheme.surface, modifier = Modifier.weight(1f).fillMaxHeight()) {
            Box(Modifier.windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Top + WindowInsetsSides.End)).consumeWindowInsets(WindowInsets.safeDrawing.only(WindowInsetsSides.Start)).padding(top = Spacing.s)) { panel() }
        }
    } else BottomSheetScaffold(
        modifier = Modifier.fillMaxSize().onGloballyPositioned { scaffoldTop = with(density) { it.positionInRoot().y.toDp() } }, scaffoldState = scaffoldState, containerColor = scheme.background,
        sheetContainerColor = scheme.surface, sheetPeekHeight = peek,
        sheetDragHandle = { Box(Modifier.testTag("candidates_sheet_handle").onSizeChanged { dragHandleHeight = with(density) { it.height.toDp() } }) { BottomSheetDefaults.DragHandle() } },
        topBar = header,
        sheetContent = { panel() },
    ) { _ -> mapArea() }
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
private fun OptionList(items: List<CandidateUi>, selectedId: Int?, request: TripRequest, content: PaddingValues, scopeNotice: String?, actions: CandidatesActions, listState: LazyListState, onVisibleEnd: (Float) -> Unit, cardFocus: (Int) -> androidx.compose.ui.focus.FocusRequester, screenReader: Boolean) {
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
    // #145: 목록이 화면에서 실제로 보이는 아래끝(목록 좌표)을 화면에 알린다. 선택 카드 보이기(Reveal 효과)가 쓴다.
    // 목록이 사라지면(미리보기) 이전 위치를 쓰지 않도록 모른다고 알린다.
    val currentOnVisibleEnd by rememberUpdatedState(onVisibleEnd)
    DisposableEffect(Unit) { onDispose { currentOnVisibleEnd(Float.NEGATIVE_INFINITY) } }
    val navigationBottomPx = with(LocalDensity.current) { WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding().toPx() }
    LazyColumn(Modifier.fillMaxSize().testTag("candidate_list").onGloballyPositioned {
        onVisibleEnd(it.findRootCoordinates().size.height - navigationBottomPx - it.positionInRoot().y)
    }, state = listState, contentPadding = PaddingValues(top = content.calculateTopPadding(), bottom = content.calculateBottomPadding())) {
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
                CandidateCard(item, item.id == selectedId, request, onClick = { haptics.selectionChanged(); actions.onHighlight(item) }, onView = { haptics.press(); actions.onSelect(item) },
                    // #158: 미리보기를 닫으면 이 카드로 초점을 돌린다. 화면 읽기가 켜져 있으면 터치 모드에서도 초점을 받는다(키보드 모드는 원래 받는다).
                    modifier = Modifier.focusRequester(cardFocus(item.id)).focusProperties { if (screenReader) canFocus = true })
                HorizontalDivider(Modifier.padding(start = Spacing.l), thickness = Sizes.hairline, color = SunSeatTheme.scheme.outlineVariant)
            }
        }
    }
}

@Composable
internal fun CandidateCard(item: CandidateUi, selected: Boolean, request: TripRequest, onClick: () -> Unit, onView: () -> Unit, modifier: Modifier = Modifier) {
    val scheme = SunSeatTheme.scheme
    val routeColors = SunSeatTheme.route
    val taxi = request.mode == TravelMode.TAXI
    val selectedDescription = stringResource(R.string.candidates_map_selected)
    Surface(color = if (selected) routeColors.selectionContainer else scheme.surface,
        modifier = modifier.fillMaxWidth().testTag("candidate_${item.id}").semantics { this.selected = selected; if (selected) stateDescription = selectedDescription }
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
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(content), // #144: 가로 패널은 낮아 안내·버튼이 넘칠 수 있다.
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

/** [CandidatesEffect.Reveal]·[CandidatesEffect.FocusCard]가 목록·카드 구성을 기다리는 최대 프레임 수. */
private const val REVEAL_WAIT_FRAMES = 10

/** 목록에서 후보 카드 앞에 오는 항목 수: 대중교통 도보 범위 안내(#73), 시각 기준 안내(#128). [OptionList]의 항목 순서와 같다. */
private fun optionListLeadingItems(request: TripRequest): Int =
    (if (request.mode == TravelMode.TRANSIT) 1 else 0) + (if (request.departure != null) 1 else 0)

/** 화면 읽기(터치 탐색)가 켜져 있는가. 켜고 끄면 다시 구성한다. */
@Composable
private fun rememberTouchExplorationEnabled(): Boolean {
    val manager = LocalContext.current.getSystemService(android.view.accessibility.AccessibilityManager::class.java) ?: return false
    var enabled by remember(manager) { mutableStateOf(manager.isTouchExplorationEnabled) }
    DisposableEffect(manager) {
        val listener = android.view.accessibility.AccessibilityManager.TouchExplorationStateChangeListener { enabled = it }
        manager.addTouchExplorationStateChangeListener(listener)
        onDispose { manager.removeTouchExplorationStateChangeListener(listener) }
    }
    return enabled
}
