package com.sunseat.app.ui.input

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.gestures.snapping.rememberSnapFlingBehavior
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import androidx.compose.ui.semantics.*
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import com.sunseat.app.R
import com.sunseat.app.domain.TravelMode
import com.sunseat.app.ui.components.PrimaryButton
import com.sunseat.app.ui.components.QuietButton
import com.sunseat.app.ui.theme.Sizes
import com.sunseat.app.ui.theme.Spacing
import com.sunseat.app.ui.theme.SunSeatTheme
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlinx.coroutines.launch

/** Four departure wheels. Only confirmation commits; dismissing discards the entire draft. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TimeSheet(
    value: LocalDateTime?, travelMode: TravelMode,
    onDismiss: () -> Unit, onApply: (LocalDateTime?) -> Unit,
    modifier: Modifier = Modifier,
    now: LocalDateTime = LocalDateTime.now(),
) {
    // Freeze the date window for this opening, including across midnight and recreation.
    val today by rememberSaveable { mutableLongStateOf(now.toLocalDate().toEpochDay()) }
    val initial = value ?: now
    val firstDay = minOf(today, initial.toLocalDate().toEpochDay())
    val lastDay = maxOf(today + 365, initial.toLocalDate().toEpochDay())
    val date = rememberLazyListState((initial.toLocalDate().toEpochDay() - firstDay).toInt())
    val period = rememberLazyListState(initial.hour / 12)
    val hour = rememberLazyListState((initial.hour + 11) % 12)
    val minute = rememberLazyListState(initial.minute)
    val wheels = listOf(date, period, hour, minute)
    val moving = wheels.any { it.isScrollInProgress }
    val selectedDateIndex by remember(date) { derivedStateOf { date.centeredIndex() } }
    val selectedDay = LocalDate.ofEpochDay(firstDay + selectedDateIndex)
    val dateFormat = remember { DateTimeFormatter.ofPattern("M월 d일 E", Locale.KOREAN) }
    val rowHeight = with(LocalDensity.current) { maxOf(Spacing.touch, MaterialTheme.typography.bodyMedium.lineHeight.toDp() * 2) }
    ModalBottomSheet(modifier = modifier, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true), onDismissRequest = onDismiss, containerColor = SunSeatTheme.scheme.surface) {
        Column(Modifier.fillMaxWidth().padding(horizontal = Spacing.screen), verticalArrangement = Arrangement.spacedBy(Spacing.m)) {
            Column(Modifier.weight(1f, fill = false).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(Spacing.m)) {
                Text(stringResource(R.string.input_time_title), style = MaterialTheme.typography.titleLarge)
                Text(stringResource(R.string.input_wheel_hint), style = MaterialTheme.typography.bodySmall, color = SunSeatTheme.scheme.onSurfaceVariant)
                Row(Modifier.fillMaxWidth().height(IntrinsicSize.Min), horizontalArrangement = Arrangement.spacedBy(Spacing.s)) {
                    listOf(R.string.input_date to 2.5f, R.string.input_period to 1f, R.string.input_hour to 1f, R.string.input_minute to 1f).forEach { (label, weight) ->
                        Box(Modifier.weight(weight).fillMaxHeight(), contentAlignment = Alignment.Center) {
                            Text(if (label == R.string.input_period) "${stringResource(R.string.input_am)}\n${stringResource(R.string.input_pm)}" else stringResource(label), style = MaterialTheme.typography.labelMedium, color = SunSeatTheme.scheme.onSurfaceVariant, textAlign = androidx.compose.ui.text.style.TextAlign.Center)
                        }
                    }
                }
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(Spacing.s)) {
                    Wheel(stringResource(R.string.input_date), date, (lastDay - firstDay + 1).toInt(), rowHeight, "schedule_date", Modifier.weight(2.5f)) { index ->
                        val day = LocalDate.ofEpochDay(firstDay + index)
                        if (day.toEpochDay() == today) stringResource(R.string.input_today) else day.format(dateFormat)
                    }
                    Wheel(stringResource(R.string.input_period), period, 2, rowHeight, "schedule_period", Modifier.weight(1f)) { stringResource(if (it == 0) R.string.input_am else R.string.input_pm) }
                    Wheel(stringResource(R.string.input_hour), hour, 12, rowHeight, "schedule_hour", Modifier.weight(1f)) { (it + 1).toString() }
                    Wheel(stringResource(R.string.input_minute), minute, 60, rowHeight, "schedule_minute", Modifier.weight(1f)) { "%02d".format(Locale.ROOT, it) }
                }
                if (travelMode == TravelMode.TRANSIT) Text(stringResource(R.string.input_time_note), style = MaterialTheme.typography.bodySmall, color = SunSeatTheme.scheme.onSurfaceVariant)
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(Spacing.m)) {
                QuietButton(onClick = { onApply(null); onDismiss() }) { Text(stringResource(R.string.input_time_now)) }
                PrimaryButton(onClick = {
                    val selectedHour = (hour.centeredIndex() + 1) % 12 + period.centeredIndex() * 12
                    onApply(selectedDay.atTime(selectedHour, minute.centeredIndex()))
                    onDismiss()
                }, enabled = !moving, modifier = Modifier.weight(1f)) { Text(stringResource(R.string.input_time_apply)) }
            }
            Spacer(Modifier.height(Spacing.l))
        }
    }
}

/** Padding puts the first/last item on the same center line as every other item. */
private fun LazyListState.centeredIndex(): Int {
    val layout = layoutInfo
    val middle = (layout.viewportStartOffset + layout.viewportEndOffset) / 2
    return layout.visibleItemsInfo.minByOrNull { kotlin.math.abs(it.offset + it.size / 2 - middle) }?.index ?: firstVisibleItemIndex
}

@Composable
private fun Wheel(label: String, state: LazyListState, count: Int, rowHeight: androidx.compose.ui.unit.Dp, tag: String, modifier: Modifier = Modifier, text: @Composable (Int) -> String) {
    val scope = rememberCoroutineScope()
    val selected by remember(state) { derivedStateOf { state.centeredIndex() } }
    val current = text(selected)
    val hint = stringResource(R.string.input_wheel_hint)
    val reduceMotion = SunSeatTheme.reduceMotion
    fun move(index: Int) {
        scope.launch { if (reduceMotion) state.scrollToItem(index.coerceIn(0, count - 1)) else state.animateScrollToItem(index.coerceIn(0, count - 1)) }
    }
    Box(modifier.fillMaxWidth().height(rowHeight * 3)) {
            Box(Modifier.align(Alignment.Center).fillMaxWidth().height(rowHeight).background(SunSeatTheme.scheme.surfaceContainerHigh, MaterialTheme.shapes.small))
            LazyColumn(state = state, flingBehavior = rememberSnapFlingBehavior(state), contentPadding = PaddingValues(vertical = rowHeight),
                modifier = Modifier.fillMaxSize().testTag(tag).semantics {
                    contentDescription = label
                    stateDescription = current
                    customActions = listOf(
                        CustomAccessibilityAction("$label 이전") { move(selected - 1); true },
                        CustomAccessibilityAction("$label 다음") { move(selected + 1); true },
                    )
                    scrollToIndex { move(it); true }
                }) {
                items(count, key = { it }) { index ->
                    val item = text(index)
                    Box(Modifier.fillMaxWidth().height(rowHeight).testTag("${tag}_$index")
                        .semantics { this.selected = selected == index }
                        .clickable(role = Role.Button, onClickLabel = "$label $item · $hint") { move(index) }, contentAlignment = Alignment.Center) {
                        Text(item, style = if (selected == index) MaterialTheme.typography.titleSmall else MaterialTheme.typography.bodyMedium,
                            color = if (selected == index) SunSeatTheme.scheme.onSurface else SunSeatTheme.scheme.onSurfaceVariant,
                            maxLines = 2, textAlign = androidx.compose.ui.text.style.TextAlign.Center)
                    }
                }
            }
    }
}
@Composable
fun NeutralTabs(labels: List<String>, selected: Int, modifier: Modifier = Modifier, onSelect: (Int) -> Unit) {
    val scheme = SunSeatTheme.scheme
    val measurer = rememberTextMeasurer()
    val density = LocalDensity.current
    val style = MaterialTheme.typography.labelLarge
    val minimumWidths = labels.map { label ->
        with(density) { measurer.measure(label, style = style, softWrap = false).size.width.toDp() } +
            Sizes.iconSmall + Spacing.s + Spacing.l * 2 // M3 Tab's horizontal content padding.
    }
    val scroll = rememberScrollState()
    BoxWithConstraints(modifier.fillMaxWidth()) {
        val total = minimumWidths.sumOf { it.value.toDouble() }.toFloat().dp
        val scrollable = total > maxWidth
        val equalWidth = (minimumWidths.maxOrNull() ?: 0.dp) * labels.size <= maxWidth
        LaunchedEffect(selected, minimumWidths, scrollable, maxWidth) {
            val offset = with(density) { minimumWidths.take(selected).sumOf { it.roundToPx() } }
            scroll.scrollTo(if (scrollable) offset else 0)
        }
        Row(Modifier.fillMaxWidth().heightIn(min = Spacing.touch)
            .then(if (scrollable) Modifier.horizontalScroll(scroll) else Modifier)) {
        labels.forEachIndexed { index, label ->
            val width = minimumWidths[index]
            val tabModifier = if (scrollable) Modifier.width(width) else Modifier.weight(if (equalWidth) 1f else width.value)
            Surface(color = if (index == selected) scheme.inverseSurface else scheme.surface, contentColor = if (index == selected) scheme.inverseOnSurface else scheme.onSurface, modifier = tabModifier) {
                androidx.compose.material3.Tab(selected = index == selected, onClick = { onSelect(index) }, text = { Row(horizontalArrangement = Arrangement.spacedBy(Spacing.s), verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                    if (index == selected) Icon(painterResource(R.drawable.ic_ms_check), null, Modifier.size(Sizes.iconSmall))
                    Text(label, style = style, maxLines = 1, softWrap = false)
                } }, selectedContentColor = scheme.inverseOnSurface, unselectedContentColor = scheme.onSurface)
            }
        }
        }
    }
}

@Composable
fun NeutralFilterChip(selected: Boolean, label: String, modifier: Modifier = Modifier, onClick: () -> Unit) {
    val scheme = SunSeatTheme.scheme
    FilterChip(selected, onClick, label = { Text(label) },
        leadingIcon = if (selected) { { Icon(painterResource(R.drawable.ic_ms_check), null, Modifier.size(Sizes.iconSmall)) } } else null,
        modifier = modifier.heightIn(min = Spacing.touch),
        colors = FilterChipDefaults.filterChipColors(selectedContainerColor = scheme.inverseSurface, selectedLabelColor = scheme.inverseOnSurface, selectedLeadingIconColor = scheme.inverseOnSurface))
}

@Composable
fun scheduleLabel(value: LocalDateTime?, now: LocalDateTime = LocalDateTime.now()): String {
    if (value == null) return stringResource(R.string.input_now_depart)
    val day = when (value.toLocalDate()) {
        now.toLocalDate() -> stringResource(R.string.input_today)
        now.toLocalDate().plusDays(1) -> stringResource(R.string.input_tomorrow)
        else -> value.format(DateTimeFormatter.ofPattern("M월 d일"))
    }
    return stringResource(R.string.input_departure_label, day, value.format(DateTimeFormatter.ofPattern("HH:mm")))
}
