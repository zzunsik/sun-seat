package com.sunseat.app.ui.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.RadioButtonDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import com.sunseat.app.ui.components.ChoiceGroup
import com.sunseat.app.ui.components.ChoiceOption
import com.sunseat.app.ui.components.choiceWidths
import com.sunseat.app.ui.components.connectedWidth
import com.sunseat.app.ui.theme.Sizes
import com.sunseat.app.ui.theme.Spacing
import com.sunseat.app.ui.theme.SunSeatTheme
import com.sunseat.app.ui.theme.rememberSunSeatHaptics

/**
 * 설정 전용 선택지(#104, docs/DESIGN.md 4.4). 모든 선택지가 측정 폭에 한 줄로 들어가면 연결형 버튼([ChoiceGroup]),
 * 들어가지 않으면 가로로 숨기지 않고 세로 라디오 목록으로 바꾼다. 공통 [ChoiceGroup] 동작은 바꾸지 않는다.
 *
 * [requiredWidth]는 화면의 모든 설정 선택지 중 가장 넓은 연결형 묶음 폭([settingsChoiceWidth])이다.
 * 한 화면에서 선택지마다 다른 조작 모양이 섞이지 않도록 모두 같은 기준으로 고른다.
 */
@Composable
internal fun SettingsChoice(label: String, options: List<ChoiceOption>, selected: Int, onSelect: (Int) -> Unit, requiredWidth: Dp, modifier: Modifier = Modifier) {
    BoxWithConstraints(modifier.fillMaxWidth()) {
        if (requiredWidth <= maxWidth) {
            ChoiceGroup(label, options, selected, onSelect)
        } else {
            Column(verticalArrangement = Arrangement.spacedBy(Spacing.s)) {
                Text(label, style = MaterialTheme.typography.labelMedium, color = SunSeatTheme.scheme.onSurface, modifier = Modifier.semantics { heading() })
                SettingsRadioList(options.map { it.label }, selected, onSelect)
            }
        }
    }
}

/** 연결형 버튼 묶음 하나가 가로 탐색 없이 한 줄에 놓이는 데 필요한 폭. */
@Composable
internal fun settingsChoiceWidth(options: List<ChoiceOption>): Dp = connectedWidth(choiceWidths(options))

/** 설정의 세로 라디오 목록. 행은 [Sizes.listRowInteractive] 이상이고 선택 상태를 semantics selected로 알린다. */
@Composable
internal fun SettingsRadioList(labels: List<String>, selected: Int, onSelect: (Int) -> Unit, modifier: Modifier = Modifier) {
    val haptics = rememberSunSeatHaptics()
    val scheme = SunSeatTheme.scheme
    Column(modifier.fillMaxWidth().selectableGroup()) {
        labels.forEachIndexed { index, label ->
            val checked = index == selected
            Row(
                Modifier.fillMaxWidth().heightIn(min = Sizes.listRowInteractive)
                    .selectable(selected = checked, role = Role.RadioButton, onClick = {
                        if (!checked) { haptics.selectionChanged(); onSelect(index) }
                    })
                    .padding(vertical = Spacing.s),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(Spacing.m),
            ) {
                RadioButton(checked, onClick = null, colors = RadioButtonDefaults.colors(selectedColor = scheme.onSurface, unselectedColor = scheme.onSurfaceVariant))
                Text(label, color = scheme.onSurface, style = MaterialTheme.typography.bodyLarge)
            }
        }
    }
}
