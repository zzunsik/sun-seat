package com.sunseat.app.ui.map

import kotlin.math.atan2

/*
 * #186: 햇빛 따라가기 지도는 차량 둘레를 맞추므로(#183) 도착지가 보이는 지도 밖에 있을 때가 많다. 보이는 영역 가장자리에
 * 도착지 쪽을 가리키는 표시를 두어 어디로 가는지 알 수 있게 한다. 위치 계산은 순수 함수다.
 */

/** 8방향(화면 기준). 글자로도 읽는다(색·모양만으로 구분하지 않는다). */
enum class CueDirection { RIGHT, DOWN_RIGHT, DOWN, DOWN_LEFT, LEFT, UP_LEFT, UP, UP_RIGHT }

/** 가장자리 표시: 가운데 위치(px)와 도착지 쪽 각도(도, 화면 기준 0 = 오른쪽, 90 = 아래). */
internal data class EdgeCue(val x: Float, val y: Float, val angleDegrees: Float, val direction: CueDirection)

/**
 * 화면 위치 ([px], [py])의 좌표가 보이는 영역 [visible] 밖이면, 보이는 영역 가운데에서 그 좌표로 가는 선이 [inset]만큼 안쪽 테두리와 만나는 점에
 * 표시를 둔다. 좌표가 보이는 영역 안이거나(그 자리에 표식이 보인다) 영역이 표시보다 작으면 null.
 */
internal fun offscreenCue(px: Float, py: Float, visible: ScreenBox, inset: Float): EdgeCue? {
    if (!px.isFinite() || !py.isFinite()) return null
    if (px >= visible.left && px <= visible.right && py >= visible.top && py <= visible.bottom) return null
    val left = visible.left + inset; val right = visible.right - inset
    val top = visible.top + inset; val bottom = visible.bottom - inset
    if (right < left || bottom < top) return null
    val cx = (left + right) / 2; val cy = (top + bottom) / 2
    val dx = px - cx; val dy = py - cy
    val sx = if (dx > 0f) (right - cx) / dx else if (dx < 0f) (left - cx) / dx else Float.POSITIVE_INFINITY
    val sy = if (dy > 0f) (bottom - cy) / dy else if (dy < 0f) (top - cy) / dy else Float.POSITIVE_INFINITY
    val s = minOf(sx, sy).coerceAtMost(1f)
    val angle = Math.toDegrees(atan2(dy.toDouble(), dx.toDouble())).toFloat()
    return EdgeCue(cx + dx * s, cy + dy * s, angle, direction(angle))
}

/** 각도(화면 기준) → 8방향. 각 방향이 45°씩 맡는다. */
internal fun direction(angleDegrees: Float): CueDirection {
    val normalized = ((angleDegrees % 360f) + 360f) % 360f
    return CueDirection.entries[(((normalized + 22.5f) / 45f).toInt()) % 8]
}
