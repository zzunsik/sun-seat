package com.sunseat.app.ui.result

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.slideInVertically
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
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
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.LineBreak
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import com.sunseat.app.ui.text.word
import com.sunseat.app.ui.text.label
import com.sunseat.app.R
import com.sunseat.app.domain.LegAdvice
import com.sunseat.app.domain.LegResult
import com.sunseat.app.domain.Mode
import com.sunseat.app.domain.Side
import com.sunseat.app.domain.SunGoal
import com.sunseat.app.domain.SunPlacement
import com.sunseat.app.domain.TravelMode
import com.sunseat.app.domain.TripAdvice
import com.sunseat.app.domain.TripRequest
import com.sunseat.app.domain.TripResult
import com.sunseat.app.ui.components.ChoiceGroup
import com.sunseat.app.ui.components.ChoiceOption
import com.sunseat.app.ui.components.DiagramSun
import com.sunseat.app.ui.components.Exposure
import com.sunseat.app.ui.components.ExposurePill
import com.sunseat.app.ui.components.FloatingCapsule
import com.sunseat.app.ui.components.PrimaryButton
import com.sunseat.app.ui.components.QuietButton
import com.sunseat.app.ui.components.SeatSunDiagram
import com.sunseat.app.ui.components.SectionCard
import com.sunseat.app.ui.components.SideTimeline
import com.sunseat.app.ui.components.SunLoader
import com.sunseat.app.ui.components.SunSpan
import com.sunseat.app.ui.components.Tick
import com.sunseat.app.ui.formatDuration
import com.sunseat.app.ui.theme.Motion
import com.sunseat.app.ui.theme.Sizes
import com.sunseat.app.ui.theme.Spacing
import com.sunseat.app.ui.theme.SunSeatTheme
import kotlinx.coroutines.launch
import java.time.Duration
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit

data class ResultActions(
    val onSimulation: () -> Unit = {},
    val onSchedule: (java.time.LocalDateTime?) -> Unit = {},
    val onSettings: () -> Unit = {},
    val onBack: () -> Unit = {},
    val onRestart: () -> Unit = {},
    val onShare: () -> Unit = {},
    val setGoal: (SunGoal) -> Unit = {},
)

internal val TimeFormat: DateTimeFormatter = DateTimeFormatter.ofPattern("HH:mm")

/** 눈금 생략 기준: 양 끝 시각과 이만큼 가까운 정각 눈금은 겹치므로 그리지 않는다 */
private const val TICK_EDGE_MARGIN = 0.18f


/** 결론 문장. 대중교통은 "진행 방향 왼쪽에 앉으세요"(버스 창가·지하철 벤치 모두), 자가용은 좌석 이름. */
internal fun headlineRes(context: android.content.Context, request: TripRequest, result: TripResult): Pair<Int, String?> = when (val a = result.advice) {
    is TripAdvice.Sit ->
        if (request.mode != TravelMode.TRANSIT) R.string.result_sit_car to a.carSeats.label(context)
        else R.string.result_sit_side to a.side.word(context)
    TripAdvice.Mixed -> R.string.result_mixed to null
    TripAdvice.NoDifference -> R.string.result_no_difference to null
    TripAdvice.NoSun -> (if (result.onlyExcludedRides()) R.string.result_no_outdoor else R.string.result_no_sun) to null
}

private fun TripResult.onlyExcludedRides(): Boolean = legs.isNotEmpty() &&
    legs.all { it.advice in setOf(LegAdvice.Walk, LegAdvice.Underground, LegAdvice.UnknownGround) }

/** 결과 화면: 결론 → (눈부심) → 해 위치 → 앉은 쪽 기준 타임라인 → 구간별. 상태 없음. */
@Composable
fun ResultScreen(
    request: TripRequest,
    state: ResultUiState,
    actions: ResultActions,
    modifier: Modifier = Modifier,
    now: java.time.LocalDateTime = java.time.LocalDateTime.now(),
    animateIn: Boolean = true,
    zone: ZoneId = ZoneId.systemDefault(),
) {
    val scheme = SunSeatTheme.scheme
    val insets = WindowInsets.safeDrawing.asPaddingValues()
    var showSchedule by rememberSaveable { mutableStateOf(false) }
    val result = state.result
    if (showSchedule) com.sunseat.app.ui.input.TimeSheet(request.departure, request.mode, { showSchedule = false }, actions.onSchedule, now = now)
    Surface(modifier.fillMaxSize(), color = scheme.background, contentColor = scheme.onSurface) {
        Box(Modifier.fillMaxSize()) {
            if (result == null) {
                Loading(insets.calculateTopPadding(), animateIn)
            } else {
                ResultContent(request, result, actions, animateIn, zone, insets.calculateTopPadding(), insets.calculateBottomPadding())
            }

            FloatingCapsule(
                Modifier
                    .align(Alignment.TopCenter)
                    .windowInsetsPadding(WindowInsets.safeDrawing)
                    .padding(horizontal = Spacing.m, vertical = Spacing.s)
                    .fillMaxWidth(),
            ) {
                Row(Modifier.padding(Spacing.xs), verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = actions.onBack) {
                        Icon(painterResource(R.drawable.ic_ms_arrow_back), contentDescription = stringResource(R.string.common_back))
                    }
                    // #103: 축약 여정 바와 같은 글(2줄까지 줄바꿈 + 조건 줄, DESIGN 5.1). 시각은 오른쪽 아이콘으로 고친다.
                    val mode = com.sunseat.app.ui.input.travelModeLabel(request.mode)
                    com.sunseat.app.ui.input.JourneySummary(
                        request.origin.name, request.destination.name,
                        if (result != null) stringResource(R.string.result_schedule, mode,
                            state.departure.atZone(zone).format(TimeFormat), result.arrival.atZone(zone).format(TimeFormat))
                        else stringResource(
                            if (request.mode != TravelMode.TRANSIT) R.string.result_subtitle_car else R.string.result_subtitle_transit,
                            state.departure.atZone(zone).format(TimeFormat),
                        ),
                        Modifier.weight(1f).testTag("result_journey_bar"),
                    )
                    IconButton(onClick = { showSchedule = true }) { Icon(painterResource(R.drawable.ic_ms_schedule), stringResource(R.string.input_time_title)) }
                }
            }

            if (result != null) {
                FloatingCapsule(
                    Modifier
                        .align(Alignment.BottomCenter)
                        .windowInsetsPadding(WindowInsets.safeDrawing)
                        .padding(horizontal = Spacing.m, vertical = Spacing.m)
                        .fillMaxWidth(),
                ) {
                    Row(Modifier.padding(Spacing.s), horizontalArrangement = Arrangement.spacedBy(Spacing.s), verticalAlignment = Alignment.CenterVertically) {
                        QuietButton(onClick = actions.onRestart, modifier = Modifier.weight(1f)) {
                            Icon(painterResource(R.drawable.ic_ms_refresh), contentDescription = null, modifier = Modifier.size(Sizes.iconMedium))
                            Spacer(Modifier.size(Spacing.s))
                            Text(stringResource(R.string.result_restart), style = MaterialTheme.typography.labelLarge)
                        }
                        PrimaryButton(onClick = actions.onShare, modifier = Modifier.weight(1f)) {
                            Icon(painterResource(R.drawable.ic_ms_share), contentDescription = null, modifier = Modifier.size(Sizes.iconMedium))
                            Spacer(Modifier.size(Spacing.s))
                            Text(stringResource(R.string.result_share), style = MaterialTheme.typography.labelLarge)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun Loading(topInset: Dp, animate: Boolean) {
    Column(
        Modifier.fillMaxWidth().padding(horizontal = Spacing.screen).padding(top = topInset + Sizes.topCapsuleClearance),
        verticalArrangement = Arrangement.spacedBy(Spacing.m),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Spacing.s)) {
            SunLoader(description = stringResource(R.string.result_loading), playing = animate)
            Text(stringResource(R.string.result_loading), style = MaterialTheme.typography.titleMedium)
        }
    }
}

@Composable
private fun ResultContent(
    request: TripRequest,
    result: TripResult,
    actions: ResultActions,
    animateIn: Boolean,
    zone: ZoneId,
    topInset: Dp,
    bottomInset: Dp,
) {
    val scheme = SunSeatTheme.scheme
    val reduce = SunSeatTheme.reduceMotion || !animateIn
    var shown by remember { mutableStateOf(reduce) }
    val shadeMin = remember { Animatable(if (reduce) result.shadeIfFollowed.toMinutes().toFloat() else 0f) }
    val sunMin = remember { Animatable(if (reduce) result.sunIfFollowed.toMinutes().toFloat() else 0f) }
    LaunchedEffect(result.shadeIfFollowed, result.sunIfFollowed) {
        shown = true
        val shadeTarget = result.shadeIfFollowed.toMinutes().toFloat()
        val sunTarget = result.sunIfFollowed.toMinutes().toFloat()
        if (reduce) {
            shadeMin.snapTo(shadeTarget)
            sunMin.snapTo(sunTarget)
        } else {
            launch { shadeMin.animateTo(shadeTarget, tween(Motion.COUNT_UP_MS)) }
            launch { sunMin.animateTo(sunTarget, tween(Motion.COUNT_UP_MS)) }
        }
    }
    val sit = result.advice as? TripAdvice.Sit
    val noSun = result.advice == TripAdvice.NoSun
    var index = 0

    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = Spacing.screen)
            .padding(top = topInset + Sizes.topCapsuleClearance, bottom = bottomInset + Sizes.bottomCapsuleClearance),
        verticalArrangement = Arrangement.spacedBy(Spacing.m),
    ) {
        Stagger(shown, index++) {
            SectionCard {
                val hasAdvice = result.advice is TripAdvice.Sit || result.advice == TripAdvice.Mixed
                if (hasAdvice) {
                    Text(
                        stringResource(if (result.goal == SunGoal.AVOID) R.string.result_label_avoid else R.string.result_label_seek),
                        style = MaterialTheme.typography.labelMedium,
                        color = scheme.onSurfaceVariant,
                    )
                }
                val (res, arg) = headlineRes(androidx.compose.ui.platform.LocalContext.current, request, result)
                Text(
                    if (arg != null) stringResource(res, arg) else stringResource(res),
                    style = MaterialTheme.typography.headlineMedium.copy(lineBreak = LineBreak.Heading),
                    color = scheme.onSurface,
                    modifier = Modifier.semantics { heading() },
                )
                when {
                    noSun -> {
                        Text(stringResource(if (result.onlyExcludedRides()) R.string.result_no_outdoor_detail else R.string.result_no_sun_detail), style = MaterialTheme.typography.bodyMedium, color = scheme.onSurfaceVariant)
                        PrimaryButton(onClick = actions.onRestart, modifier = Modifier.fillMaxWidth()) {
                            Text(stringResource(if (result.onlyExcludedRides()) R.string.result_find_route else R.string.result_change_time), style = MaterialTheme.typography.labelLarge)
                        }
                    }
                    else -> {
                        if (result.advice == TripAdvice.Mixed) {
                            Text(mixedSummary(result), style = MaterialTheme.typography.bodyLarge, color = scheme.onSurface)
                        }
                        if (hasAdvice) {
                            Text(stringResource(R.string.result_if_followed), style = MaterialTheme.typography.labelMedium, color = scheme.onSurfaceVariant)
                            Row(horizontalArrangement = Arrangement.spacedBy(Spacing.s)) {
                                ExposurePill(Exposure.Shade, stringResource(R.string.result_shade, formatDuration(Duration.ofMinutes(shadeMin.value.toLong()), zeroOk = true)))
                                ExposurePill(Exposure.Sun, stringResource(R.string.result_sun, formatDuration(Duration.ofMinutes(sunMin.value.toLong()), zeroOk = true)))
                            }
                        }
                        if (!result.aheadSun.isZero) {
                            ExposurePill(Exposure.Sun, stringResource(R.string.result_ahead_sun, formatDuration(result.aheadSun)))
                        }
                        ChoiceGroup(
                            label = stringResource(R.string.common_sun),
                            options = listOf(ChoiceOption(stringResource(R.string.input_goal_avoid)), ChoiceOption(stringResource(R.string.input_goal_seek))),
                            selected = result.goal.ordinal,
                            onSelect = { actions.setGoal(SunGoal.entries[it]) },
                        )
                    }
                }
                QuietButton(onClick = actions.onSimulation) { Text(stringResource(R.string.simulation_title)) }
            }
        }
        if (result.glare.isNotEmpty()) {
            Stagger(shown, index++) {
                SectionCard(title = stringResource(R.string.result_glare_title)) {
                    Text(stringResource(R.string.result_glare_detail), style = MaterialTheme.typography.bodyMedium, color = scheme.onSurfaceVariant)
                    result.glare.forEach { g ->
                        Text(
                            glareText(g, result, zone),
                            style = MaterialTheme.typography.titleSmall,
                            color = scheme.onSurface,
                        )
                    }
                }
            }
        }
        Stagger(shown, index++) {
            SectionCard(title = stringResource(R.string.map_title)) {
                com.sunseat.app.ui.map.SunSeatMap(
                    com.sunseat.app.ui.map.MapState(
                        routes = listOf(com.sunseat.app.domain.Route(result.legs.map { it.leg })),
                        origin = request.origin,
                        destination = request.destination,
                    ),
                    Modifier.fillMaxWidth().height(Sizes.mapHeight),
                )
            }
        }
        result.sunPlacement?.let { placement ->
            Stagger(shown, index++) {
                val title = when (placement) {
                    SunPlacement.AHEAD -> stringResource(R.string.result_sun_ahead)
                    SunPlacement.LEFT -> stringResource(R.string.result_sun_side, Side.LEFT.word())
                    SunPlacement.RIGHT -> stringResource(R.string.result_sun_side, Side.RIGHT.word())
                }
                SectionCard(title = title) {
                    if (placement == SunPlacement.AHEAD) {
                        Text(stringResource(R.string.result_sun_ahead_detail), style = MaterialTheme.typography.bodyMedium, color = scheme.onSurfaceVariant)
                    }
                    SeatSunDiagram(
                        sun = when (placement) {
                            SunPlacement.AHEAD -> DiagramSun.AHEAD
                            SunPlacement.LEFT -> DiagramSun.LEFT
                            SunPlacement.RIGHT -> DiagramSun.RIGHT
                        },
                        recommended = sit?.side,
                        car = request.mode != TravelMode.TRANSIT,
                        bus = result.legs.firstOrNull { it.advice is LegAdvice.Sit }?.leg?.mode == Mode.BUS,
                        spot = (result.legs.firstOrNull { it.advice is LegAdvice.Sit }?.advice as? LegAdvice.Sit)?.spot,
                        seatSun = (!result.leftSeatSun.isZero) to (!result.rightSeatSun.isZero),
                    )
                }
            }
            Stagger(shown, index++) {
                SectionCard(title = stringResource(R.string.result_timeline)) {
                    if (result.arrival != result.modeledArrival) Text(stringResource(R.string.result_unmodeled_time), style = MaterialTheme.typography.bodySmall, color = scheme.onSurfaceVariant)
                    Text(stringResource(R.string.result_timeline_detail), style = MaterialTheme.typography.bodySmall, color = scheme.onSurfaceVariant)
                    SideTimeline(
                        leftSun = result.leftSpans.map { SunSpan(it.start, it.end) },
                        rightSun = result.rightSpans.map { SunSpan(it.start, it.end) },
                        ticks = ticks(result.departure, result.modeledArrival, zone),
                        selectedRight = if (sit?.side == Side.RIGHT) 1f else 0f,
                        showSelection = sit != null,
                        gaps = result.walkSpans.map { SunSpan(it.start, it.end) },
                        animateIn = !reduce,
                    )
                }
            }
        }
        Stagger(shown, index) {
            SectionCard(title = stringResource(R.string.result_legs)) {
                result.legs.forEachIndexed { i, leg ->
                    if (i > 0) HorizontalDivider(color = scheme.outlineVariant)
                    LegRow(leg, request.mode, result.departure, zone, actions.onSettings)
                }
            }
        }
    }
}

@Composable
private fun LegRow(leg: LegResult, mode: TravelMode, departure: Instant, zone: ZoneId, onSettings: () -> Unit) {
    val scheme = SunSeatTheme.scheme
    val icon = when (leg.leg.mode) {
        Mode.SUBWAY -> R.drawable.ic_ms_directions_subway
        Mode.BUS -> R.drawable.ic_ms_directions_bus
        Mode.CAR -> R.drawable.ic_ms_directions_car
        Mode.WALK -> R.drawable.ic_ms_directions_walk
    }
    Column(Modifier.fillMaxWidth().heightIn(min = Sizes.listRow), verticalArrangement = Arrangement.spacedBy(Spacing.s)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Spacing.s)) {
            Icon(painterResource(icon), contentDescription = null, tint = scheme.onSurfaceVariant, modifier = Modifier.size(Sizes.iconMedium))
            val name = if (leg.leg.startName.isBlank()) stringResource(R.string.result_car_leg) else "${leg.leg.startName} → ${leg.leg.endName}"
            Text(name, style = MaterialTheme.typography.titleSmall, color = scheme.onSurface, modifier = Modifier.weight(1f), maxLines = 2, overflow = TextOverflow.Ellipsis)
            Text(formatDuration(leg.leg.duration), style = MaterialTheme.typography.bodySmall, color = scheme.onSurfaceVariant)
        }
        when (val a = leg.advice) {
            LegAdvice.Underground -> Text(stringResource(R.string.result_leg_underground), style = MaterialTheme.typography.bodySmall, color = scheme.onSurfaceVariant)
            LegAdvice.UnknownGround -> Text(stringResource(R.string.result_leg_unknown_ground), style = MaterialTheme.typography.bodySmall, color = scheme.onSurfaceVariant)
            LegAdvice.Walk -> Text(stringResource(R.string.result_leg_walk), style = MaterialTheme.typography.bodySmall, color = scheme.onSurfaceVariant)
            LegAdvice.NoSun -> Text(stringResource(R.string.result_leg_no_sun), style = MaterialTheme.typography.bodySmall, color = scheme.onSurfaceVariant)
            LegAdvice.NoDifference -> {
                Text(stringResource(R.string.result_leg_no_difference), style = MaterialTheme.typography.bodyMedium, color = scheme.onSurface)
                Text(
                    stringResource(
                        if (leg.leg.mode == Mode.SUBWAY) R.string.result_leg_both else R.string.result_leg_side_window,
                        formatDuration(leg.leftSun, zeroOk = true),
                        formatDuration(leg.rightSun, zeroOk = true),
                    ),
                    style = MaterialTheme.typography.bodySmall,
                    color = scheme.onSurfaceVariant,
                )
                if (!leg.aheadSun.isZero) ExposurePill(Exposure.Sun, stringResource(R.string.result_ahead_sun, formatDuration(leg.aheadSun)))
            }
            is LegAdvice.Sit -> {
                val subway = leg.leg.mode == Mode.SUBWAY
                val advice = when {
                    mode != TravelMode.TRANSIT -> stringResource(R.string.result_leg_sit_car, a.carSeats.label())
                    subway -> stringResource(R.string.result_leg_sit_bench, a.side.word())
                    else -> stringResource(R.string.result_leg_sit_window, a.side.word())
                }
                Text(advice, style = MaterialTheme.typography.bodyMedium, color = scheme.onSurface, fontWeight = FontWeight.SemiBold)
                SeatDetails(leg, a, mode, departure, zone)
                val got = leg.followedSun ?: leg.sun(a.side)
                if (got.isZero) ExposurePill(Exposure.Shade, stringResource(R.string.result_leg_shade))
                else ExposurePill(Exposure.Sun, stringResource(R.string.result_leg_sun, formatDuration(got)))
                if (subway) {
                    QuietButton(onClick = onSettings) { Text(stringResource(R.string.settings_bench_change)) }
                    listOf(Side.LEFT, Side.RIGHT).forEach { side ->
                        val b = leg.bench(side) ?: return@forEach
                        Text(
                            stringResource(
                                if (side == a.side) R.string.result_leg_bench_recommended else R.string.result_leg_bench,
                                side.word(),
                                formatDuration(b.back, zeroOk = true),
                                formatDuration(b.front, zeroOk = true),
                            ),
                            style = MaterialTheme.typography.bodySmall,
                            color = scheme.onSurfaceVariant,
                        )
                    }
                    if ((leg.bench(a.side)?.front ?: Duration.ZERO) > Duration.ZERO) {
                        Text(stringResource(R.string.result_subway_front_note), style = MaterialTheme.typography.bodySmall, color = scheme.onSurfaceVariant)
                    }
                } else {
                    val other = leg.sun(a.side.opposite)
                    Text(
                        stringResource(R.string.result_leg_other, if (other.isZero) stringResource(R.string.common_shade) else stringResource(R.string.result_sun, formatDuration(other))),
                        style = MaterialTheme.typography.bodySmall,
                        color = scheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}

/** 구간마다 다를 때 한 줄 요약: "시청까지 왼쪽, 시청부터 오른쪽" */
@Composable
internal fun mixedSummary(result: TripResult): String {
    val sits = result.legs.mapNotNull { l -> (l.advice as? LegAdvice.Sit)?.let { l to it } }
    val parts = sits.mapIndexed { i, (l, a) ->
        if (a.switches.isNotEmpty()) {
            (listOf(stringResource(R.string.seat_initial, a.side.word())) + a.switches.map {
                stringResource(R.string.seat_switch, it.stopName ?: stringResource(R.string.seat_after_minutes, Duration.between(result.departure, it.at).toMinutes()), it.to.label())
            }).joinToString(", ")
        } else if (i == 0) stringResource(R.string.result_mixed_part, l.leg.endName, a.side.word())
        else stringResource(R.string.result_mixed_then, l.leg.startName, a.side.word())
    }
    return parts.joinToString(", ")
}

/** 눈부심 시간대: 이동 내내 / 출발 직후 / 출발 후 N–M분 */
@Composable
internal fun glareText(g: com.sunseat.app.domain.GlareWindow, result: TripResult, zone: ZoneId): String {
    val a = g.start.atZone(zone).format(TimeFormat)
    val b = g.end.atZone(zone).format(TimeFormat)
    val totalMin = Duration.between(result.departure, result.modeledArrival).toMinutes()
    return when {
        g.fromMinute == 0L && g.toMinute >= totalMin -> stringResource(R.string.result_glare_whole, a, b)
        g.fromMinute == 0L -> stringResource(R.string.result_glare_start, g.toMinute, a, b)
        else -> stringResource(R.string.result_glare_interval, g.fromMinute, g.toMinute, a, b)
    }
}

/** 출발·도착 시각과 그 사이 정각 눈금(양 끝과 가까우면 생략). */
internal fun ticks(departure: Instant, arrival: Instant, zone: ZoneId): List<Tick> {
    val total = Duration.between(departure, arrival).toMillis().toFloat().coerceAtLeast(1f)
    val out = mutableListOf(Tick(0f, departure.atZone(zone).format(TimeFormat)))
    var hour = departure.atZone(zone).truncatedTo(ChronoUnit.HOURS).plusHours(1)
    while (hour.toInstant().isBefore(arrival)) {
        val p = Duration.between(departure, hour.toInstant()).toMillis() / total
        if (p > TICK_EDGE_MARGIN && p < 1f - TICK_EDGE_MARGIN) out += Tick(p, hour.format(TimeFormat))
        hour = hour.plusHours(1)
    }
    out += Tick(1f, arrival.atZone(zone).format(TimeFormat))
    return out
}

@Composable
private fun Stagger(shown: Boolean, index: Int, content: @Composable () -> Unit) {
    AnimatedVisibility(
        visible = shown,
        enter = fadeIn(Motion.enterFade(index)) + slideInVertically(Motion.enterSlide()) { it / Motion.ENTER_SLIDE_DIVISOR },
    ) { content() }
}
