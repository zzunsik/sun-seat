package com.sunseat.app.ui.favorites

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.sunseat.app.AppGraph
import com.sunseat.app.R
import com.sunseat.app.data.favorites.*
import com.sunseat.app.ui.components.QuietButton
import com.sunseat.app.ui.components.SectionCard
import com.sunseat.app.ui.input.InputActions
import com.sunseat.app.ui.input.SearchOverlay
import com.sunseat.app.ui.theme.Sizes
import com.sunseat.app.ui.theme.Spacing
import com.sunseat.app.ui.theme.SunSeatTheme

@Composable
fun favoriteName(favorite: Favorite): String = favorite.name.ifBlank {
    when (favorite.kind) { FavoriteKind.HOME -> stringResource(R.string.favorites_home); FavoriteKind.WORK -> stringResource(R.string.favorites_work); FavoriteKind.CUSTOM -> favorite.placeName }
}

data class FavoritesActions(
    val onBack: () -> Unit = {},
    val setup: (FavoriteKind) -> Unit = {},
    val rename: (Favorite) -> Unit = {},
    val remove: (Favorite) -> Unit = {},
    val confirmRename: (String) -> Unit = {},
    val cancelRename: () -> Unit = {},
)

@Composable
fun FavoritesRoute(graph: AppGraph, setup: FavoriteKind?, onBack: () -> Unit, modifier: Modifier = Modifier,
    model: FavoritesViewModel = viewModel(factory = viewModelFactory { initializer { FavoritesViewModel(graph.favorites, graph.kakao.placeSearch, setup) } }),
) {
    val state by model.state.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }
    val deleted = stringResource(R.string.favorites_deleted)
    val undo = stringResource(R.string.favorites_undo)
    LaunchedEffect(model) { model.deletions.collect { item -> if (snackbar.showSnackbar(deleted, undo, duration = SnackbarDuration.Short) == SnackbarResult.ActionPerformed) model.restore(item) } }
    Box(modifier) {
        FavoritesScreen(state, FavoritesActions(onBack, model::openSearch, model::requestRename, model::remove, model::rename, model::cancelRename), snackbar = snackbar)
        state.search?.let { search ->
            SearchOverlay(search, InputActions(closeSearch = model::closeSearch, onQueryChange = model::onQueryChange, searchNow = model::searchNow, choosePlace = model::choosePlace), noticeRes = state.errorRes, showFavorites = false, showLocation = false)
        }
    }
}

@Composable
fun FavoritesScreen(state: FavoritesUiState, actions: FavoritesActions, modifier: Modifier = Modifier, snackbar: SnackbarHostState = remember { SnackbarHostState() }) {
    val scheme = SunSeatTheme.scheme
    Scaffold(modifier, containerColor = scheme.background, contentColor = scheme.onSurface, snackbarHost = { SnackbarHost(snackbar) }) { padding ->
        Column(Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState()).padding(Spacing.screen), verticalArrangement = Arrangement.spacedBy(Spacing.m)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Spacing.m)) {
                IconButton(onClick = actions.onBack) { Icon(painterResource(R.drawable.ic_ms_arrow_back), stringResource(R.string.common_back), modifier = Modifier.size(Sizes.iconMedium)) }
                Text(stringResource(R.string.favorites_title), style = MaterialTheme.typography.headlineSmall, modifier = Modifier.semantics { heading() })
            }
            state.errorRes?.let { Text(stringResource(it), color = scheme.error) }
            listOf(FavoriteKind.HOME, FavoriteKind.WORK).forEach { kind ->
                val favorite = state.favorites.firstOrNull { it.kind == kind }
                SectionCard(title = stringResource(if (kind == FavoriteKind.HOME) R.string.favorites_home else R.string.favorites_work)) {
                    if (favorite != null && favorite.name.isNotBlank()) Text(favorite.name, style = MaterialTheme.typography.titleSmall)
                    Text(favorite?.placeName ?: stringResource(R.string.favorites_not_set), style = MaterialTheme.typography.bodyLarge)
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Spacing.s)) {
                        QuietButton(onClick = { actions.setup(kind) }) { Text(stringResource(if (favorite != null) R.string.favorites_change else if (kind == FavoriteKind.HOME) R.string.favorites_set_home else R.string.favorites_set_work)) }
                        if (favorite != null) FavoriteActions(favorite, actions)
                    }
                }
            }
            SectionCard(title = stringResource(R.string.favorites_custom)) {
                val custom = state.favorites.filter { it.kind == FavoriteKind.CUSTOM }
                if (custom.isEmpty()) Text(stringResource(R.string.favorites_empty), style = MaterialTheme.typography.bodyMedium, color = scheme.onSurfaceVariant)
                custom.forEach { favorite ->
                    Column(verticalArrangement = Arrangement.spacedBy(Spacing.xs)) {
                        Text(favoriteName(favorite), style = MaterialTheme.typography.titleSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text(favorite.address, style = MaterialTheme.typography.bodySmall, color = scheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Spacing.s)) { FavoriteActions(favorite, actions) }
                    }
                }
            }
        }
    }
    state.rename?.let { favorite ->
        var name by remember(favorite.id) { mutableStateOf(favorite.name.ifBlank { favorite.placeName }) }
        AlertDialog(onDismissRequest = actions.cancelRename, title = { Text(stringResource(R.string.favorites_rename)) }, text = {
            OutlinedTextField(name, { name = it.take(com.sunseat.app.data.favorites.DataStoreFavoritesRepository.MAX_NAME) }, label = { Text(stringResource(R.string.favorites_name)) }, singleLine = true)
        }, confirmButton = { QuietButton(onClick = { actions.confirmRename(name) }) { Text(stringResource(R.string.common_ok)) } }, dismissButton = { QuietButton(onClick = actions.cancelRename) { Text(stringResource(R.string.common_cancel)) } })
    }
}

@Composable
private fun FavoriteActions(favorite: Favorite, actions: FavoritesActions) {
    QuietButton(onClick = { actions.rename(favorite) }) { Text(stringResource(R.string.favorites_rename)) }
    IconButton(onClick = { actions.remove(favorite) }) { Icon(painterResource(R.drawable.ic_ms_close), stringResource(R.string.favorites_delete, favoriteName(favorite)), tint = SunSeatTheme.scheme.onSurface, modifier = Modifier.size(Sizes.iconMedium)) }
}
