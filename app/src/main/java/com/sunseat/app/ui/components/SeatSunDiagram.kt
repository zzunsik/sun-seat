package com.sunseat.app.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.unit.dp
import com.sunseat.app.ui.icons.SunSeatIcons
import com.sunseat.app.ui.theme.Sizes
import com.sunseat.app.ui.theme.Spacing
import com.sunseat.app.ui.theme.SunSeatTheme

/**
 * 위에서 내려다본 차량과 해 위치. 진행 방향은 항상 위쪽이다.
 * 해가 있는 쪽 좌석은 해 색, 반대쪽은 그늘 색으로 칠하고 양옆에 글자 라벨을 둔다.
 * 실제 계산 연결은 #13에서 한다.
 */
@Composable
fun SeatSunDiagram(
    sunOnRight: Boolean,
    modifier: Modifier = Modifier,
) {
    val sunSide = if (sunOnRight) "오른쪽" else "왼쪽"
    val shadeSide = if (sunOnRight) "왼쪽" else "오른쪽"
    Row(
        modifier
            .fillMaxWidth()
            .height(Sizes.diagramHeight)
            .clearAndSetSemantics { contentDescription = "진행 방향 기준 해는 $sunSide, $shadeSide 좌석이 그늘" },
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Spacing.m),
    ) {
        SideLabel("왼쪽", sun = !sunOnRight, alignEnd = true, modifier = Modifier.weight(1f))
        Vehicle(sunOnRight, Modifier.width(Sizes.vehicleWidth).fillMaxHeight())
        SideLabel("오른쪽", sun = sunOnRight, alignEnd = false, modifier = Modifier.weight(1f))
    }
}

@Composable
private fun SideLabel(side: String, sun: Boolean, alignEnd: Boolean, modifier: Modifier = Modifier) {
    val c = SunSeatTheme.colors
    Column(
        modifier,
        horizontalAlignment = if (alignEnd) Alignment.End else Alignment.Start,
        verticalArrangement = Arrangement.spacedBy(Spacing.s),
    ) {
        Icon(
            if (sun) SunSeatIcons.Sun else SunSeatIcons.Shade,
            contentDescription = null,
            tint = if (sun) c.sun else c.shade,
            modifier = Modifier.size(Sizes.iconLarge),
        )
        Text(side, style = MaterialTheme.typography.titleMedium, color = SunSeatTheme.scheme.onSurface)
        ExposurePill(if (sun) Exposure.Sun else Exposure.Shade, if (sun) "햇빛" else "그늘")
    }
}

@Composable
private fun Vehicle(sunOnRight: Boolean, modifier: Modifier = Modifier) {
    val c = SunSeatTheme.colors
    val scheme = SunSeatTheme.scheme
    Canvas(modifier) {
        val d = density
        val w = size.width
        val h = size.height
        // 진행 방향 화살표
        val ax = w / 2f
        drawLine(scheme.onSurface, Offset(ax, 14f * d), Offset(ax, 2f * d), 2.5f * d, StrokeCap.Round)
        drawLine(scheme.onSurface, Offset(ax, 2f * d), Offset(ax - 6f * d, 8f * d), 2.5f * d, StrokeCap.Round)
        drawLine(scheme.onSurface, Offset(ax, 2f * d), Offset(ax + 6f * d, 8f * d), 2.5f * d, StrokeCap.Round)

        // 차체
        val top = 20f * d
        val bodyW = w - 20f * d
        val left = (w - bodyW) / 2f
        val bodyH = h - top - 2f * d
        drawRoundRect(scheme.surfaceContainerHigh, Offset(left, top), Size(bodyW, bodyH), CornerRadius(18f * d))
        drawRoundRect(scheme.outline, Offset(left, top), Size(bodyW, bodyH), CornerRadius(18f * d), style = Stroke(1.5f * d))

        // 좌석 3줄 × 2열
        val seatW = 22f * d
        val seatH = 22f * d
        val gap = 12f * d
        val rows = 3
        val startY = top + (bodyH - (rows * seatH + (rows - 1) * gap)) / 2f
        val leftX = left + 10f * d
        val rightX = left + bodyW - 10f * d - seatW
        val leftColor = if (sunOnRight) c.shade else c.sun
        val rightColor = if (sunOnRight) c.sun else c.shade
        repeat(rows) { i ->
            val y = startY + i * (seatH + gap)
            drawRoundRect(leftColor, Offset(leftX, y), Size(seatW, seatH), CornerRadius(5f * d))
            drawRoundRect(rightColor, Offset(rightX, y), Size(seatW, seatH), CornerRadius(5f * d))
        }

        // 해 쪽 창으로 들어오는 햇살(점선)
        val edge = if (sunOnRight) left + bodyW else left
        val dir = if (sunOnRight) 1f else -1f
        repeat(rows) { i ->
            val y = startY + i * (seatH + gap) + seatH / 2f
            drawLine(
                c.sun, Offset(edge + dir * 3f * d, y), Offset(edge + dir * 10f * d, y),
                2f * d, StrokeCap.Round, PathEffect.dashPathEffect(floatArrayOf(3f * d, 3f * d)),
            )
        }
    }
}
