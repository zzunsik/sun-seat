package com.sunseat.app.ui.components

import androidx.compose.ui.res.stringResource
import com.sunseat.app.R
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.border
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import com.sunseat.app.domain.Side
import com.sunseat.app.ui.icons.SunSeatIcons
import com.sunseat.app.ui.theme.CapsuleShape
import com.sunseat.app.ui.theme.Sizes
import com.sunseat.app.ui.theme.Spacing
import com.sunseat.app.ui.theme.SunSeatTheme
import kotlin.math.cos
import kotlin.math.sin

/** 해가 주로 있는 곳. */
enum class DiagramSun { LEFT, RIGHT, AHEAD }

/**
 * 위에서 내려다본 차량과 해 위치. 진행 방향은 항상 위쪽이다.
 * 해가 드는 쪽 좌석은 해 색, 반대쪽은 그늘 색으로 칠하고 양옆에 글자 라벨을 둔다.
 * [recommended] 쪽 좌석에는 체크 테두리와 "추천" 라벨을 겹친다(색 외 표시).
 * [car]면 운전석·조수석·뒷좌석 2개로 그린다(운전석은 추천 대상 아님).
 */
@Composable
fun SeatSunDiagram(
    sun: DiagramSun,
    modifier: Modifier = Modifier,
    recommended: Side? = null,
    car: Boolean = false,
    /**
     * 좌석 기준으로 해를 받는지(지하철은 반대쪽 좌석도 정면으로 받을 수 있다).
     * null이면 해가 드는 창 쪽만 해를 받는 것으로 칠한다.
     */
    seatSun: Pair<Boolean, Boolean>? = null,
    sunVisible: Boolean = true,
) {
    val states = if (!sunVisible) SideState.SHADE to SideState.SHADE else seatSun?.let { (l, r) -> (if (l) SideState.SUN else SideState.SHADE) to (if (r) SideState.SUN else SideState.SHADE) }
        ?.takeIf { sun != DiagramSun.AHEAD }
        ?: (sun.state(Side.LEFT) to sun.state(Side.RIGHT))
    val desc = if (!sunVisible) stringResource(R.string.diagram_no_sun) else when (sun) {
        DiagramSun.LEFT -> stringResource(R.string.diagram_left)
        DiagramSun.RIGHT -> stringResource(R.string.diagram_right)
        DiagramSun.AHEAD -> stringResource(R.string.diagram_ahead)
    } + (recommended?.let { if (it == Side.LEFT) stringResource(R.string.diagram_recommend_left) else stringResource(R.string.diagram_recommend_right) } ?: "")
    Row(
        modifier
            .fillMaxWidth()
            .height(Sizes.diagramHeight)
            .clearAndSetSemantics { contentDescription = desc },
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Spacing.m),
    ) {
        SideLabel(stringResource(R.string.common_left), states.first, recommended == Side.LEFT, alignEnd = true, modifier = Modifier.weight(1f))
        Vehicle(sun, states, recommended, car, Modifier.width(Sizes.vehicleWidth).fillMaxHeight(), sunVisible)
        SideLabel(stringResource(R.string.common_right), states.second, recommended == Side.RIGHT, alignEnd = false, modifier = Modifier.weight(1f))
    }
}

private enum class SideState { SUN, SHADE, SIMILAR }

private fun DiagramSun.state(side: Side): SideState = when (this) {
    DiagramSun.AHEAD -> SideState.SIMILAR
    DiagramSun.LEFT -> if (side == Side.LEFT) SideState.SUN else SideState.SHADE
    DiagramSun.RIGHT -> if (side == Side.RIGHT) SideState.SUN else SideState.SHADE
}

@Composable
private fun SideLabel(side: String, state: SideState, recommended: Boolean, alignEnd: Boolean, modifier: Modifier = Modifier) {
    val c = SunSeatTheme.colors
    val scheme = SunSeatTheme.scheme
    Column(
        modifier,
        horizontalAlignment = if (alignEnd) Alignment.End else Alignment.Start,
        verticalArrangement = Arrangement.spacedBy(Spacing.s),
    ) {
        when (state) {
            SideState.SUN -> Icon(SunSeatIcons.Sun, contentDescription = null, tint = c.sun, modifier = Modifier.size(Sizes.iconLarge))
            SideState.SHADE -> Icon(SunSeatIcons.Shade, contentDescription = null, tint = c.shade, modifier = Modifier.size(Sizes.iconLarge))
            SideState.SIMILAR -> Unit
        }
        Text(side, style = MaterialTheme.typography.titleMedium, color = scheme.onSurface)
        when (state) {
            SideState.SUN -> ExposurePill(Exposure.Sun, stringResource(R.string.common_sun))
            SideState.SHADE -> ExposurePill(Exposure.Shade, stringResource(R.string.common_shade))
            SideState.SIMILAR -> Text(stringResource(R.string.common_similar), style = MaterialTheme.typography.labelMedium, color = scheme.onSurfaceVariant)
        }
        if (recommended) {
            Row(
                Modifier
                    .border(Sizes.hairline * 2, scheme.onSurface, CapsuleShape)
                    .padding(horizontal = Spacing.s, vertical = Sizes.pillVertical / 2),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(Spacing.xs),
            ) {
                Icon(painterResource(R.drawable.ic_ms_check), contentDescription = null, tint = scheme.onSurface, modifier = Modifier.size(Sizes.iconSmall))
                Text(stringResource(R.string.common_recommend), style = MaterialTheme.typography.labelMedium, color = scheme.onSurface)
            }
        }
    }
}

@Composable
private fun Vehicle(sun: DiagramSun, states: Pair<SideState, SideState>, recommended: Side?, car: Boolean, modifier: Modifier = Modifier, sunVisible: Boolean = true) {
    val c = SunSeatTheme.colors
    val scheme = SunSeatTheme.scheme
    Canvas(modifier) {
        val d = density
        val w = size.width
        val h = size.height
        val ax = w / 2f
        val top = (if (sun == DiagramSun.AHEAD) Sizes.diagramSunArea else Sizes.diagramArrowArea).toPx()

        if (sunVisible && sun == DiagramSun.AHEAD) drawSun(c.sun, Offset(ax, 10f * d), 6f * d)
        // 진행 방향 화살표
        val arrowTop = top - 18f * d
        drawLine(scheme.onSurface, Offset(ax, top - 6f * d), Offset(ax, arrowTop), 2.5f * d, StrokeCap.Round)
        drawLine(scheme.onSurface, Offset(ax, arrowTop), Offset(ax - 6f * d, arrowTop + 6f * d), 2.5f * d, StrokeCap.Round)
        drawLine(scheme.onSurface, Offset(ax, arrowTop), Offset(ax + 6f * d, arrowTop + 6f * d), 2.5f * d, StrokeCap.Round)

        // 차체
        val bodyW = w - 2 * Sizes.diagramBodyInset.toPx()
        val left = (w - bodyW) / 2f
        val bodyH = h - top - 2f * d
        val bodyR = CornerRadius(Sizes.diagramBodyRadius.toPx())
        drawRoundRect(scheme.surfaceContainerHigh, Offset(left, top), Size(bodyW, bodyH), bodyR)
        drawRoundRect(scheme.outline, Offset(left, top), Size(bodyW, bodyH), bodyR, style = Stroke(Sizes.diagramStroke.toPx()))

        fun color(side: Side): Color = when (if (side == Side.LEFT) states.first else states.second) {
            SideState.SUN -> c.sun
            SideState.SHADE -> c.shade
            SideState.SIMILAR -> scheme.surfaceContainerHighest
        }
        val rows = if (car) 2 else 3
        val seatW = Sizes.diagramSeat.toPx()
        val seatH = (if (car) Sizes.diagramCarSeatHeight else Sizes.diagramSeat).toPx()
        val gap = (if (car) Sizes.diagramCarSeatGap else Sizes.diagramSeatGap).toPx()
        val seatR = CornerRadius(Sizes.diagramSeatRadius.toPx())
        val startY = top + (bodyH - (rows * seatH + (rows - 1) * gap)) / 2f
        val leftX = left + Sizes.diagramBodyInset.toPx()
        val rightX = left + bodyW - Sizes.diagramBodyInset.toPx() - seatW
        val ring = Stroke(Sizes.diagramRing.toPx())
        repeat(rows) { i ->
            val y = startY + i * (seatH + gap)
            listOf(Side.LEFT to leftX, Side.RIGHT to rightX).forEach { (side, x) ->
                val driver = car && i == 0 && side == Side.LEFT
                if (driver) {
                    // 운전석: 운전대(바퀴살 3개)로 그려 추천 테두리와 구분한다(추천 대상 아님)
                    val cx = x + seatW / 2
                    val cy = y + seatH / 2
                    val wr = seatW * 0.42f
                    val sw = Sizes.diagramStroke.toPx() * 1.4f
                    drawCircle(scheme.onSurfaceVariant, wr, Offset(cx, cy), style = Stroke(sw))
                    drawLine(scheme.onSurfaceVariant, Offset(cx - wr, cy), Offset(cx + wr, cy), sw)
                    drawLine(scheme.onSurfaceVariant, Offset(cx, cy), Offset(cx, cy + wr), sw)
                } else {
                    drawRoundRect(color(side), Offset(x, y), Size(seatW, seatH), seatR)
                    if (side == recommended) {
                        val o = Sizes.diagramRingOffset.toPx()
                        drawRoundRect(scheme.onSurface, Offset(x - o, y - o), Size(seatW + 2 * o, seatH + 2 * o), CornerRadius(seatR.x + o), style = ring)
                    }
                }
            }
        }

        // 해 쪽 창으로 들어오는 햇살(점선)
        if (sunVisible && sun != DiagramSun.AHEAD) {
            val onRight = sun == DiagramSun.RIGHT
            val edge = if (onRight) left + bodyW else left
            val dir = if (onRight) 1f else -1f
            repeat(rows) { i ->
                val y = startY + i * (seatH + gap) + seatH / 2f
                drawLine(
                    c.sun, Offset(edge + dir * 3f * d, y), Offset(edge + dir * 10f * d, y),
                    2f * d, StrokeCap.Round, PathEffect.dashPathEffect(floatArrayOf(3f * d, 3f * d)),
                )
            }
        }
    }
}

private fun DrawScope.drawSun(color: Color, center: Offset, r: Float) {
    drawCircle(color, r, center)
    for (i in 0 until 8) {
        val a = i * Math.PI / 4
        val v = Offset(cos(a).toFloat(), sin(a).toFloat())
        drawLine(color, center + v * (r * 1.5f), center + v * (r * 2.1f), r * 0.4f, StrokeCap.Round)
    }
}
