package com.sunseat.app.ui.components

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
import com.sunseat.app.ui.theme.CapsuleShape
import com.sunseat.app.ui.theme.Sizes
import com.sunseat.app.ui.theme.SunSeatTheme

/**
 * 떠 있는 컨트롤 전용 불투명 캡슐. docs/DESIGN.md 5
 * 캡슐 안 글자·아이콘 색은 항상 onSurface다.
 */
@Composable
fun FloatingCapsule(
    modifier: Modifier = Modifier,
    content: @Composable BoxScope.() -> Unit,
) {
    val scheme = SunSeatTheme.scheme
    CompositionLocalProvider(LocalContentColor provides scheme.onSurface) {
        Box(
            modifier
                .shadow(Sizes.capsuleElevation, CapsuleShape, clip = false)
                .clip(CapsuleShape)
                .background(scheme.surface)
                .border(Sizes.hairline, scheme.outlineVariant, CapsuleShape),
            content = content,
        )
    }
}
