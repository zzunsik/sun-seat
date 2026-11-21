package com.sunseat.app.ui.map

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.remember
import androidx.compose.runtime.Composable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.text.drawText
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import com.sunseat.app.R
import com.sunseat.app.domain.Mode
import com.sunseat.app.ui.theme.MapRouteColors
import com.sunseat.app.ui.theme.Sizes
import com.sunseat.app.ui.theme.Spacing
import kotlin.math.cos
import kotlin.math.sin

enum class MapRenderer { SDK, PREVIEW }
@Suppress("ComposeCompositionLocalUsage") // 네이티브 SDK를 테스트 렌더러로 교체하는 경계
val LocalMapRenderer = staticCompositionLocalOf { MapRenderer.SDK }

@Composable
fun SunSeatMap(state: MapState, modifier: Modifier = Modifier) {
    val description = stringResource(R.string.map_description, state.origin?.name.orEmpty(), state.destination?.name.orEmpty()) +
        if (state.focus != null) " · " + stringResource(R.string.location_use_current) else ""
    Box(modifier.semantics { contentDescription = description }) {
        if (LocalMapRenderer.current == MapRenderer.PREVIEW) MapPlaceholder(state, Modifier.fillMaxSize())
        else NativeMap(state, Modifier.fillMaxSize())
        // Separate node so the existing map description stays stable; colour/line shape is never the only cue.
        routeSummary(state.routes.getOrNull(state.selectedRoute))?.let { summary -> Box(Modifier.matchParentSize().semantics { contentDescription = summary }) }
        // No separate legend: the 출발/도착 markers carry their own text (DESIGN 11.1) and a legend box covered them.
    }
}

/**
 * 네이티브 지도 없이 실제 경로 좌표를 보여 주는 테스트 렌더러. 카메라 정책(#168)이 정한 가운데 좌표·정수 줌을 Web Mercator로 투영한다
 * (줌 0에서 세계 폭 [PREVIEW_WORLD_DP]dp). 네이티브와 같은 [MapFitPolicy]·발자국·보이는 영역을 쓰므로 정수 줌 단계 차이가 그대로 보인다.
 */
@Composable
fun MapPlaceholder(state: MapState, modifier: Modifier = Modifier) {
    val scheme = com.sunseat.app.ui.theme.LightScheme
    val sun = com.sunseat.app.ui.theme.LightSunSeatColors.onSunContainer
    val policy = remember { MapFitPolicy() }
    val camera = remember { PreviewCamera() }
    // 끌기로 카메라가 바뀌면 다시 그리고 의미 속성(발자국 상자)을 갱신한다.
    var moves by remember { mutableIntStateOf(0) }
    val latest by rememberUpdatedState(state)
    val textMeasurer = androidx.compose.ui.text.rememberTextMeasurer()
    val labelStyle = androidx.compose.material3.MaterialTheme.typography.labelMedium
    val originLabel = stringResource(R.string.input_origin)
    val destinationLabel = stringResource(R.string.input_destination)
    val busLabel = stringResource(R.string.common_bus)
    val subwayLabel = stringResource(R.string.common_subway)
    val moreLines = stringResource(R.string.map_line_more)
    val density = androidx.compose.ui.platform.LocalDensity.current
    val scale = remember(density.density) { MapScale(PREVIEW_WORLD_DP * density.density) }
    fun stopName(stop: StopMarker) = when {
        stop.lines.isEmpty() -> if (stop.mode == Mode.SUBWAY) subwayLabel else busLabel
        stop.lines.size == 1 -> stop.lines.first()
        else -> String.format(moreLines, stop.lines.first(), stop.lines.size - 1)
    }
    // 발자국 크기: 그리기와 같은 글자 측정(#143 이름표 폭 + 가장자리 틈, #151 이름표 높이 + 위아래 틈, #153 첫 승차 이름표는 출발 표식 밖).
    val sizes = with(density) {
        val pin = { text: String -> placeMarkerWidth(textMeasurer.measure(text, labelStyle).size.width.toFloat(), Sizes.mapMarker.toPx(), Sizes.mapMarkerTail.toPx(), Spacing.xs.toPx()) }
        val originWidth = pin(originLabel)
        MarkerSizes(
            markerHeight = Sizes.mapMarker.toPx() + Spacing.xs.toPx(),
            originWidth = originWidth, destinationWidth = pin(destinationLabel),
            stopRadius = Sizes.mapStopMarker.toPx() / 2,
            labelHeight = maxOf(Sizes.mapLabelHeight.toPx(), textMeasurer.measure("0", labelStyle).size.height.toFloat()) + Spacing.xs.toPx() * 2,
            boardClearance = if (state.origin != null) originWidth / 2 + Spacing.xs.toPx() else 0f,
            labelWidth = { stop -> textMeasurer.measure(stopName(stop), labelStyle).size.width + Spacing.xs.toPx() * 3 },
        )
    }
    fun report() {
        val status = CameraStatus(policy.mode, policy.fits)
        if (status != camera.reported) { camera.reported = status; latest.onCameraStatus?.invoke(status) }
    }
    BoxWithConstraints(modifier.pointerInput(Unit) {
        // Map picker (#85): the pin is the view centre, so report the camera centre when a drag ends.
        fun reportCentre() { val idle = latest.onCameraIdle ?: return; camera.shown?.let { idle(it.center) } }
        detectDragGestures(onDragEnd = { reportCentre() }, onDragCancel = { reportCentre() }) { change, drag ->
            change.consume()
            val shown = camera.shown ?: return@detectDragGestures
            val w = size.width.toFloat(); val h = size.height.toFloat()
            val moved = shown.copy(center = shown.fromScreen(w / 2 - drag.x, h / 2 - drag.y, w, h, scale))
            camera.position = moved; camera.shown = moved
            policy.gesture(); moves++
            latest.onGesture()
            report()
        }
    }) {
        val width = if (constraints.hasBoundedWidth) constraints.maxWidth.toFloat() else 0f
        val height = if (constraints.hasBoundedHeight) constraints.maxHeight.toFloat() else 0f
        val viewport = with(density) { Viewport(width, height, top = state.padding.top.toPx(), bottom = state.padding.bottom.toPx()) }
        val footprints = state.footprints(sizes)
        val gap = with(density) { fitGap(viewport, Sizes.mapInset.toPx(), Spacing.s.toPx()) }
        policy.update(state, CameraInput(viewport, scale, footprints, state.fitTarget, gap, camera.position))?.let { camera.position = it }
        // 정책이 아직 카메라를 정하지 않았으면(맞춤 대기) 뷰 전체에 맞춘 모습을 보인다. 맞춤 횟수에는 넣지 않는다.
        val whole = Viewport(width, height)
        camera.shown = camera.position ?: fitCamera(footprints, whole, scale, gap)
            ?: (state.center ?: state.focus)?.let { if (whole.ready) centerCamera(it.location, FIT_MAX_ZOOM, whole, scale) else null }
        val shown = camera.shown
        moves.let { } // 끌기 뒤 다시 구성한다.
        val boxes = shown?.let { cam -> footprints.map { cam.screenBox(it, width, height, scale) } }.orEmpty()
        SideEffect { report() }
    Box(Modifier.fillMaxSize().semantics {
        this[MapFootprintBoxesKey] = boxes
        this[MapVisibleAreaKey] = ScreenBox(viewport.visibleLeft, viewport.visibleTop, viewport.visibleRight, viewport.visibleBottom)
    })
    Canvas(Modifier.fillMaxSize().background(scheme.surfaceContainer)) {
        val cam = camera.shown ?: CameraPosition(com.sunseat.app.domain.LatLng(37.5665, 126.978), FIT_MAX_ZOOM)
        fun position(point: com.sunseat.app.domain.LatLng): Offset = cam.toScreen(point, size.width, size.height, scale).let { Offset(it.first, it.second) }
        val grid = Sizes.mapInset.toPx()
        var x = 0f
        while (x < size.width) { drawLine(scheme.outlineVariant, Offset(x, 0f), Offset(x, size.height), Sizes.hairline.toPx()); x += grid }
        var y = 0f
        while (y < size.height) { drawLine(scheme.outlineVariant, Offset(0f, y), Offset(size.width, y), Sizes.hairline.toPx()); y += grid }
        fun legPath(points: List<com.sunseat.app.domain.LatLng>) = Path().apply { points.forEachIndexed { i, p -> val at = position(p); if (i == 0) moveTo(at.x, at.y) else lineTo(at.x, at.y) } }
        // Same contract and layer order as the native SDK (RouteLinePolicy, DESIGN 11.1).
        val selected = state.routes.getOrNull(state.selectedRoute)
        state.routes.forEachIndexed { index, route -> if (index != state.selectedRoute) route.legs.forEach { leg ->
            if (leg.path.size > 1) drawRouteLine(legPath(leg.path), RouteLinePolicy.forLeg(leg.mode, leg.lineName, RouteRole.ALTERNATE))
        } }
        selected?.legs?.sortedBy { if (it.mode == Mode.WALK) 0 else 1 }?.forEach { leg ->
            if (leg.path.size > 1) drawRouteLine(legPath(leg.path), RouteLinePolicy.forLeg(leg.mode, leg.lineName, RouteRole.SELECTED))
        }
        state.routeProgress?.let { progress ->
            val legs = selected?.legs.orEmpty()
            val total = legs.sumOf { leg -> leg.path.zipWithNext { a, b -> com.sunseat.app.domain.ExposureAnalyzer.distanceMeters(a, b) }.sum() }
            var remaining = total * progress.coerceIn(0f, 1f)
            legs.forEach { leg ->
                val path = Path()
                var started = false
                leg.path.zipWithNext().forEach { (from, to) ->
                    val distance = com.sunseat.app.domain.ExposureAnalyzer.distanceMeters(from, to)
                    if (remaining > 0 && distance > 0) {
                        if (!started) { val at = position(from); path.moveTo(at.x, at.y); started = true }
                        val fraction = (remaining / distance).coerceIn(0.0, 1.0)
                        val end = position(com.sunseat.app.domain.LatLng(from.latitude + (to.latitude - from.latitude) * fraction, from.longitude + (to.longitude - from.longitude) * fraction))
                        path.lineTo(end.x, end.y)
                    }
                    remaining -= distance
                }
                if (started) drawRouteLine(path, RouteLinePolicy.forLeg(leg.mode, leg.lineName, RouteRole.PASSED))
            }
        }
        val markerPx = Sizes.mapMarker.toPx()
        fun covered(at: com.sunseat.app.domain.LatLng) = state.coveredByVehicle(at, markerPx) { a, b -> (position(a) - position(b)).getDistance() }
        val originText = textMeasurer.measure(originLabel, labelStyle.copy(color = MapRouteColors.transit))
        val destinationText = textMeasurer.measure(destinationLabel, labelStyle.copy(color = MapRouteColors.casing))
        val marker = Sizes.mapMarker.toPx()
        val tail = Sizes.mapMarkerTail.toPx()
        fun pinWidth(text: androidx.compose.ui.text.TextLayoutResult) = placeMarkerWidth(text.size.width.toFloat(), marker, tail, Spacing.xs.toPx())
        // #153: 보이는 출발·도착 표식(꼬리 끝 위치, 몸통 폭). 정류장 이름표가 이 표식 밑에 들어가지 않게 민다.
        val pins = listOfNotNull(state.origin?.takeUnless { covered(it.location) }?.let { position(it.location) to pinWidth(originText) },
            state.destination?.takeUnless { covered(it.location) }?.let { position(it.location) to pinWidth(destinationText) })
        selected?.let { route -> RouteLinePolicy.stops(route).forEach { stop ->
            val p = position(stop.location)
            val radius = Sizes.mapStopMarker.toPx() / 2 - Sizes.mapStopBorder.toPx() / 2
            drawCircle(MapRouteColors.casing, radius, p)
            drawCircle(stop.color, radius, p, style = Stroke(Sizes.mapStopBorder.toPx()))
            if (stop.labeled && !covered(stop.location)) {
                val name = when {
                    stop.lines.isEmpty() -> if (stop.mode == Mode.SUBWAY) subwayLabel else busLabel
                    stop.lines.size == 1 -> stop.lines.first()
                    else -> String.format(moreLines, stop.lines.first(), stop.lines.size - 1)
                }
                val text = textMeasurer.measure(name, labelStyle.copy(color = scheme.onSurface))
                val pad = Spacing.xs.toPx()
                val box = androidx.compose.ui.geometry.Size(text.size.width + pad * 2, maxOf(Sizes.mapLabelHeight.toPx(), text.size.height.toFloat()))
                val boxes = pins.map { (tip, width) -> placeMarkerBox(tip.x - p.x, tip.y - p.y, width, marker, tail) }
                val offset = stopLabelOffset(stop.labelOnLeft, box.width, box.height, Sizes.mapStopMarker.toPx() / 2, boxes, Spacing.xs.toPx(), (pins.maxOfOrNull { it.second } ?: 0f) + Spacing.xs.toPx())
                val left = if (stop.labelOnLeft) p.x - offset - box.width else p.x + offset
                val corner = androidx.compose.ui.geometry.CornerRadius(box.height / 2)
                drawRoundRect(MapRouteColors.casing, Offset(left, p.y - box.height / 2), box, corner)
                drawRoundRect(stop.color, Offset(left, p.y - box.height / 2), box, corner, style = Stroke(Sizes.mapStopBorder.toPx() / 2))
                drawText(text, topLeft = Offset(left + pad, p.y - text.size.height / 2f))
            }
        } }
        state.origin?.takeUnless { covered(it.location) }?.let { drawPlaceMarker(position(it.location), originText, filled = false, surface = MapRouteColors.casing) }
        state.destination?.takeUnless { covered(it.location) }?.let { drawPlaceMarker(position(it.location), destinationText, filled = true, surface = MapRouteColors.casing) }
        state.focus?.let { val p = position(it.location); val radius = Sizes.mapMarker.toPx() / 3; drawCircle(scheme.surface, radius, p); drawCircle(scheme.onSurface, radius, p, style = Stroke(Sizes.mapRouteWalk.toPx())); drawCircle(scheme.onSurface, radius / 3, p) }
        state.visibleCursor?.let { cursor ->
            val p = position(cursor.position)
            if (cursor.sunAltitude > 0) {
                val angle = Math.toRadians(cursor.sunAzimuth)
                val length = (if(state.routeProgress!=null) Sizes.simulationSunRay else Sizes.mapSunRay).toPx() * (1 - cursor.sunAltitude.coerceIn(0.0, 90.0) / 120).toFloat()
                val end = p + Offset(sin(angle).toFloat(), -cos(angle).toFloat()) * length
                drawLine(sun.copy(alpha = sunRayOpacity(cursor.sunAltitude)), p, end, Sizes.mapSunRayStroke.toPx(), StrokeCap.Round)
                drawCircle(com.sunseat.app.ui.theme.LightSunSeatColors.sun,Sizes.mapMarker.toPx()*.16f,end)
                drawCircle(scheme.onSurface,Sizes.mapMarker.toPx()*.16f,end,style=Stroke(Sizes.hairline.toPx()))
                val inward=(p-end)*.2f
                val tip=p+(end-p)*.3f
                val side=Offset(-inward.y,inward.x)*.3f
                drawLine(scheme.onSurface,tip,tip-inward+side,Sizes.hairline.toPx())
                drawLine(scheme.onSurface,tip,tip-inward-side,Sizes.hairline.toPx())
            }
            rotate(cursor.heading.toFloat(), p) {
                val h=Sizes.mapMarker.toPx()
                val rect=androidx.compose.ui.geometry.Rect(p.x-h*.26f,p.y-h*.36f,p.x+h*.26f,p.y+h*.42f)
                val body=Path().apply { addRoundRect(androidx.compose.ui.geometry.RoundRect(rect,androidx.compose.ui.geometry.CornerRadius(h*.12f))) }
                drawPath(body,scheme.surface)
                if(cursor.sunAltitude>0) {
                    val relative=com.sunseat.app.domain.ExposureAnalyzer.normalize(cursor.sunAzimuth-cursor.heading)
                    val bounds=when {
                        relative<25 || relative>335 -> androidx.compose.ui.geometry.Rect(rect.left,rect.top,rect.right,p.y-h*.1f)
                        relative>155 && relative<205 -> androidx.compose.ui.geometry.Rect(rect.left,p.y+h*.16f,rect.right,rect.bottom)
                        relative<180 -> androidx.compose.ui.geometry.Rect(p.x,rect.top,rect.right,rect.bottom)
                        else -> androidx.compose.ui.geometry.Rect(rect.left,rect.top,p.x,rect.bottom)
                    }
                    clipPath(body) { drawRect(com.sunseat.app.ui.theme.LightSunSeatColors.sun,bounds.topLeft,bounds.size) }
                }
                drawPath(body,scheme.onSurface,style=Stroke(Sizes.hairline.toPx()))
                drawLine(scheme.onSurface,Offset(rect.left+h*.06f,p.y-h*.14f),Offset(rect.right-h*.06f,p.y-h*.14f),Sizes.hairline.toPx())
                drawLine(scheme.onSurface,Offset(p.x,p.y-h*.48f),Offset(p.x-h*.1f,p.y-h*.4f),Sizes.hairline.toPx())
                drawLine(scheme.onSurface,Offset(p.x,p.y-h*.48f),Offset(p.x+h*.1f,p.y-h*.4f),Sizes.hairline.toPx())
            }
        }
    }
    if (state.showDecorations) Box(Modifier.fillMaxSize(), contentAlignment = Alignment.BottomEnd) {
        Text(stringResource(R.string.map_preview), Modifier.background(scheme.surface).padding(Spacing.s), color = scheme.onSurfaceVariant, style = androidx.compose.material3.MaterialTheme.typography.labelSmall)
    }
    }
}

/** 테스트 지도의 줌 0 세계 폭(dp). 256dp 타일 하나가 세계 전체다(Web Mercator 관례). */
internal const val PREVIEW_WORLD_DP = 256.0

/** 테스트 지도 카메라. [position]은 정책·끌기가 정한 카메라, [shown]은 지금 그리는 카메라(맞춤 전 대체 포함). */
private class PreviewCamera {
    var position: CameraPosition? = null
    var shown: CameraPosition? = null
    var reported: CameraStatus? = null
}

/** [RouteLineSpec]을 테스트 렌더러에 그린다. 테두리 → 속 선 → 진행 방향 쉐브론 순서(네이티브와 같은 모양). */
private fun androidx.compose.ui.graphics.drawscope.DrawScope.drawRouteLine(path: Path, spec: RouteLineSpec) {
    val core = spec.core.toPx()
    if (spec.pattern == LinePattern.DOTTED) {
        drawPath(path, spec.color, style = Stroke(core, cap = StrokeCap.Round, pathEffect = PathEffect.dashPathEffect(floatArrayOf(0.01f, core + Sizes.mapRouteWalkGap.toPx()))))
        return
    }
    spec.edgeColor?.let { drawPath(path, it, style = Stroke(spec.total.toPx(), cap = StrokeCap.Round, join = androidx.compose.ui.graphics.StrokeJoin.Round)) }
    drawPath(path, spec.color, style = Stroke(core, cap = StrokeCap.Round, join = androidx.compose.ui.graphics.StrokeJoin.Round))
    val chevron = spec.edgeColor ?: return
    if (!spec.arrows) return
    val measure = androidx.compose.ui.graphics.PathMeasure().apply { setPath(path, false) }
    val spacing = Sizes.mapRouteArrowSpacing.toPx()
    val half = Sizes.mapRouteArrow.toPx() * .3f
    var at = spacing / 2
    while (at < measure.length - spacing / 4) {
        val p = measure.getPosition(at)
        val t = measure.getTangent(at)
        val n = Offset(-t.y, t.x)
        drawPath(Path().apply { moveTo(p.x - t.x * half + n.x * half, p.y - t.y * half + n.y * half); lineTo(p.x + t.x * half * .6f, p.y + t.y * half * .6f); lineTo(p.x - t.x * half - n.x * half, p.y - t.y * half - n.y * half) },
            chevron, style = Stroke(Sizes.mapRouteArrow.toPx() / 4, cap = StrokeCap.Round, join = androidx.compose.ui.graphics.StrokeJoin.Round))
        at += spacing
    }
}

/** 출발(흰 채움 + 경로 색 테두리)·도착(경로 색 채움) 글자 표식. 꼬리 끝이 좌표다. */
internal fun androidx.compose.ui.graphics.drawscope.DrawScope.drawPlaceMarker(tip: Offset, text: androidx.compose.ui.text.TextLayoutResult, filled: Boolean, surface: androidx.compose.ui.graphics.Color) {
    val tail = Sizes.mapMarkerTail.toPx()
    val height = Sizes.mapMarker.toPx() - tail
    val width = placeMarkerWidth(text.size.width.toFloat(), Sizes.mapMarker.toPx(), tail, Spacing.xs.toPx())
    val top = tip.y - Sizes.mapMarker.toPx()
    val body = Path().apply {
        addRoundRect(androidx.compose.ui.geometry.RoundRect(tip.x - width / 2, top, tip.x + width / 2, top + height, androidx.compose.ui.geometry.CornerRadius(height / 2)))
        moveTo(tip.x - tail, top + height - 1); lineTo(tip.x, tip.y); lineTo(tip.x + tail, top + height - 1); close()
    }
    drawPath(body, if (filled) MapRouteColors.transit else surface)
    drawPath(body, MapRouteColors.transit, style = Stroke(Sizes.mapStopBorder.toPx(), join = androidx.compose.ui.graphics.StrokeJoin.Round))
    drawText(text, topLeft = Offset(tip.x - text.size.width / 2f, top + (height - text.size.height) / 2))
}

/** "선택 경로: 1호선 서울역 승차 → 시청 2호선 환승 → 강남 하차". 탑승 구간이 없으면 null. */
@Composable
internal fun routeSummary(route: com.sunseat.app.domain.Route?): String? {
    val steps = route?.let { RouteLinePolicy.rideSteps(it) }.orEmpty()
    if (steps.isEmpty()) return null
    val bus = stringResource(R.string.common_bus)
    val subway = stringResource(R.string.common_subway)
    val parts = steps.map { step ->
        val line = step.line?.takeIf { it.isNotBlank() } ?: if (step.mode == Mode.SUBWAY) subway else bus
        when (step.kind) {
            StopKind.BOARD -> stringResource(R.string.map_route_board, line, step.stop)
            StopKind.TRANSFER -> stringResource(R.string.map_route_transfer, step.stop, line)
            StopKind.ALIGHT -> stringResource(R.string.map_route_alight, step.stop)
        }
    }
    return stringResource(R.string.map_route_summary, parts.joinToString(stringResource(R.string.map_route_separator)))
}
