package com.sunseat.app.domain

import java.time.LocalDateTime
import java.time.Instant
import java.time.ZoneId
import java.time.Duration

/** 이동 수단 선택. 대중교통은 경로 후보를 고르고, 자가용은 경로 하나로 간다. */
enum class TravelMode { TRANSIT, CAR }
enum class TimeMode { DEPART, ARRIVE }

/** 추천을 받기 위한 입력. 입력 화면(#11)이 만들고 경로 후보(#12)·결과(#13)가 쓴다. */
data class TripRequest(
    val origin: NamedPlace,
    val destination: NamedPlace,
    val mode: TravelMode,
    /** null이면 "지금" */
    val departure: LocalDateTime?,
    val goal: SunGoal,
    val timeMode: TimeMode = TimeMode.DEPART,
)

/** 이름이 붙은 좌표. 장소 검색 결과나 현재 위치. */
data class NamedPlace(val name: String, val location: LatLng)

/** Arrival requests use the route's complete duration, including transfer/wait time. */
fun TripRequest.departureInstant(totalDuration: Duration, now: Instant, zone: ZoneId): Instant {
    val selected = departure?.atZone(zone)?.toInstant() ?: return now
    return if (timeMode == TimeMode.ARRIVE) selected - totalDuration else selected
}
