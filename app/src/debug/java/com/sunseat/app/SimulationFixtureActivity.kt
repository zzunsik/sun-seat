package com.sunseat.app

import android.os.Bundle
import android.content.Intent
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.runtime.CompositionLocalProvider
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
        val type=intent.getStringExtra("mode") ?: "BUS"
        val mode=when(type) { "CAR" -> TravelMode.CAR; "TAXI" -> TravelMode.TAXI; else -> TravelMode.TRANSIT }
        val vehicle=when(type) { "CAR","TAXI" -> Mode.CAR; "SUBWAY" -> Mode.SUBWAY; else -> Mode.BUS }
        val route=Route(listOf(Leg(vehicle,listOf(LatLng(37.5,127.0),LatLng(37.52,127.0),LatLng(37.52,127.03)),Duration.ofMinutes(40),"시작","도착",if(intent.getBooleanExtra("underground",false)) GroundLevel.BELOW else GroundLevel.ABOVE)))
        val departure=Instant.parse(if(intent.getBooleanExtra("night",false)) "2026-06-21T13:00:00Z" else "2026-06-21T00:00:00Z")
        val request=TripRequest(NamedPlace("시작",route.legs.first().path.first()),NamedPlace("도착",route.legs.last().path.last()),mode,null,SunGoal.AVOID)
        val result=TripAdvisor().advise(route,departure,SunGoal.AVOID,travelMode=mode)
        setContent { CompositionLocalProvider(LocalMapRenderer provides MapRenderer.SDK,LocalCabinPreview provides false) {
            SunSeatTheme(darkTheme=intent.getBooleanExtra("dark",false)) { SimulationRoute(request,route,result,{finish()}) }
        } }
    }
}
