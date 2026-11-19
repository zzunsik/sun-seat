package com.sunseat.app.ui.theme

import androidx.compose.ui.graphics.Color

/**
 * 지도 위 경로선 색(고정). 지도 타일은 앱 화면 모드와 관계없이 밝으므로 밝음/어두움에서 같은 값을 쓴다.
 * docs/DESIGN.md 2.5·2.6·11.1 (#100). 본문 잉크·해/그늘 의미 색을 경로선에 재사용하지 않는다.
 */
object MapRouteColors {
    /** 버스·자가용·택시, 색을 모르는 지하철 노선, 출발·도착 표식 */
    val transit = Color(0xFF0B6E75)
    /** 선택 경로 양쪽 흰 테두리(지도 지물과 분리) */
    val casing = Color(0xFFFFFFFF)
    /** 도보 점선 */
    val walk = Color(0xFF4A5666)
    /** 목록 개요의 다른 후보(선택 미리보기에서는 그리지 않음, #99) */
    val alternate = Color(0xFF7A8696)
    /** 흰 바탕 대비 3:1 미만 노선 색의 테두리(흰 테두리 대신) */
    val outlineLowContrast = Color(0xFF4A5666)
    /** 시뮬레이션에서 지나온 구간 */
    val passed = Color(0xFF7A8696)
}
