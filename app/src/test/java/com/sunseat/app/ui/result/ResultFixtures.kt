package com.sunseat.app.ui.result

import com.sunseat.app.domain.ExposureAnalyzer
import com.sunseat.app.domain.LatLng
import com.sunseat.app.domain.Leg
import com.sunseat.app.domain.Mode
import com.sunseat.app.domain.NamedPlace
import com.sunseat.app.domain.Route
import com.sunseat.app.domain.SunGoal
import com.sunseat.app.domain.SunPosition
import com.sunseat.app.domain.TravelMode
import com.sunseat.app.domain.TripAdvisor
import com.sunseat.app.domain.TripRequest
import com.sunseat.app.ui.candidates.ChosenRoute
import java.time.Duration
import java.time.Instant
import java.time.ZoneId

/** 테스트용 경로와 고정된 해(직접 만든 값). */
object ResultFixtures {
    val zone: ZoneId = ZoneId.of("Asia/Seoul")
    val departure: Instant = Instant.parse("2026-11-16T00:00:00Z") // 09:00 KST

    fun advisor(azimuth: Double, altitude: Double) =
        TripAdvisor(analyzer = ExposureAnalyzer { _, _, _ -> SunPosition(azimuth, altitude) })

    private fun north(mode: Mode, min: Long, from: String, to: String, lat0: Double = 37.50) =
        Leg(mode, listOf(LatLng(lat0, 127.0), LatLng(lat0 + 0.05, 127.0), LatLng(lat0 + 0.1, 127.0)), Duration.ofMinutes(min), from, to)

    private fun south(mode: Mode, min: Long, from: String, to: String, lat0: Double = 37.60) =
        Leg(mode, listOf(LatLng(lat0, 127.0), LatLng(lat0 - 0.1, 127.0)), Duration.ofMinutes(min), from, to)

    private val walk = Leg(Mode.WALK, listOf(LatLng(37.6, 127.0), LatLng(37.6, 127.001)), Duration.ofMinutes(3), "시청", "시청")

    val transitRoute = Route(listOf(north(Mode.SUBWAY, 24, "서울역", "시청"), walk, north(Mode.BUS, 31, "시청", "광화문", lat0 = 37.6)))
    val mixedRoute = Route(listOf(north(Mode.BUS, 25, "서울역", "시청"), walk, south(Mode.SUBWAY, 20, "시청", "강남")))
    val carRoute = Route(listOf(north(Mode.CAR, 40, "", "")))

    val transitRequest = TripRequest(NamedPlace("서울역", LatLng(37.55, 126.97)), NamedPlace("광화문", LatLng(37.57, 126.98)), TravelMode.TRANSIT, null, SunGoal.AVOID)
    val carRequest = transitRequest.copy(mode = TravelMode.CAR)

    fun chosen(route: Route) = ChosenRoute(route, route.duration)
}
