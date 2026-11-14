package com.sunseat.app.ui.candidates

import androidx.compose.foundation.background
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
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import com.sunseat.app.R
import com.sunseat.app.domain.SunGoal
import com.sunseat.app.domain.TravelMode
import com.sunseat.app.domain.TripRequest
import com.sunseat.app.ui.components.SectionCard
import com.sunseat.app.ui.theme.Spacing
import com.sunseat.app.ui.theme.SunSeatTheme

/** 임시 화면: 입력 결과만 보여 준다. 경로 후보 화면은 #12에서 만든다. */
@Composable
fun RouteCandidatesPlaceholder(request: TripRequest, onBack: () -> Unit, modifier: Modifier = Modifier) {
    val scheme = SunSeatTheme.scheme
    Column(
        modifier
            .fillMaxSize()
            .background(scheme.background)
            .windowInsetsPadding(WindowInsets.safeDrawing)
            .padding(Spacing.screen),
        verticalArrangement = Arrangement.spacedBy(Spacing.m),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack) { Icon(painterResource(R.drawable.ic_ms_arrow_back), contentDescription = "뒤로") }
            Text("${request.origin.name} → ${request.destination.name}", style = MaterialTheme.typography.titleMedium, color = scheme.onSurface)
        }
        SectionCard(title = "입력한 내용") {
            val mode = if (request.mode == TravelMode.TRANSIT) "대중교통" else "자가용"
            val goal = if (request.goal == SunGoal.AVOID) "햇빛 피하기" else "햇빛 받기"
            Text("$mode · ${request.departure ?: "지금"} · $goal", style = MaterialTheme.typography.bodyLarge, color = scheme.onSurface)
            Text("경로 후보 화면은 다음 작업(#12)에서 만듭니다.", style = MaterialTheme.typography.bodySmall, color = scheme.onSurfaceVariant)
        }
    }
}
