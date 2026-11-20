package com.sunseat.app.ui.map

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * #138: 후보 목록 기본 상태의 지도 띠(실기기 약 110dp)에서 맞춤 inset(40dp × 네 변)이 높이 대부분을 차지해
 * 경로가 약 30dp에 맞춰지고 지도가 크게 축소됐다. Kakao 줌은 정수 단계라 경로가 들어갈 높이가 두 배는 되어야 한 단계
 * 가까워진다. 맞춤 틀은 inset을 줄이되 위로 솟는 출발·도착 표식과 좌우 표식 폭이 잘리지 않게 그 방향에만 여백을 둔다.
 * 값은 dp(밀도 1)로 둔다.
 */
class MapFitFrameTest {
    private val inset = 40f       // Sizes.mapInset
    private val edge = 8f         // Spacing.s
    private val markerHeight = 36f // Sizes.mapMarker + Spacing.xs(표식 위 틈)
    private val markerHalf = 24f  // Spacing.xl: 표식 절반 폭

    private fun frame(top: Float, bottom: Float, width: Float, height: Float) = fitFrame(top, bottom, width, height, inset, edge, markerHeight, markerHalf)
    /** 경로·표식 좌표가 실제로 들어가는 높이·폭. */
    private fun FitFrame.fitHeight(height: Float) = height - top - bottom - 2 * this.inset
    private fun FitFrame.fitWidth(width: Float) = width - 2 * side - 2 * this.inset

    /** 표식이 잘리지 않는 자리: 위로 표식 높이, 좌우로 표식 절반 폭(지도 여백 + 맞춤 inset). */
    private fun assertMarkerRoom(f: FitFrame, topPadding: Float) {
        assertTrue("marker must not hide under the top padding", f.top + f.inset >= topPadding + markerHeight)
        assertTrue("marker label must not be cut at the sides", f.side + f.inset >= markerHalf)
        assertTrue(f.inset >= edge)
    }

    @Test fun shortListStripLeavesRoomForOneCloserZoomLevel() {
        // 390dp 폭 기기의 목록 기본 상태: 지도 300dp 중 시트가 아래 190dp를 덮어 보이는 띠는 110dp.
        val f = frame(top = 0f, bottom = 190f, width = 384f, height = 300f)
        val before = 110f - 2 * inset // 고치기 전: 30dp
        assertTrue("fit height ${f.fitHeight(300f)} must be at least twice the old $before", f.fitHeight(300f) >= 2 * before)
        assertMarkerRoom(f, 0f)
        assertEquals(190f, f.bottom, 0.01f)
    }

    @Test fun roomyMapKeepsDefaultInsetAndPadding() {
        // 미리보기(위 여백 = 지도 맞춤 행동 아래끝)·입력·지도 선택(#85: 여백 없음, 화면 가운데 = 핀)처럼 넉넉한 영역은 지금과 같다.
        assertEquals(FitFrame(64f, 120f, 0f, inset), frame(top = 64f, bottom = 120f, width = 540f, height = 1200f))
        assertEquals(FitFrame(0f, 0f, 0f, inset), frame(top = 0f, bottom = 0f, width = 600f, height = 900f))
    }

    @Test fun markerRoomIsAddedBelowAnExistingTopPadding() {
        // 가로 입력 화면처럼 헤더가 지도 위를 덮는 경우: 표식은 헤더 아래끝에서 표식 높이만큼 내려와야 보인다.
        val f = frame(top = 250f, bottom = 0f, width = 844f, height = 390f)
        assertMarkerRoom(f, 250f)
    }

    @Test fun frameNeverGrowsInsetAndAlwaysKeepsMarkerRoom() {
        for (visible in listOf(81f, 110f, 155f, 200f, 400f, 700f)) {
            val f = frame(top = 0f, bottom = 0f, width = 384f, height = visible)
            assertTrue(f.inset in edge..inset)
            assertMarkerRoom(f, 0f)
            // 좌우 여백은 표식 폭만큼만이라 맞춤 폭이 고치기 전(폭 − 2×40)보다 줄지 않는다.
            assertTrue(f.fitWidth(384f) >= 384f - 2 * inset)
        }
    }
}
