package com.sunseat.app.ui.input

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import com.sunseat.app.R
import com.sunseat.app.ui.components.QuietButton
import com.sunseat.app.ui.theme.Sizes
import com.sunseat.app.ui.theme.Spacing
import com.sunseat.app.ui.theme.SunSeatTheme

@Composable
internal fun PlaceSearchScreen(search: SearchState, actions: InputActions, modifier: Modifier = Modifier, favorites: List<com.sunseat.app.data.favorites.Favorite> = emptyList(), noticeRes: Int? = null, showFavorites: Boolean = true, showLocation: Boolean = true, near: com.sunseat.app.domain.LatLng? = null, recent: List<com.sunseat.app.data.recent.RecentPlace> = emptyList(), showRecent: Boolean = false) {
    val scheme = SunSeatTheme.scheme
    val focus = remember { FocusRequester() }
    val keyboard = LocalSoftwareKeyboardController.current
    BackHandler(onBack = actions.closeSearch)
    LaunchedEffect(search.field) { runCatching { focus.requestFocus() } }

    Surface(modifier.fillMaxSize(), color = scheme.background, contentColor = scheme.onSurface) {
    Column(Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.safeDrawing)) {
        Row(
            Modifier.fillMaxWidth().padding(start = Spacing.xs, end = Spacing.screen, top = Spacing.s, bottom = Spacing.s),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(onClick = actions.closeSearch) {
                Icon(painterResource(R.drawable.ic_ms_arrow_back), contentDescription = stringResource(R.string.input_search_close))
            }
            OutlinedTextField(
                value = search.query,
                onValueChange = actions.onQueryChange,
                singleLine = true,
                textStyle = MaterialTheme.typography.bodyLarge,
                placeholder = {
                    Text(
                        stringResource(search.hintRes ?: if (search.field == EndpointField.ORIGIN) R.string.input_search_hint_origin else R.string.input_search_hint_destination),
                        style = MaterialTheme.typography.bodyLarge,
                    )
                },
                trailingIcon = if (search.query.isNotEmpty()) {
                    {
                        IconButton(onClick = { actions.onQueryChange("") }) {
                            Icon(painterResource(R.drawable.ic_ms_close), contentDescription = stringResource(R.string.input_search_clear))
                        }
                    }
                } else null,
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                keyboardActions = KeyboardActions(onSearch = {
                    actions.searchNow()
                    keyboard?.hide()
                }),
                shape = MaterialTheme.shapes.medium,
                modifier = Modifier.weight(1f).focusRequester(focus),
            )
        }
        if (showFavorites) {
            Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = Spacing.screen), horizontalArrangement = Arrangement.spacedBy(Spacing.s)) {
                listOf(com.sunseat.app.data.favorites.FavoriteKind.HOME, com.sunseat.app.data.favorites.FavoriteKind.WORK).forEach { kind ->
                    val saved = favorites.firstOrNull { it.kind == kind }
                    if (saved == null) com.sunseat.app.ui.components.QuietButton(onClick = { actions.manageFavorites(kind) }) { Text(stringResource(if (kind == com.sunseat.app.data.favorites.FavoriteKind.HOME) R.string.favorites_set_home else R.string.favorites_set_work)) }
                    else com.sunseat.app.ui.components.QuietButton(onClick = { actions.chooseFavorite(saved) }) { Text(com.sunseat.app.ui.favorites.favoriteName(saved)) }
                }
                favorites.filter { it.kind == com.sunseat.app.data.favorites.FavoriteKind.CUSTOM }.forEach { favorite ->
                    com.sunseat.app.ui.components.QuietButton(onClick = { actions.chooseFavorite(favorite) }) { Text(com.sunseat.app.ui.favorites.favoriteName(favorite)) }
                }
                com.sunseat.app.ui.components.QuietButton(onClick = { actions.manageFavorites(null) }) { Text(stringResource(R.string.favorites_edit)) }
            }
        }
        if (noticeRes != null) Text(stringResource(noticeRes), Modifier.padding(Spacing.screen), color = scheme.error)
        if (search.loading) LinearProgressIndicator(Modifier.fillMaxWidth())
        LazyColumn(Modifier.fillMaxSize()) {
            if (showLocation && search.field == EndpointField.ORIGIN && search.query.isBlank()) {
                item {
                    ResultRow(
                        title = stringResource(R.string.location_use_current),
                        subtitle = null,
                        iconRes = R.drawable.ic_ms_my_location,
                        onClick = actions.useCurrentLocation,
                    )
                    HorizontalDivider(color = scheme.outlineVariant, modifier = Modifier.padding(horizontal = Spacing.screen))
                }
            }
            if (showFavorites && search.query.isBlank()) {
                items(favorites, key = { "favorite-" + it.id }) { favorite ->
                    ResultRow(com.sunseat.app.ui.favorites.favoriteName(favorite), favorite.address, R.drawable.ic_ms_star_filled, { actions.chooseFavorite(favorite) })
                }
            }
            if (showRecent && search.query.isBlank()) {
                item {
                    Row(Modifier.fillMaxWidth().padding(horizontal = Spacing.screen).padding(top = Spacing.m), verticalAlignment = Alignment.CenterVertically) {
                        Text(stringResource(R.string.recent_title), Modifier.weight(1f).semantics { heading() }, style = MaterialTheme.typography.titleSmall)
                        if (recent.isNotEmpty()) QuietButton(onClick = actions.clearRecent) { Text(stringResource(R.string.recent_clear)) }
                    }
                    if (recent.isEmpty()) Text(stringResource(R.string.recent_empty), Modifier.padding(horizontal = Spacing.screen, vertical = Spacing.s), style = MaterialTheme.typography.bodyMedium, color = scheme.onSurfaceVariant)
                }
                items(recent, key = { "recent-" + it.id }) { place ->
                    ResultRow(place.name, place.address, R.drawable.ic_ms_schedule, { actions.chooseRecent(place) }, trailing = {
                        IconButton(onClick = { actions.removeRecent(place.id) }) {
                            Icon(painterResource(R.drawable.ic_ms_close), stringResource(R.string.recent_remove, place.name), tint = scheme.onSurface)
                        }
                    })
                }
            }
            if (search.messageRes != null) {
                item {
                    Column(Modifier.padding(horizontal = Spacing.screen, vertical = Spacing.l), verticalArrangement = Arrangement.spacedBy(Spacing.s)) {
                        Text(
                            stringResource(search.messageRes, search.query.trim()),
                            style = MaterialTheme.typography.bodyLarge,
                            color = scheme.onSurface,
                        )
                        if (search.canRetry) {
                            QuietButton(onClick = actions.searchNow) {
                                Icon(painterResource(R.drawable.ic_ms_refresh), contentDescription = null, modifier = Modifier.size(Sizes.iconMedium))
                                Spacer(Modifier.size(Spacing.s))
                                Text(stringResource(R.string.search_retry), style = MaterialTheme.typography.labelLarge)
                            }
                        }
                    }
                }
            }
            items(if (search.loading) emptyList() else search.results, key = { it.id }) { place ->
                ResultRow(
                    title = place.name,
                    subtitle = listOf(place.category.substringAfterLast('>').trim(), place.roadAddress.ifBlank { place.address }, near?.let { origin ->
                        val meters = com.sunseat.app.domain.ExposureAnalyzer.distanceMeters(origin, place.location)
                        if (meters < 1000) stringResource(R.string.input_distance_m, meters.toInt()) else stringResource(R.string.input_distance_km, meters / 1000)
                    }.orEmpty()).filter { it.isNotBlank() }.joinToString(" · "),
                    iconRes = R.drawable.ic_ms_location_on,
                    onClick = { actions.choosePlace(place) },
                    trailing = if (showFavorites) { {
                        val saved = favorites.any { it.matches(place) }
                        IconButton(onClick = { actions.toggleFavorite(place) }) {
                            Icon(painterResource(if (saved) R.drawable.ic_ms_star_filled else R.drawable.ic_ms_star), contentDescription = stringResource(if (saved) R.string.favorites_remove else R.string.favorites_add), tint = scheme.onSurface, modifier = Modifier.size(Sizes.iconMedium))
                        }
                    } } else null,
                )
            }
        }
    }
    }
}

@Composable
private fun ResultRow(title: String, subtitle: String?, iconRes: Int, onClick: () -> Unit, trailing: (@Composable () -> Unit)? = null) {
    val scheme = SunSeatTheme.scheme
    Row(
        Modifier
            .fillMaxWidth()
            .heightIn(min = Spacing.touch + Spacing.m)
            .clickable(role = Role.Button, onClick = onClick)
            .padding(horizontal = Spacing.screen, vertical = Spacing.s),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Spacing.m),
    ) {
        Icon(painterResource(iconRes), contentDescription = null, tint = scheme.onSurfaceVariant, modifier = Modifier.size(Sizes.iconMedium))
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.titleSmall, color = scheme.onSurface, maxLines = 1, overflow = TextOverflow.Ellipsis)
            if (!subtitle.isNullOrBlank()) {
                Text(subtitle, style = MaterialTheme.typography.bodySmall, color = scheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
        trailing?.invoke()
    }
}
