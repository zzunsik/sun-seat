package com.sunseat.app.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Slider
import androidx.compose.material3.rememberSliderState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.LayoutDirection
import com.sunseat.app.ui.theme.Sizes
import com.sunseat.app.ui.theme.Spacing
import com.sunseat.app.ui.theme.SunSeatTheme

/** DESIGN 4.2.1. Material 터치·키보드·접근성 동작을 보존하는 굵은 트랙과 막대 손잡이. */
@Composable
fun SunSeatSlider(value: Float, onValueChange: (Float) -> Unit, modifier: Modifier = Modifier) {
    val scheme = SunSeatTheme.scheme
    val state = rememberSliderState(value)
    state.value = value
    val rtl = LocalLayoutDirection.current == LayoutDirection.Rtl
    Slider(
        state = state,
        onValueChange = onValueChange,
        modifier = modifier.heightIn(min = Spacing.touch),
        thumb = {
            Box(Modifier.size(Sizes.sliderThumbWidth, Spacing.touch), contentAlignment = Alignment.Center) {
                Box(Modifier.size(Sizes.sliderThumbWidth, Sizes.sliderThumbHeight)
                    .background(scheme.primary, RoundedCornerShape(Sizes.sliderThumbRadius)))
            }
        },
        track = {
            Canvas(Modifier.fillMaxWidth().height(Sizes.sliderTrack)) {
                val radius = CornerRadius(Sizes.sliderTrackRadius.toPx())
                drawRoundRect(scheme.surfaceContainerHigh, cornerRadius = radius)
                val width = size.width * state.value.coerceIn(0f, 1f)
                drawRoundRect(scheme.primary, topLeft = Offset(if (rtl) size.width - width else 0f, 0f),
                    size = Size(width, size.height), cornerRadius = radius)
            }
        },
    )
}
