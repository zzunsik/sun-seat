package com.sunseat.app.domain

import java.time.Duration
import java.time.Instant
import kotlin.math.abs
import kotlin.math.sin

/** 진행 비율(0~1) 위의 구간. 타임라인 막대에 쓴다. */
data class Span(val start: Float, val end: Float)

/** 탑승 구간 하나의 결론. */
sealed interface LegAdvice {
    /** [side] 쪽에 앉으며 자가용이면 [carSeats]를 함께 전달한다. */
    data class Sit(
        val side: Side, val carSeats: List<CarSeat> = emptyList(),
        val spot: SeatSpot = SeatSpot(side), val alternative: SeatSpot? = null,
        val switches: List<SeatSwitch> = emptyList(), val reasons: Set<SeatReason> = emptySet(),
        val similarSides: Boolean = false,
    ) : LegAdvice
    data object NoDifference : LegAdvice
    data object NoSun : LegAdvice

    /** 도보: 추천 대상이 아니다. */
    data object Walk : LegAdvice
    data object Underground : LegAdvice
    data object UnknownGround : LegAdvice
}

/** 지하철 벤치에 앉았을 때 등 뒤·정면 직사 시간. */
data class BenchTimes(val back: Duration, val front: Duration) {
    val total: Duration get() = back + front
}

/**
 * 탑승 구간 하나. [leftSun]/[rightSun]은 "그쪽에 앉으면 해를 받는 시간"이다.
 * 버스·자가용은 그쪽 창으로 드는 햇빛, 지하철은 그쪽 벤치의 등 뒤+정면 직사.
 */
data class LegResult(
    val index: Int,
    val leg: Leg,
    val advice: LegAdvice,
    val leftSun: Duration,
    val rightSun: Duration,
    /** 해가 진행 방향 앞·뒤(좌우 차이가 거의 없는 쪽)에 있던 시간 */
    val aheadSun: Duration = Duration.ZERO,
    val leftBench: BenchTimes? = null,
    val rightBench: BenchTimes? = null,
    val frontWindows: List<FrontSunWindow> = emptyList(),
    val followedSun: Duration? = null,
) {
    fun sun(side: Side): Duration = if (side == Side.LEFT) leftSun else rightSun
    fun bench(side: Side): BenchTimes? = if (side == Side.LEFT) leftBench else rightBench
}

/** 해가 주로 있는 곳(진행 방향 기준). */
enum class SunPlacement { LEFT, RIGHT, AHEAD }

/** 경로 전체 결론. */
sealed interface TripAdvice {
    data class Sit(val side: Side, val carSeats: List<CarSeat> = emptyList()) : TripAdvice
    data object Mixed : TripAdvice
    data object NoDifference : TripAdvice
    data object NoSun : TripAdvice
}

/** 운전자 눈부심 구간을 출발 기준 분으로 나타낸 것. */
data class GlareWindow(val start: Instant, val end: Instant, val fromMinute: Long, val toMinute: Long)

data class TripResult(
    val departure: Instant,
    val arrival: Instant,
    val goal: SunGoal,
    val advice: TripAdvice,
    val legs: List<LegResult>,
    /** 추천대로 앉았을 때 그늘·햇빛 시간(탑승 구간 합). 타임라인과 같은 기준이다. */
    val shadeIfFollowed: Duration,
    val sunIfFollowed: Duration,
    /** 버스·자가용에서 해가 앞·뒤(앞유리)로 든 시간. 좌우 어느 자리든 비슷하게 받는다 */
    val aheadSun: Duration,
    /** 탑승 구간 중 그쪽에 앉으면 해를 받는 시간의 합(그림 칠하기에 쓴다) */
    val leftSeatSun: Duration,
    val rightSeatSun: Duration,
    /** 해가 주로 있는 곳. 해가 없으면 null */
    val sunPlacement: SunPlacement?,
    /** "그쪽에 앉으면 해를 받는" 구간 */
    val leftSpans: List<Span>,
    val rightSpans: List<Span>,
    /** 도보 구간(추천 대상 아님) */
    val walkSpans: List<Span>,
    val glare: List<GlareWindow>,
    /** End of the modeled leg timeline; waiting/access time has no geometry. */
    val modeledArrival: Instant = arrival,
)

/**
 * 선택한 경로·출발 시각·피하기/받기를 받아 결과 화면에 필요한 값을 모두 계산한다.
 * 숫자·타임라인·구간 카드가 같은 기준("그쪽에 앉으면 해를 받는가")에서 나오도록 한 곳에서 만든다.
 */
class TripAdvisor(
    private val analyzer: ExposureAnalyzer = ExposureAnalyzer(),
    private val seats: SeatRecommender = SeatRecommender(),
    private val benches: SubwayBenchRecommender = SubwayBenchRecommender(),
    /** 해가 진행 방향 ±이 각도 안(앞·뒤)이면 버스·자가용 옆 창 햇빛으로 치지 않는다. 구간 전체가 그렇다면 "차이 없음" */
    private val aheadConeDeg: Double = AHEAD_CONE_DEG,
    val frontMaxAltitude: Double = FRONT_MAX_ALTITUDE,
    val sideFrontMaxAngle: Double = SIDE_FRONT_MAX_ANGLE,
    val busMaxEffectiveAltitude: Double = BUS_MAX_EFFECTIVE_ALTITUDE,
    val switchMinDuration: Duration = Duration.ofMinutes(SWITCH_MIN_MINUTES),
) {
    private val minSideness = sin(Math.toRadians(aheadConeDeg))

    /** 이 선분에서 해를 받는 좌석 쪽. 앞·뒤로 판정되면 [ahead]=true */
    private fun seatSides(mode: Mode, s: SegmentExposure): Pair<Set<Side>, Boolean> {
        // 해가 지평선 아래면 아무 쪽도 아니다. 앞·뒤 판정은 가중치(정면이면 0)가 아니라 고도로 한다.
        if (s.sun.altitude <= 0.0) return emptySet<Side>() to false
        return when (mode) {
            Mode.SUBWAY -> {
                if (s.weight <= 0.0) return emptySet<Side>() to false
                val sides = mutableSetOf(s.side)
                if (SubwayBenchRecommender.effectiveAltitude(s) <= benches.frontMaxEffectiveAltitude) sides += s.side.opposite
                sides to false
            }
            Mode.BUS, Mode.CAR ->
                if (abs(sin(Math.toRadians(s.relativeAngle))) < minSideness) emptySet<Side>() to true
                else setOf(s.side) to false
            Mode.WALK -> emptySet<Side>() to false
        }
    }

    /** Shared by the timeline: no second set of seat-light rules. */
    fun illumination(leg: Leg, segment: SegmentExposure): Pair<Set<Side>, Boolean> =
        if (leg.mode == Mode.SUBWAY && leg.groundLevel != GroundLevel.ABOVE) emptySet<Side>() to false
        else seatSides(leg.mode, segment)

    fun advise(
        route: Route,
        departure: Instant,
        goal: SunGoal,
        benchPreference: BenchPreference = BenchPreference.AVOID_BACK,
        totalDuration: Duration = route.duration,
        travelMode: TravelMode = TravelMode.TRANSIT,
    ): TripResult {
        val exposure = analyzer.analyze(route, departure)
        val seatByLeg = seats.recommend(exposure, goal).associateBy { it.legIndex }
        val benchByLeg = benches.recommend(exposure, benchPreference, goal).associateBy { it.legIndex }

        val baseLegs = exposure.legs.map { le ->
            var left = Duration.ZERO
            var right = Duration.ZERO
            var ahead = Duration.ZERO
            le.segments.forEach { s ->
                val (sides, isAhead) = illumination(le.leg, s)
                if (Side.LEFT in sides) left += s.duration
                if (Side.RIGHT in sides) right += s.duration
                if (isAhead) ahead += s.duration
            }
            when (le.leg.mode) {
                Mode.WALK -> LegResult(le.legIndex, le.leg, LegAdvice.Walk, Duration.ZERO, Duration.ZERO)
                Mode.SUBWAY -> {
                    val b = benchByLeg[le.legIndex]
                    val advice = when {
                        le.leg.groundLevel == GroundLevel.BELOW -> LegAdvice.Underground
                        le.leg.groundLevel == GroundLevel.UNKNOWN -> LegAdvice.UnknownGround
                        else -> when (val a = b?.advice) {
                        is BenchAdvice.Recommend -> LegAdvice.Sit(a.side)
                        BenchAdvice.NoDifference -> LegAdvice.NoDifference
                        BenchAdvice.NoSun, null -> LegAdvice.NoSun
                        }
                    }
                    LegResult(
                        le.legIndex, le.leg, advice, left, right,
                        leftBench = b?.left?.let { BenchTimes(it.backTime, it.frontTime) },
                        rightBench = b?.right?.let { BenchTimes(it.backTime, it.frontTime) },
                    )
                }
                Mode.BUS, Mode.CAR -> {
                    val sideless = left.isZero && right.isZero && !ahead.isZero
                    val advice = if (sideless) LegAdvice.NoDifference else when (val a = seatByLeg[le.legIndex]?.advice) {
                        is SeatAdvice.Recommend -> LegAdvice.Sit(
                            a.side,
                            a.carSeats,
                        )
                        SeatAdvice.NoDifference -> LegAdvice.NoDifference
                        SeatAdvice.NoSun, null -> LegAdvice.NoSun
                    }
                    LegResult(le.legIndex, le.leg, advice, left, right, aheadSun = ahead)
                }
            }
        }

        val legs = baseLegs.map { result ->
            val le = exposure.legs.first { it.legIndex == result.index }
            val initial = result.advice as? LegAdvice.Sit
                ?: if (result.advice == LegAdvice.NoDifference && le.leg.mode != Mode.WALK) LegAdvice.Sit(Side.RIGHT, similarSides = true) else null
            val detailed = initial?.let { detail(le, it, goal, travelMode, benchPreference) }
                ?.takeUnless { le.leg.mode == Mode.SUBWAY && result.advice == LegAdvice.NoDifference && it.switches.isEmpty() }
            val followed = detailed?.let { d -> le.segments.fold(Duration.ZERO) { total, segment ->
                val side = d.sideAt(segment.startTime)
                if (side in illumination(le.leg, segment).first) total + segment.duration else total
            } }
            result.copy(advice = detailed ?: result.advice, frontWindows = frontWindows(le, detailed), followedSun = followed)
        }
        val rides = legs.filter { it.advice !in setOf(LegAdvice.Walk, LegAdvice.Underground, LegAdvice.UnknownGround) }
        val sits = rides.mapNotNull { it.advice as? LegAdvice.Sit }
        val advice = when {
            rides.isEmpty() || rides.all { it.advice == LegAdvice.NoSun } -> TripAdvice.NoSun
            sits.isEmpty() -> TripAdvice.NoDifference
            sits.map { it.side }.distinct().size > 1 || sits.any { it.switches.isNotEmpty() } -> TripAdvice.Mixed
            sits.all { it.similarSides } && travelMode != TravelMode.TAXI -> TripAdvice.NoDifference
            else -> sits.maxBy { s -> rides.first { it.advice == s }.leg.duration }.let { TripAdvice.Sit(it.side, it.carSeats) }
        }

        var shade = Duration.ZERO
        var sun = Duration.ZERO
        rides.forEach { r ->
            val side = (r.advice as? LegAdvice.Sit)?.side
            val got = r.followedSun ?: (if (side == null) minOf(r.leftSun, r.rightSun) else r.sun(side)).coerceAtMost(r.leg.duration)
            sun += got
            shade += r.leg.duration - got
        }

        val leftTotal = rides.fold(Duration.ZERO) { acc, r -> acc + r.leftSun }
        val rightTotal = rides.fold(Duration.ZERO) { acc, r -> acc + r.rightSun }
        val aheadTotal = rides.fold(Duration.ZERO) { acc, r -> acc + r.aheadSun }
        val placement = when {
            leftTotal.isZero && rightTotal.isZero && aheadTotal.isZero -> null
            aheadTotal > maxOf(leftTotal, rightTotal) -> SunPlacement.AHEAD
            leftTotal > rightTotal -> SunPlacement.LEFT
            else -> SunPlacement.RIGHT
        }

        val total = route.duration.takeIf { !it.isZero } ?: Duration.ofSeconds(1)
        fun pos(t: Instant) = frac(Duration.between(departure, t), total)
        fun spans(side: Side): List<Span> = exposure.legs.flatMap { le ->
            le.segments.filter { side in illumination(le.leg, it).first }.map { Span(pos(it.startTime), pos(it.endTime)) }
        }.let(::merge)
        val walkSpans = exposure.legs.filter { it.leg.mode == Mode.WALK || (it.leg.mode == Mode.SUBWAY && it.leg.groundLevel != GroundLevel.ABOVE) }
            .map { Span(pos(it.startTime), pos(it.startTime + it.leg.duration)) }

        val glare = if (travelMode != TravelMode.TAXI && route.legs.any { it.mode == Mode.CAR }) {
            seats.glareIntervals(exposure).map {
                GlareWindow(it.startTime, it.endTime, Duration.between(departure, it.startTime).toMinutes(), Duration.between(departure, it.endTime).toMinutes())
            }
        } else emptyList()

        return TripResult(
            departure = departure,
            arrival = departure + maxOf(totalDuration, route.duration),
            modeledArrival = departure + route.duration,
            goal = goal,
            advice = advice,
            legs = legs,
            shadeIfFollowed = shade,
            sunIfFollowed = sun,
            aheadSun = aheadTotal,
            leftSeatSun = leftTotal,
            rightSeatSun = rightTotal,
            sunPlacement = placement,
            leftSpans = spans(Side.LEFT),
            rightSpans = spans(Side.RIGHT),
            walkSpans = walkSpans,
            glare = glare,
        )
    }

    /** Detailed positions share the existing illumination and ahead-cone decision. */
    internal fun detail(le: LegExposure, initial: LegAdvice.Sit, goal: SunGoal, travel: TravelMode, preference: BenchPreference): LegAdvice.Sit {
        val lit = le.segments.filter { it.sun.altitude > 0 }
        fun angle(s: SegmentExposure) = minOf(s.relativeAngle, 360 - s.relativeAngle)
        fun duration(predicate: (SegmentExposure) -> Boolean) = lit.filter(predicate).fold(Duration.ZERO) { a, s -> a + s.duration }
        val front = duration { illumination(le.leg, it).second && angle(it) < 90 && it.sun.altitude <= frontMaxAltitude }
        val rear = duration { illumination(le.leg, it).second && angle(it) > 90 && it.sun.altitude <= frontMaxAltitude }
        val sideFront = duration { !illumination(le.leg, it).second && angle(it) <= sideFrontMaxAngle }
        val sideRear = duration { !illumination(le.leg, it).second && angle(it) >= 180 - sideFrontMaxAngle }
        val reasons = mutableSetOf<SeatReason>()
        val frontScore = front + sideFront
        val rearScore = rear + sideRear
        var zone: SeatZone? = null
        if (le.leg.mode == Mode.BUS && frontScore != rearScore) {
            val avoidFront = (frontScore > rearScore) == (goal == SunGoal.AVOID)
            zone = if (avoidFront) SeatZone.MIDDLE_REAR else SeatZone.FRONT_MIDDLE
            reasons += if (frontScore > rearScore) { if (!front.isZero) SeatReason.LOW_FRONT else SeatReason.SIDE_FRONT }
                else { if (!rear.isZero) SeatReason.LOW_REAR else SeatReason.SIDE_REAR }
        }
        if (le.leg.mode == Mode.CAR) {
            if (!front.isZero) reasons += SeatReason.LOW_FRONT
            if (!rear.isZero) reasons += SeatReason.LOW_REAR
            if (travel == TravelMode.TAXI && initial.side == Side.LEFT) reasons += SeatReason.TAXI_RIGHT_DOOR
        }
        val cross = duration { illumination(le.leg, it).first.isNotEmpty() && SubwayBenchRecommender.effectiveAltitude(it) <= busMaxEffectiveAltitude }
        if (goal == SunGoal.AVOID && le.leg.mode == Mode.BUS && !cross.isZero && cross >= le.leg.duration.dividedBy(2)) reasons += SeatReason.CROSS_CABIN
        val carSeats = if (le.leg.mode == Mode.CAR) initial.carSeats.ifEmpty { SeatRecommender.carSeats(initial.side) }
            .filter { it != CarSeat.PASSENGER || (travel != TravelMode.TAXI && (front.isZero || goal == SunGoal.SEEK)) } else emptyList()
        val spot = SeatSpot(initial.side, zone, if (le.leg.mode == Mode.BUS) SeatPosition.WINDOW else null, carSeats.firstOrNull())
        val alternative = if (le.leg.mode == Mode.BUS) SeatSpot(if (goal == SunGoal.AVOID) initial.side.opposite else initial.side, zone, SeatPosition.AISLE) else null
        val runs = mutableListOf<Pair<Side?, MutableList<SegmentExposure>>>()
        le.segments.forEach { segment ->
            val sides = illumination(le.leg, segment).first
            val preferred = if (le.leg.mode == Mode.SUBWAY) {
                (benches.recommend(RouteExposure(listOf(le.copy(segments = listOf(segment)))), preference, goal).firstOrNull()?.advice as? BenchAdvice.Recommend)?.side
            } else sides.singleOrNull()?.let { if (goal == SunGoal.AVOID) it.opposite else it }
            val last = runs.lastOrNull()
            if (last != null && last.first == preferred && last.second.lastOrNull()?.endTime == segment.startTime) last.second += segment
            else runs += preferred to mutableListOf(segment)
        }
        // Start with the first sustained recommendation, avoiding a move at departure.
        val sustained = runs.filter { it.first != null && it.second.fold(Duration.ZERO) { a, s -> a + s.duration } >= switchMinDuration }
        val firstSide = if (le.leg.mode in setOf(Mode.BUS, Mode.SUBWAY)) sustained.firstOrNull()?.first ?: initial.side else initial.side
        var current = firstSide
        val switches = if (le.leg.mode !in setOf(Mode.BUS, Mode.SUBWAY)) emptyList() else sustained.mapNotNull { (side, segments) ->
            if (side == current || side == null) null else {
                current = side
                SeatSwitch(segments.first().startTime, null, spot.copy(side = side))
            }
        }
        return initial.copy(side = firstSide, carSeats = carSeats, spot = spot.copy(side = firstSide), alternative = alternative?.copy(side = if (goal == SunGoal.AVOID) firstSide.opposite else firstSide), reasons = reasons, switches = switches)
    }

    private fun frontWindows(le: LegExposure, advice: LegAdvice.Sit?): List<FrontSunWindow> {
        if (le.leg.mode != Mode.SUBWAY || le.leg.groundLevel != GroundLevel.ABOVE || advice == null) return emptyList()
        val out = mutableListOf<FrontSunWindow>()
        le.segments.filter { advice.sideAt(it.startTime) in illumination(le.leg, it).first && it.side != advice.sideAt(it.startTime) }.forEach { s ->
            val side = advice.sideAt(s.startTime)
            val last = out.lastOrNull()
            if (last?.end == s.startTime && last.side == side) out[out.lastIndex] = last.copy(end = s.endTime)
            else out += FrontSunWindow(s.startTime, s.endTime, side)
        }
        return out
    }

    private fun frac(d: Duration, total: Duration): Float =
        (d.toMillis().toDouble() / total.toMillis()).coerceIn(0.0, 1.0).toFloat()

    /** 맞닿거나 아주 가까운(0.5% 이내) 구간을 합친다. */
    private fun merge(spans: List<Span>): List<Span> {
        val out = mutableListOf<Span>()
        spans.sortedBy { it.start }.forEach { s ->
            val last = out.lastOrNull()
            if (last != null && s.start - last.end <= 0.005f) out[out.lastIndex] = Span(last.start, maxOf(last.end, s.end))
            else out += s
        }
        return out
    }

    companion object {
        const val AHEAD_CONE_DEG = 25.0
        const val FRONT_MAX_ALTITUDE = 25.0
        const val SIDE_FRONT_MAX_ANGLE = 60.0
        const val BUS_MAX_EFFECTIVE_ALTITUDE = 30.0
        const val SWITCH_MIN_MINUTES = 5L
    }
}

/** Positions describe relative locations, never a vehicle-specific seat count. */
enum class SeatZone { FRONT_MIDDLE, MIDDLE_REAR, FRONT, MIDDLE, REAR }
enum class SeatPosition { WINDOW, AISLE }
enum class SeatReason { LOW_FRONT, LOW_REAR, SIDE_FRONT, SIDE_REAR, CROSS_CABIN, TAXI_RIGHT_DOOR }
data class SeatSpot(val side: Side, val zone: SeatZone? = null, val position: SeatPosition? = null, val carSeat: CarSeat? = null)
data class SeatSwitch(val at: Instant, val stopName: String?, val to: SeatSpot)
data class FrontSunWindow(val start: Instant, val end: Instant, val side: Side)

fun LegAdvice.Sit.spotAt(time: Instant): SeatSpot {
    var low = 0
    var high = switches.size
    while (low < high) {
        val mid = (low + high) ushr 1
        if (switches[mid].at <= time) low = mid + 1 else high = mid
    }
    return if (low == 0) spot else switches[low - 1].to
}
fun LegAdvice.Sit.sideAt(time: Instant): Side = spotAt(time).side
