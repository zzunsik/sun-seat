package com.sunseat.app.ui.input

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
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
    val time = rememberTimePickerState((value ?: now).hour, (value ?: now).minute, is24Hour = true)
    ModalBottomSheet(modifier = modifier, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true), onDismissRequest = onDismiss, containerColor = SunSeatTheme.scheme.surface) {
        Column(Modifier.fillMaxWidth().padding(horizontal = Spacing.screen), verticalArrangement = Arrangement.spacedBy(Spacing.m)) {
            Column(Modifier.weight(1f, fill = false).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(Spacing.m)) {
            Text(stringResource(R.string.input_time_title), style = MaterialTheme.typography.titleLarge)
            NeutralTabs(listOf(stringResource(R.string.input_depart_tab), stringResource(R.string.input_arrive_tab)), TimeMode.valueOf(selectedMode).ordinal) { selectedMode = TimeMode.entries[it].name }
            Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(Spacing.s)) {
                listOf(R.string.input_today, R.string.input_tomorrow, R.string.input_after_tomorrow).forEachIndexed { index, label ->
                    NeutralFilterChip(day == now.toLocalDate().plusDays(index.toLong()).toEpochDay(), stringResource(label)) { day = now.toLocalDate().plusDays(index.toLong()).toEpochDay() }
                }
            }
            Text(java.time.LocalDate.ofEpochDay(day).format(DateTimeFormatter.ofPattern("M월 d일")), style = MaterialTheme.typography.bodyMedium)
            Box(Modifier.fillMaxWidth(), contentAlignment = androidx.compose.ui.Alignment.Center) { TimePicker(time, colors = TimePickerDefaults.colors(
                selectorColor = SunSeatTheme.scheme.inverseSurface,
                clockDialSelectedContentColor = SunSeatTheme.scheme.inverseOnSurface,
                timeSelectorSelectedContainerColor = SunSeatTheme.scheme.inverseSurface,
                timeSelectorSelectedContentColor = SunSeatTheme.scheme.inverseOnSurface,
            )) }
            if (travelMode == TravelMode.TRANSIT) Text(stringResource(R.string.input_time_note), style = MaterialTheme.typography.bodySmall, color = SunSeatTheme.scheme.onSurfaceVariant)
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(Spacing.m)) {
                QuietButton(onClick = { onApply(null, TimeMode.DEPART); onDismiss() }) { Text(stringResource(R.string.input_time_now)) }
                PrimaryButton(onClick = {
                    onApply(java.time.LocalDate.ofEpochDay(day).atTime(time.hour, time.minute), TimeMode.valueOf(selectedMode))
                    onDismiss()
                }, modifier = Modifier.weight(1f)) { Text(stringResource(R.string.input_time_apply)) }
            }
            Spacer(Modifier.height(Spacing.l))
        }
    }
}

@Composable
fun NeutralTabs(labels: List<String>, selected: Int, modifier: Modifier = Modifier, onSelect: (Int) -> Unit) {
    val scheme = SunSeatTheme.scheme
    Row(modifier.fillMaxWidth().heightIn(min = Spacing.touch)) {
        labels.forEachIndexed { index, label ->
            Surface(color = if (index == selected) scheme.inverseSurface else scheme.surface, contentColor = if (index == selected) scheme.inverseOnSurface else scheme.onSurface, modifier = Modifier.weight(1f)) {
                androidx.compose.material3.Tab(selected = index == selected, onClick = { onSelect(index) }, text = { Row(horizontalArrangement = Arrangement.spacedBy(Spacing.s), verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                    if (index == selected) Icon(painterResource(R.drawable.ic_ms_check), null, Modifier.size(Sizes.iconSmall))
                    Text(label, style = MaterialTheme.typography.labelLarge)
                } }, selectedContentColor = scheme.inverseOnSurface, unselectedContentColor = scheme.onSurface)
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
