package com.sunseat.app.ui.simulation

import android.view.SurfaceView
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.Icon
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Surface
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.sunseat.app.R
import com.sunseat.app.domain.*
import com.sunseat.app.ui.components.QuietButton
import com.sunseat.app.ui.input.NeutralFilterChip
import com.sunseat.app.ui.map.LocalMapRenderer
import com.sunseat.app.ui.map.MapRenderer
import com.sunseat.app.ui.result.label
import com.sunseat.app.ui.theme.Sizes
import com.sunseat.app.ui.theme.Spacing
import com.sunseat.app.ui.theme.SunSeatTheme
import kotlin.math.abs

/** Null follows the map's test renderer; debug GPU fixtures may explicitly opt into native 3D. */
internal val LocalCabinPreview = staticCompositionLocalOf<Boolean?> { null }

@Composable
internal fun CabinPanel(point: TimelinePoint, mode: TravelMode, state: SimulationUiState, actions: SimulationActions) {
    val manual=state.models[point.legIndex]
    val profile=remember(point.leg,mode,manual) { CabinProfile.forLeg(point.leg,mode,manual) } ?: return
    val chosen=state.seats[point.legIndex]?.let { id -> profile.seats.firstOrNull { it.id==id } }
        ?: profile.recommended(point.spot) ?: profile.seats.first()
    val recommended=profile.recommended(point.spot)
    val available=point.heading!=null && point.position!=null && !point.undergroundOrUnknown
    val preview=LocalCabinPreview.current ?: (LocalMapRenderer.current==MapRenderer.PREVIEW)
    // Never display a previous instant's geometry with new time/angle labels.
    val key=listOf(profile,point.time,point.heading,point.sun,available)
    val light=remember(key) { CabinSimulation.calculate(profile,point.sun,point.heading,available) }
    var choosing by remember(profile) { mutableStateOf(false) }
    val kindName=stringResource(when(profile.kind) {
        CabinKind.BUS -> R.string.simulation_bus
        CabinKind.COACH -> R.string.simulation_coach
        CabinKind.SUBWAY -> R.string.simulation_subway
        CabinKind.TAXI -> R.string.simulation_taxi
        CabinKind.CAR -> R.string.simulation_car
    })
    // Bus models always say whether they are estimated or chosen, next to the name (#76).
    val profileLabel=stringResource(R.string.simulation_cabin_profile,if(profile.kind in BusModels.kinds)
        stringResource(if(manual!=null) R.string.simulation_bus_name_manual else R.string.simulation_bus_name_estimated,kindName) else kindName)
    Column(Modifier.fillMaxWidth(),verticalArrangement=Arrangement.spacedBy(Spacing.s)) {
    val summary=when {
        point.leg.mode==Mode.SUBWAY && point.leg.groundLevel==GroundLevel.BELOW -> stringResource(R.string.simulation_cabin_underground)
        !available -> stringResource(R.string.simulation_cabin_unavailable)
        (point.sun?.altitude ?: -1.0)<=0 -> stringResource(R.string.simulation_night)
        else -> stringResource(R.string.simulation_coverage,
            stringResource(coverageText(light.lit(chosen.id,SeatSurface.CUSHION))),
            stringResource(coverageText(light.lit(chosen.id,SeatSurface.BACK))))
    }
    @Composable fun SeatSummary() {
        Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(Spacing.s)) {
            Column(Modifier.weight(1f)) {
                Text(stringResource(R.string.simulation_selected_seat,chosen.label()),style=MaterialTheme.typography.titleSmall)
                Text(profileLabel,style=MaterialTheme.typography.bodySmall,color=SunSeatTheme.scheme.onSurfaceVariant)
            }
            Box {
                QuietButton(onClick={choosing=true}) { Text(stringResource(R.string.simulation_choose_seat)) }
                DropdownMenu(expanded=choosing,onDismissRequest={choosing=false},modifier=Modifier.heightIn(max=Sizes.cabinHeight)) {
                    profile.seats.forEach { s -> DropdownMenuItem(text={Text(s.label())},
                        modifier=Modifier.semantics { selected=s.id==chosen.id },
                        leadingIcon=if(s.id==chosen.id) ({ Icon(painterResource(R.drawable.ic_ms_check),null) }) else null,
                        onClick={actions.seat(point.legIndex,s.id);choosing=false}) }
                }
            }
        }
        Text(summary,style=MaterialTheme.typography.bodySmall)
    }
    // Keep the full viewport first in the short landscape panel; text remains scrollable below it.
    val configuration = LocalConfiguration.current
    val landscape = configuration.screenWidthDp > configuration.screenHeightDp
    if (!landscape) SeatSummary()
    val description=stringResource(R.string.simulation_scene_description,chosen.label(),summary,
        point.sun?.azimuth?.toInt() ?: 0,point.sun?.altitude?.toInt() ?: 0)
    var toggle by remember { mutableStateOf(IntSize.Zero) }
    Box {
        CabinScene(profile,light,chosen.id,state.interior,description,preview,toggle)
        Surface(Modifier.align(Alignment.TopEnd).onSizeChanged { toggle=it },color=SunSeatTheme.scheme.surface,shape=MaterialTheme.shapes.small) {
            QuietButton(onClick={actions.camera(!state.interior)}) { Text(stringResource(if(state.interior) R.string.simulation_overview else R.string.simulation_inside)) }
        }
    }
    if (landscape) SeatSummary()
    if(profile.kind in BusModels.kinds) BusModelRow(point,mode,profile,manual,state,actions)
    recommended?.let { Text(stringResource(R.string.simulation_recommended_seat,
        if(point.leg.mode==Mode.CAR) it.label() else point.spot!!.label()),style=MaterialTheme.typography.bodySmall) }
    light.cells.filter { it.seatId==chosen.id && it.window!=null }.groupBy { it.window }.forEach { (window,cells) ->
        Text(stringResource(R.string.simulation_window_angle,stringResource(when(window!!) {
            CabinWindow.LEFT -> R.string.simulation_window_left
            CabinWindow.RIGHT -> R.string.simulation_window_right
            CabinWindow.FRONT -> R.string.simulation_window_front
            CabinWindow.REAR -> R.string.simulation_window_rear
        }),cells.first().incidence!!.toInt()),style=MaterialTheme.typography.bodySmall)
    }
    Text(stringResource(R.string.simulation_cabin_note),style=MaterialTheme.typography.bodySmall,color=SunSeatTheme.scheme.onSurfaceVariant)
    }
}

/** #76: which standard bus model is shown, why (always an estimate from the route category or the user's choice), and how to change it. */
@Composable
private fun BusModelRow(point: TimelinePoint, mode: TravelMode, profile: CabinProfile, manual: CabinKind?, state: SimulationUiState, actions: SimulationActions) {
    val estimate=BusModels.estimate(point.leg.busType)
    var open by remember(point.legIndex) { mutableStateOf(false) }
    @Composable fun Note(text: String) = Text(text,style=MaterialTheme.typography.bodySmall,color=SunSeatTheme.scheme.onSurfaceVariant)
    Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(Spacing.s)) {
        Column(Modifier.weight(1f)) {
            Text(stringResource(if(manual!=null) R.string.simulation_bus_model_manual else R.string.simulation_bus_model_estimated,
                stringResource(if(profile.kind==CabinKind.COACH) R.string.simulation_coach else R.string.simulation_bus)),
                style=MaterialTheme.typography.bodyMedium)
            Note(if(manual!=null) stringResource(R.string.simulation_bus_manual_scope) else when(estimate.basis) {
                BusModelBasis.NO_TYPE -> stringResource(R.string.simulation_bus_basis_none)
                BusModelBasis.CITY_TYPE -> stringResource(R.string.simulation_bus_basis_city,estimate.type!!)
                BusModelBasis.COACH_TYPE -> stringResource(R.string.simulation_bus_basis_coach,estimate.type!!)
                BusModelBasis.UNKNOWN_TYPE -> stringResource(R.string.simulation_bus_basis_unknown,estimate.type!!)
            })
        }
        Box {
            QuietButton(onClick={open=true}) { Text(stringResource(R.string.simulation_change_model)) }
            DropdownMenu(expanded=open,onDismissRequest={open=false}) {
                BusModels.kinds.forEach { kind -> DropdownMenuItem(
                    text={Text(stringResource(if(kind==CabinKind.COACH) R.string.simulation_model_coach_option else R.string.simulation_model_city_option))},
                    modifier=Modifier.semantics { selected=kind==profile.kind },
                    leadingIcon=if(kind==profile.kind) ({ Icon(painterResource(R.drawable.ic_ms_check),null) }) else null,
                    onClick={
                        open=false
                        if(kind!=profile.kind) {
                            val target=CabinProfile.forLeg(point.leg,mode,kind)!!
                            actions.busModel(point.legIndex,kind,profile.carry(target,state.seats[point.legIndex]))
                        }
                    }) }
            }
        }
    }
    state.notice?.takeIf { it.leg==point.legIndex }?.carry?.let { carry ->
        val from=carry.from!!.label()
        Text(stringResource(if(carry.carried) R.string.simulation_seat_carried else R.string.simulation_seat_not_carried,from),style=MaterialTheme.typography.bodySmall)
    }
    Note(stringResource(if(profile.kind==CabinKind.COACH) R.string.simulation_coach_note else R.string.simulation_bus_note))
}

private fun coverageText(count: Int): Int = when(count) {
    0 -> R.string.simulation_surface_shade
    CabinSimulation.GRID*CabinSimulation.GRID -> R.string.simulation_surface_full
    else -> R.string.simulation_surface_partial
}

@Composable
private fun CabinSeat.label(): String {
    val car=spot.carSeat
    val name=if(car!=null) stringResource(when(car) {
        CarSeat.PASSENGER -> R.string.simulation_passenger
        CarSeat.REAR_LEFT -> R.string.simulation_rear_left
        CarSeat.REAR_RIGHT -> R.string.simulation_rear_right
    }) else spot.label()
    if(car!=null) return name
    if(number>0) {
        // Bus models (#76): model numbers, plus the facing when it is not forward (city bus aisle-facing seats).
        val facingName=if(abs(facing.z)<.5) stringResource(R.string.seat_separator)+stringResource(R.string.simulation_side_facing) else ""
        return stringResource(R.string.simulation_model_seat,name+facingName,number)
    }
    return stringResource(R.string.simulation_model_seat,name,id.substringAfter('-').substringBefore('-').toIntOrNull()?.plus(1) ?: 1)
}

@Composable
private fun CabinScene(profile: CabinProfile, light: CabinLight, selected: String, interior: Boolean, summary: String, preview: Boolean, overlay: IntSize) {
    val configuration=LocalConfiguration.current
    val height=if(configuration.screenWidthDp>configuration.screenHeightDp) Sizes.cabinLandscapeHeight else Sizes.cabinHeight
    val modifier=Modifier.fillMaxWidth().height(height).semantics { contentDescription=summary }
    if(preview) {
        // Native GPU verification is separate; tests exercise controls and truthful textual fallback.
        Box(modifier) { Text(stringResource(R.string.simulation_cabin_preview)) }
        return
    }
    val context=LocalContext.current
    val owner=LocalLifecycleOwner.current
    var renderer by remember { mutableStateOf<CabinRenderer?>(null) }
    var failed by remember { mutableStateOf(false) }
    var attempt by remember { mutableIntStateOf(0) }
    // Post-processing is disabled: exposure overlays use display-referred token colors.
    fun Color.renderColor() = floatArrayOf(red,green,blue,alpha)
    val scheme=SunSeatTheme.scheme; val colors=SunSeatTheme.colors
    val palette=listOf(scheme.outlineVariant.renderColor(),scheme.onSurfaceVariant.renderColor(),colors.sun.renderColor(),colors.shadeContainer.renderColor(),scheme.inverseSurface.renderColor(),scheme.background.renderColor(),scheme.inverseOnSurface.renderColor())
    key(attempt) {
        if(failed) {
            Column(modifier) {
                Text(stringResource(R.string.simulation_cabin_error))
                QuietButton(onClick={failed=false;attempt++}) { Text(stringResource(R.string.simulation_retry)) }
            }
        } else AndroidView(factory={
            SurfaceView(context).also { surface ->
                try { renderer=CabinRenderer(context,surface) } catch(_: Exception) { failed=true }
                catch(_: LinkageError) { failed=true }
            }
        },modifier=modifier,update={
            try { renderer?.update(profile,light,selected,interior,palette,overlay.width,overlay.height) }
            catch(_: Exception) { failed=true;renderer=null }
        })
    }
    val currentRenderer=renderer
    DisposableEffect(currentRenderer,owner) {
        val current=currentRenderer
        val observer=LifecycleEventObserver { _,_ -> current?.active(owner.lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED)) }
        owner.lifecycle.addObserver(observer)
        current?.active(owner.lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED))
        onDispose { owner.lifecycle.removeObserver(observer); current?.close() }
    }
}
