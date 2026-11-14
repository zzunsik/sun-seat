package com.sunseat.app.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.size
import androidx.compose.material3.ButtonGroupDefaults
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.ToggleButton
import androidx.compose.material3.ToggleButtonDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
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
    Column(modifier, verticalArrangement = Arrangement.spacedBy(Spacing.s)) {
        Text(label, style = MaterialTheme.typography.labelMedium, color = scheme.onSurface)
        Row(horizontalArrangement = Arrangement.spacedBy(ButtonGroupDefaults.ConnectedSpaceBetween)) {
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
                    modifier = Modifier.weight(1f).heightIn(min = Spacing.touch).semantics { role = Role.RadioButton },
                ) {
                    val icon = if (checked) painterResource(R.drawable.ic_ms_check) else o.icon
                    if (icon != null) {
                        Icon(icon, contentDescription = null, modifier = Modifier.size(Sizes.iconSmall))
                        Spacer(Modifier.size(Spacing.xs))
                    }
                    Text(o.label, style = MaterialTheme.typography.labelLarge)
                }
            }
        }
    }
}
