package com.sunseat.app.domain

import java.time.Duration
import java.time.Instant

/** One instant on the modeled leg timeline, using exactly TripAdvisor's light classification. */
data class TimelinePoint(
    val time: Instant, val position: LatLng?, val heading: Double?, val sun: SunPosition?,
    val litSides: Set<Side>, val ahead: Boolean, val legIndex: Int, val leg: Leg,
    val recommended: Side?, val routeProgress: Float, val sunSide: Side?,
) {
    val undergroundOrUnknown: Boolean get() = leg.mode == Mode.SUBWAY && leg.groundLevel != GroundLevel.ABOVE
}

/** Index built once. Each lookup is O(log n), with constant-time interpolation afterward. */
class TripTimeline(
    route: Route, val result: TripResult,
    source: SunPositionSource = SunCalculator,
    private val advisor: TripAdvisor = TripAdvisor(),
) {
    private data class Frame(val start: Instant, val end: Instant, val leg: Leg, val index: Int, val exposure: SegmentExposure?, val distanceStart: Double, val distance: Double)
    private val frames: List<Frame>
    private val distance: Double
    val duration: Duration = Duration.between(result.departure, result.modeledArrival).coerceAtLeast(Duration.ZERO)
    val boundaries: List<Float>
    init {
        val analyzer = ExposureAnalyzer(source)
        var start = result.departure
        var traveled = 0.0
        val built = mutableListOf<Frame>()
        val ticks = mutableListOf<Float>()
        route.legs.forEachIndexed { index, leg ->
            if (duration.toMillis() > 0) ticks += (Duration.between(result.departure, start).toMillis().toFloat() / duration.toMillis()).coerceIn(0f, 1f)
            // Preserve underground movement geometry; illumination still uses the original leg below.
            val segments = analyzer.analyzeLeg(index, leg.copy(groundLevel = GroundLevel.ABOVE), start).segments
            if (segments.isEmpty() && !leg.duration.isZero) built += Frame(start, start + leg.duration, leg, index, null, traveled, 0.0)
            segments.filter { it.endTime > it.startTime }.forEach { segment ->
                val length = ExposureAnalyzer.distanceMeters(segment.from, segment.to)
                built += Frame(segment.startTime, segment.endTime, leg, index, segment, traveled, length)
                traveled += length
            }
            start += leg.duration
        }
        frames = built
        distance = traveled
        boundaries = ticks.distinct()
    }

    fun at(time: Instant): TimelinePoint? {
        if (frames.isEmpty()) return null
        val clamped = time.coerceIn(result.departure, result.modeledArrival)
        var low = 0
        var high = frames.size
        while (low < high) {
            val middle = (low + high) ushr 1
            if (frames[middle].start <= clamped) low = middle + 1 else high = middle
        }
        val frame = frames[(low - 1).coerceIn(0, frames.lastIndex)]
        val length = Duration.between(frame.start, frame.end).toNanos().toDouble()
        val fraction = if (length <= 0) 0.0 else (Duration.between(frame.start, clamped).toNanos() / length).coerceIn(0.0, 1.0)
        val segment = frame.exposure
        val position = segment?.let { LatLng(it.from.latitude + (it.to.latitude - it.from.latitude) * fraction, it.from.longitude + (it.to.longitude - it.from.longitude) * fraction) } ?: frame.leg.path.firstOrNull()
        val illumination = segment?.let { advisor.illumination(frame.leg, it) } ?: (emptySet<Side>() to false)
        return TimelinePoint(clamped, position, segment?.heading, segment?.sun, illumination.first, illumination.second, frame.index, frame.leg,
            (result.legs.getOrNull(frame.index)?.advice as? LegAdvice.Sit)?.side,
            if (distance > 0) ((frame.distanceStart + frame.distance * fraction) / distance).toFloat().coerceIn(0f, 1f) else 0f, segment?.side)
    }
}
