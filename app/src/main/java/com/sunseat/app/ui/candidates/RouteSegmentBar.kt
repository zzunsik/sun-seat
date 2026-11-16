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
import com.sunseat.app.R
import com.sunseat.app.domain.Mode
import com.sunseat.app.ui.theme.Spacing
import com.sunseat.app.ui.theme.Sizes
import com.sunseat.app.ui.theme.SunSeatTheme

/** Segment widths follow modeled leg durations; labels remain available without color. */
@Composable
fun RouteSegmentBar(item: CandidateUi, modifier: Modifier = Modifier) {
    val scheme = SunSeatTheme.scheme
    val labels = item.chips.map { chip -> when (chip.mode) {
        Mode.WALK -> stringResource(R.string.candidates_walk, chip.walkMinutes)
        Mode.SUBWAY -> chip.lineName.ifBlank { stringResource(R.string.common_subway) }
        Mode.BUS -> stringResource(R.string.candidates_bus_name, chip.lineName)
        Mode.CAR -> stringResource(R.string.common_car)
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
