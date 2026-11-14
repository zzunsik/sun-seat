package com.sunseat.app.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Shapes
import androidx.compose.ui.unit.dp

/** 간격 토큰. docs/DESIGN.md 4 */
object Spacing {
    val xs = 4.dp
    val s = 8.dp
    val m = 12.dp
    val l = 16.dp
    val xl = 24.dp
    val xxl = 32.dp

    /** 화면 좌우 여백 */
    val screen = 16.dp

    /** 최소 터치 영역 */
    val touch = 48.dp
}

/** 부품 크기 토큰. docs/DESIGN.md 4 */
object Sizes {
    /** 상단 떠 있는 캡슐 아래로 내용이 시작하는 여백 */
    val topCapsuleClearance = 80.dp

    /** 하단 떠 있는 캡슐 위로 스크롤이 끝나는 여백 */
    val bottomCapsuleClearance = 112.dp

    val iconSmall = 18.dp
    val iconMedium = 20.dp
    val iconLarge = 32.dp

    /** 목록 한 줄 최소 높이 */
    val listRow = 44.dp

    /** 타임라인 줄 라벨 폭과 막대 높이 */
    val timelineLabel = 56.dp
    val timelineBar = 28.dp
    val timelineBarRadius = 6.dp

    /** 해·좌석 그림 */
    val diagramHeight = 148.dp
    val vehicleWidth = 96.dp
}

internal val SunSeatShapes = Shapes(
    extraSmall = RoundedCornerShape(6.dp),
    small = RoundedCornerShape(8.dp),
    medium = RoundedCornerShape(12.dp),
    large = RoundedCornerShape(16.dp),
    extraLarge = RoundedCornerShape(24.dp),
)

/** 떠 있는 컨트롤과 알약 */
val CapsuleShape = RoundedCornerShape(percent = 50)
