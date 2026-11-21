package com.sunseat.app.ui.map

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * #153: 출발지 바로 옆 승차 정류장의 노선 이름표("402")가 출발 표식 밑에 가려 "40"처럼 보였다.
 * 이름표는 출발·도착 표식과 겹치지 않는 자리까지 정류장에서 밀려난다. 값은 dp(밀도 1).
 */
class StopLabelPinTest {
    private val radius = 7f
    private val gap = 4f
    private val labelWidth = 36f
    private val labelHeight = 20f
    private val pinWidth = 44f
    private val marker = 32f
    private val tail = 6f

    /** 꼬리 끝이 정류장 기준 ([dx], [dy])에 있는 표식의 몸통 상자. */
    private fun pin(dx: Float, dy: Float) = PinBox(dx - pinWidth / 2, dy - marker, dx + pinWidth / 2, dy - tail)

    private fun overlaps(onLeft: Boolean, offset: Float, box: PinBox): Boolean {
        val left = if (onLeft) -offset - labelWidth else offset
        val right = left + labelWidth
        return left < box.right && right > box.left && -labelHeight / 2 < box.bottom && labelHeight / 2 > box.top
    }

    private fun offset(onLeft: Boolean, vararg pins: PinBox) = stopLabelOffset(onLeft, labelWidth, labelHeight, radius, pins.toList(), gap, pinWidth + gap)

    @Test fun boardingLabelClearsTheStartPinAtTheSameSpot() {
        // 축소된 미리보기: 출발지와 승차 정류장이 화면에서 거의 한 점이다.
        listOf(true, false).forEach { onLeft ->
            val box = pin(2f, 1f)
            val off = offset(onLeft, box)
            assertFalse("label (left=$onLeft, offset=$off) must not sit under the start pin", overlaps(onLeft, off, box))
        }
    }

    @Test fun labelStaysBesideTheStopWhenNoPinIsNear() {
        assertEquals(radius, offset(true, pin(0f, -80f)), 0.01f) // 표식이 위로 멀리 있음
        assertEquals(radius, offset(false, pin(-120f, 0f)), 0.01f) // 반대쪽 멀리
        assertEquals(radius, offset(true), 0.01f)
    }

    @Test fun pushIsBoundedSoTheLabelStillReadsAsTheStops() {
        // 표식이 이름표 쪽으로 멀리(이름표 한가운데) 있으면 그만큼 밀지 않는다.
        val off = offset(true, pin(-30f, 0f))
        assertTrue("offset $off must stay within the bound", off <= radius + pinWidth + gap)
    }

    @Test fun clearsBothPinsWhenStartAndEndAreNear() {
        val start = pin(0f, 0f)
        val end = pin(-30f, 4f)
        val off = offset(true, start, end)
        assertFalse(overlaps(true, off, start))
    }
}
