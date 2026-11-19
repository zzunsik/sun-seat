package com.sunseat.app.ui.candidates

import com.sunseat.app.data.route.TransitLegDetail
import com.sunseat.app.data.route.TransitRouteOption
import com.sunseat.app.data.route.TransitRouteType
import com.sunseat.app.data.route.TransitVehicle
import com.sunseat.app.domain.LatLng
import com.sunseat.app.domain.Leg
import com.sunseat.app.domain.Mode
import com.sunseat.app.domain.NamedPlace
import com.sunseat.app.domain.Route
import com.sunseat.app.domain.SunGoal
import com.sunseat.app.domain.TravelMode
import com.sunseat.app.domain.TripRequest
import java.time.Duration

/** 테스트용 가짜 경로(직접 만든 값, 실제 응답 아님). */
object CandidatesFixtures {
    private val a = LatLng(37.5547, 126.9707)
    private val b = LatLng(37.4979, 127.0276)

    val transitRequest = TripRequest(NamedPlace("서울역", a), NamedPlace("강남역", b), TravelMode.TRANSIT, null, SunGoal.AVOID)
    val carRequest = transitRequest.copy(mode = TravelMode.CAR)

    private fun leg(mode: Mode, min: Long, from: String, to: String) = Leg(mode, listOf(a, b), Duration.ofMinutes(min), from, to)

    val subwayOnly = TransitRouteOption(
        route = Route(listOf(leg(Mode.SUBWAY, 12, "서울역", "시청"), leg(Mode.WALK, 3, "시청", "시청"), leg(Mode.SUBWAY, 18, "시청", "강남"))),
        type = TransitRouteType.SUBWAY,
        totalDuration = Duration.ofMinutes(38),
        distanceMeters = 11200,
        transfers = 1,
        fare = 1550,
        legDetails = listOf(
            TransitLegDetail(listOf(TransitVehicle("1호선", "일반")), listOf("서울역", "시청"), ""),
            TransitLegDetail(emptyList(), listOf("시청", "시청"), ""),
            TransitLegDetail(listOf(TransitVehicle("2호선", "일반")), listOf("시청", "강남"), ""),
        ),
    )

    val busOnly = TransitRouteOption(
        route = Route(listOf(leg(Mode.BUS, 47, "서울역버스환승센터", "강남역.강남역사거리"))),
        type = TransitRouteType.BUS,
        totalDuration = Duration.ofMinutes(52),
        distanceMeters = 12800,
        transfers = 0,
        fare = 1500,
        legDetails = listOf(TransitLegDetail(listOf(TransitVehicle("140", "간선")), emptyList(), "")),
    )

    val readable = TransitRouteOption(
        route = Route(listOf(
            leg(Mode.BUS, 1, "견본 출발 정류장 이름이 아주 긴 곳", "견본 환승센터"),
            leg(Mode.WALK, 1, "견본 환승센터", "견본 환승역").copy(duration = Duration.ofSeconds(20)),
            leg(Mode.SUBWAY, 30, "견본 환승역", "견본 두번째 환승역"),
            leg(Mode.BUS, 2, "견본 두번째 환승역", "견본 도착 정류장"),
        )), type = TransitRouteType.BUS_AND_SUBWAY, totalDuration = Duration.ofMinutes(40),
        distanceMeters = 10000, transfers = 2, fare = null,
        legDetails = listOf(
            TransitLegDetail(listOf(TransitVehicle("견본 광역급행 아주 긴 노선 이름 12345", "광역"), TransitVehicle("대체노선 67890", "광역")), emptyList(), ""),
            TransitLegDetail(emptyList(), emptyList(), ""),
            TransitLegDetail(listOf(TransitVehicle("2호선", "일반")), emptyList(), ""),
            TransitLegDetail(emptyList(), emptyList(), ""),
        ),
    )

    val options = listOf(subwayOnly, busOnly)
}
