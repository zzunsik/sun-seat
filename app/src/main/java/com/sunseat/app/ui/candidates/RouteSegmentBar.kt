package com.sunseat.app.ui.candidates

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.*
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import com.sunseat.app.R
import com.sunseat.app.domain.Mode
import com.sunseat.app.ui.formatDuration
import com.sunseat.app.ui.theme.Spacing
import com.sunseat.app.ui.theme.Sizes
import com.sunseat.app.ui.theme.SunSeatTheme
import com.sunseat.app.ui.theme.TransitLineColors
import java.time.Duration

/** Original boarding groups, with exact provided duration. Zero stays zero. */
internal data class RouteTimePart(val range: IntRange, val duration: Duration)
internal fun routeTimeParts(item: CandidateUi): List<RouteTimePart> = routeLegGroups(item).map { range ->
    RouteTimePart(range, range.fold(Duration.ZERO) { sum, index -> sum + item.chosen.route.legs[index].duration })
}
internal fun routeTimeFractions(item: CandidateUi): List<Float> {
    val seconds = routeTimeParts(item).map { (it.duration.seconds.toDouble() + it.duration.nano / 1_000_000_000.0).coerceAtLeast(0.0) }
    val total = seconds.sum()
    return seconds.map { if (total > 0) (it / total).toFloat() else 0f }
}

@Composable
private fun modeLabel(mode: Mode, taxi: Boolean): String = stringResource(when (mode) {
    Mode.WALK -> R.string.common_walk
    Mode.BUS -> R.string.common_bus
    Mode.SUBWAY -> R.string.common_subway
    Mode.CAR -> if (taxi) R.string.common_taxi else R.string.common_car
})
@Composable
private fun partDuration(duration: Duration): String =
    if (!duration.isZero && duration < Duration.ofMinutes(1)) stringResource(R.string.candidates_under_minute)
    else formatDuration(duration, zeroOk = true)

@Composable
internal fun routeBoardingLabel(item: CandidateUi, index: Int, taxi: Boolean): String {
    val leg = item.chosen.route.legs[index]
    return listOf(modeLabel(leg.mode, taxi), item.chips.getOrNull(index)?.lineName.orEmpty()).filter { it.isNotBlank() }.joinToString(" ")
}

@Composable
private fun partColor(item: CandidateUi, index: Int): Color = when (item.chosen.route.legs[index].mode) {
    Mode.WALK -> SunSeatTheme.route.walk
    Mode.SUBWAY -> TransitLineColors.of(item.chips.getOrNull(index)?.lineName.orEmpty()) ?: SunSeatTheme.route.transit
    else -> SunSeatTheme.route.transit
}

/** Eight dp graphic; labels wrap below and never live inside proportional segments. */
@Composable
fun RouteSegmentBar(item: CandidateUi, modifier: Modifier = Modifier, taxi: Boolean = false) {
    val parts = routeTimeParts(item)
    val fractions = routeTimeFractions(item)
    val colors = parts.map { partColor(item, it.range.first) }
    val labels = parts.map { part ->
        val index = part.range.first
        val mode = item.chosen.route.legs[index].mode
        val name = if (mode == Mode.SUBWAY) item.chips.getOrNull(index)?.lineName.orEmpty().ifBlank { modeLabel(mode, taxi) }
            else modeLabel(mode, taxi)
        "$name ${partDuration(part.duration)}"
    }
    val descriptions = parts.map { part ->
        // Keep boarding identity distinct from the short, duration-focused visual label.
        val index = part.range.first
        if (item.chosen.route.legs[index].mode == Mode.WALK) labels[parts.indexOf(part)]
        else routeDescription(item, index, taxi)
    }
    val description = descriptions.joinToString(" → ")
    val scheme = SunSeatTheme.scheme
    Column(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(Spacing.xs)) {
        Canvas(Modifier.fillMaxWidth().height(Sizes.routeBar).testTag("route_bar_${item.id}")
            .clearAndSetSemantics { contentDescription = description + "; " + labels.joinToString(" · ") }) {
            val gap = Sizes.routeBarGap.toPx()
            val positive = fractions.count { it > 0 }
            val available = (size.width - gap * (positive - 1).coerceAtLeast(0)).coerceAtLeast(0f)
            var x = 0f
            parts.forEachIndexed { i, part ->
                if (fractions[i] <= 0f) return@forEachIndexed
                val width = available * fractions[i]
                val color = colors[i]
                drawRect(color, Offset(x, 0f), Size(width, size.height))
                if (item.chosen.route.legs[part.range.first].mode == Mode.WALK) {
                    // Clip hatch strokes to this segment; even a short walk keeps its own face.
                    drawContext.canvas.save()
                    drawContext.canvas.clipRect(x, 0f, x + width, size.height)
                    var hatch = x - size.height
                    while (hatch < x + width) {
                        drawLine(scheme.surface, Offset(hatch, size.height), Offset(hatch + size.height, 0f), Sizes.hairline.toPx())
                        hatch += Spacing.xs.toPx()
                    }
                    drawContext.canvas.restore()
                }
                val contrast = (maxOf(color.luminance(), scheme.surface.luminance()) + .05f) /
                    (minOf(color.luminance(), scheme.surface.luminance()) + .05f)
                if (contrast < 3f) drawRect(scheme.outline, Offset(x, 0f), Size(width, size.height), style = Stroke(Sizes.hairline.toPx()))
                x += width + gap
            }
        }
        Text(labels.joinToString(" · "), modifier = Modifier.clearAndSetSemantics { }, style = MaterialTheme.typography.labelMedium, color = scheme.onSurfaceVariant)
    }
}

@Composable
private fun routeDescription(item: CandidateUi, index: Int, taxi: Boolean) = routeBoardingLabel(item, index, taxi)

/** Fold only known estimated intervals from the same original boarding, never a transfer. */
internal fun routeLegGroups(item: CandidateUi): List<IntRange> {
    val legs = item.chosen.route.legs
    return buildList {
        var first = 0
        while (first < legs.size) {
            val leg = legs[first]
            var last = first
            while (last + 1 < legs.size && leg.mode == Mode.SUBWAY && leg.transitStepIndex != null &&
                legs[last].estimatedStationTiming && legs[last + 1].estimatedStationTiming &&
                legs[last + 1].transitStepIndex == leg.transitStepIndex && legs[last + 1].mode == leg.mode &&
                item.chips.getOrNull(last + 1)?.lineName == item.chips.getOrNull(first)?.lineName &&
                legs[last].endName.isNotBlank() && legs[last].endName == legs[last + 1].startName) last++
            add(first..last)
            first = last + 1
        }
    }
}


/** One node per original boarding, connected through walks, and one final alighting node. */
@Composable
internal fun RouteLegSummary(item: CandidateUi, taxi: Boolean) {
    val legs = item.chosen.route.legs
    val groups = routeLegGroups(item)
    val rides = groups.filter { legs[it.first].mode != Mode.WALK }
    Column(Modifier.testTag("route_timeline_${item.id}")) {
        groups.forEach { range ->
            val leg = legs[range.first]
            val walk = leg.mode == Mode.WALK
            val label = if (walk) "${modeLabel(leg.mode, taxi)} ${partDuration(range.fold(Duration.ZERO) { sum, i -> sum + legs[i].duration })}"
                else routeBoardingLabel(item, range.first, taxi)
            val board = leg.startName.ifBlank { if (range == rides.firstOrNull()) item.boardAt else "" }
            Row(Modifier.fillMaxWidth().height(IntrinsicSize.Min).semantics(mergeDescendants = true) { }, horizontalArrangement = Arrangement.spacedBy(Spacing.s)) {
                TimelineRail(partColor(item, range.first), walk, terminal = false, Modifier.fillMaxHeight())
                Column(Modifier.weight(1f).padding(bottom = Spacing.s), verticalArrangement = Arrangement.spacedBy(Spacing.xs)) {
                    Text(label, style = if (walk) MaterialTheme.typography.bodySmall else MaterialTheme.typography.titleSmall)
                    if (!walk && board.isNotBlank()) Text(stringResource(R.string.candidates_board, board), style = MaterialTheme.typography.bodyMedium, color = SunSeatTheme.scheme.onSurfaceVariant)
                    // Keep known transfer alighting stops; never invent missing names.
                    val end = legs[range.last].endName
                    if (!walk && range != rides.lastOrNull() && end.isNotBlank())
                        Text(stringResource(R.string.candidates_alight, end), style = MaterialTheme.typography.bodySmall, color = SunSeatTheme.scheme.onSurfaceVariant)
                }
            }
        }
        rides.lastOrNull()?.let { last ->
            val end = legs[last.last].endName.ifBlank { item.alightAt }
            if (end.isNotBlank()) Row(horizontalArrangement = Arrangement.spacedBy(Spacing.s), verticalAlignment = Alignment.CenterVertically) {
                TimelineRail(partColor(item, last.first), false, true, Modifier.height(Sizes.timelineNode))
                Text(stringResource(R.string.candidates_alight, end), style = MaterialTheme.typography.bodyMedium)
            }
        }
    }
}

@Composable
private fun TimelineRail(color: Color, walk: Boolean, terminal: Boolean, modifier: Modifier = Modifier) {
    val scheme = SunSeatTheme.scheme
    val background = SunSeatTheme.route.selectionContainer
    val contrast = (maxOf(color.luminance(), background.luminance()) + .05f) /
        (minOf(color.luminance(), background.luminance()) + .05f)
    Canvas(modifier.width(Sizes.timelineNode).clearAndSetSemantics { }) {
        val center = size.width / 2
        val pattern = if (walk) PathEffect.dashPathEffect(floatArrayOf(Spacing.xs.toPx(), Spacing.xs.toPx())) else null
        if (!terminal) {
            if (contrast < 3f) drawLine(scheme.outline, Offset(center, 0f), Offset(center, size.height),
                (Sizes.timelineConnector + Sizes.hairline * 2).toPx(), pathEffect = pattern)
            drawLine(color, Offset(center, 0f), Offset(center, size.height), Sizes.timelineConnector.toPx(), pathEffect = pattern)
        }
        if (!walk) {
            if (contrast < 3f) drawCircle(scheme.outline, radius = size.width / 2, center = Offset(center, size.width / 2))
            drawCircle(color, radius = size.width / 2 - if (contrast < 3f) Sizes.hairline.toPx() else 0f,
                center = Offset(center, size.width / 2))
        }
    }
}
