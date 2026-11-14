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

    /** 떠 있는 캡슐 그림자와 얇은 테두리 */
    val capsuleElevation = 8.dp
    val hairline = 1.dp

    /** 알약 안쪽 세로 여백과 아이콘-글자 간격 */
    val pillVertical = 6.dp
    val pillGap = 6.dp

    /** 로딩 해 애니메이션 크기 */
    val loader = 48.dp

    /** 뼈대 로딩 줄 */
    val skeletonLine = 14.dp
    val skeletonWide = 220.dp
    val skeletonMedium = 160.dp
    val skeletonNarrow = 96.dp
    val skeletonTitle = 28.dp
    val skeletonChip = 72.dp
    val skeletonChipHeight = 30.dp

    /** 지하철 노선 색 점 */
    val lineDot = 8.dp

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
