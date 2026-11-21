package com.sunseat.app.ui

import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.semantics.SemanticsNode
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.junit4.ComposeTestRule
import androidx.compose.ui.test.onRoot
import com.sunseat.app.ui.components.OverlayKey
import com.sunseat.app.ui.components.ScrollEdgeKey
import com.sunseat.app.ui.map.MapMarkBoxesKey
import org.junit.Assert.fail

/*
 * 화면 불변식 공통 검사(#172, docs/bug-analysis-2026-11.md 4.3·5.1).
 * - 글자가 오버레이(떠 있는 부품, `Modifier.overlay`)에 깔리지 않는다.
 * - 테스트 지도가 그린 보이는 표식 상자가 오버레이에 깔리지 않는다.
 * - 패널에서 끝나는 스크롤 본문([edgeScrollTags])에서 글자가 경계에 걸려 잘리면 "더 있음" 표시가 있다(DESIGN 4.5).
 */
// [floatingOverScroll]: 스크롤 본문 위에 떠 있어 본문이 그 밑으로 지나가는 캡슐(결과 화면 아래 행동 바 등). 쉬는 상태 검사에서는 빼고,
// 본문을 끝까지 내린 뒤 다시 검사해 마지막 내용이 캡슐 위에서 끝나는지 본다.

private fun SemanticsNode.allDescendants(): List<SemanticsNode> = children.flatMap { listOf(it) + it.allDescendants() }

private fun Rect.overlapArea(other: Rect): Float {
    val i = intersect(other)
    return if (i.width > 0f && i.height > 0f) i.width * i.height else 0f
}

/** 1px 미만 겹침(반올림)은 무시한다. */
private const val MIN_OVERLAP_PX2 = 1f

fun ComposeTestRule.assertNoTextUnderOverlays(edgeScrollTags: List<String> = emptyList(), floatingOverScroll: Set<String> = emptySet()) {
    val root = onRoot(useUnmergedTree = true).fetchSemanticsNode()
    val nodes = listOf(root) + root.allDescendants()
    val overlays = nodes.filter { it.config.getOrNull(OverlayKey).let { name -> name != null && name !in floatingOverScroll } }
    val problems = mutableListOf<String>()
    for (overlay in overlays) {
        val name = overlay.config[OverlayKey]
        val inside = overlay.allDescendants().map { it.id }.toSet() + overlay.id
        val box = overlay.boundsInRoot
        // 글자 노드(오버레이 자신의 글자는 제외)
        nodes.filter { it.id !in inside && it.config.getOrNull(SemanticsProperties.Text) != null }.forEach { text ->
            if (text.boundsInRoot.overlapArea(box) > MIN_OVERLAP_PX2) problems += "text '${text.config[SemanticsProperties.Text].joinToString()}' ${text.boundsInRoot} under overlay '$name' $box"
        }
        // 누를 수 있는 노드(아이콘만 있는 버튼 포함)도 오버레이에 가리지 않는다.
        nodes.filter { it.id !in inside && it.config.getOrNull(SemanticsProperties.Text) == null && it.config.getOrNull(androidx.compose.ui.semantics.SemanticsActions.OnClick) != null }.forEach { node ->
            if (node.boundsInRoot.overlapArea(box) > MIN_OVERLAP_PX2) problems += "clickable ${node.config.getOrNull(SemanticsProperties.ContentDescription)?.joinToString() ?: node.config.getOrNull(SemanticsProperties.TestTag) ?: node.id} ${node.boundsInRoot} under overlay '$name' $box"
        }
        // 테스트 지도의 보이는 표식(지도 뷰 좌표 → 화면 좌표)
        nodes.filter { it.id !in inside && it.config.getOrNull(MapMarkBoxesKey) != null }.forEach { map ->
            val origin = map.boundsInRoot.topLeft
            map.config[MapMarkBoxesKey].forEach { (kind, b) ->
                val mark = Rect(origin.x + b.left, origin.y + b.top, origin.x + b.right, origin.y + b.bottom)
                if (mark.overlapArea(box) > MIN_OVERLAP_PX2) problems += "map mark $kind $mark under overlay '$name' $box"
            }
        }
    }
    for (tag in edgeScrollTags) {
        val area = nodes.firstOrNull { it.config.getOrNull(SemanticsProperties.TestTag) == tag } ?: continue
        val viewport = area.boundsInRoot
        val clipped = area.allDescendants().any { node ->
            node.config.getOrNull(SemanticsProperties.Text) != null &&
                node.unclippedBoundsInRoot().let { it.top < viewport.bottom - 0.5f && it.bottom > viewport.bottom + 0.5f }
        }
        // 표시는 스크롤 영역 아래끝에 있어야 한다.
        val marked = nodes.any { it.config.getOrNull(ScrollEdgeKey) == tag && kotlin.math.abs(it.boundsInRoot.bottom - viewport.bottom) <= 2f }
        if (clipped && !marked) problems += "text is cut at the bottom of '$tag' $viewport without a scroll-edge cue"
    }
    if (problems.isNotEmpty()) fail("overlay invariants:\n" + problems.joinToString("\n"))
}

/** 조상 잘림을 무시한 화면 상자. */
private fun SemanticsNode.unclippedBoundsInRoot(): Rect = layoutInfo.coordinates.let { c ->
    val topLeft = c.localToRoot(androidx.compose.ui.geometry.Offset.Zero)
    Rect(topLeft.x, topLeft.y, topLeft.x + c.size.width, topLeft.y + c.size.height)
}
