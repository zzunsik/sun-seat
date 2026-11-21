package com.sunseat.app.ui.map

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * #151: 낮은 지도 띠(#138)에서 맞춤 inset이 8dp(Spacing.s)까지 줄면, 정류장 좌표를 세로 가운데로 둔 노선 이름표
 * (높이 Sizes.mapLabelHeight 20dp, 큰 글자에서 더 높음)의 절반이 inset보다 커서 맨 아래 정류장의 이름표가 시트 경계에 걸렸다.
 * 예: 강남역 → 서울역처럼 북쪽으로 가는 경로는 출발지 옆 승차 정류장이 맨 아래에 온다. 위쪽은 출발·도착 표식 여백이 지킨다.
 * 값은 dp(밀도 1).
 */
class MapLabelEdgeTest {
    private val inset = 40f
    private val edge = 8f
    private val markerHeight = 36f

    /** 좌표 위·아래로 남는 자리(지도 여백에 더한 몫 + 맞춤 inset). */
    private fun FitFrame.roomBelow(bottomPadding: Float) = bottom - bottomPadding + inset
    private fun FitFrame.roomAbove(topPadding: Float) = top - topPadding + inset

    @Test fun bottomStopLabelIsNotCutInTheShortListStrip() {
        for (labelHeight in listOf(20f, 28f)) { // 기본 글자, 큰 글자(1.5배)
            val f = fitFrame(0f, 190f, 384f, 300f, inset, edge, markerHeight, labelHeight = labelHeight)
            assertTrue("room below ${f.roomBelow(190f)} must hold half of a $labelHeight label", f.roomBelow(190f) >= labelHeight / 2)
            assertTrue(f.roomAbove(0f) >= labelHeight / 2)
        }
    }

    @Test fun roomyMapIsUnchanged() {
        assertEquals(fitFrame(64f, 120f, 384f, 760f, inset, edge, markerHeight), fitFrame(64f, 120f, 384f, 760f, inset, edge, markerHeight, labelHeight = 20f))
    }
}
