package com.sunseat.app.ui.components

import android.os.Build
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.material3.LocalContentColor
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.unit.dp
import com.kyant.backdrop.Backdrop
import com.kyant.backdrop.drawBackdrop
import com.kyant.backdrop.effects.blur
import com.kyant.backdrop.effects.lens
import com.kyant.backdrop.effects.vibrancy
import com.sunseat.app.ui.theme.CapsuleShape
import com.sunseat.app.ui.theme.SunSeatTheme

/** 유리 효과(굴절)를 쓸 수 있는 기기인지. lens는 RuntimeShader(Android 13+)가 필요하다. */
val isGlassSupported: Boolean get() = Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU

/**
 * 떠 있는 컨트롤 전용 캡슐. docs/DESIGN.md 5
 *
 * 유리 효과가 꺼져 있거나 지원되지 않으면 불투명 캡슐로 그린다.
 * [backdrop]은 캡슐 뒤에 있는 화면(`Modifier.layerBackdrop`)이다.
 * 캡슐 안 글자·아이콘 색은 항상 onSurface다.
 */
@Composable
fun GlassCapsule(
    backdrop: Backdrop,
    modifier: Modifier = Modifier,
    content: @Composable BoxScope.() -> Unit,
) {
    val glass = SunSeatTheme.glassEnabled && isGlassSupported
    val scheme = SunSeatTheme.scheme
    val glassSurface = SunSeatTheme.colors.glassSurface

    val capsule = if (glass) {
        modifier.drawBackdrop(
            backdrop = backdrop,
            shape = { CapsuleShape },
            effects = {
                vibrancy()
                blur(14.dp.toPx())
                lens(16.dp.toPx(), 28.dp.toPx())
            },
            onDrawSurface = { drawRect(glassSurface) },
        )
    } else {
        modifier
            .shadow(8.dp, CapsuleShape, clip = false)
            .clip(CapsuleShape)
            .background(scheme.surface)
            .border(1.dp, scheme.outlineVariant, CapsuleShape)
    }
    CompositionLocalProvider(LocalContentColor provides scheme.onSurface) {
        Box(capsule, content = content)
    }
}
