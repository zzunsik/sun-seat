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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.text.style.TextOverflow
import com.sunseat.app.ui.formatDuration
import com.sunseat.app.R
import com.sunseat.app.domain.Mode
import com.sunseat.app.ui.theme.Spacing
import com.sunseat.app.ui.theme.Sizes
import com.sunseat.app.ui.theme.SunSeatTheme

/** Segment widths follow modeled leg durations; labels remain available without color. */
@Composable
fun RouteSegmentBar(item: CandidateUi, modifier: Modifier = Modifier, taxi: Boolean = false) {
    val scheme = SunSeatTheme.scheme
    val labels = item.chips.mapIndexed { index, chip -> when (chip.mode) {
        Mode.WALK -> if (item.chosen.route.legs.getOrNull(index)?.duration?.let { !it.isZero && it < java.time.Duration.ofMinutes(1) } == true) stringResource(R.string.candidates_walk_under_minute) else stringResource(R.string.candidates_walk, chip.walkMinutes)
        Mode.SUBWAY -> listOf(stringResource(R.string.common_subway), chip.lineName).filter { it.isNotBlank() }.joinToString(" ")
        Mode.BUS -> listOf(stringResource(R.string.common_bus), chip.lineName).filter { it.isNotBlank() }.joinToString(" ")
        Mode.CAR -> stringResource(if (taxi) R.string.common_taxi else R.string.common_car)
    } }
    val weights = item.chosen.route.legs.map { it.duration.seconds.toFloat().coerceAtLeast(1f) }
    val total = weights.sum().coerceAtLeast(1f)
    val description = labels.joinToString(" → ")
    Box(modifier.fillMaxWidth().height(Spacing.touch).clearAndSetSemantics { contentDescription = description }) {
        Canvas(Modifier.matchParentSize()) {
            var x = 0f
            weights.forEachIndexed { i, weight ->
                val width = size.width * weight / total
                drawRect(if (item.chips.getOrNull(i)?.mode == Mode.WALK) scheme.outlineVariant else scheme.surfaceContainerHighest, Offset(x, 0f), Size(width, size.height))
                if (i > 0) drawLine(scheme.surface, Offset(x, 0f), Offset(x, size.height), Sizes.hairline.toPx())
                x += width
            }
        }
        Row(Modifier.fillMaxSize(), verticalAlignment = Alignment.CenterVertically) {
            labels.forEachIndexed { i, label -> Text(label, Modifier.weight(weights.getOrElse(i) { 1f }).padding(horizontal = Spacing.xs), style = MaterialTheme.typography.labelSmall, color = scheme.onSurface, maxLines = 1, overflow = TextOverflow.Ellipsis) }
        }
    }
}

/** Readable itinerary independent of proportional segment widths. Only estimated splits are folded. */
@Composable
internal fun RouteLegSummary(item: CandidateUi, taxi: Boolean) {
    val scheme = SunSeatTheme.scheme
    Column(verticalArrangement = Arrangement.spacedBy(Spacing.s)) {
        val legs = item.chosen.route.legs
        var index = 0
        while (index < legs.size) {
            val first = index
            val leg = legs[first]
            val chip = item.chips.getOrNull(first) ?: LegChip(leg.mode)
            var last = first
            while (last + 1 < legs.size && legs[last].estimatedStationTiming &&
                legs[last + 1].estimatedStationTiming && legs[last + 1].mode == leg.mode &&
                item.chips.getOrNull(last + 1)?.lineName == chip.lineName &&
                legs[last].endName == legs[last + 1].startName) last++
            val mode = stringResource(when (leg.mode) {
                Mode.WALK -> R.string.common_walk
                Mode.BUS -> R.string.common_bus
                Mode.SUBWAY -> R.string.common_subway
                Mode.CAR -> if (taxi) R.string.common_taxi else R.string.common_car
            })
            val label = listOf(mode, chip.lineName).filter { it.isNotBlank() }.joinToString(" ")
            Column(verticalArrangement = Arrangement.spacedBy(Spacing.xs)) {
                Row(horizontalArrangement = Arrangement.spacedBy(Spacing.s), verticalAlignment = Alignment.CenterVertically) {
                    if (leg.mode == Mode.SUBWAY) com.sunseat.app.ui.theme.TransitLineColors.of(chip.lineName)?.let { color ->
                        Canvas(Modifier.size(Sizes.lineDot)) { drawCircle(color) }
                    }
                    Text(label, style = MaterialTheme.typography.titleSmall, color = scheme.onSurface, modifier = Modifier.weight(1f))
                }
                if (leg.mode == Mode.WALK) {
                    val duration = legs.subList(first, last + 1).fold(java.time.Duration.ZERO) { sum, part -> sum.plus(part.duration) }
                    Text(if (duration < java.time.Duration.ofMinutes(1)) stringResource(R.string.candidates_under_minute) else formatDuration(duration), style = MaterialTheme.typography.bodySmall, color = scheme.onSurfaceVariant)
                } else {
                    val stops = listOfNotNull(
                        leg.startName.ifBlank { if (first == legs.indexOfFirst { it.mode != Mode.WALK }) item.boardAt else "" }.takeIf { it.isNotBlank() }?.let { stringResource(R.string.candidates_board, it) },
                        legs[last].endName.ifBlank { if (last == legs.indexOfLast { it.mode != Mode.WALK }) item.alightAt else "" }.takeIf { it.isNotBlank() }?.let { stringResource(R.string.candidates_alight, it) },
                    )
                    if (stops.isNotEmpty()) Text(stops.joinToString(" → "), style = MaterialTheme.typography.bodyMedium, color = scheme.onSurfaceVariant)
                }
            }
            index = last + 1
        }
    }
}
