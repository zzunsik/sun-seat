package com.sunseat.app.ui.candidates

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import com.sunseat.app.R
import com.sunseat.app.domain.Mode
import com.sunseat.app.domain.TravelMode
import com.sunseat.app.domain.TripRequest
import com.sunseat.app.ui.components.PrimaryButton
import com.sunseat.app.ui.formatDuration
import com.sunseat.app.ui.formatWon
import com.sunseat.app.ui.theme.Sizes
import com.sunseat.app.ui.theme.Spacing
import com.sunseat.app.ui.theme.SunSeatTheme
import java.time.format.DateTimeFormatter

private val ScheduleFormat = DateTimeFormatter.ofPattern("HH:mm")

/** 원래 탑승 단위(#92 역 구간 묶음 포함)의 탈것 구간. 도보는 빠진다. */
internal fun routeRideGroups(item: CandidateUi): List<IntRange> =
    routeLegGroups(item).filter { item.chosen.route.legs[it.first].mode != Mode.WALK }

/**
 * 노선 조합 "1호선 → 2호선", "버스 140 → 2호선"(DESIGN 16.2). 지하철은 노선명, 버스는 "버스"와 번호를 함께 쓰고,
 * 노선명이 없으면 수단 이름만 쓴다. 대중교통이 아니거나 탈것이 없으면(도보만) 빈 문자열이다.
 */
@Composable
internal fun routeLineCombination(item: CandidateUi, transit: Boolean): String {
    if (!transit) return ""
    return routeRideGroups(item).map { range ->
        val index = range.first
        if (item.chosen.route.legs[index].mode == Mode.SUBWAY) item.chips.getOrNull(index)?.lineName.orEmpty().ifBlank { routeBoardingLabel(item, index, false) }
        else routeBoardingLabel(item, index, false)
    }.joinToString(" → ")
}

/** 후보 행과 미리보기 펼침이 같이 쓰는 "09:00 → 09:38"·요금 줄. 제공된 값만 쓴다(둘 다 없으면 빈 줄 자리만 남는다. 후보 행 배치 유지). */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun CandidateScheduleFare(item: CandidateUi, taxi: Boolean) {
    val scheme = SunSeatTheme.scheme
    FlowRow(horizontalArrangement = Arrangement.spacedBy(Spacing.s), verticalArrangement = Arrangement.spacedBy(Spacing.xs)) {
        item.arrival?.let { arrival ->
            val departure = arrival.minus(item.totalDuration)
            val arrivalLabel = if (arrival.toLocalDate() > departure.toLocalDate())
                stringResource(R.string.candidates_next_day, arrival.format(ScheduleFormat)) else arrival.format(ScheduleFormat)
            Text(stringResource(R.string.candidates_compact_schedule, departure.format(ScheduleFormat), arrivalLabel),
                style = MaterialTheme.typography.bodyMedium, color = scheme.onSurfaceVariant)
        }
        item.fare?.let { Text(if (taxi) stringResource(R.string.candidates_taxi_fare, formatWon(it)) else formatWon(it),
            style = MaterialTheme.typography.bodyMedium, color = scheme.onSurfaceVariant) }
    }
}

/**
 * 선택 경로 미리보기 시트 내용(#102, DESIGN 12.1·16.2).
 * 접힌 부분([onCollapsedHeight]로 높이를 알린다): 총시간·환승 수·자리 보기 / 시간 막대+라벨 / 노선 조합.
 * 펼친 부분: 시각·요금, 전체 장소명, 승하차 연결 타임라인. 접힌 상태에 시각·요금·전 구간을 강제하지 않는다.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun RoutePreviewSheet(item: CandidateUi, request: TripRequest, onSeat: () -> Unit, onCollapsedHeight: (Int) -> Unit) {
    val scheme = SunSeatTheme.scheme
    val transit = request.mode == TravelMode.TRANSIT
    val taxi = request.mode == TravelMode.TAXI
    val lines = routeLineCombination(item, transit)
    val rides = routeRideGroups(item)
    Column {
        Column(Modifier.fillMaxWidth().onSizeChanged { onCollapsedHeight(it.height) }
            .padding(start = Spacing.screen, end = Spacing.screen, bottom = Spacing.l), verticalArrangement = Arrangement.spacedBy(Spacing.s)) {
            // 자리 보기는 요약 줄 오른쪽에 둬 작은/가로 화면의 접힌 높이에서도 가려지지 않는다.
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Spacing.s)) {
                FlowRow(Modifier.weight(1f).semantics(mergeDescendants = true) { heading() },
                    horizontalArrangement = Arrangement.spacedBy(Spacing.s), verticalArrangement = Arrangement.spacedBy(Spacing.xs),
                    itemVerticalAlignment = Alignment.CenterVertically) {
                    Text(formatDuration(item.totalDuration), style = MaterialTheme.typography.titleLarge, color = scheme.onSurface)
                    if (transit && rides.isNotEmpty()) Text(stringResource(R.string.candidates_transfers, item.transfers),
                        style = MaterialTheme.typography.bodyMedium, color = scheme.onSurfaceVariant)
                }
                PrimaryButton(onClick = onSeat) { Text(stringResource(R.string.candidates_view_seat)) }
            }
            RouteSegmentBar(item, taxi = taxi)
            if (lines.isNotBlank()) Text(lines, Modifier.testTag("preview_lines"), style = MaterialTheme.typography.bodyMedium, color = scheme.onSurface)
        }
        HorizontalDivider(Modifier.padding(horizontal = Spacing.screen), thickness = Sizes.hairline, color = scheme.outlineVariant)
        Column(Modifier.fillMaxWidth().padding(Spacing.screen), verticalArrangement = Arrangement.spacedBy(Spacing.s)) {
            CandidateScheduleFare(item, taxi)
            Column(verticalArrangement = Arrangement.spacedBy(Spacing.xs)) {
                Text(stringResource(R.string.candidates_preview_origin, request.origin.name), style = MaterialTheme.typography.bodyMedium, color = scheme.onSurfaceVariant)
                Text(stringResource(R.string.candidates_preview_destination, request.destination.name), style = MaterialTheme.typography.bodyMedium, color = scheme.onSurfaceVariant)
            }
            RouteLegSummary(item, taxi)
        }
    }
}
