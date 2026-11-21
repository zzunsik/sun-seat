package com.sunseat.app.ui.input

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.rememberTextMeasurer
import com.sunseat.app.R
import com.sunseat.app.ui.components.PrimaryButton
import com.sunseat.app.ui.components.QuietButton
import com.sunseat.app.ui.map.MapState
import com.sunseat.app.ui.map.SunSeatMap
import com.sunseat.app.ui.map.drawPlaceMarker
import com.sunseat.app.ui.theme.MapRouteColors
import com.sunseat.app.ui.theme.Sizes
import com.sunseat.app.ui.theme.Spacing
import com.sunseat.app.ui.theme.SunSeatTheme

/**
 * 지도에서 출발·도착 위치 고르기(#85). 지도 가운데 고정 핀 아래 좌표가 후보이고 '선택'을 눌러야 확정된다.
 * 지도는 위아래 막대와 겹치지 않는 영역에 그려서 보이는 지도의 가운데 = 카메라 가운데 = 핀 끝이 되게 한다.
 * 위쪽 상단 바(닫기·제목), 아래 패널(주소·안내·주 행동 하나)은 앱 화면 모드를 따르고 지도는 밝게 고정한다(DESIGN 2.6).
 */
@Composable
internal fun MapPickScreen(
    pick: MapPickState,
    actions: InputActions,
    origin: com.sunseat.app.domain.NamedPlace?,
    destination: com.sunseat.app.domain.NamedPlace?,
    modifier: Modifier = Modifier,
    mapLocating: Boolean = false,
    notice: (@Composable () -> Unit)? = null,
) {
    val scheme = SunSeatTheme.scheme
    BackHandler(onBack = actions.cancelMapPick)
    val isOrigin = pick.field == EndpointField.ORIGIN
    val title = stringResource(if (isOrigin) R.string.map_pick_title_origin else R.string.map_pick_title_destination)
    val unnamed = stringResource(R.string.map_pick_unnamed)
    val loading = stringResource(R.string.map_pick_loading)
    val headline = when (pick.status) {
        PickAddressStatus.FOUND -> pick.address ?: unnamed
        PickAddressStatus.LOADING -> loading
        PickAddressStatus.NONE, PickAddressStatus.FAILED -> unnamed
    }
    Surface(modifier.fillMaxSize(), color = scheme.background, contentColor = scheme.onSurface) {
        Column(Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.safeDrawing)) {
            Row(
                Modifier.fillMaxWidth().heightIn(min = Sizes.journeyBar).padding(start = Spacing.xs, end = Spacing.screen),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(Spacing.xs),
            ) {
                IconButton(onClick = actions.cancelMapPick) {
                    Icon(painterResource(R.drawable.ic_ms_arrow_back), contentDescription = stringResource(R.string.map_pick_close))
                }
                Text(title, Modifier.weight(1f).semantics { heading() }, style = MaterialTheme.typography.titleMedium)
            }
            HorizontalDivider(color = scheme.outlineVariant)
            Box(Modifier.weight(1f).fillMaxWidth()) {
                // The edited endpoint's marker is replaced by the pin; the other endpoint stays for context.
                val description = stringResource(R.string.map_pick_description, title, headline)
                // #147: 지도를 쓸 수 없으면 지도 노드의 안내(지도 위 글자)를 그대로 읽는다. 쓸 수 있을 때만 핀 위치 설명 하나로 바꾼다.
                Box(Modifier.fillMaxSize().then(if (pick.mapUnavailable) Modifier else Modifier.clearAndSetSemantics { contentDescription = description })) {
                    SunSeatMap(
                        MapState(
                            origin = origin.takeUnless { isOrigin },
                            destination = destination.takeIf { isOrigin },
                            showDecorations = false,
                            fitEnabled = false,
                            // Current pin position with the request sequence: a recreated map opens where the pin was.
                            center = com.sunseat.app.ui.map.MapFocus(pick.center, pick.camera.sequence),
                            focus = null,
                            onGesture = actions.onPickCameraMoving,
                            onCameraIdle = actions.onPickCameraIdle,
                            onUnavailable = actions.onPickMapUnavailable,
                        ),
                        Modifier.fillMaxSize(),
                    )
                }
                if (!pick.mapUnavailable) {
                    PickPin(isOrigin, Modifier.fillMaxSize())
                    Surface(
                        Modifier.align(Alignment.TopEnd).padding(Spacing.m),
                        shape = CircleShape, color = scheme.surface, contentColor = scheme.onSurface,
                    ) {
                        IconButton(onClick = actions.showCurrentLocationOnMap, enabled = !mapLocating, modifier = Modifier.size(Spacing.touch)) {
                            Icon(painterResource(R.drawable.ic_ms_my_location), stringResource(R.string.map_pick_recenter), tint = scheme.onSurface)
                        }
                    }
                }
            }
            HorizontalDivider(color = scheme.outlineVariant)
            Column(
                Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(horizontal = Spacing.screen, vertical = Spacing.m),
                verticalArrangement = Arrangement.spacedBy(Spacing.s),
            ) {
                if (pick.mapUnavailable) {
                    Text(stringResource(R.string.map_pick_unavailable), Modifier.semantics { liveRegion = LiveRegionMode.Polite }, style = MaterialTheme.typography.bodyMedium, color = scheme.onSurface)
                } else {
                    Column(Modifier.semantics(mergeDescendants = true) { liveRegion = LiveRegionMode.Polite }) {
                        Text(headline, style = MaterialTheme.typography.titleMedium, color = scheme.onSurface)
                        val detail = when (pick.status) {
                            PickAddressStatus.NONE -> stringResource(R.string.map_pick_no_address)
                            PickAddressStatus.FAILED -> stringResource(R.string.map_pick_address_error)
                            else -> stringResource(R.string.map_pick_guide).takeIf { pick.messageRes == null }
                        }
                        // The same-endpoint warning replaces the guide line so the panel stays short.
                        if (detail != null) Text(detail, style = MaterialTheme.typography.bodySmall, color = scheme.onSurfaceVariant)
                    }
                    if (pick.status == PickAddressStatus.FAILED && pick.canRetry) {
                        QuietButton(onClick = actions.retryPickAddress) {
                            Icon(painterResource(R.drawable.ic_ms_refresh), contentDescription = null, modifier = Modifier.size(Sizes.iconMedium))
                            androidx.compose.foundation.layout.Spacer(Modifier.size(Spacing.s))
                            Text(stringResource(R.string.search_retry), style = MaterialTheme.typography.labelLarge)
                        }
                    }
                    pick.messageRes?.let {
                        Text(stringResource(it), Modifier.semantics { liveRegion = LiveRegionMode.Polite }, style = MaterialTheme.typography.bodyMedium, color = scheme.error)
                    }
                }
                notice?.invoke()
                PrimaryButton(onClick = actions.confirmMapPick, enabled = pick.canConfirm, modifier = Modifier.fillMaxWidth()) {
                    Text(stringResource(if (isOrigin) R.string.map_pick_confirm_origin else R.string.map_pick_confirm_destination))
                }
            }
        }
    }
}

/** 핀: 지도 위 출발·도착 글자 표식과 같은 모양(DESIGN 11.1). 꼬리 끝이 정확히 지도 가운데다. */
@Composable
private fun PickPin(isOrigin: Boolean, modifier: Modifier = Modifier) {
    val measurer = rememberTextMeasurer()
    val style = MaterialTheme.typography.labelMedium
    val label = stringResource(if (isOrigin) R.string.input_origin else R.string.input_destination)
    Canvas(modifier.clearAndSetSemantics {}) {
        val text = measurer.measure(label, style.copy(color = if (isOrigin) MapRouteColors.transit else MapRouteColors.casing))
        val tip = Offset(size.width / 2, size.height / 2)
        drawCircle(MapRouteColors.transit, Sizes.mapStopBorder.toPx() * 2, tip)
        drawPlaceMarker(tip, text, filled = !isOrigin, surface = MapRouteColors.casing)
    }
}
