package com.sunseat.app.ui.theme

import androidx.compose.ui.graphics.Color

/**
 * 수도권 지하철 노선 공식 색. 노선 칩의 작은 점에만 쓴다(이름과 함께 표시, 색만으로 구분하지 않음).
 * 해·그늘 의미 색과는 별개다. docs/DESIGN.md 2.4
 */
object TransitLineColors {
    private val byName = linkedMapOf(
        "신분당" to Color(0xFFD4003B),
        "수인분당" to Color(0xFFF5A200),
        "경의중앙" to Color(0xFF77C4A3),
        "공항철도" to Color(0xFF0090D2),
        "1호선" to Color(0xFF0052A4),
        "2호선" to Color(0xFF00A84D),
        "3호선" to Color(0xFFEF7C1C),
        "4호선" to Color(0xFF00A5DE),
        "5호선" to Color(0xFF996CAC),
        "6호선" to Color(0xFFCD7C2F),
        "7호선" to Color(0xFF747F00),
        "8호선" to Color(0xFFE6186C),
        "9호선" to Color(0xFFBDB092),
    )

    /** 노선 이름에 맞는 색. 모르는 노선은 null(점을 그리지 않는다). */
    fun of(lineName: String): Color? = byName.entries.firstOrNull { lineName.contains(it.key) }?.value
}
