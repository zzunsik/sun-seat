package com.sunseat.app.ui.input

import android.content.Intent
import androidx.compose.foundation.layout.*
import androidx.compose.ui.Modifier
import androidx.compose.runtime.*
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.sunseat.app.domain.TripRequest
import com.sunseat.app.location.*

/** Search overlay on the retained candidates entry; cancel never changes the request. */
@Composable
internal fun EndpointSearchRoute(
    viewModel: InputViewModel,
    request: TripRequest,
    onConfirm: (TripRequest) -> Unit,
    onCancel: () -> Unit,
    onFavorites: (com.sunseat.app.data.favorites.FavoriteKind?) -> Unit,
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val context = LocalContext.current
    var locationPending by androidx.compose.runtime.saveable.rememberSaveable { mutableStateOf(false) }
    var locationRevision by androidx.compose.runtime.saveable.rememberSaveable { mutableLongStateOf(state.originLocationRevision) }
    val permission = rememberLocationPermissionState(onResult = { viewModel.onLocationPermission(it, keepSearch = true) })
    LaunchedEffect(permission.status) {
        if (locationPending && permission.status.isGranted && !state.locating && state.locationAction == LocationAction.OpenAppSettings) {
            viewModel.dismissLocationMessage()
            viewModel.fetchCurrentLocation(keepSearch = true)
        }
    }
    fun cancel() {
        locationPending = false
        viewModel.cancelEndpointEdit(request)
        onCancel()
    }
    fun choose(place: com.sunseat.app.data.place.Place) {
        viewModel.chooseEditedPlace(place)?.let(onConfirm)
    }
    LaunchedEffect(state.locating, state.originLocationRevision) {
        if (locationPending && !state.locating && state.originLocationRevision > locationRevision) {
            locationPending = false
            if (state.sameEndpoints) {
                viewModel.acceptRequest(request)
                viewModel.rejectSameEndpoint()
            } else {
                viewModel.closeSearch()
                viewModel.buildRequest()?.let { viewModel.acceptRequest(it); onConfirm(it) }
            }
        }
    }
    state.search?.let { search ->
        val actions = InputActions(
            closeSearch = ::cancel,
            onQueryChange = viewModel::onQueryChange,
            searchNow = viewModel::searchNow,
            choosePlace = ::choose,
            chooseRecent = { choose(it.asPlace()) },
            chooseFavorite = { choose(it.asPlace()) },
            toggleFavorite = viewModel::toggleFavorite,
            removeRecent = viewModel::removeRecent,
            clearRecent = viewModel::clearRecent,
            manageFavorites = onFavorites,
            useCurrentLocation = {
                locationRevision = state.originLocationRevision
                viewModel.dismissLocationMessage()
                // Mark pending after fetch sets locating, so the old current location is never confirmed.
                if (permission.status.isGranted) {
                    viewModel.fetchCurrentLocation(keepSearch = true)
                    locationPending = true
                } else {
                    viewModel.prepareLocation(LocationPurpose.ORIGIN)
                    permission.request()
                    locationPending = true
                }
            },
            onLocationAction = { action -> when (action) {
                LocationAction.RequestPermission -> permission.request()
                LocationAction.OpenAppSettings -> permission.openAppSettings()
                LocationAction.OpenLocationSettings -> context.startActivity(locationSettingsIntent().addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                LocationAction.Retry -> viewModel.fetchCurrentLocation(keepSearch = true)
            } },
            dismissLocationMessage = viewModel::dismissLocationMessage,
            openSearch = { viewModel.dismissLocationMessage() },
        )
        Column(Modifier.fillMaxSize()) {
            PlaceSearchScreen(search, actions, Modifier.weight(1f), favorites = state.favorites,
                recent = state.recent, showRecent = true,
                noticeRes = state.favoriteMessageRes ?: state.recentMessageRes,
                near = state.origin?.takeIf { it.isCurrentLocation }?.place?.location)
            if (state.locationMessageRes != null) LocationNotice(state, actions)
        }
    }
}
