package com.sunseat.app.domain

import java.time.Duration

/** 이동 수단. */
enum class Mode { BUS, SUBWAY, CAR, WALK }

/** 위도·경도(도). */
data class LatLng(val latitude: Double, val longitude: Double)

/**
 * 경로의 한 구간.
 * @param path 진행 순서대로의 좌표열
 * @param duration 구간 전체 소요시간
 */
data class Leg(
    val mode: Mode,
    val path: List<LatLng>,
    val duration: Duration,
    val startName: String,
    val endName: String,
    /** Only ABOVE subway legs participate in sunlight recommendations. */
    val groundLevel: GroundLevel = GroundLevel.UNKNOWN,
)

/** 구간을 순서대로 이은 경로. */
data class Route(val legs: List<Leg>) {
    val duration: Duration get() = legs.fold(Duration.ZERO) { acc, leg -> acc + leg.duration }
}
