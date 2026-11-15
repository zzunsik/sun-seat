package com.sunseat.app.ui.components

import androidx.compose.ui.res.stringResource
import com.sunseat.app.R
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.sunseat.app.ui.theme.Motion
import com.sunseat.app.ui.theme.Sizes
import com.sunseat.app.ui.theme.Spacing
import com.sunseat.app.ui.theme.SunSeatTheme

/** 구간 진행 비율(0~1) 위의 햇빛 구간 */
data class SunSpan(val start: Float, val end: Float)

/** 시각 눈금: [position]은 0~1 진행 비율 */
data class Tick(val position: Float, val label: String)

/**
 * 좌·우 햇빛 타임라인. 줄마다 글자 라벨을 붙이고, 해 구간은 색에 더해 사선 무늬로도 구분한다.
 * [selectedRight]가 1이면 오른쪽 줄, 0이면 왼쪽 줄에 선택 테두리를 그린다(흐리게 처리하지 않는다).
 * 눈금은 실제 시각 비율 위치에 놓는다.
 */
@Composable
fun SideTimeline(
    leftSun: List<SunSpan>,
    rightSun: List<SunSpan>,
    ticks: List<Tick>,
    modifier: Modifier = Modifier,
    selectedRight: Float = 0f,
    animateIn: Boolean = true,
    /** 추천할 쪽이 없으면(구간마다 다름·차이 없음) 선택 테두리를 그리지 않는다 */
    showSelection: Boolean = true,
    /** 추천 대상이 아닌 구간(도보). 회색으로 칠하고 범례에 "도보"를 더한다 */
    gaps: List<SunSpan> = emptyList(),
) {
    val reduce = SunSeatTheme.reduceMotion
    val fill = remember { Animatable(if (animateIn && !reduce) 0f else 1f) }
    LaunchedEffect(Unit) {
        if (fill.value < 1f) fill.animateTo(1f, tween(durationMillis = Motion.TIMELINE_FILL_MS, delayMillis = Motion.TIMELINE_DELAY_MS))
    }
    Column(modifier, verticalArrangement = Arrangement.spacedBy(Spacing.s)) {
        Legend(showGap = gaps.isNotEmpty())
        TimelineRow(stringResource(R.string.common_left), leftSun, gaps, fill.value, selected = if (showSelection) 1f - selectedRight else 0f)
        TimelineRow(stringResource(R.string.common_right), rightSun, gaps, fill.value, selected = if (showSelection) selectedRight else 0f)
        TickRow(ticks, Modifier.fillMaxWidth().padding(start = Sizes.timelineLabel))
    }
}

@Composable
private fun Legend(showGap: Boolean) {
    val c = SunSeatTheme.colors
    val gap = SunSeatTheme.scheme.outline
    Row(
        horizontalArrangement = Arrangement.spacedBy(Spacing.l),
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.clearAndSetSemantics { },
    ) {
        LegendItem(stringResource(R.string.common_sun)) { drawSwatch(c.sun, hatch = c.onSun) }
        LegendItem(stringResource(R.string.common_shade)) { drawSwatch(c.shade, hatch = null) }
        if (showGap) LegendItem(stringResource(R.string.common_walk)) { drawSwatch(gap, hatch = null) }
    }
}

@Composable
private fun LegendItem(label: String, swatch: DrawScope.() -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        Canvas(Modifier.size(Sizes.iconSmall), onDraw = swatch)
        Text(label, style = MaterialTheme.typography.labelMedium, color = SunSeatTheme.scheme.onSurface)
    }
}

private fun DrawScope.drawSwatch(color: Color, hatch: Color?) {
    val r = CornerRadius(4.dp.toPx())
    drawRoundRect(color, cornerRadius = r)
    if (hatch != null) clipPath(Path().apply { addRoundRect(RoundRect(0f, 0f, size.width, size.height, r)) }) {
        drawHatch(0f, size.width, hatch)
    }
}

private fun DrawScope.drawHatch(x0: Float, x1: Float, color: Color) {
    val h = size.height
    val step = 7.dp.toPx()
    var x = x0 - h
    while (x < x1) {
        drawLine(color.copy(alpha = 0.35f), Offset(x, h), Offset(x + h, 0f), strokeWidth = 1.5.dp.toPx())
        x += step
    }
}

@Composable
private fun TimelineRow(label: String, sun: List<SunSpan>, gaps: List<SunSpan>, progress: Float, selected: Float) {
    val c = SunSeatTheme.colors
    val gapColor = SunSeatTheme.scheme.outline
    val ring = SunSeatTheme.scheme.onSurface
    val sunPct = (sun.sumOf { (it.end - it.start).toDouble() } * 100).toInt()
    val description = stringResource(R.string.timeline_description, label, sunPct, 100 - sunPct)
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.semantics(mergeDescendants = true) {
            contentDescription = description
        },
    ) {
        Text(
            label,
            style = MaterialTheme.typography.labelMedium,
            fontWeight = if (selected > 0.5f) FontWeight.ExtraBold else null,
            color = SunSeatTheme.scheme.onSurface,
            modifier = Modifier.width(Sizes.timelineLabel),
        )
        Canvas(Modifier.weight(1f).height(Sizes.timelineBar)) {
            val inset = 3.dp.toPx()
            val r = CornerRadius(Sizes.timelineBarRadius.toPx())
            val inner = Size(size.width - inset * 2, size.height - inset * 2)
            val w = inner.width * progress
            if (selected > 0.01f) {
                drawRoundRect(
                    ring.copy(alpha = selected),
                    topLeft = Offset(1.dp.toPx(), 1.dp.toPx()),
                    size = Size(size.width - 2.dp.toPx(), size.height - 2.dp.toPx()),
                    cornerRadius = CornerRadius(r.x + 2.dp.toPx()),
                    style = Stroke(2.dp.toPx()),
                )
            }
            val clip = Path().apply { addRoundRect(RoundRect(inset, inset, inset + w, inset + inner.height, r)) }
            clipPath(clip) {
                drawRect(c.shade, Offset(inset, inset), inner)
                gaps.forEach { g ->
                    val x0 = inset + g.start * inner.width
                    drawRect(gapColor, Offset(x0, inset), Size(inset + g.end * inner.width - x0, inner.height))
                }
                sun.forEach { s ->
                    val x0 = inset + s.start * inner.width
                    val x1 = inset + s.end * inner.width
                    drawRect(c.sun, Offset(x0, inset), Size(x1 - x0, inner.height))
                    clipPath(Path().apply { addRect(androidx.compose.ui.geometry.Rect(x0, 0f, x1, size.height)) }) {
                        drawHatch(x0, x1, c.onSun)
                    }
                }
            }
        }
    }
}

/** 눈금 글자를 실제 비율 위치에 놓는다. 양 끝 글자는 막대 안쪽으로 정렬한다. */
@Composable
private fun TickRow(ticks: List<Tick>, modifier: Modifier = Modifier) {
    Layout(
        content = {
            ticks.forEach {
                Box { Text(it.label, style = MaterialTheme.typography.labelSmall, color = SunSeatTheme.scheme.onSurfaceVariant) }
            }
        },
        modifier = modifier,
    ) { measurables, constraints ->
        val placeables = measurables.map { it.measure(constraints.copy(minWidth = 0)) }
        val width = constraints.maxWidth
        val height = placeables.maxOfOrNull { it.height } ?: 0
        layout(width, height) {
            placeables.forEachIndexed { i, p ->
                val center = (ticks[i].position * width).toInt()
                val x = (center - p.width / 2).coerceIn(0, width - p.width)
                p.place(x, 0)
            }
        }
    }
}
