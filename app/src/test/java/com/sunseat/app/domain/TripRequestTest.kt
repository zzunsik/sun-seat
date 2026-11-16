package com.sunseat.app.domain

import java.time.*
import org.junit.Assert.assertEquals
import org.junit.Test

class TripRequestTest {
    private val zone = ZoneId.of("Asia/Seoul")
    private val selected = LocalDateTime.of(2026, 11, 17, 9, 0)
    private val place = NamedPlace("A", LatLng(37.5, 127.0))
    private val request = TripRequest(place, place, TravelMode.TRANSIT, selected, SunGoal.AVOID)
    @Test fun arrivalSubtractsTotalDurationAcrossMidnight() {
        val duration = Duration.ofHours(10)
        assertEquals(selected.atZone(zone).toInstant() - duration, request.copy(timeMode = TimeMode.ARRIVE).departureInstant(duration, Instant.EPOCH, zone))
    }
    @Test fun departureAndNowStayUnchanged() {
        assertEquals(selected.atZone(zone).toInstant(), request.departureInstant(Duration.ofHours(1), Instant.EPOCH, zone))
        assertEquals(Instant.EPOCH, request.copy(departure = null).departureInstant(Duration.ofHours(1), Instant.EPOCH, zone))
    }
}
