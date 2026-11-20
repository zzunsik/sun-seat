package com.sunseat.app.ui.map

import androidx.compose.ui.graphics.Color
import com.sunseat.app.domain.LatLng
import com.sunseat.app.domain.Mode
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * #143: 선택 경로 맞춤은 좌표만 보므로, 정류장 옆에 붙는 노선 이름표(예: "402", "2호선 외 1")가 맞춤 inset(40dp)보다 넓으면
 * 가장자리 정류장의 이름표가 화면 밖으로 잘렸다. 맞춤 틀은 각 쪽 이름표가 들어갈 만큼 그쪽 지도 여백을 둔다. 값은 dp(밀도 1).
 */
class MapLabelMarginTest {
    private val inset = 40f
    private val edge = 8f
    private val markerHeight = 36f

    @Test fun wideLeftLabelGetsRoomOnTheLeftOnly() {
        // 세로 미리보기(넉넉한 높이). 정류장 반지름 7 + 이름표 63 = 왼쪽으로 70dp가 필요하다.
        val f = fitFrame(64f, 120f, 384f, 760f, inset, edge, markerHeight, labelLeft = 70f, labelRight = 0f)
        assertTrue("left room ${f.left + f.inset} must hold the 70dp label", f.left + f.inset >= 70f)
        // 오른쪽에는 이름표가 없으므로 기본 폭 그대로다.
        assertEquals(inset, f.right + f.inset, 0.01f)
    }

    @Test fun wideRightLabelGetsRoomOnTheRightInAShortStrip() {
        // 낮은 목록 띠(#138)에서도 같다: inset이 줄어도 오른쪽 이름표 자리는 남는다.
        val f = fitFrame(0f, 190f, 384f, 300f, inset, edge, markerHeight, labelLeft = 0f, labelRight = 56f)
        assertTrue(f.right + f.inset >= 56f)
        assertEquals(inset, f.left + f.inset, 0.01f)
    }

    @Test fun labelsNarrowerThanTheInsetChangeNothing() {
        val plain = fitFrame(64f, 120f, 384f, 760f, inset, edge, markerHeight)
        assertEquals(plain, fitFrame(64f, 120f, 384f, 760f, inset, edge, markerHeight, labelLeft = 30f, labelRight = 39f))
    }

    @Test fun labelExtentsFollowStopSides() {
        // 이름표 폭을 재는 쪽(SDK 비트맵·테스트 지도 글자 측정)과 무관하게, 승차·환승 표식만 이름표가 있고 표시 쪽으로만 센다.
        val stops = listOf(
            StopMarker(LatLng(37.55, 126.97), StopKind.BOARD, Color.Black, Mode.BUS, listOf("402"), labelOnLeft = true),
            StopMarker(LatLng(37.52, 127.00), StopKind.TRANSFER, Color.Black, Mode.SUBWAY, listOf("2호선"), labelOnLeft = false),
            StopMarker(LatLng(37.50, 127.03), StopKind.ALIGHT, Color.Black, Mode.SUBWAY),
        )
        val (left, right) = labelExtents(stops, stopRadius = 7f) { stop -> if (stop.lines.firstOrNull() == "402") 50f else 60f }
        assertEquals(57f, left, 0.01f)
        assertEquals(67f, right, 0.01f)
    }
}
