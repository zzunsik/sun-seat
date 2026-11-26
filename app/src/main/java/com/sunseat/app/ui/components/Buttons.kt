package com.sunseat.app.ui.components

import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.heightIn
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.sunseat.app.ui.theme.CapsuleShape
import com.sunseat.app.ui.theme.Spacing
import com.sunseat.app.ui.theme.SunSeatTheme
import com.sunseat.app.ui.theme.rememberSunSeatHaptics

/**
 * 화면의 주 행동 버튼. 행동색(primary) 채움. 화면마다 하나만 쓴다.
 * 해·그늘 색은 의미 전용이라 버튼에 쓰지 않는다. docs/DESIGN.md 2.3
 */
@Composable
fun PrimaryButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    content: @Composable RowScope.() -> Unit,
) {
    val haptics = rememberSunSeatHaptics()
    Button(
        onClick = {
            haptics.press()
            onClick()
        },
        modifier = modifier.heightIn(min = Spacing.touch),
        enabled = enabled,
        shape = CapsuleShape,
        colors = ButtonDefaults.buttonColors(
            containerColor = SunSeatTheme.scheme.primary,
            contentColor = SunSeatTheme.scheme.onPrimary,
        ),
        content = content,
    )
}

/** 보조 행동. 글자 버튼. */
@Composable
fun QuietButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    content: @Composable RowScope.() -> Unit,
) {
    TextButton(
        onClick = onClick,
        modifier = modifier.heightIn(min = Spacing.touch),
        shape = CapsuleShape,
        colors = ButtonDefaults.textButtonColors(contentColor = SunSeatTheme.scheme.onSurface),
        content = content,
    )
}

/** 행 안 보조 행동. 보이는 높이 40, 터치 높이 48의 선택 톤 캡슐. */
@Composable
fun SecondaryButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    content: @Composable RowScope.() -> Unit,
) {
    androidx.compose.foundation.layout.Box(
        Modifier.heightIn(min = Spacing.touch),
        contentAlignment = androidx.compose.ui.Alignment.Center,
    ) {
        androidx.compose.material3.FilledTonalButton(
            onClick = onClick,
            modifier = modifier.heightIn(min = com.sunseat.app.ui.theme.Sizes.secondaryButton),
            shape = CapsuleShape,
            colors = ButtonDefaults.filledTonalButtonColors(
                containerColor = SunSeatTheme.scheme.primaryContainer,
                contentColor = SunSeatTheme.scheme.onPrimaryContainer,
            ),
            content = content,
        )
    }
}
