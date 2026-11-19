package com.sunseat.app.ui.candidates

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.sunseat.app.AppGraph
import com.sunseat.app.domain.TripRequest

/** 경로 후보 화면을 ViewModel과 연결한다. 자가용은 경로를 찾으면 [onAutoSelect]로 바로 넘긴다. */
@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
@Composable
fun CandidatesRoute(
    graph: AppGraph,
    request: TripRequest,
    onBack: () -> Unit,
    onSelect: (ChosenRoute) -> Unit,
    onAutoSelect: (ChosenRoute) -> Unit,
    onSearchByCar: () -> Unit,
    modifier: Modifier = Modifier,
    onRequestChange: (TripRequest) -> Unit = {},
    requestRevision: Long = 0,
    onEditEndpoint: (com.sunseat.app.ui.input.EndpointField) -> Unit = {},
    onSwap: () -> Unit = {},
    viewModel: CandidatesViewModel = viewModel(
        key = "candidates",
        factory = viewModelFactory {
            initializer { CandidatesViewModel(request, graph.kakao.transitRoutes, graph.kakao.carRoutes, now = { java.time.LocalDateTime.now(graph.clock) }, preference = graph.settings.settings.value.benchPreference) }
        },
    ),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    LaunchedEffect(viewModel, request, requestRevision) { viewModel.updateRequest(request, requestRevision) }
    val currentAutoSelect by androidx.compose.runtime.rememberUpdatedState(onAutoSelect)
    LaunchedEffect(viewModel) { viewModel.autoSelected.collect { currentAutoSelect(it) } }
    CandidatesScreen(
        request = request,
        state = state,
        modifier = modifier,
        actions = CandidatesActions(
            onEditEndpoint = onEditEndpoint, onSwap = onSwap,
            onBack = onBack,
            onSelect = { onSelect(it.chosen) },
            onRetry = viewModel::load,
            onHighlight = { item ->
                if ((viewModel.state.value as? CandidatesState.Options)?.selectedId == item.id) onSelect(item.chosen)
                else viewModel.highlight(item.id)
            },
            onSort = viewModel::setSort, onFilter = viewModel::toggleFilter, onClearFilters = viewModel::clearFilters,
            onRequestChange = onRequestChange,
            onSearchByCar = onSearchByCar,
        ),
    )
}
