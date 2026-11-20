package com.sunseat.app.ui.components

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.remember
import androidx.compose.ui.layout.onPlaced
import androidx.compose.ui.layout.positionInParent
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import com.sunseat.app.ui.theme.Sizes
import com.sunseat.app.ui.theme.Spacing
import com.sunseat.app.ui.theme.SunSeatTheme
import com.sunseat.app.ui.theme.rememberSunSeatHaptics

/** 범주 탭 하나: 보이는 이름·개수와 TalkBack이 읽을 설명. */
data class CategoryTab(val label: String, val count: Int, val description: String)

/**
 * 같은 목록의 상호 배타 범주 탭(DESIGN 4.1 `CategoryTabs`, 12.2). 하나만 고르며 다시 눌러도 선택이 풀리지 않는다.
 * 선택 표시는 색만이 아니라 3dp 밑줄 + 굵은 글자이고, semantics는 Role.Tab + selected다.
 * 탭은 높이 48 이상이고 측정 폭에 모두 들어가면 고르게 나누며, 안 들어갈 때만 가로로 넘긴다(DESIGN 4.4).
 * 이름과 개수는 줄이거나 자르지 않는다.
 */
@Composable
fun CategoryTabs(tabs: List<CategoryTab>, selected: Int, modifier: Modifier = Modifier, onSelect: (Int) -> Unit) {
    val scheme = SunSeatTheme.scheme
    val haptics = rememberSunSeatHaptics()
    val scroll = rememberScrollState()
    val style = MaterialTheme.typography.titleSmall
    Column(modifier.fillMaxWidth()) {
        BoxWithConstraints(Modifier.fillMaxWidth()) {
            // 넘칠 때도 다음 탭이 일부 보이도록 양 끝에 여백을 둔다.
            val edge = Spacing.s
            val equal = (maxWidth - edge * 2) / tabs.size.coerceAtLeast(1)
            // 탭마다 (시작 x, 폭) px. 고른 탭 전체와 끝 여백이 보이도록 애니메이션 없이 바로 옮긴다(스크린샷도 같은 위치).
            val placed = remember { mutableStateMapOf<Int, Pair<Int, Int>>() }
            val viewport = constraints.maxWidth
            val edgePx = with(LocalDensity.current) { edge.roundToPx() }
            val target = placed[selected]
            LaunchedEffect(selected, target, viewport) {
                val (start, width) = target ?: return@LaunchedEffect
                val end = start + width + edgePx
                when {
                    end > scroll.value + viewport -> scroll.scrollTo(end - viewport)
                    start - edgePx < scroll.value -> scroll.scrollTo((start - edgePx).coerceAtLeast(0))
                }
            }
            Row(Modifier.fillMaxWidth().horizontalScroll(scroll).selectableGroup()) {
                // 끝 여백을 자식으로 두어 탭 위치(positionInParent)가 스크롤 내용 좌표와 같게 한다.
                androidx.compose.foundation.layout.Spacer(Modifier.width(edge))
                tabs.forEachIndexed { index, tab ->
                    val isSelected = index == selected
                    val color = if (isSelected) scheme.onSurface else scheme.onSurfaceVariant
                    Box(
                        Modifier.widthIn(min = equal).heightIn(min = Sizes.tabHeight)
                            .onPlaced { placed[index] = it.positionInParent().x.toInt() to it.size.width }
                            .testTag("category_tab_$index")
                            .selectable(isSelected, role = Role.Tab) {
                                if (!isSelected) { haptics.selectionChanged(); onSelect(index) }
                            }
                            .semantics { contentDescription = tab.description }
                            .drawBehind {
                                if (isSelected) {
                                    val height = Sizes.tabIndicator.toPx()
                                    drawRect(scheme.onSurface, Offset(0f, size.height - height), Size(size.width, height))
                                }
                            }
                            .padding(horizontal = Spacing.m),
                        contentAlignment = Alignment.Center,
                    ) {
                        Row(horizontalArrangement = Arrangement.spacedBy(Spacing.xs), verticalAlignment = Alignment.CenterVertically) {
                            val weight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal
                            Text(tab.label, style = style, fontWeight = weight, color = color, maxLines = 1, softWrap = false)
                            Text(tab.count.toString(), style = style, fontWeight = weight, color = color, maxLines = 1, softWrap = false)
                        }
                    }
                }
                androidx.compose.foundation.layout.Spacer(Modifier.width(edge))
            }
        }
        HorizontalDivider(thickness = Sizes.hairline, color = scheme.outlineVariant)
    }
}
