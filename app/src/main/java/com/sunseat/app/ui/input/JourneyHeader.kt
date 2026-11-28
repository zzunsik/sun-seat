package com.sunseat.app.ui.input

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import com.sunseat.app.R
import com.sunseat.app.domain.SunGoal
import com.sunseat.app.domain.TravelMode
import com.sunseat.app.ui.components.QuietButton
import com.sunseat.app.ui.icons.SunSeatIcons
import com.sunseat.app.ui.theme.rememberSunSeatHaptics
import com.sunseat.app.ui.theme.CapsuleShape
import com.sunseat.app.ui.theme.Sizes
import com.sunseat.app.ui.theme.Spacing
import com.sunseat.app.ui.theme.SunSeatTheme
import java.time.LocalDateTime

/** 펼친 여정 헤더 한 표면(DESIGN 5.1). 입력·후보 목록이 같은 태그를 쓴다. */
const val JOURNEY_HEADER_TAG = "journey_header"
/** 입력 화면 지도(테스트 관측용). */
const val INPUT_MAP_TAG = "input_map"

/** 헤더의 한 끝점 표시값. [value]가 없으면 자리표시 문구를 보인다. */
data class JourneyEndpointUi(val value: String?, val currentLocation: Boolean = false, val loading: Boolean = false)

/** 펼친 여정 헤더의 편집 행동. 설정은 입력 화면에만 있다. */
data class JourneyHeaderActions(
    val onEdit: (EndpointField) -> Unit = {},
    val onSwap: () -> Unit = {},
    val onMode: (TravelMode) -> Unit = {},
    val onTime: () -> Unit = {},
    val onGoal: (SunGoal) -> Unit = {},
    val onSettings: (() -> Unit)? = null,
)

/**
 * 펼친 여정 헤더(#103, DESIGN 5.1). 입력과 후보 목록이 같은 순서·위치로 여정을 읽고 고친다.
 * 출발 → 도착 → (맞바꾸기) → 수단 → 출발 시각 → 햇빛 목적. 맞바꾸기는 장소 행 오른쪽,
 * 설정(입력만)은 장소 행 바깥 오른쪽 위다. 표면(배경·모서리)은 화면이 정한다.
 * 시각·햇빛 목적은 공간이 부족하면 다음 줄로 흐르고 가로로 밀려 숨지 않는다.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun JourneyHeader(
    origin: JourneyEndpointUi,
    destination: JourneyEndpointUi,
    mode: TravelMode,
    departure: LocalDateTime?,
    goal: SunGoal,
    now: LocalDateTime,
    actions: JourneyHeaderActions,
    modifier: Modifier = Modifier,
    swapEnabled: Boolean = true,
    leading: (@Composable () -> Unit)? = null,
    showPurposeTiles: Boolean = false,
) {
    var showGoal by rememberSaveable { mutableStateOf(false) }
    Column(Modifier.testTag(JOURNEY_HEADER_TAG).then(modifier), verticalArrangement = Arrangement.spacedBy(Spacing.s)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            leading?.invoke()
            Column(Modifier.weight(1f)) {
                EndpointRow(
                    label = stringResource(R.string.input_origin),
                    value = if (origin.loading) stringResource(R.string.input_locating) else origin.value,
                    placeholder = stringResource(R.string.input_origin_placeholder),
                    iconRes = if (origin.currentLocation) R.drawable.ic_ms_my_location else R.drawable.ic_ms_search,
                    onClick = { actions.onEdit(EndpointField.ORIGIN) },
                )
                if (origin.loading) LinearProgressIndicator(Modifier.fillMaxWidth())
                HorizontalDivider(color = SunSeatTheme.scheme.outlineVariant)
                EndpointRow(
                    label = stringResource(R.string.input_destination),
                    value = destination.value,
                    placeholder = stringResource(R.string.input_destination_placeholder),
                    iconRes = if (destination.currentLocation) R.drawable.ic_ms_my_location else R.drawable.ic_ms_search,
                    onClick = { actions.onEdit(EndpointField.DESTINATION) },
                )
            }
            // 설정(입력만)은 장소 행 바깥 오른쪽 위(긴 이름에서도 위에 고정), 맞바꾸기는 그 아래. 설정이 없으면 맞바꾸기가 두 행 가운데에 온다.
            Column(Modifier.align(if (actions.onSettings != null) Alignment.Top else Alignment.CenterVertically)) {
                actions.onSettings?.let { onSettings ->
                    IconButton(onClick = onSettings) { Icon(painterResource(R.drawable.ic_ms_settings), stringResource(R.string.settings_title)) }
                }
                IconButton(onClick = actions.onSwap, enabled = swapEnabled) {
                    Icon(painterResource(R.drawable.ic_ms_swap_vert), stringResource(R.string.input_swap))
                }
            }
        }
        NeutralTabs(listOf(stringResource(R.string.input_mode_transit), stringResource(R.string.common_taxi), stringResource(R.string.common_car)), mode.ordinal) {
            actions.onMode(TravelMode.entries[it])
        }
        if (showPurposeTiles) {
            DepartureChip(scheduleLabel(departure, now), actions.onTime)
            PurposeTiles(goal, actions.onGoal)
        } else FlowRow(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(Spacing.s)) {
            QuietButton(onClick = actions.onTime) { Text(scheduleLabel(departure, now)); DropArrow() }
            QuietButton(onClick = { showGoal = true }) { Text(stringResource(goalChipLabel(goal))); DropArrow() }
        }
    }
    if (showGoal) GoalSheet(goal, onDismiss = { showGoal = false }) { chosen ->
        showGoal = false
        // 같은 목적을 다시 고르면 바꾸지 않는다(햇빛 재계산 없음).
        if (chosen != goal) actions.onGoal(chosen)
    }
}

/** 입력만 쓰는 시각 칩. 긴 날짜도 높이를 늘려 전체 라벨을 보여 준다. */
@Composable
private fun DepartureChip(label: String, onClick: () -> Unit) {
    Surface(
        onClick = onClick, shape = CapsuleShape,
        color = SunSeatTheme.scheme.surfaceContainerHigh,
        contentColor = SunSeatTheme.scheme.onSurface,
    ) {
        Row(Modifier.heightIn(min = Spacing.touch).padding(horizontal = Spacing.l, vertical = Spacing.s),
            verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Spacing.s)) {
            Icon(painterResource(R.drawable.ic_ms_schedule), null, Modifier.size(Sizes.iconMedium))
            Text(label, Modifier.weight(1f, fill = false), style = MaterialTheme.typography.labelLarge.copy(
                lineBreak = androidx.compose.ui.text.style.LineBreak(
                    strategy = androidx.compose.ui.text.style.LineBreak.Strategy.HighQuality,
                    strictness = androidx.compose.ui.text.style.LineBreak.Strictness.Normal,
                    wordBreak = androidx.compose.ui.text.style.LineBreak.WordBreak.Phrase,
                ),
                localeList = androidx.compose.ui.text.intl.LocaleList("ko-KR"),
            ))
        }
    }
}

/** 실제 라벨 폭에 아이콘·체크·여백을 더해 두 칸이 들어갈 때만 나란히 놓는다. */
@Composable
private fun PurposeTiles(goal: SunGoal, onSelect: (SunGoal) -> Unit) {
    val labels = SunGoal.entries.map { stringResource(goalChipLabel(it)) }
    val groupName = stringResource(R.string.input_goal_group)
    val haptics = rememberSunSeatHaptics()
    val density = LocalDensity.current
    val measurer = rememberTextMeasurer()
    val style = MaterialTheme.typography.labelLarge
    val tileWidth = labels.maxOf { with(density) { measurer.measure(it, style, softWrap = false).size.width.toDp() } } +
        Sizes.iconMedium + Sizes.iconSmall + Spacing.s * 2 + Spacing.m * 2
    BoxWithConstraints(Modifier.fillMaxWidth().selectableGroup().semantics { contentDescription = groupName }) {
        val horizontal = maxWidth >= tileWidth * 2 + Spacing.s
        @Composable fun Tile(option: SunGoal, modifier: Modifier) {
            val selected = option == goal
            val colors = SunSeatTheme.colors
            val foreground = if (!selected) SunSeatTheme.scheme.onSurface else
                if (option == SunGoal.AVOID) colors.onShadeContainer else colors.onSunContainer
            val background = if (!selected) SunSeatTheme.scheme.surfaceContainer else
                if (option == SunGoal.AVOID) colors.shadeContainer else colors.sunContainer
            Surface(
                modifier.selectable(selected, role = Role.RadioButton, onClick = { if (!selected) { haptics.selectionChanged(); onSelect(option) } }),
                shape = MaterialTheme.shapes.medium, color = background, contentColor = foreground,
                border = BorderStroke(if (selected) Sizes.tileSelectedBorder else Sizes.tileBorder,
                    if (selected) foreground else SunSeatTheme.scheme.outline),
            ) {
                Row(Modifier.heightIn(min = Sizes.purposeTile).padding(horizontal = Spacing.m, vertical = Spacing.s),
                    verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Spacing.s)) {
                    Icon(if (option == SunGoal.AVOID) SunSeatIcons.Shade else SunSeatIcons.Sun, null, Modifier.size(Sizes.iconMedium))
                    Text(labels[option.ordinal], Modifier.weight(1f), style = style, softWrap = false)
                    if (selected) Icon(painterResource(R.drawable.ic_ms_check), null, Modifier.size(Sizes.iconSmall))
                    else Spacer(Modifier.size(Sizes.iconSmall))
                }
            }
        }
        if (horizontal) Row(horizontalArrangement = Arrangement.spacedBy(Spacing.s)) {
            SunGoal.entries.forEach { Tile(it, Modifier.weight(1f)) }
        } else Column(verticalArrangement = Arrangement.spacedBy(Spacing.s)) {
            SunGoal.entries.forEach { Tile(it, Modifier.fillMaxWidth()) }
        }
    }
}

internal fun goalChipLabel(goal: SunGoal): Int = if (goal == SunGoal.AVOID) R.string.input_goal_chip_avoid else R.string.input_goal_chip_seek

@Composable private fun DropArrow() {
    Icon(painterResource(R.drawable.ic_ms_chevron_right), null, Modifier.size(Sizes.iconSmall).rotate(90f))
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable private fun GoalSheet(goal: SunGoal, onDismiss: () -> Unit, onSelect: (SunGoal) -> Unit) {
    androidx.compose.material3.ModalBottomSheet(onDismissRequest = onDismiss, containerColor = SunSeatTheme.scheme.surface) {
        Column(Modifier.fillMaxWidth().padding(Spacing.screen), verticalArrangement = Arrangement.spacedBy(Spacing.s)) {
            Text(stringResource(R.string.common_sun), style = MaterialTheme.typography.titleLarge)
            SunGoal.entries.forEach { option -> NeutralFilterChip(option == goal, stringResource(if (option == SunGoal.AVOID) R.string.input_goal_avoid else R.string.input_goal_seek)) { onSelect(option) } }
            Spacer(Modifier.size(Spacing.l))
        }
    }
}

/**
 * 출발·도착 한 행. 펼친 헤더는 장소 이름 전체를 보인다(말줄임은 축약 여정 바에서만, DESIGN 4.4).
 */
@Composable
internal fun EndpointRow(label: String, value: String?, placeholder: String, iconRes: Int, onClick: () -> Unit) {
    val scheme = SunSeatTheme.scheme
    Row(
        Modifier
            .fillMaxWidth()
            .heightIn(min = Spacing.touch + Spacing.m)
            .clickable(role = Role.Button, onClickLabel = placeholder, onClick = onClick)
            .padding(start = Spacing.s),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Spacing.m),
    ) {
        Icon(painterResource(iconRes), contentDescription = null, tint = scheme.onSurfaceVariant, modifier = Modifier.size(Sizes.iconMedium))
        Column(Modifier.weight(1f).padding(vertical = Spacing.xs)) {
            Text(label, style = MaterialTheme.typography.labelMedium, color = scheme.onSurfaceVariant)
            Text(
                value ?: placeholder,
                style = if (value == null) MaterialTheme.typography.bodyLarge else MaterialTheme.typography.titleMedium,
                color = if (value == null) scheme.onSurfaceVariant else scheme.onSurface,
            )
        }
    }
}

/** 수단 전체 라벨(대중교통·택시·자가용). 헤더·축약 여정 바·결과 캡슐이 같은 말을 쓴다. */
@Composable
fun travelModeLabel(mode: TravelMode): String = stringResource(when (mode) {
    TravelMode.TRANSIT -> R.string.input_mode_transit
    TravelMode.TAXI -> R.string.common_taxi
    TravelMode.CAR -> R.string.common_car
})

/**
 * 축약 여정 바의 글(DESIGN 5.1): 1줄 "출발 → 도착"(`titleMedium`, 2줄까지 줄바꿈 후 말줄임) + 2줄 조건(`bodySmall`).
 * 후보 완전 확장·미리보기의 상단 바와 결과의 상단 캡슐이 같이 쓴다.
 */
@Composable
fun JourneySummary(origin: String, destination: String, subtitle: String, modifier: Modifier = Modifier) {
    val scheme = SunSeatTheme.scheme
    Column(modifier, verticalArrangement = Arrangement.Center) {
        Text("$origin → $destination", style = MaterialTheme.typography.titleMedium, color = scheme.onSurface, maxLines = 2, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis)
        Text(subtitle, style = MaterialTheme.typography.bodySmall, color = scheme.onSurfaceVariant)
    }
}
