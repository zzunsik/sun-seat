package com.sunseat.app.ui.input

import android.content.Intent
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.sunseat.app.AppGraph
import com.sunseat.app.domain.TripRequest
import com.sunseat.app.location.LocationAction
import com.sunseat.app.location.locationSettingsIntent
import com.sunseat.app.location.rememberLocationPermissionState

/** 입력 화면을 ViewModel·위치 권한과 연결한다. */
@Composable
fun InputRoute(
    graph: AppGraph,
    onSubmit: (TripRequest) -> Unit,
    modifier: Modifier = Modifier,
    onSettings: () -> Unit = {},
    onFavorites: (com.sunseat.app.data.favorites.FavoriteKind?) -> Unit = {},
    currentLocationName: String = androidx.compose.ui.res.stringResource(com.sunseat.app.R.string.location_use_current),
    viewModel: InputViewModel = viewModel(
        factory = viewModelFactory {
            initializer { InputViewModel(graph.kakao.placeSearch, graph.locationProvider, currentLocationName = currentLocationName, settings = graph.settings.settings, favoriteRepository = graph.favorites, recentRepository = graph.recent) }
        },
    ),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val permission = rememberLocationPermissionState(onResult = viewModel::onLocationPermission)

    LaunchedEffect(state.origin, state.destination, state.mode, state.departure, state.timeMode, state.goal, state.search, state.locating) {
        viewModel.automaticRequest()?.let(onSubmit)
    }
    LaunchedEffect(permission.status) {
        if (permission.status.isGranted) viewModel.onLocationPermissionAvailable()
    }

    InputScreen(
        state = state,
        modifier = modifier,
        actions = InputActions(
            onSettings = onSettings,
            manageFavorites = onFavorites,
            chooseRecent = viewModel::chooseRecent,
            removeRecent = viewModel::removeRecent,
            clearRecent = viewModel::clearRecent,
            chooseFavorite = viewModel::chooseFavorite,
            toggleFavorite = viewModel::toggleFavorite,
            openSearch = viewModel::openSearch,
            closeSearch = viewModel::closeSearch,
            onQueryChange = viewModel::onQueryChange,
            searchNow = viewModel::searchNow,
            choosePlace = viewModel::choosePlace,
            useCurrentLocation = {
                viewModel.prepareLocation(LocationPurpose.ORIGIN)
                if (permission.status.isGranted) viewModel.fetchCurrentLocation() else permission.request()
            },
            showCurrentLocationOnMap = {
                viewModel.prepareLocation(LocationPurpose.MAP)
                if (permission.status.isGranted) viewModel.fetchCurrentLocation(LocationPurpose.MAP) else permission.request()
            },
            onLocationAction = { action ->
                when (action) {
                    LocationAction.RequestPermission -> permission.request()
                    LocationAction.OpenAppSettings -> permission.openAppSettings()
                    LocationAction.OpenLocationSettings ->
                        context.startActivity(locationSettingsIntent().addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                    LocationAction.Retry -> viewModel.retryLocation()
                }
            },
            dismissLocationMessage = viewModel::dismissLocationMessage,
            swap = viewModel::swap,
            setMode = viewModel::setMode,
            setDeparture = viewModel::setDeparture,
            setSchedule = viewModel::setSchedule,
            setGoal = viewModel::setGoal,
            submit = { viewModel.submit()?.let(onSubmit) },
        ),
    )
}
