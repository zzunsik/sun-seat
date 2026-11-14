package com.sunseat.app.ui.components

import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import com.airbnb.lottie.LottieProperty
import com.airbnb.lottie.compose.LottieAnimation
import com.airbnb.lottie.compose.LottieCompositionSpec
import com.airbnb.lottie.compose.LottieConstants
import com.airbnb.lottie.compose.rememberLottieComposition
import com.airbnb.lottie.compose.rememberLottieDynamicProperties
import com.airbnb.lottie.compose.rememberLottieDynamicProperty
import com.sunseat.app.R
import com.sunseat.app.ui.theme.Sizes
import com.sunseat.app.ui.theme.SunSeatTheme

/**
 * 로딩 연출: 햇살이 천천히 도는 해(Lottie, res/raw/sun_loading.json, 직접 제작).
 * 색은 테마의 `sun` 토큰으로 바꿔 그린다. 애니메이션 삭제 설정이면 멈춘 그림으로 보여 준다.
 * docs/DESIGN.md 7: Lottie는 로딩 연출에만 쓴다.
 */
@Composable
fun SunLoader(
    description: String,
    modifier: Modifier = Modifier,
    playing: Boolean = true,
) {
    val composition = rememberLottieComposition(LottieCompositionSpec.RawRes(R.raw.sun_loading))
    val sun = SunSeatTheme.colors.sun.toArgb()
    val dynamic = rememberLottieDynamicProperties(
        rememberLottieDynamicProperty(LottieProperty.COLOR, sun, "**"),
    )
    val animate = playing && !SunSeatTheme.reduceMotion
    LottieAnimation(
        composition = composition.value,
        isPlaying = animate,
        iterations = LottieConstants.IterateForever,
        dynamicProperties = dynamic,
        modifier = modifier
            .size(Sizes.loader)
            .semantics { contentDescription = description },
    )
}
