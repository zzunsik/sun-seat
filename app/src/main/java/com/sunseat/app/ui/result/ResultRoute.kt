package com.sunseat.app.ui.result

import android.content.Context
import android.content.Intent
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.sunseat.app.R
import com.sunseat.app.domain.LegAdvice
import com.sunseat.app.domain.Mode
import com.sunseat.app.domain.SunGoal
import com.sunseat.app.domain.TravelMode
import com.sunseat.app.domain.TripAdvice
import com.sunseat.app.domain.TripRequest
import com.sunseat.app.domain.TripResult
import com.sunseat.app.ui.candidates.ChosenRoute
import com.sunseat.app.ui.formatDuration
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/** 결과 화면을 ViewModel과 연결한다. */
@Composable
fun ResultRoute(
    request: TripRequest,
    chosen: ChosenRoute,
    onBack: () -> Unit,
    onRestart: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: ResultViewModel = viewModel(
        key = request.toString() + chosen.hashCode(),
        factory = viewModelFactory { initializer { ResultViewModel(request, chosen) } },
    ),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val context = LocalContext.current
    ResultScreen(
        request = request,
        state = state,
        modifier = modifier,
        actions = ResultActions(
            onBack = onBack,
            onRestart = onRestart,
            onShare = { state.result?.let { share(context, request, it) } },
            setGoal = viewModel::setGoal,
        ),
    )
}

/** 공유 문구: 경로·출발 시각·목적·결론·추천대로 앉았을 때 시간·구간별·눈부심. */
internal fun shareText(context: Context, request: TripRequest, result: TripResult, zone: ZoneId = ZoneId.systemDefault()): String {
    val time = result.departure.atZone(zone).format(DateTimeFormatter.ofPattern("M/d HH:mm"))
    val (res, arg) = headlineRes(request, result)
    val conclusion = if (arg != null) context.getString(res, arg) else context.getString(res)
    val goal = context.getString(if (result.goal == SunGoal.AVOID) R.string.input_goal_avoid else R.string.input_goal_seek)
    val lines = mutableListOf(
        "${request.origin.name} → ${request.destination.name} ($time 출발)",
        "햇빛 $goal: $conclusion",
    )
    if (result.advice is TripAdvice.Sit || result.advice == TripAdvice.Mixed) {
        lines += "추천대로 앉으면 그늘 ${formatDuration(result.shadeIfFollowed, zeroOk = true)} · 햇빛 ${formatDuration(result.sunIfFollowed, zeroOk = true)}"
    }
    result.legs.forEach { leg ->
        val sit = leg.advice as? LegAdvice.Sit ?: return@forEach
        val where = when {
            request.mode == TravelMode.CAR -> sit.label
            leg.leg.mode == Mode.SUBWAY -> "진행 방향 ${sit.side.word()} 좌석"
            else -> "진행 방향 ${sit.side.word()} 창가"
        }
        val name = if (leg.leg.startName.isBlank()) "자가용" else "${leg.leg.startName}→${leg.leg.endName}"
        lines += "- $name: $where"
    }
    if (!result.aheadSun.isZero) lines += "앞유리로 해가 들어요 ${formatDuration(result.aheadSun)}"
    val totalMin = java.time.Duration.between(result.departure, result.arrival).toMinutes()
    result.glare.forEach { g ->
        lines += when {
            g.fromMinute == 0L && g.toMinute >= totalMin -> "운전자 눈부심: 이동 내내"
            g.fromMinute == 0L -> "운전자 눈부심: 출발 직후–${g.toMinute}분"
            else -> "운전자 눈부심: 출발 후 ${g.fromMinute}–${g.toMinute}분"
        }
    }
    lines += "— " + context.getString(R.string.app_name)
    return lines.joinToString("\n")
}

private fun share(context: Context, request: TripRequest, result: TripResult) {
    val send = Intent(Intent.ACTION_SEND).apply {
        type = "text/plain"
        putExtra(Intent.EXTRA_TEXT, shareText(context, request, result))
    }
    context.startActivity(Intent.createChooser(send, context.getString(R.string.result_share_chooser)))
}
