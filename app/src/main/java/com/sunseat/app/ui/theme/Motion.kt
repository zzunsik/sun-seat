package com.sunseat.app.ui.theme

import androidx.compose.animation.core.FiniteAnimationSpec
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.ui.unit.IntOffset

/** 움직임 토큰. docs/DESIGN.md 7 */
object Motion {
    /** About 30fps; 33ms avoids waiting a third 60Hz vsync for a 34ms threshold. */
    const val SIMULATION_FRAME_MS = 33L
    /** 결과 카드 순차 등장 간격(ms) */
    const val STAGGER_STEP_MS = 70

    /** 숫자 카운트 올라가는 시간(ms) */
    const val COUNT_UP_MS = 900

    /** 타임라인 막대 채움 */
    const val TIMELINE_FILL_MS = 700
    const val TIMELINE_DELAY_MS = 150

    /** 카드가 올라오는 거리: 카드 높이의 1/n */
    const val ENTER_SLIDE_DIVISOR = 6

    fun enterFade(index: Int): FiniteAnimationSpec<Float> = tween(300, delayMillis = index * STAGGER_STEP_MS)

    fun enterSlide(): FiniteAnimationSpec<IntOffset> =
        spring(dampingRatio = Spring.DampingRatioLowBouncy, stiffness = Spring.StiffnessMediumLow)
}
