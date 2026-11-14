package com.sunseat.app.ui.theme

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.hapticfeedback.HapticFeedback
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback

/** 햅틱. 사용자가 직접 조작한 순간에만 쓴다. docs/DESIGN.md 7 */
class SunSeatHaptics internal constructor(private val haptic: HapticFeedback) {
    /** 주 행동 버튼 누름 */
    fun press() = haptic.performHapticFeedback(HapticFeedbackType.Confirm)

    /** 좌·우, 수단, 모드 전환 */
    fun selectionChanged() = haptic.performHapticFeedback(HapticFeedbackType.SegmentTick)
}

@Composable
fun rememberSunSeatHaptics(): SunSeatHaptics {
    val haptic = LocalHapticFeedback.current
    return remember(haptic) { SunSeatHaptics(haptic) }
}
