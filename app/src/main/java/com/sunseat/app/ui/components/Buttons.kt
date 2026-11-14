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
 * 화면의 주 행동 버튼. 진한 잉크색(onSurface) 채움. 화면마다 하나만 쓴다.
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
            containerColor = SunSeatTheme.scheme.inverseSurface,
            contentColor = SunSeatTheme.scheme.inverseOnSurface,
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
