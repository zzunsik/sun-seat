package com.sunseat.app.domain

import java.time.LocalDateTime
import java.time.Instant
import java.time.ZoneId

/** 이동 수단 선택. 대중교통은 경로 후보를 고르고, 자가용은 경로 하나로 간다. */
@kotlinx.serialization.Serializable
enum class TravelMode { TRANSIT, TAXI, CAR }

/** 추천을 받기 위한 입력. 입력 화면(#11)이 만들고 경로 후보(#12)·결과(#13)가 쓴다. */
data class TripRequest(
    val origin: NamedPlace,
    val destination: NamedPlace,
    val mode: TravelMode,
    /** null이면 "지금" */
    val departure: LocalDateTime?,
    val goal: SunGoal,
)

/** 이름이 붙은 좌표. 장소 검색 결과나 현재 위치. */
data class NamedPlace(val name: String, val location: LatLng)

/** A selected departure is local to the supplied zone; null means the fresh current instant. */
fun TripRequest.departureInstant(now: Instant, zone: ZoneId): Instant =
    departure?.atZone(zone)?.toInstant() ?: now
