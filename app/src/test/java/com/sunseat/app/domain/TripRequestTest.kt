package com.sunseat.app.domain

import java.time.*
import org.junit.Assert.assertEquals
import org.junit.Test

class TripRequestTest {
    private val zone = ZoneId.of("Asia/Seoul")
    private val selected = LocalDateTime.of(2026, 11, 17, 9, 0)
    private val place = NamedPlace("A", LatLng(37.5, 127.0))
    private val request = TripRequest(place, place, TravelMode.TRANSIT, selected, SunGoal.AVOID)
    @Test fun departureAndNowUseSuppliedZoneAndFreshInstant() {
        assertEquals(selected.atZone(zone).toInstant(), request.departureInstant(Instant.EPOCH, zone))
        assertEquals(selected.atZone(ZoneId.of("UTC")).toInstant(), request.departureInstant(Instant.EPOCH, ZoneId.of("UTC")))
        assertEquals(Instant.EPOCH, request.copy(departure = null).departureInstant(Instant.EPOCH, zone))
        val later = Instant.EPOCH.plusSeconds(3600)
        assertEquals(later, request.copy(departure = null).departureInstant(later, zone))
    }
}
