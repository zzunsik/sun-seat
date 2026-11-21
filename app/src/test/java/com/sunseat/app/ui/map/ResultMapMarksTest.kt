package com.sunseat.app.ui.map

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.junit4.createComposeRule
import com.sunseat.app.ui.result.ResultFixtures
import com.sunseat.app.ui.theme.Sizes
import com.sunseat.app.ui.theme.SunSeatTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * #160: 결과 화면 이동 경로 카드(`result_transit_light.png`)는 출발(서울역)·도착(광화문)이 화면에서 가까워 도착 표식이 출발 표식 글자를 덮었다.
 * 테스트 지도가 그린 표식 상자에서 "출발"·"도착"이 모두 보이고 서로 겹치지 않는지, 숨긴 표식은 정보가 남는 곳이 있는지 본다.
 */
@RunWith(RobolectricTestRunner::class)
@Config(application = android.app.Application::class, sdk = [36], qualifiers = "w390dp-h844dp-xxhdpi")
class ResultMapMarksTest {
    @get:Rule val compose = createComposeRule()

    private fun overlaps(a: ScreenBox, b: ScreenBox) = minOf(a.right, b.right) - maxOf(a.left, b.left) > 0.01f && minOf(a.bottom, b.bottom) - maxOf(a.top, b.top) > 0.01f

    private fun marks(fontScale: Float): Pair<List<Pair<String, ScreenBox>>, List<Pair<String, Fallback>>> {
        compose.setContent {
            val density = androidx.compose.ui.platform.LocalDensity.current
            CompositionLocalProvider(LocalMapRenderer provides MapRenderer.PREVIEW,
                androidx.compose.ui.platform.LocalDensity provides androidx.compose.ui.unit.Density(density.density, fontScale)) {
                SunSeatTheme {
                    SunSeatMap(MapState(routes = listOf(ResultFixtures.transitRoute), origin = ResultFixtures.transitRequest.origin, destination = ResultFixtures.transitRequest.destination),
                        Modifier.fillMaxWidth().height(Sizes.mapHeight))
                }
            }
        }
        compose.waitForIdle()
        val config = compose.onNode(SemanticsMatcher.keyIsDefined(MapMarkBoxesKey)).fetchSemanticsNode().config
        return config[MapMarkBoxesKey] to config[MapHiddenMarksKey]
    }

    private fun check(fontScale: Float) {
        val (visible, hidden) = marks(fontScale)
        val origin = visible.single { it.first == "ORIGIN" }.second
        val destination = visible.single { it.first == "DESTINATION" }.second
        assertFalse("출발 $origin and 도착 $destination must not overlap", overlaps(origin, destination))
        for (i in visible.indices) for (j in i + 1 until visible.size) assertFalse("${visible[i]} overlaps ${visible[j]}", overlaps(visible[i].second, visible[j].second))
        hidden.forEach { (kind, fallback) -> assertNotEquals("hidden $kind keeps its information elsewhere", Fallback.NONE, fallback) }
        assertTrue(visible.size + hidden.size >= 2)
        assertEquals(0, hidden.count { it.first == "ORIGIN" || it.first == "DESTINATION" })
    }

    @Test fun resultRouteCardKeepsBothPinsReadable() = check(1f)
    @Test fun resultRouteCardKeepsBothPinsReadableWithLargeText() = check(1.5f)
}
