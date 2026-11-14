package com.sunseat.app.domain

import java.time.Instant
import org.shredzone.commons.suncalc.SunPosition as SuncalcPosition

/** 태양 위치. 방위각은 북쪽 기준 시계 방향(도, 0 이상 360 미만), 고도는 지평선 기준(도). */
data class SunPosition(val azimuth: Double, val altitude: Double)

/** 위치·시각으로 태양 위치를 구한다. 테스트에서 태양을 고정할 수 있도록 인터페이스로 둔다. */
fun interface SunPositionSource {
    fun positionAt(latitude: Double, longitude: Double, time: Instant): SunPosition
}

/** commons-suncalc로 기기 안에서 오프라인 계산한다. 고도는 대기 굴절을 반영한 겉보기 고도다. */
object SunCalculator : SunPositionSource {
    override fun positionAt(latitude: Double, longitude: Double, time: Instant): SunPosition {
        val p = SuncalcPosition.compute()
            .on(time)
            .at(latitude, longitude)
            .execute()
        val azimuth = ((p.azimuth % 360.0) + 360.0) % 360.0
        return SunPosition(azimuth = azimuth, altitude = p.altitude)
    }
}
