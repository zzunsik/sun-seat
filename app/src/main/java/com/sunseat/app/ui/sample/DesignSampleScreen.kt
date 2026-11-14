package com.sunseat.app.ui.sample

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.slideInVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ButtonGroupDefaults
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.ToggleButton
import androidx.compose.material3.ToggleButtonDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import com.sunseat.app.R
import com.sunseat.app.ui.components.Exposure
import com.sunseat.app.ui.components.ExposurePill
import com.sunseat.app.ui.components.FloatingCapsule
import com.sunseat.app.ui.components.PrimaryButton
import com.sunseat.app.ui.components.QuietButton
import com.sunseat.app.ui.components.SeatSunDiagram
import com.sunseat.app.ui.components.SideTimeline
import com.sunseat.app.ui.components.SunSpan
import com.sunseat.app.ui.components.Tick
import com.sunseat.app.ui.theme.Sizes
import com.sunseat.app.ui.theme.Spacing
import com.sunseat.app.ui.theme.SunSeatTheme
import com.sunseat.app.ui.theme.rememberSunSeatHaptics
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

enum class ThemeMode(val label: String) { System("시스템"), Light("밝게"), Dark("어둡게") }

/** 1차 점검용 화면 설정. 실제 화면이 생기면(#11~#13) 이 샘플은 지운다. */
data class SampleSettings(val theme: ThemeMode = ThemeMode.System)

private enum class Side(val label: String) { Left("왼쪽"), Right("오른쪽") }

private fun fmt(min: Int): String {
    val h = min / 60
    val m = min % 60
    return if (h > 0) "${h}시간 ${m}분" else "${m}분"
}

// 가짜 데이터: 서울 09:00 → 부산 11:40(160분). 해는 진행 방향 오른쪽, 10:33~10:55에만 왼쪽.
private const val TOTAL_MIN = 160
private val LEFT_SUN = listOf(SunSpan(93f / TOTAL_MIN, 115f / TOTAL_MIN))
private val RIGHT_SUN = listOf(SunSpan(0f, 93f / TOTAL_MIN), SunSpan(115f / TOTAL_MIN, 1f))
private val TICKS = listOf(
    Tick(0f, "09:00"),
    Tick(60f / TOTAL_MIN, "10:00"),
    Tick(120f / TOTAL_MIN, "11:00"),
    Tick(1f, "11:40"),
)

/**
 * 디자인 샘플: 가짜 데이터로 그린 결과 화면. 토큰·부품·움직임·햅틱을 한 화면에서 확인한다.
 * 순서: 결론 → 근거(해 위치, 타임라인과 좌·우 비교) → 구간별.
 */
@Composable
fun DesignSampleScreen(
    settings: SampleSettings,
    onSettingsChange: (SampleSettings) -> Unit,
    modifier: Modifier = Modifier,
    animateIn: Boolean = true,
) {
    val scheme = SunSeatTheme.scheme
    val haptics = rememberSunSeatHaptics()
    val reduce = SunSeatTheme.reduceMotion || !animateIn
    var compare by rememberSaveable { mutableStateOf(Side.Left) }
    var shown by remember { mutableStateOf(reduce) }
    val shadeMin = remember { Animatable(if (reduce) 138f else 0f) }
    val sunMin = remember { Animatable(if (reduce) 22f else 0f) }

    LaunchedEffect(Unit) {
        if (!reduce) {
            delay(150)
            shown = true
            launch { shadeMin.animateTo(138f, tween(900)) }
            launch { sunMin.animateTo(22f, tween(900)) }
        }
    }
    val selectedRight by animateFloatAsState(if (compare == Side.Right) 1f else 0f, label = "compare")

    val insets = WindowInsets.safeDrawing.asPaddingValues()

    Box(modifier.fillMaxSize()) {
        Box(
            Modifier
                .fillMaxSize()
                .background(scheme.background),
        ) {
            Column(
                Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = Spacing.screen)
                    .padding(
                        top = insets.calculateTopPadding() + Sizes.topCapsuleClearance,
                        bottom = insets.calculateBottomPadding() + Sizes.bottomCapsuleClearance,
                    ),
                verticalArrangement = Arrangement.spacedBy(Spacing.m),
            ) {
                Stagger(shown, 0) {
                    Card {
                        Text("추천 좌석", style = MaterialTheme.typography.labelMedium, color = scheme.tertiary)
                        Text(
                            "왼쪽 창가에 앉으세요",
                            style = MaterialTheme.typography.headlineMedium,
                            color = scheme.onSurface,
                            modifier = Modifier.semantics { heading() },
                        )
                        Row(horizontalArrangement = Arrangement.spacedBy(Spacing.s)) {
                            ExposurePill(Exposure.Shade, "그늘 ${fmt(shadeMin.value.toInt())}")
                            ExposurePill(Exposure.Sun, "햇빛 ${fmt(sunMin.value.toInt())}")
                        }
                    }
                }
                Stagger(shown, 1) {
                    Card(title = "해는 진행 방향 오른쪽에 있어요") {
                        SeatSunDiagram(sunOnRight = true)
                    }
                }
                Stagger(shown, 2) {
                    Card(title = "좌·우 햇빛 타임라인") {
                        CompareToggle(compare) {
                            compare = it
                            haptics.selectionChanged()
                        }
                        SideTimeline(
                            leftSun = LEFT_SUN,
                            rightSun = RIGHT_SUN,
                            ticks = TICKS,
                            selectedRight = selectedRight,
                            animateIn = !reduce,
                        )
                    }
                }
                Stagger(shown, 3) {
                    Card(title = "구간별 · ${compare.label} 창가") {
                        val left = compare == Side.Left
                        SegmentRow("서울 → 대전", if (left) "그늘" else "햇빛 1시간 0분", if (left) Exposure.Shade else Exposure.Sun)
                        HorizontalDivider(color = scheme.outlineVariant)
                        SegmentRow("대전 → 동대구", if (left) "햇빛 22분" else "그늘 22분", if (left) Exposure.Sun else Exposure.Shade)
                        HorizontalDivider(color = scheme.outlineVariant)
                        SegmentRow("동대구 → 부산", if (left) "그늘" else "햇빛 1시간 18분", if (left) Exposure.Shade else Exposure.Sun)
                    }
                }
                Stagger(shown, 4) {
                    SampleSettingsCard(settings, onSettingsChange)
                }
            }
        }

        FloatingCapsule(
            modifier = Modifier
                .align(Alignment.TopCenter)
                .windowInsetsPadding(WindowInsets.safeDrawing)
                .padding(horizontal = Spacing.m, vertical = Spacing.s)
                .fillMaxWidth(),
        ) {
            Row(Modifier.padding(Spacing.xs), verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = {}) {
                    Icon(painterResource(R.drawable.ic_ms_arrow_back), contentDescription = "뒤로")
                }
                Column(Modifier.weight(1f)) {
                    Text("서울 → 부산", style = MaterialTheme.typography.titleMedium)
                    Text("KTX 101 · 09:00 출발", style = MaterialTheme.typography.bodySmall)
                }
            }
        }

        FloatingCapsule(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .windowInsetsPadding(WindowInsets.safeDrawing)
                .padding(horizontal = Spacing.xl, vertical = Spacing.m)
                .fillMaxWidth(),
        ) {
            Row(
                Modifier.padding(Spacing.s),
                horizontalArrangement = Arrangement.spacedBy(Spacing.s),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                QuietButton(onClick = {}, modifier = Modifier.weight(1f)) {
                    Icon(painterResource(R.drawable.ic_ms_refresh), contentDescription = null, modifier = Modifier.size(Sizes.iconMedium))
                    Spacer(Modifier.size(Spacing.s))
                    Text("다시 찾기", style = MaterialTheme.typography.labelLarge)
                }
                PrimaryButton(onClick = {}, modifier = Modifier.weight(1f)) {
                    Icon(painterResource(R.drawable.ic_ms_share), contentDescription = null, modifier = Modifier.size(Sizes.iconMedium))
                    Spacer(Modifier.size(Spacing.s))
                    Text("좌석 공유", style = MaterialTheme.typography.labelLarge)
                }
            }
        }
    }
}

@Composable
private fun CompareToggle(selected: Side, onSelect: (Side) -> Unit) {
    Choice(
        label = "비교할 창가",
        options = Side.entries.map { it.label },
        selected = selected.ordinal,
        onSelect = { onSelect(Side.entries[it]) },
    )
}

@Composable
private fun Stagger(shown: Boolean, index: Int, content: @Composable () -> Unit) {
    AnimatedVisibility(
        visible = shown,
        enter = fadeIn(tween(300, delayMillis = index * 70)) +
            slideInVertically(tween(380, delayMillis = index * 70)) { it / 6 },
    ) { content() }
}

@Composable
private fun Card(title: String? = null, content: @Composable ColumnScope.() -> Unit) {
    Surface(
        color = SunSeatTheme.scheme.surface,
        shape = MaterialTheme.shapes.large,
        modifier = Modifier.fillMaxWidth(),
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

@Composable
private fun SegmentRow(name: String, result: String, exposure: Exposure) {
    Row(
        Modifier.fillMaxWidth().heightIn(min = Sizes.listRow),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(name, style = MaterialTheme.typography.bodyMedium, color = SunSeatTheme.scheme.onSurface)
        ExposurePill(exposure, result)
    }
}

@Composable
private fun SampleSettingsCard(settings: SampleSettings, onChange: (SampleSettings) -> Unit) {
    val haptics = rememberSunSeatHaptics()
    Card(title = "1차 점검용 설정") {
        Text(
            "실제 앱에는 없는 항목입니다. 밖에서 밝은·어두운 화면을 바꿔 가며 확인해 주세요.",
            style = MaterialTheme.typography.bodySmall,
            color = SunSeatTheme.scheme.onSurfaceVariant,
        )
        Choice("화면", ThemeMode.entries.map { it.label }, settings.theme.ordinal) {
            haptics.selectionChanged()
            onChange(settings.copy(theme = ThemeMode.entries[it]))
        }
    }
}

/** 연결된 토글 버튼 묶음. 선택 표시는 중립 잉크색 채움 + 체크 아이콘(해·그늘 색 미사용). */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun Choice(label: String, options: List<String>, selected: Int, onSelect: (Int) -> Unit) {
    val scheme = SunSeatTheme.scheme
    Column(verticalArrangement = Arrangement.spacedBy(Spacing.s)) {
        Text(label, style = MaterialTheme.typography.labelMedium, color = scheme.onSurface)
        Row(horizontalArrangement = Arrangement.spacedBy(ButtonGroupDefaults.ConnectedSpaceBetween)) {
            options.forEachIndexed { i, o ->
                val checked = i == selected
                ToggleButton(
                    checked = checked,
                    onCheckedChange = { if (!checked) onSelect(i) },
                    shapes = when (i) {
                        0 -> ButtonGroupDefaults.connectedLeadingButtonShapes()
                        options.lastIndex -> ButtonGroupDefaults.connectedTrailingButtonShapes()
                        else -> ButtonGroupDefaults.connectedMiddleButtonShapes()
                    },
                    colors = ToggleButtonDefaults.colors(
                        containerColor = scheme.surfaceContainerHigh,
                        contentColor = scheme.onSurface,
                        checkedContainerColor = scheme.inverseSurface,
                        checkedContentColor = scheme.inverseOnSurface,
                    ),
                    modifier = Modifier.weight(1f).heightIn(min = Spacing.touch).semantics { role = Role.RadioButton },
                ) {
                    if (checked) {
                        Icon(painterResource(R.drawable.ic_ms_check), contentDescription = null, modifier = Modifier.size(Sizes.iconSmall))
                        Spacer(Modifier.size(Spacing.xs))
                    }
                    Text(o, style = MaterialTheme.typography.labelLarge)
                }
            }
        }
    }
}
