package com.sunseat.app.ui.candidates

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import com.sunseat.app.R
import com.sunseat.app.domain.Mode
import com.sunseat.app.domain.TravelMode
import com.sunseat.app.domain.TripRequest
import com.sunseat.app.ui.components.FloatingCapsule
import com.sunseat.app.ui.components.PrimaryButton
import com.sunseat.app.ui.components.QuietButton
import com.sunseat.app.ui.components.SunLoader
import com.sunseat.app.ui.formatDuration
import com.sunseat.app.ui.formatWon
import com.sunseat.app.ui.theme.CapsuleShape
import com.sunseat.app.ui.theme.Sizes
import com.sunseat.app.ui.theme.Spacing
import com.sunseat.app.ui.theme.SunSeatTheme
import com.sunseat.app.ui.theme.TransitLineColors
import com.sunseat.app.ui.theme.rememberSunSeatHaptics
import com.valentinilk.shimmer.shimmer
import java.time.format.DateTimeFormatter

data class CandidatesActions(
    val onBack: () -> Unit = {},
    val onSelect: (CandidateUi) -> Unit = {},
    val onRetry: () -> Unit = {},
    val onSearchByCar: () -> Unit = {},
)

private val ArrivalFormat = DateTimeFormatter.ofPattern("HH:mm")

/** 경로 후보 화면. 상태 없음(미리보기·스크린샷용). */
@Composable
fun CandidatesScreen(
    request: TripRequest,
    state: CandidatesState,
    actions: CandidatesActions,
    modifier: Modifier = Modifier,
    animateLoader: Boolean = true,
) {
    val scheme = SunSeatTheme.scheme
    val insets = WindowInsets.safeDrawing.asPaddingValues()
    Surface(modifier.fillMaxSize(), color = scheme.background, contentColor = scheme.onSurface) {
        Box(Modifier.fillMaxSize()) {
            val content = PaddingValues(
                start = Spacing.screen,
                end = Spacing.screen,
                top = insets.calculateTopPadding() + Sizes.topCapsuleClearance,
                bottom = insets.calculateBottomPadding() + Spacing.xl,
            )
            when (state) {
                is CandidatesState.Loading -> Loading(content, state.mode, animateLoader)
                is CandidatesState.Options -> OptionList(state.items, content, actions)
                is CandidatesState.NoRoute -> Message(
                    content = content,
                    iconRes = R.drawable.ic_ms_directions_bus,
                    title = stringResource(state.titleRes),
                    detail = state.detailRes?.let { stringResource(it) },
                    primary = if (state.suggestCar) stringResource(R.string.candidates_search_by_car) to actions.onSearchByCar else null,
                    secondary = stringResource(R.string.candidates_edit_input) to actions.onBack,
                )
                is CandidatesState.Error -> Message(
                    content = content,
                    iconRes = state.iconRes,
                    title = stringResource(state.messageRes),
                    detail = null,
                    primary = if (state.canRetry) stringResource(R.string.search_retry) to actions.onRetry else null,
                    secondary = stringResource(R.string.candidates_edit_input) to actions.onBack,
                )
            }

            FloatingCapsule(
                Modifier
                    .align(Alignment.TopCenter)
                    .windowInsetsPadding(WindowInsets.safeDrawing)
                    .padding(horizontal = Spacing.m, vertical = Spacing.s)
                    .fillMaxWidth(),
            ) {
                Row(Modifier.padding(Spacing.xs), verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = actions.onBack) {
                        Icon(painterResource(R.drawable.ic_ms_arrow_back), contentDescription = stringResource(R.string.common_back))
                    }
                    Column(Modifier.weight(1f)) {
                        Text(
                            "${request.origin.name} → ${request.destination.name}",
                            style = MaterialTheme.typography.titleMedium,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        Text(
                            stringResource(if (request.mode == TravelMode.TRANSIT) R.string.candidates_subtitle_transit else R.string.candidates_subtitle_car),
                            style = MaterialTheme.typography.bodySmall,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun Loading(content: PaddingValues, mode: TravelMode, animate: Boolean) {
    val scheme = SunSeatTheme.scheme
    Column(
        Modifier.fillMaxSize().padding(content),
        verticalArrangement = Arrangement.spacedBy(Spacing.m),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Spacing.s)) {
            SunLoader(description = stringResource(R.string.candidates_loading), playing = animate)
            Text(stringResource(R.string.candidates_loading), style = MaterialTheme.typography.titleMedium, color = scheme.onSurface)
        }
        // 자가용은 고를 목록이 없으므로 카드 뼈대를 보여 주지 않는다
        if (mode == TravelMode.TRANSIT) repeat(3) { SkeletonCard() }
    }
}

/** 실제 후보 카드와 같은 구조의 뼈대: 시간+메타 / 구간 칩 / 승하차. 반짝임은 막대에만. */
@Composable
private fun SkeletonCard() {
    val scheme = SunSeatTheme.scheme
    Surface(color = scheme.surface, shape = MaterialTheme.shapes.large, modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(Spacing.l).shimmer(), verticalArrangement = Arrangement.spacedBy(Spacing.m)) {
            Row(verticalAlignment = Alignment.Bottom) {
                Bar(Sizes.skeletonNarrow, Sizes.skeletonTitle)
                Spacer(Modifier.weight(1f))
                Bar(Sizes.skeletonMedium, Sizes.skeletonLine)
            }
            Row(horizontalArrangement = Arrangement.spacedBy(Spacing.s)) {
                repeat(3) { Bar(Sizes.skeletonChip, Sizes.skeletonChipHeight, CapsuleShape) }
            }
            Bar(Sizes.skeletonWide, Sizes.skeletonLine)
        }
    }
}

@Composable
private fun Bar(width: Dp, height: Dp, shape: androidx.compose.ui.graphics.Shape = MaterialTheme.shapes.extraSmall) {
    Box(Modifier.width(width).height(height).clip(shape).background(SunSeatTheme.scheme.surfaceContainerHighest))
}

@Composable
private fun OptionList(items: List<CandidateUi>, content: PaddingValues, actions: CandidatesActions) {
    val scheme = SunSeatTheme.scheme
    val haptics = rememberSunSeatHaptics()
    LazyColumn(Modifier.fillMaxSize(), contentPadding = content, verticalArrangement = Arrangement.spacedBy(Spacing.m)) {
        item {
            Column(verticalArrangement = Arrangement.spacedBy(Spacing.xs)) {
                Text(
                    stringResource(R.string.candidates_title),
                    style = MaterialTheme.typography.headlineSmall,
                    color = scheme.onSurface,
                    modifier = Modifier.semantics { heading() },
                )
                Text(
                    stringResource(
                        if (items.firstOrNull()?.arrival != null) R.string.candidates_note_now else R.string.candidates_note_now_with_time,
                        items.size,
                    ),
                    style = MaterialTheme.typography.bodySmall,
                    color = scheme.onSurfaceVariant,
                )
            }
        }
        items(items, key = { it.id }) { item ->
            CandidateCard(item) {
                haptics.press()
                actions.onSelect(item)
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun CandidateCard(item: CandidateUi, onClick: () -> Unit) {
    val scheme = SunSeatTheme.scheme
    val transfers = if (item.transfers == 0) stringResource(R.string.candidates_no_transfer) else stringResource(R.string.candidates_transfers, item.transfers)
    val walk = if (item.walkMinutes > 0) stringResource(R.string.candidates_walk, item.walkMinutes) else null
    val meta = listOfNotNull(transfers, walk, item.fare?.let { formatWon(it) }).joinToString(" · ")
    Surface(
        color = scheme.surface,
        shape = MaterialTheme.shapes.large,
        modifier = Modifier
            .fillMaxWidth()
            .clip(MaterialTheme.shapes.large)
            .clickable(role = Role.Button, onClickLabel = stringResource(R.string.candidates_choose), onClick = onClick),
    ) {
        Row(Modifier.padding(start = Spacing.l, top = Spacing.l, bottom = Spacing.l, end = Spacing.s), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(Spacing.m)) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Spacing.s)) {
                    Text(formatDuration(item.totalDuration), style = MaterialTheme.typography.headlineSmall, color = scheme.onSurface)
                    item.arrival?.let {
                        Text(stringResource(R.string.candidates_arrival, it.format(ArrivalFormat)), style = MaterialTheme.typography.bodyMedium, color = scheme.onSurfaceVariant)
                    }
                    if (item.fastest) {
                        Text(
                            stringResource(R.string.candidates_fastest),
                            style = MaterialTheme.typography.labelMedium,
                            color = scheme.onSurface,
                            modifier = Modifier
                                .border(Sizes.hairline, scheme.outline, CapsuleShape)
                                .padding(horizontal = Spacing.s, vertical = Sizes.pillVertical / 2),
                        )
                    }
                }
                Text(meta, style = MaterialTheme.typography.bodyMedium, color = scheme.onSurfaceVariant)
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(Spacing.xs),
                    verticalArrangement = Arrangement.spacedBy(Spacing.s),
                    itemVerticalAlignment = Alignment.CenterVertically,
                ) {
                    item.chips.forEachIndexed { i, chip ->
                        if (i > 0) {
                            Icon(painterResource(R.drawable.ic_ms_chevron_right), contentDescription = null, tint = scheme.onSurfaceVariant, modifier = Modifier.size(Sizes.iconSmall))
                        }
                        LegChipView(chip)
                    }
                }
                if (item.boardAt.isNotBlank()) {
                    Text(
                        stringResource(R.string.candidates_board_alight, item.boardAt, item.alightAt),
                        style = MaterialTheme.typography.bodySmall,
                        color = scheme.onSurfaceVariant,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
            Icon(painterResource(R.drawable.ic_ms_chevron_right), contentDescription = null, tint = scheme.onSurfaceVariant, modifier = Modifier.size(Sizes.iconMedium))
        }
    }
}

@Composable
private fun LegChipView(chip: LegChip) {
    val scheme = SunSeatTheme.scheme
    val walk = chip.mode == Mode.WALK
    Row(
        Modifier
            .clip(CapsuleShape)
            .background(if (walk) scheme.surface else scheme.surfaceContainerHighest)
            .then(if (walk) Modifier.border(Sizes.hairline, scheme.outline, CapsuleShape) else Modifier)
            .padding(horizontal = Spacing.m, vertical = Sizes.pillVertical),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Sizes.pillGap),
    ) {
        val icon = when (chip.mode) {
            Mode.SUBWAY -> R.drawable.ic_ms_directions_subway
            Mode.BUS -> R.drawable.ic_ms_directions_bus
            Mode.CAR -> R.drawable.ic_ms_directions_car
            Mode.WALK -> R.drawable.ic_ms_directions_walk
        }
        Icon(painterResource(icon), contentDescription = null, tint = if (walk) scheme.onSurfaceVariant else scheme.onSurface, modifier = Modifier.size(Sizes.iconSmall))
        TransitLineColors.of(chip.lineName)?.let { color ->
            Box(Modifier.size(Sizes.lineDot).clip(CircleShape).background(color))
        }
        Text(when (chip.mode) {
            Mode.SUBWAY -> chip.lineName.ifBlank { stringResource(R.string.common_subway) }
            Mode.BUS -> if (chip.lineName.isBlank()) stringResource(R.string.common_bus) else stringResource(R.string.candidates_bus_name, chip.lineName)
            Mode.WALK -> stringResource(R.string.candidates_walk, chip.walkMinutes)
            Mode.CAR -> stringResource(R.string.common_car)
        }, style = MaterialTheme.typography.labelMedium, color = if (walk) scheme.onSurfaceVariant else scheme.onSurface)
    }
}

@Composable
private fun Message(
    content: PaddingValues,
    iconRes: Int,
    title: String,
    detail: String?,
    primary: Pair<String, () -> Unit>?,
    secondary: Pair<String, () -> Unit>,
) {
    val scheme = SunSeatTheme.scheme
    Column(
        Modifier.fillMaxSize().padding(content),
        verticalArrangement = Arrangement.spacedBy(Spacing.m),
    ) {
        Spacer(Modifier.height(Spacing.xl))
        Icon(painterResource(iconRes), contentDescription = null, tint = scheme.onSurfaceVariant, modifier = Modifier.size(Sizes.iconLarge))
        Text(title, style = MaterialTheme.typography.titleLarge, color = scheme.onSurface, modifier = Modifier.semantics { heading() })
        if (detail != null) Text(detail, style = MaterialTheme.typography.bodyLarge, color = scheme.onSurfaceVariant)
        Spacer(Modifier.height(Spacing.s))
        if (primary != null) {
            PrimaryButton(onClick = primary.second, modifier = Modifier.fillMaxWidth()) {
                Text(primary.first, style = MaterialTheme.typography.labelLarge)
            }
        }
        QuietButton(onClick = secondary.second, modifier = Modifier.fillMaxWidth()) {
            Text(secondary.first, style = MaterialTheme.typography.labelLarge)
        }
    }
}
