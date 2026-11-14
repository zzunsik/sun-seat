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
import com.sunseat.app.ui.candidates.RouteCandidatesPlaceholder
import com.sunseat.app.ui.input.InputRoute

/** 화면 키. 입력 → 경로 후보 → 결과. */
sealed interface Screen {
    data object Input : Screen
    data class Candidates(val request: TripRequest) : Screen
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
            entry<Screen.Input> {
                InputRoute(graph = graph, onSubmit = { backStack.add(Screen.Candidates(it)) })
            }
            entry<Screen.Candidates> { key ->
                RouteCandidatesPlaceholder(
                    request = key.request,
                    onBack = { backStack.removeAt(backStack.lastIndex) },
                )
            }
        },
    )
}
