package com.sunseat.app.ui.input

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import com.sunseat.app.R
import com.sunseat.app.domain.TimeMode
import com.sunseat.app.domain.TravelMode
import com.sunseat.app.ui.components.PrimaryButton
import com.sunseat.app.ui.components.QuietButton
import com.sunseat.app.ui.theme.Sizes
import com.sunseat.app.ui.theme.Spacing
import com.sunseat.app.ui.theme.SunSeatTheme
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

/** Shared input/candidate schedule picker. Draft changes only apply on confirmation. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TimeSheet(
    value: LocalDateTime?, mode: TimeMode, travelMode: TravelMode,
    onDismiss: () -> Unit, onApply: (LocalDateTime?, TimeMode) -> Unit,
    modifier: Modifier = Modifier,
    now: LocalDateTime = LocalDateTime.now(),
) {
    var selectedMode by rememberSaveable { mutableStateOf(mode.name) }
    var day by rememberSaveable { mutableLongStateOf((value ?: now).toLocalDate().toEpochDay()) }
    var hour by rememberSaveable { mutableIntStateOf((value ?: now).hour) }
    var minute by rememberSaveable { mutableIntStateOf((value ?: now).minute) }
    val dates = (listOf(java.time.LocalDate.ofEpochDay(day)) + listOfNotNull(value?.toLocalDate()) + (0L..2L).map { now.toLocalDate().plusDays(it) }).distinct().sorted()
    val dateLabels = dates.map { date ->
        when (date) {
            now.toLocalDate() -> stringResource(R.string.input_today)
            now.toLocalDate().plusDays(1) -> stringResource(R.string.input_tomorrow)
            now.toLocalDate().plusDays(2) -> stringResource(R.string.input_after_tomorrow)
            else -> date.format(DateTimeFormatter.ofPattern("M월 d일"))
        }
    }
    ModalBottomSheet(modifier = modifier, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true), onDismissRequest = onDismiss, containerColor = SunSeatTheme.scheme.surface) {
        Column(Modifier.fillMaxWidth().padding(horizontal = Spacing.screen), verticalArrangement = Arrangement.spacedBy(Spacing.m)) {
            Column(Modifier.weight(1f, fill = false).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(Spacing.m)) {
            Text(stringResource(R.string.input_time_title), style = MaterialTheme.typography.titleLarge)
            NeutralTabs(listOf(stringResource(R.string.input_depart_tab), stringResource(R.string.input_arrive_tab)), TimeMode.valueOf(selectedMode).ordinal) { selectedMode = TimeMode.entries[it].name }
            ScheduleDropdown(stringResource(R.string.input_date), dateLabels, dates.indexOf(java.time.LocalDate.ofEpochDay(day)),
                onSelect = { day = dates[it].toEpochDay() }, modifier = Modifier.fillMaxWidth(), tag = "schedule_date")
            Text(java.time.LocalDate.ofEpochDay(day).format(DateTimeFormatter.ofPattern("M월 d일")), style = MaterialTheme.typography.bodyMedium)
            Row(horizontalArrangement = Arrangement.spacedBy(Spacing.m)) {
                ScheduleDropdown(stringResource(R.string.input_hour), (0..23).map { stringResource(R.string.input_hour_value, it) }, hour,
                    onSelect = { hour = it }, modifier = Modifier.weight(1f), tag = "schedule_hour")
                ScheduleDropdown(stringResource(R.string.input_minute), (0..59).map { stringResource(R.string.input_minute_value, it) }, minute,
                    onSelect = { minute = it }, modifier = Modifier.weight(1f), tag = "schedule_minute")
            }
            if (travelMode == TravelMode.TRANSIT) Text(stringResource(R.string.input_time_note), style = MaterialTheme.typography.bodySmall, color = SunSeatTheme.scheme.onSurfaceVariant)
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(Spacing.m)) {
                QuietButton(onClick = { onApply(null, TimeMode.DEPART); onDismiss() }) { Text(stringResource(R.string.input_time_now)) }
                PrimaryButton(onClick = {
                    onApply(java.time.LocalDate.ofEpochDay(day).atTime(hour, minute), TimeMode.valueOf(selectedMode))
                    onDismiss()
                }, modifier = Modifier.weight(1f)) { Text(stringResource(R.string.input_time_apply)) }
            }
            Spacer(Modifier.height(Spacing.l))
        }
    }
}

/** Read-only anchored fields keep time selection out of the keyboard and clock dial. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ScheduleDropdown(label: String, options: List<String>, selected: Int, onSelect: (Int) -> Unit, tag: String, modifier: Modifier = Modifier) {
    var expanded by remember { mutableStateOf(false) }
    val scroll = rememberScrollState()
    val rowPixels = with(androidx.compose.ui.platform.LocalDensity.current) { Spacing.touch.roundToPx() }
    LaunchedEffect(expanded) { if (expanded) scroll.scrollTo(selected.coerceAtLeast(0) * rowPixels) }
    ExposedDropdownMenuBox(expanded = expanded, onExpandedChange = { expanded = it }, modifier = modifier) {
        OutlinedTextField(
            value = options.getOrElse(selected) { "" }, onValueChange = {}, readOnly = true,
            label = { Text(label) }, singleLine = true,
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded) },
            colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = SunSeatTheme.scheme.onSurface, focusedLabelColor = SunSeatTheme.scheme.onSurface),
            modifier = Modifier.fillMaxWidth().menuAnchor(ExposedDropdownMenuAnchorType.PrimaryNotEditable).testTag(tag),
        )
        ExposedDropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }, scrollState = scroll,
            containerColor = SunSeatTheme.scheme.surface) {
            options.forEachIndexed { index, option ->
                DropdownMenuItem(
                    text = { Text(option) },
                    trailingIcon = if (index == selected) { { Icon(painterResource(R.drawable.ic_ms_check), null, Modifier.size(Sizes.iconSmall)) } } else null,
                    modifier = Modifier.heightIn(min = Spacing.touch).semantics { this.selected = index == selected },
                    onClick = { onSelect(index); expanded = false },
                )
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
fun scheduleLabel(value: LocalDateTime?, mode: TimeMode, now: LocalDateTime = LocalDateTime.now()): String {
    if (value == null) return stringResource(R.string.input_now_depart)
    val day = when (value.toLocalDate()) {
        now.toLocalDate() -> stringResource(R.string.input_today)
        now.toLocalDate().plusDays(1) -> stringResource(R.string.input_tomorrow)
        else -> value.format(DateTimeFormatter.ofPattern("M월 d일"))
    }
    return stringResource(if (mode == TimeMode.ARRIVE) R.string.input_arrival_label else R.string.input_departure_label, day, value.format(DateTimeFormatter.ofPattern("HH:mm")))
}
