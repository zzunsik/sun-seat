package com.sunseat.app.ui.map

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * #138: 후보 목록 기본 상태의 지도 띠(실기기 약 110dp)에서 맞춤 inset(40dp × 위아래)이 높이 대부분을 차지해
 * 경로가 약 30dp에 맞춰지고 지도가 크게 축소됐다. 맞춤 틀은 보이는 지도 크기에 맞춰 inset을 줄이되
 * 출발·도착 표식(좌표 위로 솟음)과 좌우 표식 폭이 잘리지 않을 만큼은 남긴다. 값은 dp(밀도 1)로 둔다.
 */
class MapFitFrameTest {
    private val inset = 40f      // Sizes.mapInset
    private val minInset = 24f   // Spacing.xl: 표식 절반 폭
    private val marker = 36f     // Sizes.mapMarker + Spacing.xs(표식 위 틈)

    private fun frame(top: Float, bottom: Float, width: Float, height: Float) = fitFrame(top, bottom, width, height, inset, minInset, marker)
    /** 경로·표식 좌표가 실제로 들어가는 높이. */
    private fun FitFrame.fitHeight(height: Float) = height - top - bottom - 2 * inset

    @Test fun shortListStripKeepsMostOfItsHeightForTheRoute() {
        // 390dp 폭 기기의 목록 기본 상태: 지도 300dp 중 시트가 아래 190dp를 덮어 보이는 띠는 110dp.
        val f = frame(top = 0f, bottom = 190f, width = 384f, height = 300f)
        val visible = 110f
        assertTrue("fit height ${f.fitHeight(300f)} must be at least 45% of the visible strip", f.fitHeight(300f) >= visible * 0.45f)
        // 출발·도착 표식은 좌표 위로 솟으므로 위쪽에 표식 높이만큼, 좌우에는 표식 절반 폭만큼 남는다.
        assertTrue(f.top + f.inset >= marker)
        assertTrue(f.inset >= minInset)
        assertEquals(190f, f.bottom, 0.01f)
    }

    @Test fun roomyMapKeepsDefaultInsetAndPadding() {
        // 미리보기(위 여백 = 지도 맞춤 행동 아래끝)와 입력 지도처럼 넉넉한 영역은 지금과 같다.
        assertEquals(FitFrame(64f, 120f, inset), frame(top = 64f, bottom = 120f, width = 384f, height = 760f))
        assertEquals(FitFrame(0f, 0f, inset), frame(top = 0f, bottom = 0f, width = 384f, height = 600f))
    }

    @Test fun markerRoomIsAddedBelowAnExistingTopPadding() {
        // 가로 입력 화면처럼 헤더가 지도 위를 덮는 경우: 표식은 헤더 아래끝에서 표식 높이만큼 내려와야 보인다.
        val f = frame(top = 250f, bottom = 0f, width = 844f, height = 390f)
        assertTrue("marker must not hide under the header", f.top + f.inset >= 250f + marker)
        assertTrue(f.inset >= minInset)
    }

    @Test fun insetNeverGrowsBeyondDefaultOrShrinksBelowMarkerRoom() {
        for (visible in listOf(81f, 110f, 155f, 200f, 400f)) {
            val f = frame(top = 0f, bottom = 0f, width = 384f, height = visible)
            assertTrue(f.inset in minInset..inset)
            assertTrue(f.top + f.inset >= marker)
        }
    }
}
