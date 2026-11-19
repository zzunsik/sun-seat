package com.sunseat.app.ui.navigation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.lifecycle.viewmodel.navigation3.rememberViewModelStoreNavEntryDecorator
import androidx.lifecycle.viewmodel.initializer
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
    data class Favorites(val setup: com.sunseat.app.data.favorites.FavoriteKind? = null) : Screen
    data object Settings : Screen
    data object Licenses : Screen
    data object Input : Screen
    data class Candidates(val request: TripRequest) : Screen
    data class Simulation(val request: TripRequest, val chosen: ChosenRoute, val result: com.sunseat.app.domain.TripResult) : Screen
    data class Result(val request: TripRequest, val chosen: ChosenRoute, val revision: Long = 0) : Screen
}

/** Navigation 3 화면 흐름. 시스템 뒤로 가기는 NavDisplay가 처리한다. */
@Composable
fun SunSeatNavHost(graph: AppGraph, modifier: Modifier = Modifier, navigation: SunSeatNavigationViewModel = androidx.lifecycle.viewmodel.compose.viewModel()) {
    val backStack = navigation.backStack
    val locationName = androidx.compose.ui.res.stringResource(com.sunseat.app.R.string.location_use_current)
    val input: com.sunseat.app.ui.input.InputViewModel = androidx.lifecycle.viewmodel.compose.viewModel(
        key = "trip-input",
        factory = androidx.lifecycle.viewmodel.viewModelFactory {
            initializer { com.sunseat.app.ui.input.InputViewModel(graph.kakao.placeSearch, graph.locationProvider,
                locationName, graph.settings.settings, graph.recent, graph.favorites) }
        },
    )
    fun updateRequest(request: TripRequest) {
        input.acceptRequest(request)
        navigation.updateRequest(request)
    }
    NavDisplay(
        backStack = backStack,
        modifier = modifier,
        onBack = { if (backStack.size > 1) backStack.removeAt(backStack.lastIndex) },
        entryDecorators = listOf(
            rememberSaveableStateHolderNavEntryDecorator(),
            rememberViewModelStoreNavEntryDecorator(),
        ),
        entryProvider = entryProvider {
            entry<Screen.Favorites> { key ->
                com.sunseat.app.ui.favorites.FavoritesRoute(graph, key.setup, onBack = { backStack.removeAt(backStack.lastIndex) })
            }
            entry<Screen.Settings> {
                com.sunseat.app.ui.settings.SettingsRoute(graph.settings, onBack = { backStack.removeAt(backStack.lastIndex) }, onLicenses = { backStack.add(Screen.Licenses) }, onFavorites = { backStack.add(Screen.Favorites()) })
            }
            entry<Screen.Licenses> {
                com.sunseat.app.ui.settings.LicensesScreen(onBack = { backStack.removeAt(backStack.lastIndex) })
            }
            entry<Screen.Input> {
                InputRoute(graph = graph, viewModel = input,
                    onSubmit = { navigation.updateRequest(it); backStack.add(Screen.Candidates(it)) },
                    onSettings = { backStack.add(Screen.Settings) }, onFavorites = { backStack.add(Screen.Favorites(it)) })
            }
            entry<Screen.Candidates> { key ->
                val request = navigation.request ?: key.request
                var editing by androidx.compose.runtime.saveable.rememberSaveable { mutableStateOf(false) }
                androidx.compose.foundation.layout.Box {
                CandidatesRoute(
                    graph = graph,
                    modifier = if (editing) Modifier.clearAndSetSemantics {} else Modifier,
                    request = request,
                    requestRevision = navigation.revision,
                    onBack = { backStack.removeAt(backStack.lastIndex) },
                    onSelect = { backStack.add(Screen.Result(request, it, navigation.revision)) },
                    // 자가용: 후보 화면을 결과 화면으로 바꿔 끼워 뒤로 가면 입력 화면으로 간다
                    onAutoSelect = { backStack[backStack.lastIndex] = Screen.Result(request, it, navigation.revision) },
                    onRequestChange = ::updateRequest,
                    onSearchByCar = { updateRequest(request.copy(mode = TravelMode.CAR)) },
                    onEditEndpoint = { field -> input.acceptRequest(request); input.openSearch(field); editing = true },
                    onSwap = {
                        input.acceptRequest(request)
                        input.swap()
                        input.buildRequest()?.let(::updateRequest)
                    },
                )
                if (editing) com.sunseat.app.ui.input.EndpointSearchRoute(input, request,
                    onConfirm = { editing = false; if (it != request) updateRequest(it) },
                    onCancel = { editing = false },
                    onFavorites = { backStack.add(Screen.Favorites(it)) })
                }
            }
            entry<Screen.Simulation> { key ->
                com.sunseat.app.ui.simulation.SimulationRoute(key.request, key.chosen.route, key.result, onBack = { backStack.removeAt(backStack.lastIndex) })
            }
            entry<Screen.Result> { key ->
                val request = navigation.request ?: key.request
                val chosen = if (key.revision == navigation.revision) key.chosen else key.chosen.copy(cachedResult = null)
                ResultRoute(
                    graph = graph,
                    onSettings = { backStack.add(Screen.Settings) },
                    request = request,
                    chosen = chosen,
                    calculationRevision = navigation.revision,
                    onSimulation = { result -> backStack.add(Screen.Simulation(request.copy(goal = result.goal), chosen, result)) },
                    onSchedule = { value, mode, goal -> updateRequest(request.copy(departure = value, timeMode = mode, goal = goal)) },
                    onBack = { backStack.removeAt(backStack.lastIndex) },
                    // 다시 찾기: 입력 화면으로 돌아간다(입력 내용은 입력 화면 상태에 남아 있다)
                    onRestart = { while (backStack.size > 1) backStack.removeAt(backStack.lastIndex) },
                )
            }
        },
    )
}

/** Activity-scoped stack survives configuration changes with its entry ViewModels. */
class SunSeatNavigationViewModel : androidx.lifecycle.ViewModel() {
    val backStack = mutableStateListOf<Screen>(Screen.Input)
    var request by mutableStateOf<TripRequest?>(null)
        private set
    var revision by mutableLongStateOf(0L)
        private set

    fun updateRequest(value: TripRequest) {
        request = value
        revision++
    }
}
