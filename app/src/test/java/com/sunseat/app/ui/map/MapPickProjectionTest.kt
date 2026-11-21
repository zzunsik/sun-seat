package com.sunseat.app.ui.map

import com.sunseat.app.domain.LatLng
import org.junit.Assert.assertEquals
import org.junit.Test

/** 테스트 지도에서 핀(화면 가운데) 좌표를 구하는 역투영(#85). */
class MapPickProjectionTest {
    @Test fun unprojectInvertsProject() {
        val projection = MapProjection(listOf(LatLng(37.5547, 126.9707), LatLng(37.4979, 127.0276)), 1170f, 1800f, 120f)
        listOf(LatLng(37.55, 126.98), LatLng(37.5, 127.02), LatLng(37.53, 127.0)).forEach { point ->
            val (x, y) = projection.project(point)
            val back = projection.unproject(x, y)
            assertEquals(point.latitude, back.latitude, 1e-6)
            assertEquals(point.longitude, back.longitude, 1e-6)
        }
    }

    @Test fun singlePointCentreMapsToItself() {
        val pin = LatLng(37.5665, 126.978)
        val projection = MapProjection(listOf(pin), 1170f, 1800f, 120f)
        val centre = projection.unproject(585f, 900f)
        assertEquals(pin.latitude, centre.latitude, 1e-6)
        assertEquals(pin.longitude, centre.longitude, 1e-6)
    }
}
