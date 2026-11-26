package com.sunseat.app.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import com.sunseat.app.ui.theme.Spacing
import com.sunseat.app.ui.theme.SunSeatTheme

/** 정보 카드. 옅은 surfaceContainer, 큰 모서리, 제목은 titleMedium. docs/DESIGN.md 3·4 */
@Composable
fun SectionCard(
    modifier: Modifier = Modifier,
    title: String? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    Surface(
        color = SunSeatTheme.scheme.surfaceContainer,
        shape = MaterialTheme.shapes.large,
        border = if (SunSeatTheme.scheme.surface.luminance() < 0.5f)
            androidx.compose.foundation.BorderStroke(com.sunseat.app.ui.theme.Sizes.hairline, SunSeatTheme.scheme.outlineVariant) else null,
        modifier = modifier.fillMaxWidth(),
    ) {
        Column(Modifier.padding(Spacing.l), verticalArrangement = Arrangement.spacedBy(Spacing.m)) {
            if (title != null) {
                Text(
                    title,
                    style = MaterialTheme.typography.titleMedium,
                    color = SunSeatTheme.scheme.onSurface,
                    modifier = Modifier.semantics { heading() },
                )
            }
            content()
        }
    }
}
