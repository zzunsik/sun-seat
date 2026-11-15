package com.sunseat.app.ui.map

import android.content.Context
import androidx.core.graphics.createBitmap
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.kakao.vectormap.GestureType
import com.kakao.vectormap.KakaoMap
import com.kakao.vectormap.KakaoMapReadyCallback
import com.kakao.vectormap.MapLifeCycleCallback
import com.kakao.vectormap.MapView
import com.kakao.vectormap.camera.CameraUpdateFactory
import com.kakao.vectormap.label.Label
import com.kakao.vectormap.label.LabelOptions
import com.kakao.vectormap.label.LabelStyle
import com.kakao.vectormap.label.TransformMethod
import com.kakao.vectormap.route.RouteLineOptions
import com.kakao.vectormap.route.RouteLinePattern
import com.kakao.vectormap.route.RouteLineSegment
import com.kakao.vectormap.route.RouteLineStyle
import com.kakao.vectormap.route.RouteLineStylesSet
import com.kakao.vectormap.route.RouteLineStyles
import com.sunseat.app.BuildConfig
import com.sunseat.app.R
import com.sunseat.app.domain.LatLng
import com.sunseat.app.domain.Mode
import com.sunseat.app.domain.Route
import com.sunseat.app.ui.theme.Sizes
import com.sunseat.app.ui.theme.Spacing
import com.sunseat.app.ui.theme.SunSeatTheme

@Composable
internal fun NativeMap(state: MapState, modifier: Modifier = Modifier, keyAvailable: Boolean = BuildConfig.KAKAO_NATIVE_APP_KEY.isNotBlank()) {
    if (!keyAvailable) {
        MapNotice(R.string.map_missing_key, modifier)
        return
    }
    val context = LocalContext.current
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    val density = LocalDensity.current
    val scheme = SunSeatTheme.scheme
    val ink = scheme.onSurface.toArgb()
    val surface = scheme.surface.toArgb()
    val muted = scheme.outline.toArgb()
    val sun = SunSeatTheme.colors.onSunContainer.toArgb()
    val latest by rememberUpdatedState(state)
    val metrics = with(density) { MapMetrics(Sizes.mapMarker.toPx(), Sizes.mapRoute.toPx(), Sizes.mapWalk.toPx(), Sizes.mapSubway.toPx(), Sizes.mapInset.toPx(), Sizes.mapSunRay.toPx()) }
    val session = remember(context, lifecycle, ink, surface, muted, sun, metrics) { MapSession(context, ink, surface, muted, sun, metrics) }
    var ready by remember(session) { mutableStateOf(false) }
    var failed by remember(session) { mutableStateOf(false) }
    DisposableEffect(session, lifecycle) {
        session.view.start(object : MapLifeCycleCallback() {
            override fun onMapDestroy() = Unit
            override fun onMapError(error: Exception) { session.view.post { if (!session.closed) failed = true } }
        }, object : KakaoMapReadyCallback() {
            override fun getPosition(): com.kakao.vectormap.LatLng =
                (latest.points().firstOrNull() ?: LatLng(37.5665, 126.9780)).sdk()
            override fun onMapReady(map: KakaoMap) {
                session.view.post {
                    if (!session.closed) { session.map = map; ready = true; session.update(latest, density.density) }
                }
            }
        })
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_RESUME -> if (!session.closed) session.view.resume()
                Lifecycle.Event.ON_PAUSE -> if (!session.closed) session.view.pause()
                Lifecycle.Event.ON_DESTROY -> session.close()
                else -> Unit
            }
        }
        lifecycle.addObserver(observer)
        if (lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)) session.view.resume()
        onDispose { lifecycle.removeObserver(observer); session.close() }
    }
    Box(modifier) {
        androidx.compose.runtime.key(session) {
            AndroidView(factory = { session.view }, modifier = Modifier.fillMaxSize(), update = { if (ready && !failed) session.update(state, density.density) })
        }
        if (failed) MapNotice(R.string.map_error, Modifier.fillMaxSize())
        else if (!ready) MapNotice(R.string.map_loading, Modifier.fillMaxSize())
    }
}

@Composable
internal fun MapNotice(message: Int, modifier: Modifier = Modifier) {
    androidx.compose.material3.Surface(modifier, color = SunSeatTheme.scheme.surfaceContainer) {
        Box(Modifier.padding(Spacing.l), contentAlignment = Alignment.Center) {
            Text(stringResource(message), color = SunSeatTheme.scheme.onSurfaceVariant, style = androidx.compose.material3.MaterialTheme.typography.bodyMedium)
        }
    }
}

private data class MapMetrics(val marker: Float, val route: Float, val walk: Float, val subway: Float, val inset: Float, val ray: Float)
private fun LatLng.sdk(): com.kakao.vectormap.LatLng = com.kakao.vectormap.LatLng.from(latitude, longitude)

/** 네이티브 객체의 소유권과 갱신을 한 곳에서 관리한다. */
private class MapSession(context: Context, private val ink: Int, private val surface: Int, private val muted: Int, private val sun: Int, private val metrics: MapMetrics) {
    val view = MapView(context).apply { setFinishManually(true) }
    var map: KakaoMap? = null
    var closed = false
        private set
    private var geometry: Triple<List<Route>, Int, Pair<com.sunseat.app.domain.NamedPlace?, com.sunseat.app.domain.NamedPlace?>>? = null
    private var padding: MapPadding? = null
    private var cursor: Label? = null
    private var ray: Label? = null
    private var lastCursor: MapCursor? = null
    private var following = false
    private var gesture: () -> Unit = {}
    private val startIcon = marker(0, ink)
    private val endIcon = marker(1, ink)
    private val vehicleIcon = marker(2, ink)
    private val rayIcon = marker(3, sun)

    fun close() { if (!closed) { closed = true; map = null; view.pause(); view.finish() } }

    fun update(state: MapState, density: Float) {
        if (closed) return
        val target = map ?: return
        gesture = state.onGesture
        if (geometry == null) target.setOnCameraMoveStartListener { _, type -> if (type != GestureType.Unknown) gesture() }
        val next = Triple(state.routes, state.selectedRoute, state.origin to state.destination)
        val geometryChanged = geometry != next
        if (geometryChanged) {
            geometry = next
            target.routeLineManager?.layer?.let { layer ->
                layer.removeAll()
                state.routes.withIndex().sortedBy { it.index == state.selectedRoute }.forEach { (index, route) ->
                    val styles = mutableListOf<RouteLineStyles>()
                    val segments = route.legs.filter { it.path.size > 1 }.map { leg ->
                        val color = if (index == state.selectedRoute) ink else muted
                        val style = when (leg.mode) {
                            Mode.WALK -> RouteLineStyle.from(RouteLinePattern.from(dot(color), metrics.walk * 2))
                            Mode.SUBWAY -> RouteLineStyle.from(metrics.subway, color)
                            else -> RouteLineStyle.from(metrics.route, color)
                        }
                        val lineStyles = RouteLineStyles.from(style)
                        styles += lineStyles
                        RouteLineSegment.from(leg.path.map { it.sdk() }, lineStyles)
                    }
                    if (segments.isNotEmpty()) layer.addRouteLine(RouteLineOptions.from(segments).setStylesSet(RouteLineStylesSet.from(styles)))
                }
            }
            target.labelManager?.layer?.let { layer ->
                layer.removeAll(); cursor = null; ray = null; lastCursor = null
                state.origin?.let { layer.addLabel(LabelOptions.from(it.location.sdk()).setStyles(LabelStyle.from(startIcon).setApplyDpScale(false))) }
                state.destination?.let { layer.addLabel(LabelOptions.from(it.location.sdk()).setStyles(LabelStyle.from(endIcon).setApplyDpScale(false))) }
            }
        }
        if (geometryChanged || padding != state.padding) {
            padding = state.padding
            target.setPadding(0, (state.padding.top.value * density).toInt(), 0, (state.padding.bottom.value * density).toInt())
            val points = state.points()
            if (points.isNotEmpty()) target.moveCamera(CameraUpdateFactory.fitMapPoints(points.map { it.sdk() }.toTypedArray(), metrics.inset.toInt(), 17))
        }
        val position = state.cursor
        if (position == null) { cursor?.hide(); ray?.hide() }
        else {
            val layer = target.labelManager?.layer ?: return
            if (cursor == null) cursor = layer.addLabel(LabelOptions.from(position.position.sdk()).setStyles(LabelStyle.from(vehicleIcon).setApplyDpScale(false)).setTransform(TransformMethod.AbsoluteRotation))
            if (ray == null) ray = layer.addLabel(LabelOptions.from(position.position.sdk()).setStyles(LabelStyle.from(rayIcon).setApplyDpScale(false).setAnchorPoint(0.5f, 1f)).setTransform(TransformMethod.AbsoluteRotation))
            cursor?.show()
            if (position != lastCursor) {
                cursor?.moveTo(position.position.sdk()); cursor?.rotateTo(Math.toRadians(position.heading).toFloat())
                ray?.moveTo(position.position.sdk()); ray?.rotateTo(Math.toRadians(position.sunAzimuth).toFloat())
                ray?.scaleTo(1f, (1 - position.sunAltitude.coerceIn(0.0, 90.0) / 120).toFloat())
            }
            if (position.sunAltitude > 0) ray?.show() else ray?.hide()
            if (state.followCursor && (position != lastCursor || !following)) target.moveCamera(CameraUpdateFactory.newCenterPosition(position.position.sdk()))
        }
        following = state.followCursor
        lastCursor = position
    }

    /** 직접 만든 원·사각·차량 화살표와 짧은 해 방향 선. */
    private fun marker(kind: Int, color: Int): Bitmap {
        val width = metrics.marker.toInt().coerceAtLeast(1)
        val height = if (kind == 3) metrics.ray.toInt().coerceAtLeast(1) else width
        return createBitmap(width, height).also { bitmap ->
            val canvas = Canvas(bitmap)
            val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply { this.color = color; strokeWidth = metrics.walk; strokeCap = Paint.Cap.ROUND }
            val mid = width / 2f
            val radius = width / 3f
            when (kind) {
                0 -> { paint.color = surface; canvas.drawCircle(mid, mid, radius, paint); paint.color = color; paint.style = Paint.Style.STROKE; canvas.drawCircle(mid, mid, radius, paint) }
                1 -> canvas.drawRect(mid - radius, mid - radius, mid + radius, mid + radius, paint)
                2 -> canvas.drawPath(Path().apply { moveTo(mid, 0f); lineTo(width.toFloat(), height.toFloat()); lineTo(mid, height * 0.7f); lineTo(0f, height.toFloat()); close() }, paint)
                3 -> { canvas.drawLine(mid, height.toFloat(), mid, radius, paint); canvas.drawLine(mid, radius, mid - radius / 2, radius * 2, paint); canvas.drawLine(mid, radius, mid + radius / 2, radius * 2, paint) }
            }
        }
    }
    private fun dot(color: Int): Bitmap = createBitmap(metrics.walk.toInt().coerceAtLeast(1), metrics.walk.toInt().coerceAtLeast(1)).also { it.eraseColor(color) }
}
