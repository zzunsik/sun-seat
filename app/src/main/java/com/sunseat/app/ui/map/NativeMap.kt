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
import kotlin.math.pow

@Composable
internal fun NativeMap(state: MapState, modifier: Modifier = Modifier, keyAvailable: Boolean = BuildConfig.KAKAO_NATIVE_APP_KEY.isNotBlank()) {
    if (!keyAvailable) {
        val unavailable by rememberUpdatedState(state.onUnavailable)
        androidx.compose.runtime.LaunchedEffect(Unit) { unavailable() }
        MapNotice(R.string.map_missing_key, modifier)
        return
    }
    val context = LocalContext.current
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    val density = LocalDensity.current
    val scheme = com.sunseat.app.ui.theme.LightScheme
    val ink = scheme.onSurface.toArgb()
    val surface = scheme.surface.toArgb()
    val sun = com.sunseat.app.ui.theme.LightSunSeatColors.sun.toArgb()
    val latest by rememberUpdatedState(state)
    val labelSize = androidx.compose.material3.MaterialTheme.typography.labelMedium.fontSize
    val origin = stringResource(R.string.input_origin)
    val destination = stringResource(R.string.input_destination)
    val bus = stringResource(R.string.common_bus)
    val subway = stringResource(R.string.common_subway)
    val moreLines = stringResource(R.string.map_line_more)
    val metrics = with(density) {
        MapMetrics(Sizes.mapMarker.toPx(), Sizes.mapRouteWalk.toPx(), Sizes.mapInset.toPx(), Spacing.s.toPx(), (if(state.routeProgress!=null) Sizes.simulationSunRay else Sizes.mapSunRay).toPx(),
            Sizes.mapStopMarker.toPx(), Sizes.mapRouteArrow.toPx(), Sizes.mapStopBorder.toPx(), Sizes.mapMarkerTail.toPx(), Spacing.xs.toPx(), labelSize.toPx(), origin, destination, Sizes.mapLabelHeight.toPx(), bus, subway, moreLines)
    }
    val session = remember(context, lifecycle, ink, surface, sun, metrics) { MapSession(context, ink, surface, sun, metrics) }
    var ready by remember(session) { mutableStateOf(false) }
    var failed by remember(session) { mutableStateOf(false) }
    DisposableEffect(session, lifecycle) {
        session.view.start(object : MapLifeCycleCallback() {
            override fun onMapDestroy() = Unit
            override fun onMapError(error: Exception) { session.view.post { if (!session.closed) failed = true } }
        }, object : KakaoMapReadyCallback() {
            override fun getPosition(): com.kakao.vectormap.LatLng =
                (latest.center?.location ?: latest.points().firstOrNull() ?: LatLng(37.5665, 126.9780)).sdk()
            // The map picker (#85) opens at street level so the pin lands on a building, not a district.
            override fun getZoomLevel(): Int = if (latest.center != null) PICK_ZOOM else super.getZoomLevel()
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
        if (failed) {
            androidx.compose.runtime.LaunchedEffect(Unit) { latest.onUnavailable() }
            MapNotice(R.string.map_error, Modifier.fillMaxSize())
        }
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

/** 픽셀 값(밀도 적용). 경로선 두께는 지도 준비 후 mapDpScale로 따로 변환한다([RouteLinePolicy.sdkWidth]). */
private const val STOP_RANK = 10L
private const val STOP_NAME_RANK = 5L
private const val PLACE_RANK = 20L
private const val PICK_ZOOM = 17
private const val ARROW_RANK = 1L
private const val MAX_WALK_DOTS = 150
/** SDK lines have round caps of half the width on each end, so a short stub renders as a round dot of the line width. */
private const val DOT_STUB = 0.2
private const val LOG_TAG = "SunSeatMapCamera"
private const val LOG_DELAY_MS = 400L
private const val SCALE_PROBE_PX = 100

private data class MapMetrics(
    val marker: Float, val walk: Float, val inset: Float, val edge: Float, val ray: Float,
    val stop: Float, val arrow: Float, val stopBorder: Float, val tail: Float, val textPadding: Float, val text: Float,
    val origin: String, val destination: String, val labelHeight: Float,
    val bus: String, val subway: String, val moreLines: String,
) {
    /** 승차·환승 라벨: 첫 노선(여럿이면 "외 n"), 없으면 수단 이름. 전체 이름은 TalkBack 요약에 있다. */
    fun stopLabel(stop: StopMarker): String = when {
        stop.lines.isEmpty() -> if (stop.mode == Mode.SUBWAY) subway else bus
        stop.lines.size == 1 -> stop.lines.first()
        else -> String.format(moreLines, stop.lines.first(), stop.lines.size - 1)
    }
}
/** Playback icons rotate around the current route coordinate. */
internal fun playbackLabelStyle(bitmap: Bitmap): LabelStyle = LabelStyle.from(bitmap).setApplyDpScale(false).setAnchorPoint(.5f,.5f)
private fun LatLng.sdk(): com.kakao.vectormap.LatLng = com.kakao.vectormap.LatLng.from(latitude, longitude)

/** 네이티브 객체의 소유권과 갱신을 한 곳에서 관리한다. */
private class MapSession(context: Context, private val ink: Int, private val surface: Int, private val sun: Int, private val metrics: MapMetrics) {
    private val typeface = runCatching { androidx.core.content.res.ResourcesCompat.getFont(context, R.font.pretendard_variable) }.getOrNull()
    val view = MapView(context).apply { setFinishManually(true) }
    var map: KakaoMap? = null
    var closed = false
        private set
    private var geometry: Triple<List<Route>, Int, Pair<com.sunseat.app.domain.NamedPlace?, com.sunseat.app.domain.NamedPlace?>>? = null
    /** 맞춤 대상 발자국(#168). 경로가 바뀔 때만 잰다. */
    private var footprints: List<Footprint> = emptyList()
    /** 지금 줌에서 잰 배율. 카메라 이동이 끝날 때마다 다시 잰다. */
    private var scale: MapScale? = null
    private var lastState: MapState? = null
    private var lastDensity = 1f
    private var lastViewport: Viewport? = null
    private var reported: CameraStatus? = null
    private var logoBottom: Int? = null
    private val fitPolicy = MapFitPolicy()
    private var cursor: Label? = null
    private var ray: Label? = null
    private var myLocation: Label? = null
    private var cameraIdle: ((LatLng) -> Unit)? = null
    private var lastCursor: MapCursor? = null
    private var playbackMode = false
    private val progressLines = mutableListOf<com.kakao.vectormap.route.RouteLine>()
    private var lastProgress: Float? = null
    private var lastRayAltitude: Double? = null
    private var lastVehicleLight: Int? = null
    private var gesture: () -> Unit = {}
    /** 출발·도착 표식의 몸통 방향별 그림(#170: 겹치면 비킨다). */
    private val startIcons = PinSide.entries.associateWith { placeIcon(metrics.origin, filled = false, side = it) }
    private val endIcons = PinSide.entries.associateWith { placeIcon(metrics.destination, filled = true, side = it) }
    private val startIcon = startIcons.getValue(PinSide.UP)
    private val endIcon = endIcons.getValue(PinSide.UP)
    private val arrows = HashMap<Int, Bitmap>()
    private var decorRoute: Route? = null
    private var decorZoom: Int? = null
    private var lineDensity = 1f
    private val decorations = mutableListOf<Label>()
    /** 재생 중 차량과 겹치면 숨기는 글자 표식(출발·도착·노선 배지)과 그 좌표(#146). */
    /** 정류장 노선 이름표와 지금 정류장에서 떨어진 거리(#153), 출발·도착 표식과 그 좌표·몸통 폭. */
    private class StopName(val label: Label, val stop: StopMarker, val name: String, val index: Int, var offset: Int, var hidden: Boolean = false)
    private val stopNames = mutableListOf<StopName>()
    /** 출발·도착 표식, 지금 몸통 방향과 숨김. */
    private class PinLabel(val label: Label, val key: MarkKey, val icons: Map<PinSide, Bitmap>, var side: PinSide = PinSide.UP, var hidden: Boolean = false)
    private val pinLabels = mutableListOf<PinLabel>()
    private var metersPerPx = 0.0
    private var coverState: MapState? = null
    private val walkLines = mutableListOf<com.kakao.vectormap.route.RouteLine>()
    private val vehicleIcon = marker(2, ink)
    private val rayIcon = marker(3, sun)
    private val myLocationIcon = marker(4, ink)

    init {
        // 뷰 크기를 알기 전의 맞춤 요청은 정책에 넘기지 않고 남겨 두었다가, 크기가 정해지거나 바뀌면 마지막 상태로 다시 적용한다(#168).
        view.addOnLayoutChangeListener { _, left, top, right, bottom, oldLeft, oldTop, oldRight, oldBottom ->
            if (right - left != oldRight - oldLeft || bottom - top != oldBottom - oldTop) view.post { lastState?.let { update(it, lastDensity) } }
        }
    }

    fun close() { if (!closed) { closed = true; map = null; view.pause(); view.finish() } }

    fun update(state: MapState, density: Float) {
        if (closed) return
        val target = map ?: return
        gesture = state.onGesture
        cameraIdle = state.onCameraIdle
        if (geometry == null) {
            target.setOnCameraMoveStartListener { _, type -> if (type != GestureType.Unknown) view.post { if (!closed) { fitPolicy.gesture(); reportStatus(); gesture() } } }
            target.setOnCameraMoveEndListener { moved, position, _ ->
                view.post { if (map === moved) { measureScale(moved)?.let { scale = it }; scheduleLog() } }
                if (position.zoomLevel != decorZoom) view.post { if (map === moved) decorate(moved) }
                else view.post { if (map === moved) layoutMarks(moved) } // 회전 등 줌 밖의 카메라 변화(#153)
                // Map picker (#85): the pin sits at the view centre with no padding, so the camera target is the pin.
                cameraIdle?.let { report -> val at = LatLng(position.position.latitude, position.position.longitude); view.post { if (map === moved) report(at) } }
            }
        }
        val next = Triple(state.routes, state.selectedRoute, state.origin to state.destination)
        val geometryChanged = geometry != next || playbackMode != (state.routeProgress != null)
        if (geometryChanged) {
            geometry = next
            playbackMode = state.routeProgress != null
            progressLines.clear(); lastProgress = null
            target.routeLineManager?.layer?.let { layer ->
                layer.removeAll()
                val scale = target.mapDpScale
                fun add(legs: List<com.sunseat.app.domain.Leg>, role: RouteRole): com.kakao.vectormap.route.RouteLine? {
                    val styles = mutableListOf<RouteLineStyles>()
                    val segments = legs.filter { it.path.size > 1 }.map { leg ->
                        val lineStyles = RouteLineStyles.from(sdkStyle(RouteLinePolicy.forLeg(leg.mode, leg.lineName, role), density, scale))
                        styles += lineStyles
                        RouteLineSegment.from(leg.path.map { it.sdk() }, lineStyles)
                    }
                    return if (segments.isEmpty()) null
                    else layer.addRouteLine(RouteLineOptions.from(segments).setStylesSet(RouteLineStylesSet.from(styles)).setZOrder(legs.maxOf { RouteLinePolicy.forLeg(it.mode, it.lineName, role).layer }))
                }
                // DESIGN 11.1 layers: alternates(1) < walking(2) < rides(3) < passed playback overlay.
                state.routes.forEachIndexed { index, route -> if (index != state.selectedRoute) add(route.legs, RouteRole.ALTERNATE) }
                state.routes.getOrNull(state.selectedRoute)?.let { route ->
                    // Walking dots (layer 2) and direction chevrons are zoom-dependent geometry: see decorate().
                    add(route.legs.filter { it.mode != Mode.WALK }, RouteRole.SELECTED)
                    if (playbackMode) add(route.legs, RouteRole.PASSED)?.let { progressLines += it }
                }
            }
            target.labelManager?.layer?.let { layer ->
                layer.removeAll(); cursor = null; ray = null; myLocation = null; lastCursor = null; lastRayAltitude = null; lastVehicleLight=null; stopNames.clear(); pinLabels.clear()
                state.routes.getOrNull(state.selectedRoute)?.let { route ->
                    RouteLinePolicy.stops(route).forEachIndexed { index, stop ->
                        layer.addLabel(LabelOptions.from(stop.location.sdk()).setRank(STOP_RANK).setStyles(LabelStyle.from(stopIcon(stop.color.toArgb())).setApplyDpScale(false).setAnchorPoint(.5f, .5f)))
                        if (stop.labeled) {
                            // Line name beside the marker, on the side away from the outgoing line; dropped on collision.
                            val name = metrics.stopLabel(stop)
                            layer.addLabel(LabelOptions.from(stop.location.sdk()).setRank(STOP_NAME_RANK).setStyles(LabelStyle.from(nameIcon(name, stop.color.toArgb(), stop.labelOnLeft, (metrics.stop / 2).toInt())).setApplyDpScale(false).setAnchorPoint(if (stop.labelOnLeft) 1f else 0f, .5f)))
                                ?.let { stopNames += StopName(it, stop, name, index, (metrics.stop / 2).toInt()) }
                        }
                    }
                }
                state.origin?.let { place -> layer.addLabel(LabelOptions.from(place.location.sdk()).setRank(PLACE_RANK).setStyles(LabelStyle.from(startIcon).setApplyDpScale(false).setAnchorPoint(.5f, 1f)))?.let { pinLabels += PinLabel(it, MarkKey.Origin, startIcons) } }
                state.destination?.let { place -> layer.addLabel(LabelOptions.from(place.location.sdk()).setRank(PLACE_RANK).setStyles(LabelStyle.from(endIcon).setApplyDpScale(false).setAnchorPoint(.5f, 1f)))?.let { pinLabels += PinLabel(it, MarkKey.Destination, endIcons) } }
            }
            decorRoute = state.routes.getOrNull(state.selectedRoute); decorZoom = null; lineDensity = density; walkLines.clear(); decorations.clear()
        }
        // #168: 카메라는 정책(cameraPolicy)이 정한 가운데·정수 줌을 그대로 적용한다. 덮는 부품은 보이는 영역으로만 넘기고 SDK 지도 여백(setPadding)은 쓰지 않는다:
        // 시트를 끌어 보이는 영역만 바뀌면 카메라도 화면 내용도 그대로다(#148). 발자국(핀·이름표 상자)은 그리기와 같은 측정이다(#138 #143 #151 #153).
        lastState = state; lastDensity = density
        val viewport = Viewport(view.width.toFloat(), view.height.toFloat(), top = state.padding.top.value * density, bottom = state.padding.bottom.value * density)
        if (geometryChanged || footprints.isEmpty()) footprints = state.footprints(markerSizes(state))
        if (scale == null && viewport.ready) scale = measureScale(target)
        scale?.let { scale ->
            val input = CameraInput(viewport, scale, footprints, state.fitTarget, fitGap(viewport, metrics.inset, metrics.edge, metrics.marker + metrics.textPadding), currentCamera(target), footprintsAt = state.layoutFootprints(markSizes(), scale))
            fitPolicy.update(state, input)?.let { move -> target.moveCamera(CameraUpdateFactory.newCenterPosition(move.center.sdk(), move.zoom)) }
        }
        reportStatus()
        placeLogo(target, viewport)
        if (viewport != lastViewport) { lastViewport = viewport; scheduleLog() }
        if (decorZoom == null) view.post { map?.let(::decorate) }
        state.focus?.let { focus ->
            val layer = target.labelManager?.layer
            if (myLocation == null) myLocation = layer?.addLabel(LabelOptions.from(focus.location.sdk()).setStyles(LabelStyle.from(myLocationIcon).setApplyDpScale(false)))
            else myLocation?.moveTo(focus.location.sdk())
        }
        if (state.routeProgress != lastProgress) {
            state.routeProgress?.let { progress -> progressLines.forEach { it.setProgress(progress.coerceIn(0f, 1f) - 1f) } }
            lastProgress = state.routeProgress
        }
        val position = state.visibleCursor
        if (position == null) { cursor?.hide(); ray?.hide() }
        else {
            val layer = target.labelManager?.layer ?: return
            if (cursor == null) cursor = layer.addLabel(LabelOptions.from(position.position.sdk()).setStyles(playbackLabelStyle(vehicleIcon)).setTransform(TransformMethod.AbsoluteRotation))
            if (ray == null) ray = layer.addLabel(LabelOptions.from(position.position.sdk()).setStyles(playbackLabelStyle(rayIcon)).setTransform(TransformMethod.AbsoluteRotation))
            cursor?.show()
            if (position != lastCursor) {
                cursor?.moveTo(position.position.sdk()); cursor?.rotateTo(Math.toRadians(position.heading).toFloat())
                ray?.moveTo(position.position.sdk()); ray?.rotateTo(Math.toRadians(position.sunAzimuth).toFloat())
            }
            val altitudeBucket=position.sunAltitude.toInt().toDouble()
            if (altitudeBucket != lastRayAltitude) {
                ray?.setStyles(playbackLabelStyle(marker(3, sun,altitude=position.sunAltitude)))
                ray?.invalidate(false)
                ray?.rotateTo(Math.toRadians(position.sunAzimuth).toFloat())
                lastRayAltitude = altitudeBucket
            }
            val relative=com.sunseat.app.domain.ExposureAnalyzer.normalize(position.sunAzimuth-position.heading)
            val lightSide=when {position.sunAltitude<=0 -> 0;relative<25 || relative>335 -> 1;relative>155 && relative<205 -> 2;relative<180 -> 3;else -> 4}
            if(lightSide!=lastVehicleLight) { cursor?.setStyles(playbackLabelStyle(marker(2,ink,lightSide)));cursor?.invalidate(false);cursor?.rotateTo(Math.toRadians(position.heading).toFloat());lastVehicleLight=lightSide }
            if (position.sunAltitude > 0) ray?.show() else ray?.hide()
        }
        lastCursor = position
        coverState = state
        // #153: 새로 만든 이름표·차량에 가려 숨기거나 다시 보인 표식에 맞춰 이름표 자리를 다시 잰다(바뀐 것만 다시 그린다).
        layoutMarks(target)
    }

    /** #170: 표식 배치 크기(px). 그리기와 같은 측정이다. */
    private fun markSizes(): MarkSizes {
        val paint = textPaint(ink)
        return MarkSizes(
            marker = metrics.marker, tail = metrics.tail, gap = metrics.textPadding,
            originWidth = startIcon.width.toFloat(), destinationWidth = endIcon.width.toFloat(),
            stopRadius = metrics.stop / 2, labelHeight = nameHeight(paint),
            vehicle = metrics.marker, currentLocation = metrics.marker * 2 / 3,
            labelWidth = { stop -> paint.measureText(metrics.stopLabel(stop)) + metrics.textPadding * 2 },
        )
    }

    /**
     * #170: 표식 배치(markerLayout, 테스트 지도와 같은 규칙)를 SDK 투영으로 다시 재서 출발·도착 표식의 몸통 방향, 정류장 이름표의 비킨 거리와 숨김을 적용한다.
     * 화면 거리는 카메라에 따라 바뀌므로 [update]마다와 카메라 이동이 끝날 때(줌 변경 포함) 다시 잰다. 바뀐 표식만 다시 그린다(changeStyles, #153).
     */
    private fun layoutMarks(target: KakaoMap) {
        val state = coverState ?: return
        if (stopNames.isEmpty() && pinLabels.isEmpty()) return
        val sizes = markSizes()
        val marks = state.marks(sizes) { at -> target.toScreenPoint(at.sdk())?.let { it.x.toFloat() to it.y.toFloat() } ?: (Float.NaN to Float.NaN) }
        val placements = markerLayout(marks).associateBy { it.key }
        pinLabels.forEach { pin ->
            val placement = placements[pin.key] ?: return@forEach
            if (placement.hidden != pin.hidden) { pin.hidden = placement.hidden; if (placement.hidden) pin.label.hide() else pin.label.show() }
            val side = pinSide(placement)
            if (!placement.hidden && side != pin.side) {
                pin.side = side
                pin.label.changeStyles(com.kakao.vectormap.label.LabelStyles.from(pinStyle(pin.icons.getValue(side), side)))
            }
        }
        stopNames.forEach { name ->
            val placement = placements[MarkKey.StopLabel(name.index)] ?: return@forEach
            if (placement.hidden != name.hidden) { name.hidden = placement.hidden; if (placement.hidden) name.label.hide() else name.label.show() }
            val offset = labelOffset(placement, sizes).toInt()
            if (!placement.hidden && offset != name.offset) {
                name.offset = offset
                name.label.changeStyles(com.kakao.vectormap.label.LabelStyles.from(LabelStyle.from(nameIcon(name.name, name.stop.color.toArgb(), name.stop.labelOnLeft, offset)).setApplyDpScale(false).setAnchorPoint(if (name.stop.labelOnLeft) 1f else 0f, .5f)))
            }
        }
    }

    /** 몸통 방향에 맞춘 출발·도착 표식 스타일: 꼬리 끝(좌표)이 기준점이다. */
    private fun pinStyle(icon: Bitmap, side: PinSide): LabelStyle = LabelStyle.from(icon).setApplyDpScale(false).let {
        when (side) {
            PinSide.UP -> it.setAnchorPoint(.5f, 1f)
            PinSide.DOWN -> it.setAnchorPoint(.5f, 0f)
            PinSide.RIGHT -> it.setAnchorPoint(0f, .5f)
            PinSide.LEFT -> it.setAnchorPoint(1f, .5f)
        }
    }

    /** 발자국 크기(px): 그리기와 같은 측정. 이름표 폭 = 상자 폭 + 가장자리 틈(Spacing.xs), 높이 = 상자 높이 + 위아래 틈. */
    private fun markerSizes(state: MapState): MarkerSizes {
        val paint = textPaint(ink)
        return MarkerSizes(
            markerHeight = metrics.marker + metrics.textPadding,
            originWidth = startIcon.width.toFloat(), destinationWidth = endIcon.width.toFloat(),
            stopRadius = metrics.stop / 2,
            labelHeight = nameHeight(paint) + metrics.textPadding * 2,
            boardClearance = if (state.origin != null) startIcon.width / 2f + metrics.textPadding else 0f,
            labelWidth = { stop -> paint.measureText(metrics.stopLabel(stop)) + metrics.textPadding * 3 },
        )
    }

    private fun currentCamera(target: KakaoMap): CameraPosition? =
        target.cameraPosition?.let { CameraPosition(LatLng(it.position.latitude, it.position.longitude), it.zoomLevel) }

    /**
     * 지금 줌에서 잰 배율(#168). 카카오 줌은 한 단계마다 두 배다(실기기 z10~z14 m/px 측정). 화면 가운데 가로 200px 사이의 경도 차로
     * 세계 폭(px)을 구한다. 지도 여백을 쓰지 않으므로 잰 값은 여백과 무관하다.
     */
    private fun measureScale(target: KakaoMap): MapScale? {
        val zoom = target.cameraPosition?.zoomLevel ?: return null
        val w = view.width
        val h = view.height
        if (w <= SCALE_PROBE_PX * 2 || h <= 0) return null
        val a = target.fromScreenPoint(w / 2 - SCALE_PROBE_PX, h / 2) ?: return null
        val b = target.fromScreenPoint(w / 2 + SCALE_PROBE_PX, h / 2) ?: return null
        val span = Mercator.x(b.longitude) - Mercator.x(a.longitude)
        if (!(span > 0)) return null
        return MapScale(SCALE_PROBE_PX * 2 / span / 2.0.pow(zoom))
    }

    private fun reportStatus() {
        val status = CameraStatus(fitPolicy.mode, fitPolicy.fits)
        if (status == reported) return
        reported = status
        val report = lastState?.onCameraStatus ?: return
        view.post { if (!closed) report(status) }
    }

    /** 지도 여백이 없으면 SDK는 로고를 지도 전체 기준으로 둔다. 보이는 영역 왼쪽 아래에 둔다(DESIGN 11: 로고를 가리지 않는다). */
    private fun placeLogo(target: KakaoMap, viewport: Viewport) {
        val bottom = (viewport.height - viewport.visibleBottom + metrics.edge).toInt()
        if (bottom == logoBottom) return
        logoBottom = bottom
        target.logo?.setPosition(com.kakao.vectormap.MapGravity.LEFT or com.kakao.vectormap.MapGravity.BOTTOM, metrics.edge, bottom.toFloat())
    }

    private val logRunnable = Runnable { logFootprints() }
    private fun scheduleLog() {
        if (!BuildConfig.DEBUG) return
        view.removeCallbacks(logRunnable)
        view.postDelayed(logRunnable, LOG_DELAY_MS)
    }

    /**
     * debug 빌드 점검용(실기기 점검 스크립트, #169): 정책의 보이는 영역과 SDK 투영으로 잰 발자국 상자(px, 지도 뷰 기준)를 logcat 한 줄로 남긴다. 좌표는 남기지 않는다.
     * `route`는 경로 좌표의 외곽 상자, `marks`는 출발·도착 표식과 정류장(이름표 포함) 상자다.
     */
    private fun logFootprints() {
        if (closed) return
        val target = map ?: return
        val viewport = lastViewport ?: return
        fun screen(footprint: Footprint): ScreenBox? = target.toScreenPoint(footprint.at.sdk())?.let { p ->
            ScreenBox(p.x + footprint.box.left, p.y + footprint.box.top, p.x + footprint.box.right, p.y + footprint.box.bottom)
        }
        fun ScreenBox.text() = "${left.toInt()},${top.toInt()},${right.toInt()},${bottom.toInt()}"
        val points = footprints.filter { it.box == ScreenBox.Point }.mapNotNull(::screen)
        val route = if (points.isEmpty()) "-" else ScreenBox(points.minOf { it.left }, points.minOf { it.top }, points.maxOf { it.right }, points.maxOf { it.bottom }).text()
        val marks = footprints.filter { it.box != ScreenBox.Point }.mapNotNull(::screen).joinToString(";") { it.text() }
        val mode = fitPolicy.mode.let { if (it is CameraMode.Fitted) "Fitted(${it.target})" else it.toString() }
        val visible = ScreenBox(viewport.visibleLeft, viewport.visibleTop, viewport.visibleRight, viewport.visibleBottom).text()
        android.util.Log.d(LOG_TAG, "mode=$mode zoom=${target.cameraPosition?.zoomLevel} view=${viewport.width.toInt()}x${viewport.height.toInt()} visible=$visible route=$route marks=$marks")
    }

    /** 현재 위치 원·차량 화살표와 짧은 해 방향 선. */
    private fun marker(kind: Int, color: Int, lightSide: Int=0,altitude: Double=20.0): Bitmap {
        val width = (if(kind==3) metrics.ray*2 else metrics.marker).toInt().coerceAtLeast(1)
        val height = width
        return createBitmap(width, height).also { bitmap ->
            val canvas = Canvas(bitmap)
            val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply { this.color = color; strokeWidth = metrics.walk; strokeCap = Paint.Cap.ROUND }
            val mid = width / 2f
            val radius = width / 3f
            when (kind) {
                4 -> { paint.color = surface; canvas.drawCircle(mid, mid, radius, paint); paint.color = color; paint.style = Paint.Style.STROKE; canvas.drawCircle(mid, mid, radius, paint); paint.style = Paint.Style.FILL; canvas.drawCircle(mid, mid, radius / 3, paint) }
                2 -> {
                    val body=Path().apply { addRoundRect(width*.24f,height*.14f,width*.76f,height*.92f,width*.12f,width*.12f,Path.Direction.CW) }
                    paint.color=surface;canvas.drawPath(body,paint)
                    if(lightSide!=0) {
                        canvas.save();canvas.clipPath(body);paint.color=sun
                        when(lightSide) {
                            1 -> canvas.drawRect(0f,0f,width.toFloat(),height*.4f,paint)
                            2 -> canvas.drawRect(0f,height*.64f,width.toFloat(),height.toFloat(),paint)
                            3 -> canvas.drawRect(mid,0f,width.toFloat(),height.toFloat(),paint)
                            4 -> canvas.drawRect(0f,0f,mid,height.toFloat(),paint)
                        };canvas.restore()
                    }
                    paint.color=ink;paint.style=Paint.Style.STROKE;paint.strokeWidth=metrics.walk*.65f;canvas.drawPath(body,paint)
                    canvas.drawLine(width*.31f,height*.36f,width*.69f,height*.36f,paint)
                    canvas.drawLine(mid,height*.02f,width*.4f,height*.1f,paint);canvas.drawLine(mid,height*.02f,width*.6f,height*.1f,paint)
                }
                3 -> {
                    val length=metrics.ray*(1-.3*altitude.coerceIn(0.0,90.0)/90).toFloat()
                    val top=mid-length*.82f;val r=metrics.marker*.16f
                    paint.color=sun;paint.alpha=80;paint.strokeWidth=metrics.marker*.24f
                    canvas.drawLine(mid,top+r,mid,mid-metrics.marker*.55f,paint)
                    paint.alpha=255;paint.strokeWidth=metrics.walk*.65f;paint.color=ink
                    val tip=mid-metrics.marker*.55f
                    canvas.drawLine(mid,tip-r,mid,tip,paint)
                    canvas.drawLine(mid,tip,mid-r*.45f,tip-r*.6f,paint);canvas.drawLine(mid,tip,mid+r*.45f,tip-r*.6f,paint)
                    paint.color=sun;canvas.drawCircle(mid,top,r,paint)
                    paint.style=Paint.Style.STROKE;paint.color=ink;canvas.drawCircle(mid,top,r,paint)
                    for(i in 0 until 8) { val a=i*Math.PI/4;canvas.drawLine(mid+kotlin.math.cos(a).toFloat()*r*1.35f,top+kotlin.math.sin(a).toFloat()*r*1.35f,mid+kotlin.math.cos(a).toFloat()*r*1.7f,top+kotlin.math.sin(a).toFloat()*r*1.7f,paint) }
                }
            }
        }
    }
    /** [RouteLineSpec] → SDK 스타일. 두께와 패턴 이미지는 SDK가 mapDpScale을 곱하므로 미리 나눈다. */
    private fun sdkStyle(spec: RouteLineSpec, density: Float, scale: Float): RouteLineStyle {
        val color = spec.color.toArgb()
        val core = RouteLinePolicy.sdkWidth(spec.core, density, scale)
        val edgeColor = spec.edgeColor ?: return RouteLineStyle.from(core, color)
        val edge = RouteLinePolicy.sdkWidth(spec.edge, density, scale)
        return RouteLineStyle.from(core, color, edge, edgeColor.toArgb())
    }

    /**
     * 도보 점선(층 2)과 진행 방향 쉐브론을 현재 줌의 미터/픽셀로 다시 만든다. SDK 2.15.2의 RouteLinePattern 이미지는
     * 실기기에서 그려지지 않았다(#100 실측). 줌 단계가 바뀔 때만 다시 계산한다.
     */
    private fun decorate(target: KakaoMap) {
        if (closed) return
        val w = view.width
        val h = view.height
        if (w <= 0 || h <= 0) return
        val left = target.fromScreenPoint(0, h / 2) ?: return
        val right = target.fromScreenPoint(w, h / 2) ?: return
        val metersPerPx = com.sunseat.app.domain.ExposureAnalyzer.distanceMeters(LatLng(left.latitude, left.longitude), LatLng(right.latitude, right.longitude)) / w
        if (!(metersPerPx > 0)) return
        decorZoom = target.cameraPosition?.zoomLevel
        this.metersPerPx = metersPerPx
        layoutMarks(target)
        walkLines.forEach { it.remove() }; walkLines.clear()
        target.labelManager?.layer?.let { layer -> decorations.forEach { layer.remove(it) } }
        decorations.clear()
        val route = decorRoute ?: return
        val scale = target.mapDpScale
        val px = { dp: androidx.compose.ui.unit.Dp -> dp.value * lineDensity * metersPerPx }
        val walk = RouteLinePolicy.forLeg(Mode.WALK, null, RouteRole.SELECTED)
        val style = RouteLineStyles.from(RouteLineStyle.from(RouteLinePolicy.sdkWidth(walk.core, lineDensity, scale), walk.color.toArgb()))
        target.routeLineManager?.layer?.let { lines ->
            route.legs.filter { it.mode == Mode.WALK && it.path.size > 1 }.forEach { leg ->
                val dots = RouteGeometry.dashes(leg.path, px(walk.core) * DOT_STUB, px(walk.core) * (1 - DOT_STUB) + px(Sizes.mapRouteWalkGap), MAX_WALK_DOTS)
                // Over the cap (very long walk at high zoom) draw the whole walk as a plain thin line rather than dropping part of it.
                (dots ?: listOf(leg.path)).forEach { dash ->
                    // One RouteLine per dot: segments inside a RouteLine are joined into a continuous line by the SDK.
                    walkLines += lines.addRouteLine(RouteLineOptions.from(RouteLineSegment.from(dash.map { p -> p.sdk() }, style)).setStylesSet(RouteLineStylesSet.from(style)).setZOrder(walk.layer))
                }
            }
        }
        val layer = target.labelManager?.layer ?: return
        route.legs.forEach { leg ->
            val spec = RouteLinePolicy.forLeg(leg.mode, leg.lineName, RouteRole.SELECTED)
            if (!spec.arrows || playbackMode) return@forEach
            val icon = arrow((spec.edgeColor ?: spec.color).toArgb())
            RouteGeometry.marks(leg.path, px(Sizes.mapRouteArrowSpacing)).forEach { (at, bearing) ->
                layer.addLabel(LabelOptions.from(at.sdk()).setRank(ARROW_RANK).setTransform(TransformMethod.AbsoluteRotation)
                    .setStyles(LabelStyle.from(icon).setApplyDpScale(false).setAnchorPoint(.5f, .5f)))?.let { label ->
                    label.rotateTo(Math.toRadians(bearing).toFloat()); decorations += label
                }
            }
        }
    }

    /** 위쪽(북)을 가리키는 쉐브론. 흰 테두리 노선은 흰색, 저대비 노선은 어두운 테두리 색. 라벨 회전으로 진행 방향을 맞춘다. */
    private fun arrow(color: Int): Bitmap = arrows.getOrPut(color) {
        val px = (metrics.arrow).toInt().coerceAtLeast(2)
        createBitmap(px, px).also { bitmap ->
            val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply { this.color = color; style = Paint.Style.STROKE; strokeWidth = px / 4f; strokeCap = Paint.Cap.ROUND; strokeJoin = Paint.Join.ROUND }
            Canvas(bitmap).drawPath(Path().apply { moveTo(px * .2f, px * .7f); lineTo(px * .5f, px * .35f); lineTo(px * .8f, px * .7f) }, paint)
        }
    }

    private fun textPaint(color: Int) = Paint(Paint.ANTI_ALIAS_FLAG).apply { this.color = color; textSize = metrics.text; typeface = this@MapSession.typeface; isFakeBoldText = typeface == null }

    /** 승차·환승·하차: 흰 원 + 구간 색 테두리. */
    private fun stopIcon(color: Int): Bitmap {
        val px = metrics.stop.toInt().coerceAtLeast(2)
        return createBitmap(px, px).also { bitmap ->
            val canvas = Canvas(bitmap)
            val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply { this.color = surface }
            val radius = px / 2f - metrics.stopBorder / 2
            canvas.drawCircle(px / 2f, px / 2f, radius, paint)
            paint.color = color; paint.style = Paint.Style.STROKE; paint.strokeWidth = metrics.stopBorder
            canvas.drawCircle(px / 2f, px / 2f, radius, paint)
        }
    }

    private fun nameHeight(paint: Paint) = maxOf(metrics.labelHeight, -paint.ascent() + paint.descent() + metrics.textPadding)

    /** 탈 노선 이름. 표식 한쪽([onLeft])에 흰 바탕 글자로 두며, 이미지 가장자리(정류장 좌표)에서 [offset]만큼 떨어진다(기본 표식 반지름, #153). */
    private fun nameIcon(name: String, color: Int, onLeft: Boolean, offset: Int): Bitmap {
        val paint = textPaint(ink)
        val width = paint.measureText(name) + metrics.textPadding * 2 + offset
        val height = nameHeight(paint)
        return createBitmap(width.toInt().coerceAtLeast(1), height.toInt().coerceAtLeast(1)).also { bitmap ->
            val canvas = Canvas(bitmap)
            val left = if (onLeft) 0f else offset.toFloat()
            val right = if (onLeft) width - offset else width
            val box = Paint(Paint.ANTI_ALIAS_FLAG).apply { this.color = surface }
            canvas.drawRoundRect(left, 0f, right, height, height / 2, height / 2, box)
            box.color = color; box.style = Paint.Style.STROKE; box.strokeWidth = metrics.stopBorder / 2
            canvas.drawRoundRect(left + box.strokeWidth, box.strokeWidth, right - box.strokeWidth, height - box.strokeWidth, height / 2, height / 2, box)
            canvas.drawText(name, left + metrics.textPadding, height / 2 - (paint.ascent() + paint.descent()) / 2, paint)
        }
    }

    /**
     * 출발(흰 채움 + 경로 색 테두리)·도착(경로 색 채움 + 흰 글자) 글자 표식. 꼬리 끝이 좌표다. [side]는 몸통이 꼬리 끝의 어느 쪽에 있는지다(#170).
     */
    private fun placeIcon(text: String, filled: Boolean, side: PinSide = PinSide.UP): Bitmap {
        val transit = com.sunseat.app.ui.theme.MapRouteColors.transit.toArgb()
        val paint = textPaint(if (filled) surface else transit)
        val tail = metrics.tail
        val height = metrics.marker - tail
        val width = placeMarkerWidth(paint.measureText(text), metrics.marker, tail, metrics.textPadding)
        val vertical = side == PinSide.UP || side == PinSide.DOWN
        val w = if (vertical) width else width + tail
        val h = if (vertical) metrics.marker else height
        return createBitmap(w.toInt(), h.toInt()).also { bitmap ->
            val canvas = Canvas(bitmap)
            val stroke = metrics.stopBorder
            // 몸통 상자와 꼬리(몸통의 좌표 쪽 변 가운데 → 꼬리 끝).
            val (bl, bt) = when (side) { PinSide.UP -> 0f to 0f; PinSide.DOWN -> 0f to tail; PinSide.RIGHT -> tail to 0f; PinSide.LEFT -> 0f to 0f }
            val br = bl + width; val bb = bt + height
            val body = Path().apply {
                addRoundRect(bl + stroke / 2, bt + stroke / 2, br - stroke / 2, bb - stroke / 2, height / 2, height / 2, Path.Direction.CW)
                when (side) {
                    PinSide.UP -> { moveTo(w / 2 - tail, bb - stroke); lineTo(w / 2, h - stroke / 2); lineTo(w / 2 + tail, bb - stroke) }
                    PinSide.DOWN -> { moveTo(w / 2 - tail, bt + stroke); lineTo(w / 2, stroke / 2); lineTo(w / 2 + tail, bt + stroke) }
                    PinSide.RIGHT -> { moveTo(bl + stroke, h / 2 - tail); lineTo(stroke / 2, h / 2); lineTo(bl + stroke, h / 2 + tail) }
                    PinSide.LEFT -> { moveTo(br - stroke, h / 2 - tail); lineTo(w - stroke / 2, h / 2); lineTo(br - stroke, h / 2 + tail) }
                }
                close()
            }
            val fill = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = if (filled) transit else surface }
            canvas.drawPath(body, fill)
            fill.color = transit; fill.style = Paint.Style.STROKE; fill.strokeWidth = stroke; fill.strokeJoin = Paint.Join.ROUND
            canvas.drawPath(body, fill)
            canvas.drawText(text, bl + (width - paint.measureText(text)) / 2, bt + height / 2 - (paint.ascent() + paint.descent()) / 2, paint)
        }
    }
}
