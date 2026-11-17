package com.sunseat.app.ui.simulation

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import com.sunseat.app.R
import com.sunseat.app.domain.*
import com.sunseat.app.ui.components.*
import com.sunseat.app.ui.input.NeutralFilterChip
import com.sunseat.app.ui.map.*
import com.sunseat.app.ui.theme.Sizes
import com.sunseat.app.ui.theme.Spacing
import com.sunseat.app.ui.theme.SunSeatTheme
import com.sunseat.app.ui.icons.SunSeatIcons
import java.time.ZoneId
import java.time.format.DateTimeFormatter


data class SimulationActions(
    val back: () -> Unit = {}, val seek: (Float) -> Unit = {}, val toggle: () -> Unit = {},
    val speed: (Int) -> Unit = {}, val unfollow: () -> Unit = {}, val follow: () -> Unit = {},
    val seat: (Int,String) -> Unit = { _,_ -> }, val camera: (Boolean) -> Unit = {},
)

@Composable
fun SimulationScreen(
    request: TripRequest, route: Route, result: TripResult, state: SimulationUiState,
    point: TimelinePoint?, boundaries: List<Float>, actions: SimulationActions,
    modifier: Modifier = Modifier, zone: ZoneId = ZoneId.systemDefault(),
) {
    val configuration=LocalConfiguration.current
    val landscape=configuration.screenWidthDp>configuration.screenHeightDp
    val scheme = SunSeatTheme.scheme
    val duration = java.time.Duration.between(result.departure, result.modeledArrival).toMillis().coerceAtLeast(1)
    val current = result.departure.plusMillis(state.elapsedMillis)
    fun time(value: java.time.Instant) = value.atZone(zone).format(DateTimeFormatter.ofPattern("HH:mm"))
    val currentTime = time(current)
    val sliderLabel = stringResource(R.string.simulation_slider)
    val currentAngle=point?.sun?.let { sun -> point.heading?.let { ExposureAnalyzer.normalize(sun.azimuth-it) } }
    val currentDirection=when {
        currentAngle==null -> R.string.simulation_ahead
        currentAngle<TripAdvisor.AHEAD_CONE_DEG || currentAngle>360-TripAdvisor.AHEAD_CONE_DEG -> R.string.seat_front
        currentAngle>180-TripAdvisor.AHEAD_CONE_DEG && currentAngle<180+TripAdvisor.AHEAD_CONE_DEG -> R.string.seat_rear
        currentAngle<180 -> R.string.common_right
        else -> R.string.common_left
    }
    val sunLabel=when {
        point?.heading==null -> stringResource(R.string.simulation_no_geometry)
        point.leg.mode==Mode.WALK -> stringResource(R.string.result_leg_walk)
        point.undergroundOrUnknown -> stringResource(if(point.leg.groundLevel==GroundLevel.BELOW) R.string.result_leg_underground else R.string.result_leg_unknown_ground)
        point.sun==null || point.sun.altitude<=0 -> stringResource(R.string.simulation_night)
        else -> stringResource(R.string.simulation_map_sun,stringResource(currentDirection),point.sun.altitude.toInt())
    }
    val cursor = point?.let { p ->
        if (p.position == null || p.heading == null) null
        else MapCursor(p.position, p.heading, p.sun?.azimuth ?: 0.0, if (p.undergroundOrUnknown) -90.0 else p.sun?.altitude ?: -90.0)
    }
    @Composable fun JourneyMap(mapModifier: Modifier) {
            Box(mapModifier.clipToBounds()) {
                SunSeatMap(MapState(routes = listOf(route), origin = request.origin, destination = request.destination,
                    cursor = cursor, followCursor = state.following, onGesture = actions.unfollow,
                    routeProgress = point?.routeProgress ?: 0f, showDecorations = false,
                    padding=MapPadding(top=Sizes.simulationMapInfo,bottom=Spacing.s)), Modifier.fillMaxSize())
                Surface(Modifier.align(Alignment.TopStart).fillMaxWidth().padding(Spacing.s),color=scheme.surface,shape=MaterialTheme.shapes.medium) {
                    Column(Modifier.padding(horizontal=Spacing.m,vertical=Spacing.s),verticalArrangement=Arrangement.spacedBy(Spacing.xs)) {
                        Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.SpaceBetween) {
                            Text(stringResource(if(state.elapsedMillis>=duration) R.string.simulation_arrived_time else if(state.playing) R.string.simulation_moving_time else R.string.simulation_paused_time,currentTime),style=MaterialTheme.typography.titleSmall)
                            Text(stringResource(R.string.simulation_progress,(point?.routeProgress?.times(100) ?: 0f).toInt()),style=MaterialTheme.typography.labelSmall)
                        }
                        Row(verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(Spacing.s)) {
                            Icon(SunSeatIcons.Sun,null,Modifier.size(Sizes.iconMedium),tint=scheme.onSurfaceVariant)
                            Text(sunLabel,style=MaterialTheme.typography.bodySmall)
                        }
                        Text(stringResource(R.string.simulation_route_overview,request.origin.name,request.destination.name),style=MaterialTheme.typography.labelSmall,maxLines=1,overflow=androidx.compose.ui.text.style.TextOverflow.Ellipsis)
                    }
                }
                if (!state.following) Surface(Modifier.align(Alignment.BottomEnd).padding(Spacing.s), color = scheme.surface, shape = MaterialTheme.shapes.large) {
                    QuietButton(onClick = actions.follow) { Text(stringResource(R.string.simulation_follow)) }
                }
            }
    }
    @Composable fun CabinDetails(detailModifier: Modifier) {
            Column(detailModifier.verticalScroll(rememberScrollState()).padding(horizontal=Spacing.screen,vertical=if(landscape) Spacing.s else Spacing.screen), verticalArrangement = Arrangement.spacedBy(Spacing.s)) {
                if (point != null && point.leg.mode != Mode.WALK) CabinPanel(point, request.mode, state, actions)
                if (result.arrival != result.modeledArrival) Text(stringResource(R.string.simulation_modeled_note, time(result.arrival)), style = MaterialTheme.typography.bodySmall, color = scheme.onSurfaceVariant)
            }
    }
    Surface(modifier.fillMaxSize(), color = scheme.background, contentColor = scheme.onSurface) {
        Column(Modifier.fillMaxSize()) {
            Row(Modifier.fillMaxWidth().statusBarsPadding().padding(horizontal = Spacing.s), verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = actions.back) { Icon(painterResource(R.drawable.ic_ms_arrow_back), stringResource(R.string.common_back)) }
                Text(stringResource(R.string.simulation_title), Modifier.weight(1f), style = MaterialTheme.typography.titleLarge)
                if (!SunSeatTheme.reduceMotion) Text(stringResource(R.string.simulation_speed_hint, state.speed), style = MaterialTheme.typography.bodySmall, color = scheme.onSurfaceVariant)
            }
            if(landscape) Row(Modifier.fillMaxWidth().weight(1f)) {
                JourneyMap(Modifier.weight(1f).fillMaxHeight())
                CabinDetails(Modifier.weight(1f).fillMaxHeight())
            } else {
                JourneyMap(Modifier.fillMaxWidth().weight(2f))
                CabinDetails(Modifier.fillMaxWidth().weight(3f))
            }
            Surface(color = scheme.surface) {
                if(landscape) Row(Modifier.fillMaxWidth().navigationBarsPadding().padding(horizontal=Spacing.screen,vertical=Spacing.s),verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(Spacing.l)) {
                    Column(Modifier.weight(1f)) {
                        Slider(value=(state.elapsedMillis.toFloat()/duration).coerceIn(0f,1f),onValueChange=actions.seek,
                            modifier=Modifier.fillMaxWidth().heightIn(min=Spacing.touch).semantics { contentDescription=sliderLabel;stateDescription=currentTime },
                            colors=SliderDefaults.colors(thumbColor=scheme.inverseSurface,activeTrackColor=scheme.inverseSurface))
                        Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.SpaceBetween) {
                            Text(time(result.departure),style=MaterialTheme.typography.labelSmall)
                            Text(stringResource(R.string.simulation_scrub_time,currentTime),style=MaterialTheme.typography.labelSmall)
                            Text(time(result.modeledArrival),style=MaterialTheme.typography.labelSmall)
                        }
                    }
                    if(!SunSeatTheme.reduceMotion) Row(horizontalArrangement=Arrangement.spacedBy(Spacing.s),verticalAlignment=Alignment.CenterVertically) {
                        PrimaryButton(onClick=actions.toggle,modifier=Modifier.width(Sizes.simulationCompactPlay),enabled=point!=null) { Text(stringResource(if(state.playing) R.string.simulation_pause else R.string.simulation_play)) }
                        listOf(1,2,4).forEach { speed -> NeutralFilterChip(state.speed==speed,stringResource(R.string.simulation_speed,speed)) { actions.speed(speed) } }
                    }
                } else Column(Modifier.fillMaxWidth().navigationBarsPadding().padding(horizontal = Spacing.screen, vertical = Spacing.s), verticalArrangement = Arrangement.spacedBy(Spacing.xs)) {
                    if(!landscape) MiniTimeline(result, boundaries)
                    Slider(value = (state.elapsedMillis.toFloat() / duration).coerceIn(0f, 1f), onValueChange = actions.seek,
                        modifier = Modifier.fillMaxWidth().heightIn(min = Spacing.touch).semantics { contentDescription = sliderLabel; stateDescription = currentTime },
                        colors = SliderDefaults.colors(thumbColor = scheme.inverseSurface, activeTrackColor = scheme.inverseSurface))
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text(time(result.departure), style = MaterialTheme.typography.labelSmall)
                        Text(stringResource(R.string.simulation_scrub_time, currentTime), style = MaterialTheme.typography.labelSmall)
                        Text(time(result.modeledArrival), style = MaterialTheme.typography.labelSmall)
                    }
                    if (!SunSeatTheme.reduceMotion) Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(Spacing.s), verticalAlignment = Alignment.CenterVertically) {
                        PrimaryButton(onClick = actions.toggle, modifier = Modifier.weight(1f), enabled = point != null) { Text(stringResource(if (state.playing) R.string.simulation_pause else R.string.simulation_play)) }
                        listOf(1, 2, 4).forEach { speed -> NeutralFilterChip(state.speed == speed, stringResource(R.string.simulation_speed, speed)) { actions.speed(speed) } }
                    }
                }
            }
        }
    }
}

@Composable
private fun MiniTimeline(result: TripResult, boundaries: List<Float>, modifier: Modifier = Modifier) {
    val colors = SunSeatTheme.colors
    val scheme = SunSeatTheme.scheme
    Column(modifier.fillMaxWidth().clearAndSetSemantics { }, verticalArrangement = Arrangement.spacedBy(Spacing.xs)) {
        listOf(Side.LEFT to result.leftSpans, Side.RIGHT to result.rightSpans).forEach { (side, spans) ->
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(stringResource(if (side == Side.LEFT) R.string.common_left else R.string.common_right), Modifier.width(Sizes.timelineLabel), style = MaterialTheme.typography.labelSmall)
                Canvas(Modifier.weight(1f).height(Sizes.timelineBar / 4)) {
                    drawRect(colors.shade)
                    result.walkSpans.forEach { drawRect(scheme.outline, Offset(it.start * size.width, 0f), Size((it.end - it.start) * size.width, size.height)) }
                    spans.forEach { span ->
                        val left = span.start * size.width
                        val right = span.end * size.width
                        drawRect(colors.sun, Offset(left, 0f), Size(right - left, size.height))
                        var x = left
                        while (x < right) { drawLine(colors.onSun.copy(alpha = 0.35f), Offset(x, size.height), Offset(minOf(x + size.height, right), 0f), Sizes.hairline.toPx()); x += Spacing.s.toPx() }
                    }
                    boundaries.forEach { x -> drawLine(scheme.onSurface, Offset(x * size.width, 0f), Offset(x * size.width, size.height), Sizes.hairline.toPx()) }
                }
            }
        }
    }
}
