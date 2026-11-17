package com.sunseat.app

import android.os.Bundle
import android.content.Intent
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.core.view.WindowCompat
import androidx.compose.runtime.CompositionLocalProvider
import androidx.lifecycle.createSavedStateHandle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.sunseat.app.domain.*
import com.sunseat.app.ui.map.*
import com.sunseat.app.ui.simulation.*
import com.sunseat.app.ui.theme.SunSeatTheme
import java.time.Duration
import java.time.Instant

/** Synthetic offline GPU fixture. This entry point does not exist in release builds. */
class SimulationFixtureActivity : ComponentActivity() {
    override fun onNewIntent(intent: Intent) { super.onNewIntent(intent); setIntent(intent); recreate() }
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if(intent.getBooleanExtra("landscape",false)) requestedOrientation=android.content.pm.ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE
        enableEdgeToEdge()
        val dark=intent.getBooleanExtra("dark",false)
        WindowCompat.getInsetsController(window,window.decorView).apply {
            isAppearanceLightStatusBars=!dark; isAppearanceLightNavigationBars=!dark
        }
        val type=intent.getStringExtra("mode") ?: "BUS"
        val mode=when(type) { "CAR" -> TravelMode.CAR; "TAXI" -> TravelMode.TAXI; else -> TravelMode.TRANSIT }
        val vehicle=when(type) { "CAR","TAXI" -> Mode.CAR; "SUBWAY" -> Mode.SUBWAY; else -> Mode.BUS }
        val ground=when { intent.getBooleanExtra("underground",false)->GroundLevel.BELOW;intent.getBooleanExtra("unknown",false)->GroundLevel.UNKNOWN;else->GroundLevel.ABOVE }
        val route=Route(listOf(Leg(vehicle,listOf(LatLng(37.5,127.0),LatLng(37.52,127.0),LatLng(37.52,127.03)),Duration.ofMinutes(40),"시작","도착",ground,busType=if(type=="COACH") "광역" else null)))
        val departure=Instant.parse(if(intent.getBooleanExtra("night",false)) "2026-06-21T13:00:00Z" else "2026-06-21T00:00:00Z")
        val request=TripRequest(NamedPlace("시작",route.legs.first().path.first()),NamedPlace("도착",route.legs.last().path.last()),mode,null,SunGoal.AVOID)
        val source=if(intent.hasExtra("altitude")) SunPositionSource { _,_,_ ->
            SunPosition(intent.getFloatExtra("azimuth",90f).toDouble(),intent.getFloatExtra("altitude",20f).toDouble())
        } else SunCalculator
        val advisor=TripAdvisor(ExposureAnalyzer(source))
        val result=advisor.advise(route,departure,SunGoal.AVOID,travelMode=mode)
        val timeline=TripTimeline(route,result,source,advisor)
        setContent { CompositionLocalProvider(LocalMapRenderer provides MapRenderer.SDK,LocalCabinPreview provides false) {
            val vm: SimulationViewModel=viewModel(key="fixture:$type:${intent.extras}",factory=viewModelFactory { initializer {
                val saved=createSavedStateHandle()
                if(!saved.contains("elapsed")) {
                    saved["elapsed"]=intent.getLongExtra("elapsed",0L)
                    saved["interior"]=intent.getBooleanExtra("inside",false)
                    saved["playing"]=intent.getBooleanExtra("playing",false)
                    saved["entered"]=intent.hasExtra("playing") || intent.hasExtra("altitude")
                    intent.getStringExtra("seat")?.let { saved["seats"]=hashMapOf("0" to it) }
                }
                SimulationViewModel(timeline,saved)
            } })
            SunSeatTheme(darkTheme=intent.getBooleanExtra("dark",false)) { SimulationRoute(request,route,result,{finish()},timeline=timeline,viewModel=vm) }
        } }
    }
}
