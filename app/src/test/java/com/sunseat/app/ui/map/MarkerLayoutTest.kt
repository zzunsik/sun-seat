package com.sunseat.app.ui.map

import com.sunseat.app.domain.LatLng
import com.sunseat.app.domain.Leg
import com.sunseat.app.domain.Mode
import com.sunseat.app.domain.NamedPlace
import com.sunseat.app.domain.Route
import java.time.Duration
import kotlin.random.Random
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * #170: 표식 배치 규칙(순수 함수). 보이는 표식끼리 상자가 겹치지 않고, 숨긴 표식은 모두 대체 정보 위치가 있다.
 * 값은 px(밀도 3).
 */
class MarkerLayoutTest {
    private val d = 3f
    private fun sizes(fontScale: Float = 1f) = MarkSizes(
        marker = 32f * d, tail = 6f * d, gap = 4f * d,
        originWidth = 40f * d * fontScale, destinationWidth = 40f * d * fontScale,
        stopRadius = 7f * d, labelHeight = maxOf(20f, 16f * fontScale) * d,
        vehicle = 32f * d, currentLocation = 21f * d, sunRay = 56f * d,
        labelWidth = { stop -> ((stop.lines.firstOrNull()?.length ?: 2) * 8f * fontScale + 8f) * d },
    )

    private fun box(mark: Mark, placement: Placement) = mark.variants[placement.variant].let { ScreenBox(mark.x + it.left, mark.y + it.top, mark.x + it.right, mark.y + it.bottom) }
    private fun overlaps(a: ScreenBox, b: ScreenBox) = minOf(a.right, b.right) - maxOf(a.left, b.left) > 0.01f && minOf(a.bottom, b.bottom) - maxOf(a.top, b.top) > 0.01f

    /** 경도·위도 차를 그대로 px로 쓰는 단순 투영(1e-5° = 1px). */
    private fun project(origin: LatLng) = { p: LatLng -> ((p.longitude - origin.longitude) * 1e5).toFloat() to ((origin.latitude - p.latitude) * 1e5).toFloat() }

    private val seoul = LatLng(37.5547, 126.9707)

    // ---------- #160: 출발·도착이 화면에서 가까우면 두 핀이 서로 가리지 않는다 ----------

    @Test fun closeOriginAndDestinationPinsBothStayReadable() {
        // 결과 화면 이동 경로 카드(result_transit_light): 축소된 맞춤에서 출발·도착이 몇 px 차이로 거의 한 점이다.
        for ((dx, dy) in listOf(0f to 0f, 6f to 4f, -10f to -12f, 20f to 30f, 30f to -8f)) {
            val origin = NamedPlace("출발지", seoul)
            val destination = NamedPlace("도착지", LatLng(seoul.latitude - dy * 1e-5, seoul.longitude + dx * 1e-5))
            val state = MapState(origin = origin, destination = destination)
            val marks = state.marks(sizes(), project(seoul))
            val placements = markerLayout(marks)
            assertTrue("both pins are drawn", placements.none { it.hidden })
            val (a, b) = marks.zip(placements).map { (m, p) -> box(m, p) }
            assertFalse("pins at ($dx,$dy) must not overlap: $a $b", overlaps(a, b))
        }
    }

    @Test fun beforeTheFixBothPinsSatOnTheSameSpot() {
        // 고치기 전 규칙(두 핀 모두 몸통이 좌표 위)으로는 같은 자리에서 겹친다. 배치는 한쪽을 비킨다.
        val up = pinBox(PinSide.UP, 40f * d, 32f * d, 6f * d)
        assertTrue(overlaps(up, up))
        val state = MapState(origin = NamedPlace("출발지", seoul), destination = NamedPlace("도착지", seoul))
        val placements = markerLayout(state.marks(sizes(), project(seoul)))
        assertEquals("the origin keeps its place", 0, placements[0].variant)
        assertNotEquals("the destination moves aside", 0, placements[1].variant)
    }

    @Test fun pinsAvoidCoveringAnotherStopWhenTheyCan() {
        // 디자인 리뷰(#170): 몸통을 돌린 도착 표식이 다른 정류장 원을 덮으면 그 정류장 표식처럼 읽힌다. 덮지 않는 자리가 있으면 그쪽을 고른다.
        val stop = LatLng(seoul.latitude - 80e-5, seoul.longitude)
        val route = Route(listOf(Leg(Mode.SUBWAY, listOf(seoul, stop), Duration.ofMinutes(3), "a", "b", lineName = "2호선")))
        val state = MapState(routes = listOf(route), origin = NamedPlace("출발지", seoul), destination = NamedPlace("도착지", seoul))
        val marks = state.marks(sizes(), project(seoul))
        val placements = markerLayout(marks)
        // 출발·도착 좌표에 있는 제 정류장 원은 꼬리 끝이 가리키므로 빼고 본다.
        val circles = marks.zip(placements).filter { it.first.kind == MarkKind.STOP && it.first.at != seoul }.map { (m, p) -> box(m, p) }
        marks.zip(placements).filter { it.first.kind == MarkKind.ORIGIN || it.first.kind == MarkKind.DESTINATION }.forEach { (m, p) ->
            assertTrue("${m.kind} must not cover a stop circle", circles.none { overlaps(box(m, p), it) })
        }
    }

    // ---------- #152: 도착 순간의 도착 표식은 숨기지 않고 먼저 자리를 잡는다 ----------

    @Test fun arrivedDestinationIsNeverHiddenAndWins() {
        val route = Route(listOf(Leg(Mode.BUS, listOf(seoul, LatLng(37.55, 126.98)), Duration.ofMinutes(10), "a", "b", lineName = "402")))
        val end = LatLng(37.55, 126.98)
        val arrived = MapState(routes = listOf(route), origin = NamedPlace("출발지", end), destination = NamedPlace("도착지", end),
            cursor = MapCursor(end, 0.0, 90.0, 30.0), followCursor = true, routeProgress = 1f)
        val marks = arrived.marks(sizes(), project(seoul))
        val placements = markerLayout(marks)
        val destination = marks.indexOfFirst { it.kind == MarkKind.DESTINATION }
        assertFalse(placements[destination].hidden)
        assertEquals("the arrived destination keeps its place", 0, placements[destination].variant)
        assertEquals(Fallback.NONE, marks[destination].fallback)
        assertTrue("no vehicle after arrival", marks.none { it.kind == MarkKind.VEHICLE })
    }

    @Test fun textUnderThePlaybackVehicleMovesOrHidesOnlyWithAnInfoCardFallback() {
        // 재생 시작: 출발지가 곧 승차 정류장이라 출발 표식·402 이름표·차량이 한 점(#146). 차량은 움직이지 않는다.
        val stop = LatLng(seoul.latitude + 0.00002, seoul.longitude + 0.00002)
        val route = Route(listOf(Leg(Mode.WALK, listOf(seoul, stop), Duration.ofMinutes(1), "출발지", "정류장"),
            Leg(Mode.BUS, listOf(stop, LatLng(37.50, 127.0)), Duration.ofMinutes(20), "정류장", "종점", lineName = "402")))
        val playing = MapState(routes = listOf(route), origin = NamedPlace("출발지", seoul), destination = NamedPlace("도착지", LatLng(37.50, 127.0)),
            cursor = MapCursor(seoul, 0.0, 90.0, 30.0), followCursor = true, routeProgress = 0f)
        val marks = playing.marks(sizes(), project(seoul))
        val placements = markerLayout(marks)
        // 차량과 해 방향 선(고정 표식) 모두 글자 표식을 덮지 않는다.
        val fixedBoxes = marks.zip(placements).filter { it.first.fixed }.map { (m, p) -> box(m, p) }
        assertTrue("vehicle and sun ray are fixed marks", marks.count { it.fixed } == 2)
        marks.zip(placements).forEach { (mark, placement) ->
            if (mark.fixed || mark.soft) return@forEach
            if (placement.hidden) assertEquals("${mark.kind} hidden while playing keeps its text in the info card", Fallback.INFO_CARD, mark.fallback)
            else assertTrue("${mark.kind} must not sit under the vehicle or its sun ray", fixedBoxes.none { overlaps(box(mark, placement), it) })
        }
    }

    // ---------- 성질 기반(고정 시드) ----------

    @Test fun randomMarksNeverOverlapAndHideOnlyWithAFallback() {
        val random = Random(170)
        var checked = 0
        repeat(500) {
            val stops = random.nextInt(1, 12)
            // 화면에서 수백 px 안에 몰린 경로(축소된 맞춤): 표식끼리 자주 부딪친다.
            val spread = random.nextDouble(0.0002, 0.004)
            val points = (0..stops).map { LatLng(seoul.latitude + random.nextDouble(-spread, spread), seoul.longitude + random.nextDouble(-spread, spread)) }
            val lines = listOf("402", "2호선", "N16", "간선 470", "9호선 외 1", "공항철도")
            val legs = points.zipWithNext().mapIndexed { i, (a, b) ->
                Leg(if (i % 2 == 0) Mode.BUS else Mode.SUBWAY, listOf(a, b), Duration.ofMinutes(5), "s$i", "s${i + 1}", transitStepIndex = i, lineName = lines[random.nextInt(lines.size)])
            }
            val playing = random.nextInt(3) == 0
            val progress = if (playing) random.nextDouble(0.0, 1.0).toFloat() else null
            val cursor = if (playing) MapCursor(points[random.nextInt(points.size)], 0.0, 90.0, 30.0) else null
            val state = MapState(routes = listOf(Route(legs)), origin = NamedPlace("출발지", points.first()), destination = NamedPlace("도착지", points.last()),
                cursor = cursor, followCursor = playing, routeProgress = progress)
            for (fontScale in listOf(1f, 1.5f)) {
                val marks = state.marks(sizes(fontScale), project(seoul))
                val placements = markerLayout(marks)
                val visible = marks.zip(placements).filter { !it.second.hidden }
                for (i in visible.indices) for (j in i + 1 until visible.size) {
                    val (a, pa) = visible[i]; val (b, pb) = visible[j]
                    if (a.fixed && b.fixed || a.soft || b.soft) continue
                    assertFalse("${a.kind} and ${b.kind} overlap (font $fontScale, playing $playing)", overlaps(box(a, pa), box(b, pb)))
                }
                marks.zip(placements).filter { it.second.hidden }.forEach { (mark, _) ->
                    assertNotEquals("hidden ${mark.kind} must keep its information elsewhere", Fallback.NONE, mark.fallback)
                }
                checked++
            }
        }
        assertEquals(1000, checked)
    }

    @Test fun movedStopLabelStaysWithinOnePinWidthOfItsStop() {
        // #153: 이름표는 정류장에서 출발·도착 표식 폭 + 틈보다 멀리 밀리지 않는다(어느 정류장 것인지 읽혀야 한다).
        val s = sizes()
        val variants = labelVariants(onLeft = false, width = 30f * d, height = s.labelHeight, stopRadius = s.stopRadius, gap = s.gap, maxPush = s.originWidth + s.gap)
        assertTrue(variants.all { it.left - s.stopRadius <= s.originWidth + s.gap + 0.01f })
        assertEquals(s.stopRadius, variants.first().left, 0.01f)
    }
}
