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
import com.sunseat.app.ui.text.word
import com.sunseat.app.ui.text.label
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
    graph: com.sunseat.app.AppGraph,
    request: TripRequest,
    chosen: ChosenRoute,
    onBack: () -> Unit,
    onRestart: () -> Unit,
    modifier: Modifier = Modifier,
    onSettings: () -> Unit = {},
    onSimulation: (TripResult) -> Unit = {},
    onSchedule: (java.time.LocalDateTime?, SunGoal) -> Unit = { _, _ -> },
    calculationRevision: Long = 0,
    viewModel: ResultViewModel = viewModel(
        key = request.toString() + chosen.hashCode() + ":" + calculationRevision,
        factory = viewModelFactory { initializer { ResultViewModel(request, chosen, now = { graph.clock.instant() }, settings = graph.settings.settings) } },
    ),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val context = LocalContext.current
    ResultScreen(
        now = java.time.LocalDateTime.now(graph.clock),
        request = request,
        state = state,
        modifier = modifier,
        actions = ResultActions(
            onSettings = onSettings,
            onSimulation = { state.result?.let(onSimulation) },
            onSchedule = { value -> onSchedule(value, state.goal) },
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
    val (res, arg) = headlineRes(context, request, result)
    val conclusion = if (arg != null) context.getString(res, arg) else context.getString(res)
    val goal = context.getString(if (result.goal == SunGoal.AVOID) R.string.input_goal_avoid else R.string.input_goal_seek)
    fun oneLine(value: String) = value.replace(Regex("\\s+"), " ").trim()
    fun brief(value: String): String {
        val text = oneLine(value)
        return if (text.length <= 12) text else text.take(11) + "…"
    }
    val lines = mutableListOf(
        context.getString(R.string.share_route, oneLine(request.origin.name), oneLine(request.destination.name), time),
        context.getString(R.string.share_goal, goal, conclusion),
        if (!(result.shadeIfFollowed + result.sunIfFollowed).isZero) context.getString(R.string.share_exposure, formatDuration(context, result.shadeIfFollowed, zeroOk = true), formatDuration(context, result.sunIfFollowed, zeroOk = true))
        else context.getString(R.string.result_no_outdoor_detail),
    )
    val recommendations = result.legs.mapNotNull { leg ->
        val sit = leg.advice as? LegAdvice.Sit ?: return@mapNotNull null
        val where = when {
            request.mode != TravelMode.TRANSIT -> sit.carSeats.label(context)
            leg.leg.mode == Mode.SUBWAY -> context.getString(R.string.share_bench, sit.side.word(context))
            else -> context.getString(R.string.share_window, sit.side.word(context))
        }
        val name = if (leg.leg.startName.isBlank()) context.getString(R.string.common_car) else brief(leg.leg.endName)
        "$name: $where"
    }
    if (recommendations.isNotEmpty()) {
        val parts = recommendations.take(2).toMutableList()
        if (recommendations.size > 2) parts += context.getString(R.string.share_more, recommendations.size - 2)
        lines += "- " + parts.joinToString(" · ")
    }
    val risks = mutableListOf<String>()
    if (!result.aheadSun.isZero) risks += context.getString(R.string.share_ahead, formatDuration(context, result.aheadSun))
    result.glare.firstOrNull()?.let { g ->
        val totalMin = java.time.Duration.between(result.departure, result.modeledArrival).toMinutes()
        var text = when {
            g.fromMinute == 0L && g.toMinute >= totalMin -> context.getString(R.string.share_glare_whole)
            g.fromMinute == 0L -> context.getString(R.string.share_glare_start, g.toMinute)
            else -> context.getString(R.string.share_glare_after, g.fromMinute, g.toMinute)
        }
        if (result.glare.size > 1) text += " · " + context.getString(R.string.share_more, result.glare.size - 1)
        risks += text
    }
    if (risks.isNotEmpty()) lines += risks.joinToString(" · ")
    lines += "— " + context.getString(R.string.app_name)
    return lines.joinToString("\n") { oneLine(it) }
}

private fun share(context: Context, request: TripRequest, result: TripResult) {
    val send = Intent(Intent.ACTION_SEND).apply {
        type = "text/plain"
        putExtra(Intent.EXTRA_TEXT, shareText(context, request, result))
    }
    context.startActivity(Intent.createChooser(send, context.getString(R.string.result_share_chooser)))
}
