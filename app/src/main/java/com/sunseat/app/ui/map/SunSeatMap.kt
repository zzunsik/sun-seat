package com.sunseat.app.ui.map

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import com.sunseat.app.R
import com.sunseat.app.domain.Mode
import com.sunseat.app.ui.theme.Sizes
import com.sunseat.app.ui.theme.Spacing
import com.sunseat.app.ui.theme.SunSeatTheme
import kotlin.math.cos
import kotlin.math.sin

enum class MapRenderer { SDK, PREVIEW }
@Suppress("ComposeCompositionLocalUsage") // 네이티브 SDK를 테스트 렌더러로 교체하는 경계
val LocalMapRenderer = staticCompositionLocalOf { MapRenderer.SDK }

@Composable
fun SunSeatMap(state: MapState, modifier: Modifier = Modifier) {
    val description = stringResource(R.string.map_description, state.origin?.name.orEmpty(), state.destination?.name.orEmpty())
    Box(modifier.semantics { contentDescription = description }) {
        if (LocalMapRenderer.current == MapRenderer.PREVIEW) MapPlaceholder(state, Modifier.fillMaxSize())
        else NativeMap(state, Modifier.fillMaxSize())
        if (state.showDecorations && (state.origin != null || state.destination != null)) {
            Row(Modifier.align(Alignment.TopStart).background(SunSeatTheme.scheme.surface).padding(Spacing.s), horizontalArrangement = Arrangement.spacedBy(Spacing.s), verticalAlignment = Alignment.CenterVertically) {
                val ink = SunSeatTheme.scheme.onSurface
                if (state.origin != null) {
                    Canvas(Modifier.size(Sizes.iconSmall)) { drawCircle(ink, size.minDimension / 3, style = Stroke(Sizes.mapWalk.toPx())) }
                    Text(stringResource(R.string.input_origin), color = ink, style = androidx.compose.material3.MaterialTheme.typography.labelSmall)
                }
                if (state.destination != null) {
                    Canvas(Modifier.size(Sizes.iconSmall)) { drawRect(ink, Offset(size.width / 6, size.height / 6), androidx.compose.ui.geometry.Size(size.width * 2 / 3, size.height * 2 / 3)) }
                    Text(stringResource(R.string.input_destination), color = ink, style = androidx.compose.material3.MaterialTheme.typography.labelSmall)
                }
            }
        }
    }
}

/** 네이티브 지도 없이 실제 경로 좌표를 보여 주는 테스트 렌더러. */
@Composable
fun MapPlaceholder(state: MapState, modifier: Modifier = Modifier) {
    val scheme = com.sunseat.app.ui.theme.LightScheme
    val sun = com.sunseat.app.ui.theme.LightSunSeatColors.onSunContainer
    Box(modifier) {
    Canvas(Modifier.fillMaxSize().background(scheme.surfaceContainer)) {
        val top = state.padding.top.toPx()
        val bottom = state.padding.bottom.toPx()
        val projection = MapProjection(state.points(), size.width, (size.height - top - bottom).coerceAtLeast(1f), Sizes.mapInset.toPx())
        fun position(point: com.sunseat.app.domain.LatLng): Offset = projection.project(point).let { Offset(it.first, it.second + top) }
        val grid = Sizes.mapInset.toPx()
        var x = 0f
        while (x < size.width) { drawLine(scheme.outlineVariant, Offset(x, 0f), Offset(x, size.height), Sizes.hairline.toPx()); x += grid }
        var y = 0f
        while (y < size.height) { drawLine(scheme.outlineVariant, Offset(0f, y), Offset(size.width, y), Sizes.hairline.toPx()); y += grid }
        state.routes.withIndex().sortedBy { it.index == state.selectedRoute }.forEach { (index, route) -> route.legs.forEach { leg ->
            if (leg.path.size > 1) {
                val path = Path().apply { leg.path.forEachIndexed { i, p -> val at = position(p); if (i == 0) moveTo(at.x, at.y) else lineTo(at.x, at.y) } }
                val width = when (leg.mode) { Mode.WALK -> Sizes.mapWalk; Mode.SUBWAY -> Sizes.mapSubway; else -> Sizes.mapRoute }.toPx()
                drawPath(path, if (index == state.selectedRoute && state.routeProgress == null) scheme.onSurface else scheme.outline, style = Stroke(width, cap = StrokeCap.Round, pathEffect = if (leg.mode == Mode.WALK) PathEffect.dashPathEffect(floatArrayOf(grid / 4, grid / 5)) else null))
            }
        } }
        state.routeProgress?.let { progress ->
            val legs = state.routes.getOrNull(state.selectedRoute)?.legs.orEmpty()
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
                if (started) drawPath(path, scheme.onSurface, style = Stroke(Sizes.mapRoute.toPx(), cap = StrokeCap.Round))
            }
        }
        state.origin?.let { drawCircle(scheme.surface, Sizes.mapMarker.toPx() / 3, position(it.location)); drawCircle(scheme.onSurface, Sizes.mapMarker.toPx() / 3, position(it.location), style = Stroke(Sizes.mapWalk.toPx())) }
        state.destination?.let { val p = position(it.location); val radius = Sizes.mapMarker.toPx() / 3; drawRect(scheme.onSurface, p - Offset(radius, radius), androidx.compose.ui.geometry.Size(radius * 2, radius * 2)) }
        state.cursor?.let { cursor ->
            val p = position(cursor.position)
            if (cursor.sunAltitude > 0) {
                val angle = Math.toRadians(cursor.sunAzimuth)
                val length = (if(state.routeProgress!=null) Sizes.simulationSunRay else Sizes.mapSunRay).toPx() * (1 - cursor.sunAltitude.coerceIn(0.0, 90.0) / 120).toFloat()
                val end = p + Offset(sin(angle).toFloat(), -cos(angle).toFloat()) * length
                drawLine(sun.copy(alpha = sunRayOpacity(cursor.sunAltitude)), p, end, Sizes.mapRoute.toPx(), StrokeCap.Round)
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
