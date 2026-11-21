package com.sunseat.app.ui.map

import kotlin.random.Random
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** #186: 화면 밖 도착지 방향 표시의 자리(순수 함수). 값은 px. */
class OffscreenCueTest {
    // 지도 1170×606px 중 위 252px를 정보 카드가 덮는다.
    private val visible = ScreenBox(0f, 252f, 1170f, 603f)
    private val inset = 90f

    @Test fun noCueWhileTheDestinationIsVisible() {
        assertNull(offscreenCue(585f, 400f, visible, inset))
        assertNull(offscreenCue(10f, 260f, visible, inset)) // 가장자리 근처라도 보이면 표식이 직접 보인다
        assertNull(offscreenCue(Float.NaN, 400f, visible, inset))
    }

    @Test fun cueSitsOnTheInsetEdgeTowardTheDestination() {
        val up = offscreenCue(585f, -5000f, visible, inset)!!
        assertEquals(CueDirection.UP, up.direction)
        assertEquals(visible.top + inset, up.y, 0.5f)
        assertEquals(585f, up.x, 0.5f)
        val right = offscreenCue(5000f, 427.5f, visible, inset)!!
        assertEquals(CueDirection.RIGHT, right.direction)
        assertEquals(visible.right - inset, right.x, 0.5f)
        // 정보 카드 밑(위쪽)으로 숨은 도착지도 보이는 영역 위 가장자리에 표시한다.
        val underCard = offscreenCue(200f, 100f, visible, inset)!!
        assertTrue(underCard.y >= visible.top + inset - 0.5f)
        assertEquals(CueDirection.UP_LEFT, offscreenCue(-5000f, -5000f, visible, inset)!!.direction)
    }

    @Test fun cueStaysInsideTheVisibleAreaForAnyOffscreenPoint() {
        val random = Random(186)
        repeat(1000) {
            val x = random.nextDouble(-20000.0, 20000.0).toFloat()
            val y = random.nextDouble(-20000.0, 20000.0).toFloat()
            val cue = offscreenCue(x, y, visible, inset) ?: return@repeat
            assertTrue("$cue", cue.x in (visible.left + inset - 0.5f)..(visible.right - inset + 0.5f))
            assertTrue("$cue", cue.y in (visible.top + inset - 0.5f)..(visible.bottom - inset + 0.5f))
            // 가운데에서 표시로 가는 쪽이 가운데에서 도착지로 가는 쪽과 같다.
            val cx = (visible.left + visible.right) / 2; val cy = (visible.top + visible.bottom) / 2
            assertTrue((cue.x - cx) * (x - cx) >= -0.5f && (cue.y - cy) * (y - cy) >= -0.5f)
        }
    }

    @Test fun lowStripMovesTheCueAwayFromTheCentre() {
        // 보이는 높이가 낮으면 위 가장자리 표시가 가운데(차량) 가까이에 온다. keepClear만큼 가장자리를 따라 비킨다.
        val strip = ScreenBox(0f, 252f, 1170f, 603f)
        val cue = offscreenCue(585f, -5000f, strip, 60f, keepClear = 150f)!!
        val cx = 585f; val cy = (252f + 603f) / 2
        assertTrue(kotlin.math.abs(cue.x - cx) >= 150f - 0.5f || kotlin.math.abs(cue.y - cy) >= 150f - 0.5f)
        // 가장자리의 모서리(안쪽 여백만큼 안)로 간다.
        assertEquals(1170f - 60f, cue.x, 0.5f)
        assertEquals(CueDirection.UP, cue.direction)
        assertEquals(252f + 60f, cue.y, 0.5f)
    }

    @Test fun tooSmallVisibleAreaShowsNothing() {
        assertNull(offscreenCue(0f, -5000f, ScreenBox(0f, 0f, 100f, 100f), 60f))
    }

    @Test fun eightDirections() {
        assertEquals(CueDirection.RIGHT, direction(0f))
        assertEquals(CueDirection.DOWN, direction(90f))
        assertEquals(CueDirection.LEFT, direction(180f))
        assertEquals(CueDirection.UP, direction(-90f))
        assertEquals(CueDirection.UP_RIGHT, direction(-45f))
        assertEquals(CueDirection.DOWN_LEFT, direction(135f))
        assertEquals(CueDirection.RIGHT, direction(22f))
        assertEquals(CueDirection.DOWN_RIGHT, direction(23f))
        assertNotNull(direction(-720f))
    }
}
