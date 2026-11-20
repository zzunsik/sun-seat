package com.sunseat.app.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import com.sunseat.app.ui.icons.SunSeatIcons
import com.sunseat.app.ui.theme.CapsuleShape
import com.sunseat.app.ui.theme.Sizes
import com.sunseat.app.ui.theme.Spacing
import com.sunseat.app.ui.theme.SunSeatTheme

enum class Exposure { Sun, Shade }

/** 햇빛·그늘 시간 알약. 색과 함께 아이콘·글자로 구분한다. */
@Composable
fun ExposurePill(
    exposure: Exposure,
    text: String,
    modifier: Modifier = Modifier,
    compact: Boolean = false,
) {
    val c = SunSeatTheme.colors
    val (bg, fg) = when (exposure) {
        Exposure.Sun -> c.sunContainer to c.onSunContainer
        Exposure.Shade -> c.shadeContainer to c.onShadeContainer
    }
    Row(
        modifier = modifier
            .then(if (compact) Modifier.heightIn(min = Sizes.compactPillHeight) else Modifier)
            .then(if (compact) Modifier.heightIn(min = Sizes.compactPillHeight) else Modifier)
            .background(bg, CapsuleShape)
            .padding(horizontal = if (compact) Spacing.s else Spacing.m, vertical = if (compact) Spacing.xs else Sizes.pillVertical),
        horizontalArrangement = Arrangement.spacedBy(Sizes.pillGap),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            imageVector = if (exposure == Exposure.Sun) SunSeatIcons.Sun else SunSeatIcons.Shade,
            contentDescription = null,
            tint = fg,
            modifier = Modifier.size(Sizes.iconSmall),
        )
        Text(text, style = MaterialTheme.typography.labelMedium, color = fg)
    }
}
