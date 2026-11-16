package com.sunseat.app.ui.result

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import com.sunseat.app.R
import com.sunseat.app.domain.*
import com.sunseat.app.ui.components.DiagramSun
import com.sunseat.app.ui.components.SeatSunDiagram
import com.sunseat.app.ui.text.word
import com.sunseat.app.ui.theme.SunSeatTheme
import java.time.Duration
import java.time.Instant
import java.time.ZoneId

@Composable
internal fun SeatSpot.label(): String = listOfNotNull(
    side.word(), zone?.let { stringResource(when (it) {
        SeatZone.FRONT_MIDDLE -> R.string.seat_front_middle
        SeatZone.MIDDLE_REAR -> R.string.seat_middle_rear
        SeatZone.FRONT -> R.string.seat_front
        SeatZone.MIDDLE -> R.string.seat_middle
        SeatZone.REAR -> R.string.seat_rear
    }) }, position?.let { stringResource(if (it == SeatPosition.WINDOW) R.string.seat_window else R.string.seat_aisle) },
).joinToString(stringResource(R.string.seat_separator))

@Composable
internal fun SeatDetails(leg: LegResult, advice: LegAdvice.Sit, mode: TravelMode, departure: Instant, zone: ZoneId) {
    @Composable fun Note(text: String) { Text(text, style = MaterialTheme.typography.bodySmall, color = SunSeatTheme.scheme.onSurfaceVariant) }
    if (leg.leg.estimatedStationTiming) Note(stringResource(R.string.seat_station_estimate))
    if (advice.similarSides) Note(stringResource(R.string.seat_similar_sides))
    if (leg.leg.mode == Mode.BUS) {
        Text(advice.spot.label(), style = MaterialTheme.typography.titleSmall)
        Note(stringResource(if (leg.leg.busType == "광역") R.string.seat_bus_express else R.string.seat_bus_layout))
    }
    advice.reasons.forEach { reason -> Note(stringResource(when (reason) {
        SeatReason.LOW_FRONT -> R.string.seat_reason_front
        SeatReason.LOW_REAR -> R.string.seat_reason_rear
        SeatReason.SIDE_FRONT -> R.string.seat_reason_side_front
        SeatReason.SIDE_REAR -> R.string.seat_reason_side_rear
        SeatReason.CROSS_CABIN -> R.string.seat_reason_cross
        SeatReason.TAXI_RIGHT_DOOR -> R.string.seat_taxi_door
    })) }
    if (mode == TravelMode.TAXI) Note(stringResource(if (advice.side == Side.LEFT) R.string.seat_taxi_left else R.string.seat_taxi_right))
    advice.alternative?.let { Note(stringResource(R.string.seat_alternative, it.label())) }
    advice.switches.forEach { change ->
        val point = change.stopName ?: stringResource(R.string.seat_after_minutes, Duration.between(departure, change.at).toMinutes())
        Note(stringResource(R.string.seat_switch, point, change.to.label()))
    }
    leg.frontWindows.forEach { window -> Note(stringResource(R.string.seat_subway_front,
        leg.leg.startName, leg.leg.endName, window.start.atZone(zone).format(TimeFormat), window.end.atZone(zone).format(TimeFormat))) }
    if (leg.leg.mode == Mode.SUBWAY) {
        val shadeSide = if ((leg.leftBench?.back ?: Duration.ZERO) > (leg.rightBench?.back ?: Duration.ZERO)) Side.RIGHT else Side.LEFT
        Note(stringResource(R.string.seat_standing, shadeSide.word()))
    }
    SeatSunDiagram(
        sun = if (leg.aheadSun > maxOf(leg.leftSun, leg.rightSun)) DiagramSun.AHEAD else if (leg.leftSun > leg.rightSun) DiagramSun.LEFT else DiagramSun.RIGHT,
        recommended = advice.side, car = leg.leg.mode == Mode.CAR, bus = leg.leg.mode == Mode.BUS,
        spot = advice.spot, seatSun = (!leg.leftSun.isZero) to (!leg.rightSun.isZero),
    )
}
