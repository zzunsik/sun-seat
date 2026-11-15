package com.sunseat.app.ui.navigation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.lifecycle.viewmodel.navigation3.rememberViewModelStoreNavEntryDecorator
import androidx.navigation3.runtime.entryProvider
import androidx.navigation3.runtime.rememberSaveableStateHolderNavEntryDecorator
import androidx.navigation3.ui.NavDisplay
import com.sunseat.app.AppGraph
import com.sunseat.app.domain.TripRequest
import com.sunseat.app.domain.TravelMode
import com.sunseat.app.ui.candidates.CandidatesRoute
import com.sunseat.app.ui.candidates.ChosenRoute
import com.sunseat.app.ui.result.ResultRoute
import com.sunseat.app.ui.input.InputRoute

/** 화면 키. 입력 → 경로 후보 → 결과. */
sealed interface Screen {
    data object Settings : Screen
    data object Licenses : Screen
    data object Input : Screen
    data class Candidates(val request: TripRequest) : Screen
    data class Result(val request: TripRequest, val chosen: ChosenRoute) : Screen
}

/** Navigation 3 화면 흐름. 시스템 뒤로 가기는 NavDisplay가 처리한다. */
@Composable
fun SunSeatNavHost(graph: AppGraph, modifier: Modifier = Modifier) {
    val backStack = remember { mutableStateListOf<Screen>(Screen.Input) }
    NavDisplay(
        backStack = backStack,
        modifier = modifier,
        onBack = { if (backStack.size > 1) backStack.removeAt(backStack.lastIndex) },
        entryDecorators = listOf(
            rememberSaveableStateHolderNavEntryDecorator(),
            rememberViewModelStoreNavEntryDecorator(),
        ),
        entryProvider = entryProvider {
            entry<Screen.Settings> {
                com.sunseat.app.ui.settings.SettingsRoute(graph.settings, onBack = { backStack.removeAt(backStack.lastIndex) }, onLicenses = { backStack.add(Screen.Licenses) })
            }
            entry<Screen.Licenses> {
                com.sunseat.app.ui.settings.LicensesScreen(onBack = { backStack.removeAt(backStack.lastIndex) })
            }
            entry<Screen.Input> {
                InputRoute(graph = graph, onSubmit = { backStack.add(Screen.Candidates(it)) }, onSettings = { backStack.add(Screen.Settings) })
            }
            entry<Screen.Candidates> { key ->
                CandidatesRoute(
                    graph = graph,
                    request = key.request,
                    onBack = { backStack.removeAt(backStack.lastIndex) },
                    onSelect = { backStack.add(Screen.Result(key.request, it)) },
                    // 자가용: 후보 화면을 결과 화면으로 바꿔 끼워 뒤로 가면 입력 화면으로 간다
                    onAutoSelect = { backStack[backStack.lastIndex] = Screen.Result(key.request, it) },
                    onSearchByCar = { backStack[backStack.lastIndex] = Screen.Candidates(key.request.copy(mode = TravelMode.CAR)) },
                )
            }
            entry<Screen.Result> { key ->
                ResultRoute(
                    graph = graph,
                    onSettings = { backStack.add(Screen.Settings) },
                    request = key.request,
                    chosen = key.chosen,
                    onBack = { backStack.removeAt(backStack.lastIndex) },
                    // 다시 찾기: 입력 화면으로 돌아간다(입력 내용은 입력 화면 상태에 남아 있다)
                    onRestart = { while (backStack.size > 1) backStack.removeAt(backStack.lastIndex) },
                )
            }
        },
    )
}
