package com.sunseat.app.ui.result

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import com.sunseat.app.R
import com.sunseat.app.domain.TripRequest
import com.sunseat.app.ui.candidates.ChosenRoute
import com.sunseat.app.ui.components.SectionCard
import com.sunseat.app.ui.formatDuration
import com.sunseat.app.ui.theme.Spacing
import com.sunseat.app.ui.theme.SunSeatTheme

/** 임시 화면: 선택한 경로만 보여 준다. 결과 화면은 #13에서 만든다. */
@Composable
fun ResultPlaceholder(request: TripRequest, chosen: ChosenRoute, onBack: () -> Unit, modifier: Modifier = Modifier) {
    val scheme = SunSeatTheme.scheme
    Surface(modifier.fillMaxSize(), color = scheme.background, contentColor = scheme.onSurface) {
        Column(
            Modifier.windowInsetsPadding(WindowInsets.safeDrawing).padding(Spacing.screen),
            verticalArrangement = Arrangement.spacedBy(Spacing.m),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onBack) { Icon(painterResource(R.drawable.ic_ms_arrow_back), contentDescription = stringResource(R.string.common_back)) }
                Text("${request.origin.name} → ${request.destination.name}", style = MaterialTheme.typography.titleMedium)
            }
            SectionCard(title = "선택한 경로") {
                Text("${formatDuration(chosen.totalDuration)} · 구간 ${chosen.route.legs.size}개", style = MaterialTheme.typography.bodyLarge)
                chosen.route.legs.forEach { leg ->
                    Text("${leg.mode} ${leg.startName} → ${leg.endName} (${formatDuration(leg.duration)})", style = MaterialTheme.typography.bodySmall, color = scheme.onSurfaceVariant)
                }
                Text("결과 화면은 다음 작업(#13)에서 만듭니다.", style = MaterialTheme.typography.bodySmall, color = scheme.onSurfaceVariant)
            }
        }
    }
}
