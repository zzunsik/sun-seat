package com.sunseat.app.ui.map

import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.sunseat.app.domain.LatLng
import com.sunseat.app.domain.Route
import com.sunseat.app.domain.NamedPlace
import kotlin.math.cos

/** 지도에 필요한 값만 전달한다. 진행 위치 변경은 경로선을 다시 만들지 않는다. */
data class MapState(
    val routes: List<Route> = emptyList(),
    val selectedRoute: Int = 0,
    val origin: NamedPlace? = null,
    val destination: NamedPlace? = null,
    val cursor: MapCursor? = null,
    val followCursor: Boolean = false,
    val padding: MapPadding = MapPadding(),
    val onGesture: () -> Unit = {},
    val routeProgress: Float? = null,
    val showDecorations: Boolean = true,
    val focus: MapFocus? = null,
)

/** A new sequence requests one camera move, even when the coordinate is unchanged. */
data class MapFocus(val location: LatLng, val sequence: Long)
data class MapCursor(val position: LatLng, val heading: Double, val sunAzimuth: Double, val sunAltitude: Double)
data class MapPadding(val top: Dp = 0.dp, val bottom: Dp = 0.dp)

/** 테스트 지도는 실제 좌표를 종횡비를 유지해 투영한다. */
internal class MapProjection(points: List<LatLng>, private val width: Float, private val height: Float, private val inset: Float) {
    private val centerLat = points.map { it.latitude }.average().takeIf { it.isFinite() } ?: 0.0
    private val longitudeScale = cos(Math.toRadians(centerLat)).coerceAtLeast(0.01)
    private val minX = points.minOfOrNull { it.longitude * longitudeScale } ?: 0.0
    private val maxX = points.maxOfOrNull { it.longitude * longitudeScale } ?: 0.0
    private val minY = points.minOfOrNull { it.latitude } ?: 0.0
    private val maxY = points.maxOfOrNull { it.latitude } ?: 0.0
    private val scale = minOf((width - 2 * inset).coerceAtLeast(1f) / (maxX - minX).coerceAtLeast(0.0001), (height - 2 * inset).coerceAtLeast(1f) / (maxY - minY).coerceAtLeast(0.0001))
    fun project(point: LatLng): Pair<Float, Float> =
        ((width / 2 + (point.longitude * longitudeScale - (minX + maxX) / 2) * scale).toFloat()) to
        ((height / 2 - (point.latitude - (minY + maxY) / 2) * scale).toFloat())
}
internal fun MapState.points(): List<LatLng> = routes.flatMap { it.legs }.flatMap { it.path } + listOfNotNull(origin?.location, destination?.location)

/** Low sun is visually stronger; all rays retain the semantic sun color. */
internal fun sunRayOpacity(altitude: Double): Float = (1.0 - 0.30 * altitude.coerceIn(0.0, 90.0) / 90.0).toFloat()
