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
@Composable
fun CandidatesRoute(
    graph: AppGraph,
    request: TripRequest,
    onBack: () -> Unit,
    onSelect: (ChosenRoute) -> Unit,
    onAutoSelect: (ChosenRoute) -> Unit,
    onSearchByCar: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: CandidatesViewModel = viewModel(
        key = request.toString(),
        factory = viewModelFactory {
            initializer { CandidatesViewModel(request, graph.kakao.transitRoutes, graph.kakao.carRoutes) }
        },
    ),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    LaunchedEffect(viewModel) { viewModel.autoSelected.collect(onAutoSelect) }
    CandidatesScreen(
        request = request,
        state = state,
        modifier = modifier,
        actions = CandidatesActions(
            onBack = onBack,
            onSelect = { onSelect(it.chosen) },
            onRetry = viewModel::load,
            onSearchByCar = onSearchByCar,
        ),
    )
}
