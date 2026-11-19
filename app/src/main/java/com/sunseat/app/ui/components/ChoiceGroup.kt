package com.sunseat.app.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.ButtonGroupDefaults
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.ToggleButton
import androidx.compose.material3.ToggleButtonDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import com.sunseat.app.R
import com.sunseat.app.ui.theme.Sizes
import com.sunseat.app.ui.theme.Spacing
import com.sunseat.app.ui.theme.SunSeatTheme
import com.sunseat.app.ui.theme.rememberSunSeatHaptics

/** 선택지 하나. [icon]은 선택되지 않았을 때만 보이고, 선택되면 체크 아이콘으로 바뀐다. */
data class ChoiceOption(val label: String, val icon: Painter? = null)

/**
 * 하나만 고르는 연결된 토글 버튼 묶음. docs/DESIGN.md 2.3
 * 선택 표시는 중립 잉크색 채움 + 체크 아이콘(해·그늘 색 미사용). 바꿀 때 선택 전환 햅틱.
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun ChoiceGroup(
    label: String,
    options: List<ChoiceOption>,
    selected: Int,
    onSelect: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    val scheme = SunSeatTheme.scheme
    val haptics = rememberSunSeatHaptics()
    val density = LocalDensity.current
    val style = MaterialTheme.typography.labelLarge
    val widths = choiceWidths(options)
    val scroll = rememberScrollState()
    Column(modifier, verticalArrangement = Arrangement.spacedBy(Spacing.s)) {
        Text(label, style = MaterialTheme.typography.labelMedium, color = scheme.onSurface)
        BoxWithConstraints(Modifier.fillMaxWidth()) {
        val gaps = ButtonGroupDefaults.ConnectedSpaceBetween * (options.size - 1).coerceAtLeast(0)
        val scrollable = connectedWidth(widths) > maxWidth
        val equal = (widths.maxOrNull() ?: 0.dp) * options.size + gaps <= maxWidth
        LaunchedEffect(selected, widths, maxWidth) {
            val offset = with(density) { (widths.take(selected).fold(0.dp) { sum, width -> sum + width } + ButtonGroupDefaults.ConnectedSpaceBetween * selected).roundToPx() }
            scroll.scrollTo(if (scrollable) offset else 0)
        }
        Row(Modifier.fillMaxWidth().then(if (scrollable) Modifier.horizontalScroll(scroll) else Modifier), horizontalArrangement = Arrangement.spacedBy(ButtonGroupDefaults.ConnectedSpaceBetween)) {
            options.forEachIndexed { i, o ->
                val checked = i == selected
                ToggleButton(
                    checked = checked,
                    onCheckedChange = {
                        if (!checked) {
                            haptics.selectionChanged()
                            onSelect(i)
                        }
                    },
                    shapes = when (i) {
                        0 -> ButtonGroupDefaults.connectedLeadingButtonShapes()
                        options.lastIndex -> ButtonGroupDefaults.connectedTrailingButtonShapes()
                        else -> ButtonGroupDefaults.connectedMiddleButtonShapes()
                    },
                    colors = ToggleButtonDefaults.colors(
                        containerColor = scheme.surfaceContainerHighest,
                        contentColor = scheme.onSurface,
                        checkedContainerColor = scheme.inverseSurface,
                        checkedContentColor = scheme.inverseOnSurface,
                    ),
                    contentPadding = PaddingValues(horizontal = Spacing.l),
                    modifier = (if (scrollable) Modifier.width(widths[i]) else Modifier.weight(if (equal) 1f else widths[i].value))
                        .heightIn(min = Spacing.touch).semantics { role = Role.RadioButton },
                ) {
                    val icon = if (checked) painterResource(R.drawable.ic_ms_check) else o.icon
                    if (icon != null) {
                        Icon(icon, contentDescription = null, modifier = Modifier.size(Sizes.iconSmall))
                        Spacer(Modifier.size(Spacing.xs))
                    }
                    Text(o.label, style = style, maxLines = 1, softWrap = false)
                }
            }
        }
        }
    }
}

/**
 * 연결형 버튼 하나가 라벨을 한 줄로 온전히 보이는 데 필요한 폭(#93).
 * 선택되지 않은 항목도 체크 자리를 확보해 선택이 라벨을 밀어내지 않게 한다.
 */
@Composable
internal fun choiceWidths(options: List<ChoiceOption>): List<Dp> {
    val density = LocalDensity.current
    val measurer = rememberTextMeasurer()
    val style = MaterialTheme.typography.labelLarge
    return options.map {
        (with(density) { measurer.measure(it.label, style, softWrap = false).size.width.toDp() } +
            Sizes.iconSmall + Spacing.xs + Spacing.l * 2).coerceAtLeast(Spacing.touch)
    }
}

/** 연결형 버튼 묶음 전체를 가로 탐색 없이 한 줄에 놓는 데 필요한 폭. */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
internal fun connectedWidth(widths: List<Dp>): Dp =
    widths.fold(0.dp) { sum, width -> sum + width } + ButtonGroupDefaults.ConnectedSpaceBetween * (widths.size - 1).coerceAtLeast(0)
