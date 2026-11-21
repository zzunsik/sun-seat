package com.sunseat.app.ui.components

import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.HorizontalDivider
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInParent
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.SemanticsPropertyKey
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.platform.LocalDensity
import com.sunseat.app.ui.theme.Sizes
import com.sunseat.app.ui.theme.SunSeatTheme

/*
 * 겹치는 부품(오버레이)과 스크롤 경계 표시 공통 틀(#172, docs/bug-analysis-2026-11.md 4.3, DESIGN 4.5).
 *
 * 지도·본문 위에 떠 있는 부품(정보 카드, 떠 있는 행동 등)은 [overlay]로 자기 자리를 알린다.
 * - 화면은 측정한 아래끝(부모 기준 dp)을 받아 지도 여백·본문 여백에 쓴다. 고정 dp는 측정 전 첫 프레임 어림으로만 쓴다.
 * - 테스트는 [OverlayKey]가 붙은 노드를 찾아 "글자·지도 표식이 오버레이에 깔리지 않음"을 공통으로 검사한다.
 */

/** 다른 내용 위에 떠 있는 부품 표시(테스트 관측용). 값은 부품 이름. */
val OverlayKey = SemanticsPropertyKey<String>("Overlay")

/** 스크롤 영역 아래 경계에 "더 있음" 표시가 있다(테스트 관측용). 값은 스크롤 영역 테스트 태그. */
val ScrollEdgeKey = SemanticsPropertyKey<String>("ScrollEdge")

/**
 * 이 부품을 오버레이로 등록한다. [onPlaced]는 부모 레이아웃 안에서 이 부품의 위끝·아래끝(dp)을 받는다.
 * 부모의 같은 자리를 함께 쓰는 지도·본문은 이 값으로 여백을 정한다.
 */
@Composable
fun Modifier.overlay(name: String, onPlaced: (top: Dp, bottom: Dp) -> Unit = { _, _ -> }): Modifier {
    val density = LocalDensity.current
    return this.semantics { this[OverlayKey] = name }
        .onGloballyPositioned { with(density) { val y = it.positionInParent().y; onPlaced(y.toDp(), (y + it.size.height).toDp()) } }
}

/**
 * 고정 패널(조작 막대 등) 위에서 끝나는 스크롤 본문의 아래 경계 "더 있음" 표시(DESIGN 4.5).
 * 아래로 더 내릴 내용이 있을 때만 경계에 `outlineVariant` 구분선을 둔다. 장식 그라데이션·흐림은 쓰지 않는다(DESIGN 8).
 * 끝까지 내리면 사라진다. 구분선은 스크롤 영역 바로 아래(패널 위끝)에 둔다.
 */
@Composable
fun ScrollEdgeCue(scroll: ScrollState, scrollTag: String, modifier: Modifier = Modifier) {
    if (scroll.canScrollForward) HorizontalDivider(modifier.fillMaxWidth().testTag("${scrollTag}_more").semantics { this[ScrollEdgeKey] = scrollTag },
        thickness = Sizes.hairline, color = SunSeatTheme.scheme.outlineVariant)
}

/** 측정 전 첫 프레임에는 [estimate], 측정 뒤에는 [measured]를 쓴다. */
fun measuredOr(measured: Dp, estimate: Dp): Dp = if (measured > 0.dp) measured else estimate

/**
 * 글 묶음 + 행동 하나 줄(DESIGN 4.4 재배치 우선). 글의 한 줄 폭(최대 고유 폭)과 행동이 한 줄에 들어가면 나란히 두고,
 * 들어가지 않으면 행동을 글 아래 오른쪽으로 내린다. 좁은 화면·큰 글자에서 글이 단어 중간에서 꺾이지 않게 글에 가로 전체를 준다.
 */
@Composable
fun ReflowRow(text: @Composable () -> Unit, action: @Composable () -> Unit, modifier: Modifier = Modifier, gap: Dp = com.sunseat.app.ui.theme.Spacing.s) {
    androidx.compose.ui.layout.Layout(contents = listOf(text, action), modifier = modifier) { (texts, actions), constraints ->
        val width = constraints.maxWidth
        val gapPx = gap.roundToPx()
        val actionPlaceable = actions.first().measure(constraints.copy(minWidth = 0, minHeight = 0))
        val textMeasurable = texts.first()
        val oneLine = textMeasurable.maxIntrinsicWidth(androidx.compose.ui.unit.Constraints.Infinity)
        val sideBySide = oneLine + gapPx + actionPlaceable.width <= width
        if (sideBySide) {
            val textPlaceable = textMeasurable.measure(constraints.copy(minWidth = 0, maxWidth = (width - gapPx - actionPlaceable.width).coerceAtLeast(0), minHeight = 0))
            val height = maxOf(textPlaceable.height, actionPlaceable.height)
            layout(width, height) {
                textPlaceable.placeRelative(0, (height - textPlaceable.height) / 2)
                actionPlaceable.placeRelative(width - actionPlaceable.width, (height - actionPlaceable.height) / 2)
            }
        } else {
            val textPlaceable = textMeasurable.measure(constraints.copy(minWidth = 0, minHeight = 0))
            layout(width, textPlaceable.height + actionPlaceable.height) {
                textPlaceable.placeRelative(0, 0)
                actionPlaceable.placeRelative(width - actionPlaceable.width, textPlaceable.height)
            }
        }
    }
}
